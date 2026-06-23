<script setup lang="ts">
import { getWorkspaceDisplayName } from '@/utils/workspace-display'

definePageMeta({ layout: 'admin', middleware: 'admin' })

const toast = useToast()

const pathInput = ref('')
const pathValid = ref<boolean | null>(null)
const pathError = ref<string | null>(null)
const normalizedPath = ref<string | null>(null)

const scanResults = ref<ScanResults | null>(null)
const isScanning = ref(false)
const scanError = ref<string | null>(null)

const selectedProjectId = ref<string | undefined>(undefined)
const selectedWorkspaceId = ref<string | undefined>(undefined)
const overwriteExisting = ref(false)
const isImporting = ref(false)

interface ScanFileInfo {
  fileName: string
  relativePath: string
  fileSize: number
  fileType: string
  baseName: string
  variant: string
  hasConflict: boolean
  conflictType?: string
}

interface ScanResults {
  path: string
  imageCount: number
  xmlCount: number
  totalSizeBytes: number
  files: ScanFileInfo[]
  conflicts: string[]
  warnings: string[]
  quotaExceeded: boolean
  availableQuotaBytes: number
}

interface ImportJob {
  id: string
  projectId: string
  workspaceId: string
  sourcePath: string
  status: string
  totalFiles: number
  processedFiles: number
  skippedFiles: number
  failedFiles: number
  totalBytes: number
  progressPercent: number
  errorMessage?: string
  created: string
  completedAt?: string
}

interface AdminWorkspace {
  id: string
  name: string
  isPersonal: boolean
  ownerUserId: string
  ownerUsername?: string | null
}

const { data: workspaces } = await useFetch<AdminWorkspace[]>('/api/admin/workspaces')

const workspaceOptions = computed(() => (workspaces.value ?? []).map(workspace => ({
  label: getWorkspaceDisplayName(workspace),
  value: workspace.id
})))

const { data: projects, refresh: refreshProjects } = await useFetch<{ id: string, name: string }[]>(
  () => selectedWorkspaceId.value ? `/api/workspaces/${selectedWorkspaceId.value}/projects` : '/api/projects',
  {
    key: 'admin-import-projects',
    watch: [selectedWorkspaceId],
    immediate: false
  }
)

const { data: importJobs, refresh: refreshJobs } = await useFetch<ImportJob[]>('/api/admin/import/jobs', {
  key: 'admin-import-jobs'
})

watch(selectedWorkspaceId, () => {
  selectedProjectId.value = undefined
  if (selectedWorkspaceId.value) {
    refreshProjects()
  }
})

async function validatePath() {
  if (!pathInput.value.trim()) {
    pathValid.value = null
    pathError.value = null
    normalizedPath.value = null
    return
  }

  try {
    const result = await $fetch<{ valid: boolean, normalizedPath: string | null, errorMessage: string | null }>(
      '/api/admin/import/validate-path',
      {
        method: 'POST',
        body: { path: pathInput.value.trim() }
      }
    )

    pathValid.value = result.valid
    pathError.value = result.errorMessage
    normalizedPath.value = result.normalizedPath
  } catch (err) {
    pathValid.value = false
    pathError.value = err instanceof Error ? err.message : 'Validation failed'
  }
}

let validateTimeout: NodeJS.Timeout | null = null
watch(pathInput, () => {
  if (validateTimeout) clearTimeout(validateTimeout)
  validateTimeout = setTimeout(validatePath, 500)
})

async function scanDirectory() {
  if (!pathValid.value || !normalizedPath.value) return

  isScanning.value = true
  scanError.value = null
  scanResults.value = null

  try {
    const result = await $fetch<ScanResults>('/api/admin/import/scan', {
      method: 'POST',
      body: { path: normalizedPath.value },
      params: {
        projectId: selectedProjectId.value || undefined,
        workspaceId: selectedWorkspaceId.value || undefined
      }
    })

    scanResults.value = result
  } catch (err) {
    scanError.value = extractApiErrorMessage(err, 'Scan failed')
    showApiErrorToast({
      title: 'Scan Failed',
      error: err,
      fallback: scanError.value
    })
  } finally {
    isScanning.value = false
  }
}

async function startImport() {
  if (!normalizedPath.value || !selectedProjectId.value || !selectedWorkspaceId.value) {
    toast.add({
      title: 'Missing Information',
      description: 'Please select a workspace and project before importing',
      color: 'warning'
    })
    return
  }

  isImporting.value = true

  try {
    await $fetch('/api/admin/import/jobs', {
      method: 'POST',
      body: {
        sourcePath: normalizedPath.value,
        projectId: selectedProjectId.value,
        overwriteExisting: overwriteExisting.value,
        copyMode: 'COPY'
      },
      params: {
        workspaceId: selectedWorkspaceId.value
      }
    })

    toast.add({
      title: 'Import Started',
      description: 'The import job has been queued and will process in the background',
      color: 'success'
    })

    pathInput.value = ''
    pathValid.value = null
    scanResults.value = null
    selectedProjectId.value = undefined
    overwriteExisting.value = false

    await refreshJobs()
  } catch (err) {
    showApiErrorToast({
      title: 'Import Failed',
      error: err,
      fallback: extractApiErrorMessage(err, 'Failed to start import')
    })
  } finally {
    isImporting.value = false
  }
}

async function cancelJob(jobId: string) {
  try {
    await $fetch(`/api/admin/import/jobs/${jobId}`, { method: 'DELETE' })
    toast.add({
      title: 'Job Cancelled',
      description: 'The import job has been cancelled',
      color: 'success'
    })
    await refreshJobs()
  } catch (err) {
    showApiErrorToast({
      title: 'Cancel Failed',
      error: err,
      fallback: err instanceof Error ? err.message : 'Failed to cancel job'
    })
  }
}

function formatBytes(bytes: number): string {
  if (bytes === 0) return '0 B'
  const k = 1024
  const sizes = ['B', 'KB', 'MB', 'GB', 'TB']
  const i = Math.floor(Math.log(bytes) / Math.log(k))
  return `${parseFloat((bytes / Math.pow(k, i)).toFixed(2))} ${sizes[i]}`
}

function getStatusColor(status: string): 'success' | 'error' | 'neutral' | 'primary' | 'warning' {
  switch (status) {
    case 'COMPLETED': return 'success'
    case 'FAILED': return 'error'
    case 'CANCELLED': return 'neutral'
    case 'IMPORTING': return 'primary'
    case 'SCANNING':
    case 'VALIDATING': return 'warning'
    default: return 'neutral'
  }
}
</script>

<template>
  <UDashboardPanel id="admin-import">
    <template #header>
      <UDashboardNavbar title="Server-Side Import" />
    </template>

    <template #body>
      <div class="p-6 space-y-8">
        <UCard>
          <template #header>
            <div class="flex items-center gap-2">
              <UIcon name="i-lucide-folder-input" class="text-primary" />
              <span class="font-semibold">Import from Server Directory</span>
            </div>
          </template>

          <div class="space-y-4">
            <UFormField label="Server Path" hint="Enter the absolute path to the directory containing files to import">
              <UInput
                v-model="pathInput"
                placeholder="/data/imports/my-dataset"
                :color="pathValid === true ? 'success' : pathValid === false ? 'error' : 'neutral'"
                :trailing-icon="pathValid === true ? 'i-lucide-check' : pathValid === false ? 'i-lucide-x' : undefined"
              />
              <template v-if="pathError" #error>
                {{ pathError }}
              </template>
            </UFormField>

            <div class="grid gap-4 sm:grid-cols-2">
              <UFormField label="Workspace">
                <USelectMenu
                  v-model="selectedWorkspaceId"
                  :items="workspaceOptions"
                  placeholder="Select workspace"
                  value-key="value"
                />
              </UFormField>

              <UFormField label="Target Project">
                <USelectMenu
                  v-model="selectedProjectId"
                  :items="projects?.map(p => ({ label: p.name, value: p.id })) || []"
                  placeholder="Select project"
                  value-key="value"
                  :disabled="!selectedWorkspaceId"
                />
              </UFormField>
            </div>

            <div class="flex items-center gap-2">
              <UCheckbox v-model="overwriteExisting" label="Overwrite existing files" />
            </div>

            <UButton
              :disabled="!pathValid || isScanning"
              :loading="isScanning"
              icon="i-lucide-search"
              @click="scanDirectory"
            >
              Scan Directory
            </UButton>
          </div>

          <div v-if="scanResults" class="mt-6 pt-6 border-t border-(--ui-border)">
            <h3 class="font-semibold mb-4">
              Scan Results
            </h3>

            <div class="grid gap-4 sm:grid-cols-4 mb-4">
              <div class="p-3 rounded-sm bg-(--ui-bg-elevated)">
                <div class="text-2xl font-bold">
                  {{ scanResults.imageCount }}
                </div>
                <div class="text-sm text-(--ui-text-muted)">
                  Images
                </div>
              </div>
              <div class="p-3 rounded-sm bg-(--ui-bg-elevated)">
                <div class="text-2xl font-bold">
                  {{ scanResults.xmlCount }}
                </div>
                <div class="text-sm text-(--ui-text-muted)">
                  XML Files
                </div>
              </div>
              <div class="p-3 rounded-sm bg-(--ui-bg-elevated)">
                <div class="text-2xl font-bold">
                  {{ formatBytes(scanResults.totalSizeBytes) }}
                </div>
                <div class="text-sm text-(--ui-text-muted)">
                  Total Size
                </div>
              </div>
              <div class="p-3 rounded-sm bg-(--ui-bg-elevated)">
                <div class="text-2xl font-bold">
                  {{ scanResults.conflicts.length }}
                </div>
                <div class="text-sm text-(--ui-text-muted)">
                  Conflicts
                </div>
              </div>
            </div>

            <div v-if="scanResults.warnings.length > 0" class="mb-4 p-3 rounded-sm bg-(--ui-warning)/10 border border-(--ui-warning)/20">
              <div class="flex items-center gap-2 font-medium text-(--ui-warning) mb-2">
                <UIcon name="i-lucide-alert-triangle" />
                Warnings
              </div>
              <ul class="text-sm space-y-1">
                <li v-for="warning in scanResults.warnings" :key="warning">
                  {{ warning }}
                </li>
              </ul>
            </div>

            <div v-if="scanResults.quotaExceeded" class="mb-4 p-3 rounded-sm bg-(--ui-error)/10 border border-(--ui-error)/20">
              <div class="flex items-center gap-2 font-medium text-(--ui-error)">
                <UIcon name="i-lucide-alert-circle" />
                Storage quota exceeded
              </div>
              <p class="text-sm mt-1">
                Uploads and imports are blocked for this workspace until storage is freed or the quota is increased. Available now: {{ formatBytes(scanResults.availableQuotaBytes) }}
              </p>
            </div>

            <UButton
              :disabled="!selectedProjectId || scanResults.quotaExceeded || isImporting"
              :loading="isImporting"
              icon="i-lucide-download"
              color="primary"
              @click="startImport"
            >
              Start Import
            </UButton>
          </div>
        </UCard>

        <UCard>
          <template #header>
            <div class="flex items-center justify-between">
              <div class="flex items-center gap-2">
                <UIcon name="i-lucide-list" class="text-primary" />
                <span class="font-semibold">Import Jobs</span>
              </div>
              <UButton
                variant="ghost"
                size="xs"
                icon="i-lucide-refresh-cw"
                @click="() => refreshJobs()"
              >
                Refresh
              </UButton>
            </div>
          </template>

          <div v-if="!importJobs || importJobs.length === 0" class="text-center py-8 text-(--ui-text-muted)">
            No import jobs found
          </div>

          <div v-else class="divide-y divide-(--ui-border)">
            <div
              v-for="job in importJobs"
              :key="job.id"
              class="py-4 first:pt-0 last:pb-0"
            >
              <div class="flex items-start justify-between mb-2">
                <div>
                  <div class="font-medium">
                    {{ job.sourcePath }}
                  </div>
                  <div class="text-sm text-(--ui-text-muted)">
                    Project: {{ job.projectId }} | Created: {{ new Date(job.created).toLocaleString() }}
                  </div>
                </div>
                <UBadge :color="getStatusColor(job.status)">
                  {{ job.status }}
                </UBadge>
              </div>

              <div v-if="['SCANNING', 'VALIDATING', 'IMPORTING'].includes(job.status)" class="mb-2">
                <div class="flex justify-between text-sm text-(--ui-text-muted) mb-1">
                  <span>{{ job.processedFiles }} / {{ job.totalFiles }} files</span>
                  <span>{{ job.progressPercent }}%</span>
                </div>
                <UProgress v-model="job.progressPercent" size="sm" />
              </div>

              <div v-if="job.errorMessage" class="text-sm text-(--ui-error) mb-2">
                {{ job.errorMessage }}
              </div>

              <div v-if="job.status === 'COMPLETED'" class="text-sm text-(--ui-text-muted)">
                Completed at {{ new Date(job.completedAt!).toLocaleString() }} |
                {{ job.processedFiles }} processed, {{ job.skippedFiles }} skipped, {{ job.failedFiles }} failed
              </div>

              <div v-if="['PENDING', 'SCANNING', 'VALIDATING', 'IMPORTING'].includes(job.status)" class="mt-2">
                <UButton
                  variant="ghost"
                  size="xs"
                  color="error"
                  icon="i-lucide-x"
                  @click="cancelJob(job.id)"
                >
                  Cancel
                </UButton>
              </div>
            </div>
          </div>
        </UCard>
      </div>
    </template>
  </UDashboardPanel>
</template>
