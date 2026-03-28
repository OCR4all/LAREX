<script setup lang="ts">
import { extractApiErrorMessage } from '@/utils/api-error'

type PreviewConflict = {
  canvasId: string
  conflictType: string
  derivedPageName: string
  existingPageId: string
  existingPageName: string
  existingIiifImage: boolean
  message: string
}

type PreviewCanvas = {
  canvasId: string
  canvasLabel: string
  index: number
  pageName: string
  importable: boolean
  imageUrl: string | null
  estimatedBytes: number | null
  warnings: string[]
  conflict: PreviewConflict | null
}

type ManifestSummary = {
  id: string | null
  sourceUrl: string | null
  sourceType: string | null
  sourceName: string | null
  label: string | null
  provider: string | null
  thumbnailUrl: string | null
  presentationVersion: string | null
}

type PreviewResponse = {
  previewToken: string
  manifest: ManifestSummary
  totalCanvases: number
  importableCanvasCount: number
  estimatedStorageBytes: number
  unknownSizeCanvasCount: number
  warnings: string[]
  canvases: PreviewCanvas[]
}

type JobResult = {
  canvasId: string
  canvasLabel: string
  index: number
  requestedPageName: string
  finalPageName: string
  action: string
  status: string
  pageId: string | null
  message: string
}

type JobResponse = {
  id: string
  status: string
  totalCanvases: number
  processedCanvases: number
  skippedCanvases: number
  failedCanvases: number
  progressPercent: number
  estimatedStorageBytes: number
  manifest: ManifestSummary | null
  warnings: string[]
  results: JobResult[]
  errorMessage: string | null
}

type ResolutionState = {
  action: 'KEEP_EXISTING' | 'RENAME' | 'REPLACE'
  pageName: string
}

const props = defineProps<{
  projectId: string
  workspaceId: string
  onFinished?: (job: JobResponse) => void | Promise<void>
}>()

const emit = defineEmits<{ close: [imported: boolean] }>()

const toast = useToast()

const sourceMode = ref<'url' | 'file'>('url')
const manifestUrl = ref('')
const manifestFile = ref<File | null>(null)
const preview = ref<PreviewResponse | null>(null)
const job = ref<JobResponse | null>(null)
const resolutions = ref<Record<string, ResolutionState>>({})
const isLoadingPreview = ref(false)
const isStartingImport = ref(false)
const isCancelling = ref(false)
const isRefreshingProjectData = ref(false)
const pollTimer = ref<ReturnType<typeof setTimeout> | null>(null)
const hasReportedJobFinished = ref(false)

const TERMINAL_JOB_STATUSES = ['COMPLETED', 'FAILED', 'CANCELLED'] as const

const currentStep = computed<'source' | 'preview' | 'running' | 'done'>(() => {
  if (job.value && isTerminalJobStatus(job.value.status)) return 'done'
  if (job.value) return 'running'
  if (preview.value) return 'preview'
  return 'source'
})

const hasSuccessfulImports = computed(() => (job.value?.processedCanvases ?? 0) > 0)
const hasFailedImports = computed(() => (job.value?.failedCanvases ?? 0) > 0)
const jobProgressValue = computed<number | null>(() => {
  if (!job.value) return 0
  if (job.value.status === 'PENDING' && job.value.progressPercent <= 0) {
    return null
  }
  return job.value.progressPercent
})
const jobOutcomeTone = computed<'success' | 'warning' | 'error'>(() => {
  if (!job.value) return 'success'
  if (job.value.status === 'FAILED') return 'error'
  if (job.value.status === 'CANCELLED') return 'warning'
  if (hasSuccessfulImports.value && hasFailedImports.value) return 'warning'
  if (hasSuccessfulImports.value) return 'success'
  if (hasFailedImports.value) return 'error'
  return 'warning'
})
const jobOutcomeTitle = computed(() => {
  if (!job.value) return 'IIIF import completed'
  if (job.value.status === 'FAILED') return 'IIIF import failed'
  if (job.value.status === 'CANCELLED') return 'IIIF import cancelled'
  if (hasSuccessfulImports.value && hasFailedImports.value) return 'IIIF import completed with failures'
  if (hasSuccessfulImports.value) return 'IIIF import completed'
  if (hasFailedImports.value) return 'IIIF import failed'
  return 'IIIF import completed'
})
const jobOutcomeDescription = computed(() => {
  if (!job.value) return undefined
  if (job.value.errorMessage?.trim()) return job.value.errorMessage

  const failedResults = job.value.results.filter(result => result.status === 'FAILED' && result.message.trim())
  if (failedResults.length > 0) {
    const firstMessage = failedResults[0]?.message.trim()
    if (failedResults.length === 1) {
      return firstMessage
    }
    return `${failedResults.length} canvases failed. First error: ${firstMessage}`
  }

  if (job.value.status === 'CANCELLED' && hasSuccessfulImports.value) {
    return `Imported ${job.value.processedCanvases} ${job.value.processedCanvases === 1 ? 'page' : 'pages'} before cancellation.`
  }

  if (hasSuccessfulImports.value && job.value.skippedCanvases > 0) {
    return `Imported ${job.value.processedCanvases} ${job.value.processedCanvases === 1 ? 'page' : 'pages'} and skipped ${job.value.skippedCanvases}.`
  }

  return undefined
})

const conflictCanvases = computed(() => (preview.value?.canvases ?? []).filter(canvas => canvas.conflict))
const importableCanvases = computed(() => (preview.value?.canvases ?? []).filter(canvas => canvas.importable))
const hasImportableCanvases = computed(() => importableCanvases.value.length > 0)

watch(conflictCanvases, (items) => {
  const next: Record<string, ResolutionState> = {}
  for (const canvas of items) {
    next[canvas.canvasId] = {
      action: 'KEEP_EXISTING',
      pageName: `${canvas.pageName}-copy`
    }
  }
  resolutions.value = next
}, { immediate: true })

onBeforeUnmount(() => {
  stopPolling()
})

function stopPolling() {
  if (pollTimer.value) {
    clearTimeout(pollTimer.value)
    pollTimer.value = null
  }
}

function isTerminalJobStatus(status: string | null | undefined): status is typeof TERMINAL_JOB_STATUSES[number] {
  return TERMINAL_JOB_STATUSES.includes((status ?? '') as typeof TERMINAL_JOB_STATUSES[number])
}

function formatBytes(bytes: number | null | undefined): string {
  if (!bytes || bytes <= 0) return '0 B'
  const units = ['B', 'KB', 'MB', 'GB', 'TB']
  let value = bytes
  let unit = 0
  while (value >= 1024 && unit < units.length - 1) {
    value /= 1024
    unit++
  }
  return `${value.toFixed(1)} ${units[unit]}`
}

async function parseErrorResponse(response: Response, fallback: string): Promise<string> {
  try {
    const data = await response.json()
    return extractApiErrorMessage({ data, statusCode: response.status }, fallback)
  } catch {
    const text = await response.text().catch(() => '')
    return text || fallback
  }
}

async function requestPreview() {
  isLoadingPreview.value = true
  try {
    let data: PreviewResponse
    if (sourceMode.value === 'url') {
      data = await $fetch<PreviewResponse>(`/api/workspaces/${props.workspaceId}/projects/${props.projectId}/iiif-import/preview`, {
        method: 'POST',
        body: { manifestUrl: manifestUrl.value.trim() }
      })
    } else {
      if (!manifestFile.value) {
        throw new Error('Choose a manifest file first.')
      }
      const form = new FormData()
      form.append('file', manifestFile.value)
      const response = await fetch(`/api/upload-proxy/workspaces/${props.workspaceId}/projects/${props.projectId}/iiif-import/preview`, {
        method: 'POST',
        body: form
      })
      if (!response.ok) {
        throw new Error(await parseErrorResponse(response, 'Failed to preview IIIF manifest'))
      }
      data = await response.json() as PreviewResponse
    }

    preview.value = data
    job.value = null
  } catch (error: unknown) {
    toast.add({
      title: 'Preview failed',
      description: extractApiErrorMessage(error, 'Failed to preview IIIF manifest'),
      color: 'error'
    })
  } finally {
    isLoadingPreview.value = false
  }
}

async function startImport() {
  if (!preview.value) return
  isStartingImport.value = true
  hasReportedJobFinished.value = false
  try {
    const response = await $fetch<JobResponse>(`/api/workspaces/${props.workspaceId}/projects/${props.projectId}/iiif-import/jobs`, {
      method: 'POST',
      body: {
        previewToken: preview.value.previewToken,
        resolutions: conflictCanvases.value.map(canvas => ({
          canvasId: canvas.canvasId,
          action: resolutions.value[canvas.canvasId]?.action ?? 'KEEP_EXISTING',
          pageName: resolutions.value[canvas.canvasId]?.pageName ?? canvas.pageName
        }))
      }
    })
    job.value = response
    if (isTerminalJobStatus(response.status)) {
      stopPolling()
      await handleFinishedJob(response)
    } else {
      schedulePoll()
    }
  } catch (error: unknown) {
    toast.add({
      title: 'Import failed to start',
      description: extractApiErrorMessage(error, 'Failed to start IIIF import'),
      color: 'error'
    })
  } finally {
    isStartingImport.value = false
  }
}

function schedulePoll() {
  stopPolling()
  if (!job.value || isTerminalJobStatus(job.value.status)) {
    return
  }
  pollTimer.value = setTimeout(() => {
    void refreshJob()
  }, 750)
}

async function handleFinishedJob(latest: JobResponse) {
  if (hasReportedJobFinished.value) return
  hasReportedJobFinished.value = true

  if (!props.onFinished || latest.processedCanvases <= 0) {
    return
  }

  isRefreshingProjectData.value = true
  try {
    await props.onFinished(latest)
  } catch (error: unknown) {
    toast.add({
      title: 'Refresh failed',
      description: extractApiErrorMessage(error, 'Imported pages but failed to refresh project data'),
      color: 'warning'
    })
  } finally {
    isRefreshingProjectData.value = false
  }
}

async function refreshJob() {
  if (!job.value) return
  try {
    const latest = await $fetch<JobResponse>(`/api/workspaces/${props.workspaceId}/projects/${props.projectId}/iiif-import/jobs/${job.value.id}`)
    job.value = latest
    if (isTerminalJobStatus(latest.status)) {
      stopPolling()
      await handleFinishedJob(latest)
    } else {
      schedulePoll()
    }
  } catch (error: unknown) {
    stopPolling()
    toast.add({
      title: 'Status refresh failed',
      description: extractApiErrorMessage(error, 'Failed to refresh IIIF import status'),
      color: 'error'
    })
  }
}

async function cancelImport() {
  if (!job.value) return
  isCancelling.value = true
  try {
    job.value = await $fetch<JobResponse>(`/api/workspaces/${props.workspaceId}/projects/${props.projectId}/iiif-import/jobs/${job.value.id}`, {
      method: 'DELETE'
    })
    stopPolling()
    if (job.value && isTerminalJobStatus(job.value.status)) {
      await handleFinishedJob(job.value)
    }
  } catch (error: unknown) {
    toast.add({
      title: 'Cancel failed',
      description: extractApiErrorMessage(error, 'Failed to cancel IIIF import'),
      color: 'error'
    })
  } finally {
    isCancelling.value = false
  }
}

function resetPreview() {
  preview.value = null
  job.value = null
  hasReportedJobFinished.value = false
  stopPolling()
}

function handleFileChange(event: Event) {
  const input = event.target as HTMLInputElement
  manifestFile.value = input.files?.[0] ?? null
}

async function close(imported: boolean) {
  stopPolling()
  emit('close', imported)
}

function shouldRefreshProjectPages(): boolean {
  if (!job.value) return false
  return job.value.processedCanvases > 0
}
</script>

<template>
  <USlideover
    side="right"
    title="Import IIIF"
    :close="{ onClick: () => close(shouldRefreshProjectPages()) }"
  >
    <template #body>
      <div class="space-y-5">
        <template v-if="currentStep === 'source'">
          <div class="space-y-2">
            <p class="text-sm text-muted">
              Import a public IIIF Presentation manifest into this project. Each importable canvas becomes a page with one local `iiif` image variant.
            </p>
            <div class="flex gap-2">
              <UButton
                :variant="sourceMode === 'url' ? 'solid' : 'outline'"
                color="neutral"
                @click="sourceMode = 'url'"
              >
                Manifest URL
              </UButton>
              <UButton
                :variant="sourceMode === 'file' ? 'solid' : 'outline'"
                color="neutral"
                @click="sourceMode = 'file'"
              >
                Manifest File
              </UButton>
            </div>
          </div>

          <div v-if="sourceMode === 'url'" class="space-y-2">
            <UFormField label="Manifest URL" required>
              <UInput
                v-model="manifestUrl"
                placeholder="https://example.org/iiif/manifest.json"
              />
            </UFormField>
          </div>

          <div v-else class="space-y-2">
            <UFormField label="Manifest File" required>
              <input
                type="file"
                accept=".json,application/json,application/ld+json"
                class="block w-full text-sm"
                @change="handleFileChange"
              >
            </UFormField>
            <p v-if="manifestFile" class="text-xs text-muted">
              {{ manifestFile.name }}
            </p>
          </div>
        </template>

        <template v-else-if="currentStep === 'preview' && preview">
          <UAlert
            icon="i-lucide-info"
            color="info"
            variant="subtle"
            :title="preview.manifest.label || 'IIIF manifest'"
            :description="preview.manifest.provider || undefined"
          />

          <div class="grid grid-cols-2 gap-3 text-sm">
            <div class="rounded-sm border border-default p-3">
              <div class="text-muted">
                Presentation
              </div>
              <div class="font-medium">
                v{{ preview.manifest.presentationVersion || '?' }}
              </div>
            </div>
            <div class="rounded-sm border border-default p-3">
              <div class="text-muted">
                Estimated Storage
              </div>
              <div class="font-medium">
                {{ formatBytes(preview.estimatedStorageBytes) }}
              </div>
            </div>
            <div class="rounded-sm border border-default p-3">
              <div class="text-muted">
                Canvases
              </div>
              <div class="font-medium">
                {{ preview.totalCanvases }}
              </div>
            </div>
            <div class="rounded-sm border border-default p-3">
              <div class="text-muted">
                Importable
              </div>
              <div class="font-medium">
                {{ preview.importableCanvasCount }}
              </div>
            </div>
          </div>

          <UAlert
            v-if="preview.warnings.length > 0"
            icon="i-lucide-triangle-alert"
            color="warning"
            variant="subtle"
            title="Preview warnings"
            :description="preview.warnings.join(' ')"
          />

          <div v-if="conflictCanvases.length > 0" class="space-y-3">
            <div class="text-sm font-medium">
              Resolve conflicts
            </div>
            <div
              v-for="canvas in conflictCanvases"
              :key="canvas.canvasId"
              class="rounded-sm border border-default p-3 space-y-3"
            >
              <div>
                <div class="font-medium">
                  {{ canvas.canvasLabel }}
                </div>
                <div class="text-xs text-muted">
                  Existing page: {{ canvas.conflict?.existingPageName }}
                </div>
                <div class="text-xs text-muted">
                  {{ canvas.conflict?.message }}
                </div>
              </div>

              <URadioGroup
                v-model="resolutions[canvas.canvasId].action"
                :items="[
                  { label: 'Skip', value: 'KEEP_EXISTING' },
                  { label: 'Rename', value: 'RENAME' },
                  { label: 'Replace IIIF Image', value: 'REPLACE' }
                ]"
              />

              <UFormField
                v-if="resolutions[canvas.canvasId]?.action === 'RENAME'"
                label="New page name"
              >
                <UInput v-model="resolutions[canvas.canvasId].pageName" />
              </UFormField>
            </div>
          </div>

          <div class="space-y-2">
            <div class="text-sm font-medium">
              Canvas preview
            </div>
            <div class="max-h-80 overflow-y-auto space-y-2">
              <div
                v-for="canvas in preview.canvases"
                :key="canvas.canvasId"
                class="rounded-sm border border-default p-3"
              >
                <div class="flex items-start justify-between gap-3">
                  <div>
                    <div class="font-medium">
                      {{ canvas.canvasLabel }}
                    </div>
                    <div class="text-xs text-muted">
                      Page: {{ canvas.pageName }}
                    </div>
                    <div class="text-xs text-muted">
                      Size: {{ formatBytes(canvas.estimatedBytes) }}
                    </div>
                  </div>
                  <UBadge :color="canvas.importable ? 'success' : 'warning'" variant="subtle">
                    {{ canvas.importable ? 'Importable' : 'Skipped' }}
                  </UBadge>
                </div>
                <div v-if="canvas.warnings.length > 0" class="mt-2 text-xs text-warning">
                  {{ canvas.warnings.join(' ') }}
                </div>
              </div>
            </div>
          </div>
        </template>

        <template v-else-if="currentStep === 'running' && job">
          <UAlert
            color="info"
            variant="subtle"
            title="IIIF import running"
            :description="job.manifest?.label || undefined"
          >
            <template #icon>
              <UIcon name="i-lucide-loader-circle" class="size-5 animate-spin" />
            </template>
          </UAlert>

          <div class="space-y-2">
            <div class="flex justify-between text-sm">
              <span>{{ jobProgressValue === null ? 'Preparing import' : 'Import progress' }}</span>
              <span>
                {{ job.processedCanvases + job.skippedCanvases + job.failedCanvases }} / {{ job.totalCanvases }}
                <template v-if="jobProgressValue !== null">
                  ({{ job.progressPercent }}%)
                </template>
              </span>
            </div>
            <UProgress
              :model-value="jobProgressValue"
            />
          </div>

          <p v-if="isRefreshingProjectData" class="text-xs text-muted">
            Refreshing project data…
          </p>

          <div class="grid grid-cols-3 gap-3 text-sm">
            <div class="rounded-sm border border-default p-3">
              <div class="text-muted">
                Imported
              </div>
              <div class="font-medium">
                {{ job.processedCanvases }}
              </div>
            </div>
            <div class="rounded-sm border border-default p-3">
              <div class="text-muted">
                Skipped
              </div>
              <div class="font-medium">
                {{ job.skippedCanvases }}
              </div>
            </div>
            <div class="rounded-sm border border-default p-3">
              <div class="text-muted">
                Failed
              </div>
              <div class="font-medium">
                {{ job.failedCanvases }}
              </div>
            </div>
          </div>
        </template>

        <template v-else-if="currentStep === 'done' && job">
          <UAlert
            :icon="jobOutcomeTone === 'success' ? 'i-lucide-check-circle' : jobOutcomeTone === 'warning' ? 'i-lucide-triangle-alert' : 'i-lucide-alert-circle'"
            :color="jobOutcomeTone"
            variant="subtle"
            :title="jobOutcomeTitle"
            :description="jobOutcomeDescription"
          />

          <div class="grid grid-cols-3 gap-3 text-sm">
            <div class="rounded-sm border border-default p-3">
              <div class="text-muted">
                Imported
              </div>
              <div class="font-medium">
                {{ job.processedCanvases }}
              </div>
            </div>
            <div class="rounded-sm border border-default p-3">
              <div class="text-muted">
                Skipped
              </div>
              <div class="font-medium">
                {{ job.skippedCanvases }}
              </div>
            </div>
            <div class="rounded-sm border border-default p-3">
              <div class="text-muted">
                Failed
              </div>
              <div class="font-medium">
                {{ job.failedCanvases }}
              </div>
            </div>
          </div>

          <div v-if="job.results.length > 0" class="space-y-2">
            <div class="text-sm font-medium">
              Results
            </div>
            <div class="max-h-96 overflow-y-auto space-y-2">
              <div
                v-for="result in job.results"
                :key="`${result.canvasId}-${result.status}`"
                class="rounded-sm border border-default p-3"
              >
                <div class="flex items-start justify-between gap-3">
                  <div>
                    <div class="font-medium">
                      {{ result.canvasLabel }}
                    </div>
                    <div class="text-xs text-muted">
                      {{ result.finalPageName || result.requestedPageName }}
                    </div>
                    <div class="text-xs text-muted">
                      {{ result.message }}
                    </div>
                  </div>
                  <UBadge
                    :color="result.status === 'IMPORTED' ? 'success' : result.status === 'SKIPPED' ? 'warning' : 'error'"
                    variant="subtle"
                  >
                    {{ result.status }}
                  </UBadge>
                </div>
              </div>
            </div>
          </div>
        </template>
      </div>
    </template>

    <template #footer>
      <div class="flex justify-end gap-2">
        <UButton
          v-if="currentStep === 'source'"
          color="neutral"
          variant="ghost"
          @click="close(false)"
        >
          Cancel
        </UButton>

        <UButton
          v-if="currentStep === 'preview'"
          color="neutral"
          variant="ghost"
          @click="resetPreview"
        >
          Back
        </UButton>

        <UButton
          v-if="currentStep === 'running'"
          color="warning"
          variant="ghost"
          :loading="isCancelling"
          @click="cancelImport"
        >
          Cancel Import
        </UButton>

        <UButton
          v-if="currentStep === 'done'"
          color="primary"
          variant="solid"
          @click="close(shouldRefreshProjectPages())"
        >
          Close
        </UButton>

        <UButton
          v-if="currentStep === 'source'"
          color="primary"
          variant="solid"
          :loading="isLoadingPreview"
          :disabled="(sourceMode === 'url' && !manifestUrl.trim()) || (sourceMode === 'file' && !manifestFile)"
          @click="requestPreview"
        >
          Preview Import
        </UButton>

        <UButton
          v-if="currentStep === 'preview'"
          color="primary"
          variant="solid"
          :loading="isStartingImport"
          :disabled="!hasImportableCanvases"
          @click="startImport"
        >
          Start Import
        </UButton>
      </div>
    </template>
  </USlideover>
</template>
