<script setup lang="ts">
import { getWorkspaceDisplayName, getWorkspaceSecondaryLabel } from '@/utils/workspace-display'

definePageMeta({ layout: 'admin', middleware: 'admin' })

const toast = useToast()
const realtime = useRealtimeSocket()

const jobType = ref<'DUMP' | 'VERIFY' | 'RESEED'>('DUMP')
const sourcePath = ref('')
const workspaceMappingRaw = ref('')
const dumpWorkspaceId = ref('all')

const sourceValidation = ref<{ valid: boolean, normalizedPath?: string | null, errorMessage?: string | null } | null>(null)

const isStarting = ref(false)
const selectedJobId = ref<string | null>(null)
const requiresSource = computed(() => jobType.value !== 'DUMP')

interface BackupJobSummary {
  id: string
  type: 'DUMP' | 'VERIFY' | 'RESEED'
  status: 'PENDING' | 'RUNNING' | 'COMPLETED' | 'FAILED' | 'CANCELLED'
  progressPercent: number
  currentStep?: string
  created: string
  completedAt?: string | null
}

interface BackupJobDetail extends BackupJobSummary {
  sourcePath?: string | null
  outputPath?: string | null
  processedItems: number
  totalItems: number
  errorMessage?: string | null
  resultPath?: string | null
  warnings: string[]
}

interface AdminWorkspace {
  id: string
  name: string
  isPersonal: boolean
  ownerUserId: string
  ownerUsername?: string
}

const { data: workspaces } = await useFetch<AdminWorkspace[]>('/api/admin/workspaces', {
  key: globalKey('admin', 'workspaces', 'all'),
  default: () => []
})

const dumpScopeItems = computed(() => [
  { label: 'All workspaces', value: 'all' },
  ...workspaces.value
    .map(workspace => ({
      label: [
        getWorkspaceDisplayName(workspace),
        getWorkspaceSecondaryLabel(workspace)
      ].filter(Boolean).join(' · '),
      value: workspace.id
    }))
    .sort((a, b) => a.label.localeCompare(b.label))
])

const { data: jobs, refresh: refreshJobs, pending: jobsPending } = await useFetch<BackupJobSummary[]>('/api/admin/backup/jobs', {
  key: 'admin-backup-jobs',
  default: () => []
})

const { data: selectedJobDetail, refresh: refreshSelectedJob } = await useFetch<BackupJobDetail>(
  () => selectedJobId.value ? `/api/admin/backup/jobs/${selectedJobId.value}` : '/api/admin/backup/jobs/none',
  {
    key: () => selectedJobId.value ? `admin-backup-job-${selectedJobId.value}` : 'admin-backup-job-none',
    immediate: false,
    watch: [selectedJobId]
  }
)
const hasActiveJobs = computed(() => {
  const selectedStatus = selectedJobDetail.value?.status
  return selectedStatus === 'PENDING'
    || selectedStatus === 'RUNNING'
    || jobs.value.some(job => job.status === 'PENDING' || job.status === 'RUNNING')
})

watch(selectedJobId, async (id) => {
  if (id) {
    await refreshSelectedJob()
  }
})

let pollHandle: NodeJS.Timeout | null = null
let realtimeUnsubscribe: (() => void) | null = null
let realtimeRefreshTimer: ReturnType<typeof setTimeout> | null = null
let lastRealtimeAuditAt = Date.now()

async function refreshBackupData() {
  await refreshJobs()
  if (selectedJobId.value) {
    await refreshSelectedJob()
  }
}

onMounted(() => {
  realtimeUnsubscribe = realtime.subscribe((message) => {
    if (message.type !== 'JOB_UPDATED') return
    const payload = message.payload as { kind?: unknown } | null
    if (payload?.kind !== 'BACKUP' || realtimeRefreshTimer) return
    realtimeRefreshTimer = setTimeout(() => {
      realtimeRefreshTimer = null
      void refreshBackupData()
    }, 50)
  })
  pollHandle = setInterval(() => {
    if (!realtime.isPageVisible.value) return
    const realtimeConnected = realtime.connectionStatus.value === 'connected'
    if (realtimeConnected && !hasActiveJobs.value && Date.now() - lastRealtimeAuditAt < 60_000) return
    if (realtimeConnected) lastRealtimeAuditAt = Date.now()
    void refreshBackupData()
  }, 3000)
})

watch([
  () => realtime.connectionStatus.value,
  () => realtime.isPageVisible.value
], ([status, pageVisible], previous) => {
  const [previousStatus, previouslyVisible] = previous ?? []
  if (!pageVisible) return
  if (previouslyVisible === false || (status !== 'connected' && previousStatus === 'connected')) {
    void refreshBackupData()
  }
})

onUnmounted(() => {
  if (pollHandle) {
    clearInterval(pollHandle)
  }
  if (realtimeRefreshTimer) clearTimeout(realtimeRefreshTimer)
  realtimeUnsubscribe?.()
})

async function validateSourcePath() {
  const path = sourcePath.value
  if (!path?.trim()) {
    sourceValidation.value = null
    return
  }

  try {
    const result = await $fetch<{ valid: boolean, normalizedPath: string | null, errorMessage: string | null }>('/api/admin/backup/validate-path', {
      method: 'POST',
      body: {
        path: path.trim(),
        role: 'SOURCE'
      }
    })

    sourceValidation.value = result
  } catch (error: unknown) {
    const message = error instanceof Error ? error.message : 'Path validation failed'
    const failed = { valid: false, errorMessage: message, normalizedPath: null }
    sourceValidation.value = failed
  }
}

function parseWorkspaceMapping(raw: string): Record<string, string> {
  const result: Record<string, string> = {}
  const trimmed = raw.trim()
  if (!trimmed) return result

  for (const pair of trimmed.split(',')) {
    const [source, target] = pair.split('=').map(part => part?.trim())
    if (source && target) {
      result[source] = target
    }
  }

  return result
}

async function startJob() {
  if (requiresSource.value && !sourcePath.value.trim()) {
    toast.add({ title: 'Source path required', color: 'warning' })
    return
  }

  isStarting.value = true
  try {
    if (requiresSource.value) {
      await validateSourcePath()
    }

    if (requiresSource.value && sourceValidation.value && !sourceValidation.value.valid) {
      throw new Error(sourceValidation.value.errorMessage || 'Invalid source path')
    }

    const payload: Record<string, unknown> = {
      type: jobType.value
    }

    if (requiresSource.value) {
      payload.sourcePath = sourceValidation.value?.normalizedPath || sourcePath.value.trim()
    }
    if (jobType.value === 'RESEED') {
      payload.workspaceMapping = parseWorkspaceMapping(workspaceMappingRaw.value)
    }
    if (jobType.value === 'DUMP' && dumpWorkspaceId.value !== 'all') {
      payload.workspaceId = dumpWorkspaceId.value
    }

    const created = await $fetch<BackupJobDetail>('/api/admin/backup/jobs', {
      method: 'POST',
      body: payload
    })

    selectedJobId.value = created.id
    await refreshJobs()
    await refreshSelectedJob()

    toast.add({
      title: `${jobType.value === 'DUMP' ? 'Dump' : jobType.value === 'VERIFY' ? 'Verification' : 'Reseed'} job started`,
      description: `Job ${created.id} is running in the background.`,
      color: 'success'
    })
  } catch (error: unknown) {
    const message = error instanceof Error ? error.message : 'Failed to start backup job'
    showApiErrorToast({ title: 'Failed to start job', error, fallback: message })
  } finally {
    isStarting.value = false
  }
}

async function cancelJob(jobId: string) {
  try {
    await $fetch(`/api/admin/backup/jobs/${jobId}`, { method: 'DELETE' })
    await refreshJobs()
    if (selectedJobId.value === jobId) {
      await refreshSelectedJob()
    }
    toast.add({ title: 'Job cancelled', color: 'success' })
  } catch (error: unknown) {
    const message = error instanceof Error ? error.message : 'Failed to cancel job'
    showApiErrorToast({ title: 'Cancel failed', error, fallback: message })
  }
}

function statusColor(status: BackupJobSummary['status']) {
  switch (status) {
    case 'COMPLETED': return 'success'
    case 'FAILED': return 'error'
    case 'CANCELLED': return 'neutral'
    case 'RUNNING': return 'primary'
    default: return 'warning'
  }
}
</script>

<template>
  <UDashboardPanel id="admin-backup">
    <template #header>
      <UDashboardNavbar title="Backup & Reseed" />
    </template>

    <template #body>
      <div class="p-6 space-y-6">
        <UCard>
          <template #header>
            <div class="flex items-center gap-2">
              <UIcon name="i-lucide-database-backup" class="text-primary" />
              <span class="font-semibold">Start Backup/Reseed Job</span>
            </div>
          </template>

          <div class="grid gap-4 md:grid-cols-2">
            <UFormField label="Job Type">
              <USelectMenu
                v-model="jobType"
                :items="[
                  { label: 'Create portable dump', value: 'DUMP' },
                  { label: 'Verify portable dump', value: 'VERIFY' },
                  { label: 'Reseed from dump archive', value: 'RESEED' }
                ]"
                value-key="value"
              />
            </UFormField>

            <UFormField
              v-if="jobType === 'DUMP'"
              label="Dump Scope"
              hint="Export every workspace or only the selected workspace"
            >
              <USelectMenu
                v-model="dumpWorkspaceId"
                :items="dumpScopeItems"
                value-key="value"
                :search-input="{ placeholder: 'Search workspaces...' }"
              />
            </UFormField>

            <UFormField
              v-if="requiresSource"
              label="Source Dump Path"
              :error="sourceValidation?.valid === false ? sourceValidation.errorMessage || undefined : undefined"
              class="md:col-span-2"
            >
              <UInput
                v-model="sourcePath"
                placeholder="/mnt/data/backups/larex-dump-20260719-120000.larex-dump.zip"
                :color="sourceValidation?.valid === false ? 'error' : sourceValidation?.valid ? 'success' : 'neutral'"
                @blur="validateSourcePath"
              />
            </UFormField>

            <UFormField
              v-if="jobType === 'RESEED'"
              label="Workspace Mapping"
              hint="Optional: source=target pairs separated by commas"
              class="md:col-span-2"
            >
              <UInput
                v-model="workspaceMappingRaw"
                placeholder="old-workspace-id=new-workspace-id,old2=new2"
              />
            </UFormField>

            <p
              v-if="jobType !== 'VERIFY'"
              class="text-xs text-muted md:col-span-2"
            >
              Output is written to the backup directory configured by the deployment operator.
            </p>
          </div>

          <div class="mt-4">
            <UButton
              :loading="isStarting"
              icon="i-lucide-play"
              @click="startJob"
            >
              Start {{ jobType === 'DUMP' ? 'Dump' : jobType === 'VERIFY' ? 'Verification' : 'Reseed' }} Job
            </UButton>
          </div>
        </UCard>

        <UCard>
          <template #header>
            <div class="flex items-center justify-between gap-2">
              <div class="flex items-center gap-2">
                <UIcon name="i-lucide-list-checks" class="text-primary" />
                <span class="font-semibold">Jobs</span>
              </div>
              <UButton
                color="neutral"
                variant="ghost"
                icon="i-lucide-refresh-cw"
                @click="() => refreshJobs()"
              />
            </div>
          </template>

          <div v-if="jobsPending" class="py-6 text-center text-sm text-muted">
            Loading jobs...
          </div>

          <div v-else-if="!jobs || jobs.length === 0" class="py-6 text-center text-sm text-muted">
            No backup/reseed jobs yet.
          </div>

          <div v-else class="space-y-2">
            <div
              v-for="job in jobs"
              :key="job.id"
              class="border border-(--ui-border) rounded-sm p-3 cursor-pointer hover:bg-(--ui-bg-elevated)"
              :class="selectedJobId === job.id ? 'ring-2 ring-primary' : ''"
              @click="selectedJobId = job.id"
            >
              <div class="flex items-start justify-between gap-3">
                <div class="min-w-0">
                  <div class="flex items-center gap-2">
                    <code class="text-xs">{{ job.id }}</code>
                    <UBadge :color="statusColor(job.status)" variant="soft" size="xs">
                      {{ job.status }}
                    </UBadge>
                    <UBadge variant="subtle" size="xs">
                      {{ job.type }}
                    </UBadge>
                  </div>
                  <p class="text-xs text-muted mt-1 truncate">
                    {{ job.currentStep || 'No step message' }}
                  </p>
                  <UProgress v-model="job.progressPercent" class="mt-2" />
                </div>

                <UButton
                  v-if="job.status === 'RUNNING' || job.status === 'PENDING'"
                  color="error"
                  variant="ghost"
                  size="xs"
                  icon="i-lucide-x"
                  @click.stop="cancelJob(job.id)"
                >
                  Cancel
                </UButton>
              </div>
            </div>
          </div>
        </UCard>

        <UCard v-if="selectedJobDetail">
          <template #header>
            <div class="flex items-center gap-2">
              <UIcon name="i-lucide-info" class="text-primary" />
              <span class="font-semibold">Job Details</span>
            </div>
          </template>

          <div class="space-y-2 text-sm">
            <p><strong>ID:</strong> <code>{{ selectedJobDetail.id }}</code></p>
            <p><strong>Status:</strong> {{ selectedJobDetail.status }}</p>
            <p><strong>Source:</strong> {{ selectedJobDetail.sourcePath || '—' }}</p>
            <p><strong>Output:</strong> {{ selectedJobDetail.outputPath || '—' }}</p>
            <p><strong>Progress:</strong> {{ selectedJobDetail.processedItems }} / {{ selectedJobDetail.totalItems }}</p>
            <p v-if="selectedJobDetail.resultPath">
              <strong>Result path:</strong>
              <code>{{ selectedJobDetail.resultPath }}</code>
            </p>
            <p v-if="selectedJobDetail.errorMessage" class="text-error">
              <strong>Error:</strong>
              {{ selectedJobDetail.errorMessage }}
            </p>
            <div v-if="selectedJobDetail.warnings?.length">
              <p><strong>Warnings:</strong></p>
              <ul class="list-disc pl-5">
                <li v-for="warning in selectedJobDetail.warnings" :key="warning">
                  {{ warning }}
                </li>
              </ul>
            </div>
          </div>
        </UCard>
      </div>
    </template>
  </UDashboardPanel>
</template>
