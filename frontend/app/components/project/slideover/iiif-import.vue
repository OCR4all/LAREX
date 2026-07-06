<script setup lang="ts">
import { useVirtualizer, type VirtualItem } from '@tanstack/vue-virtual'

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
  thumbnailUrl: string | null
  estimatedBytes: number | null
  warnings: string[]
  sourceManifestLabel: string | null
  conflict: PreviewConflict | null
}

type ManifestSummary = {
  id: string | null
  sourceUrl: string | null
  sourceType: string | null
  sourceName: string | null
  resourceType: string | null
  label: string | null
  provider: string | null
  thumbnailUrl: string | null
  presentationVersion: string | null
  manifestCount: number
}

type PreviewJobResponse = {
  id: string
  status: string
  phase: string | null
  previewToken: string | null
  manifest: ManifestSummary | null
  totalCanvases: number
  importableCanvasCount: number
  processedCanvases: number
  progressPercent: number
  estimatedStorageBytes: number
  unknownSizeCanvasCount: number
  warnings: string[]
  canvases: PreviewCanvas[]
  errorMessage: string | null
  created: string
  updated: string
  completedAt: string | null
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

const formId = useId()
const { uploadFormDataWithProgress } = useTrackedUpload()
const toast = useToast()

const sourceMode = ref<'url' | 'file'>('url')
const manifestUrl = ref('')
const manifestFile = ref<File | null>(null)
const preview = ref<PreviewJobResponse | null>(null)
const job = ref<JobResponse | null>(null)
const resolutions = ref<Record<string, ResolutionState>>({})

function ensureResolutionState(canvasId: string): ResolutionState {
  const existing = resolutions.value[canvasId]
  if (existing) return existing

  const nextState: ResolutionState = {
    action: 'KEEP_EXISTING',
    pageName: ''
  }
  resolutions.value[canvasId] = nextState
  return nextState
}
const selectedCanvasIds = ref<string[]>([])
const rangeStart = ref<number | null>(null)
const rangeEnd = ref<number | null>(null)
const isLoadingPreview = ref(false)
const isStartingImport = ref(false)
const isCancelling = ref(false)
const isRetryingFailedImport = ref(false)
const isRefreshingProjectData = ref(false)
const pollTimer = ref<ReturnType<typeof setTimeout> | null>(null)
const previewPollTimer = ref<ReturnType<typeof setTimeout> | null>(null)
const hasReportedJobFinished = ref(false)
const previewScrollerRef = ref<HTMLElement | null>(null)

const TERMINAL_JOB_STATUSES = ['COMPLETED', 'FAILED', 'CANCELLED'] as const

const currentStep = computed<'source' | 'preview' | 'running' | 'done'>(() => {
  if (job.value && isTerminalJobStatus(job.value.status)) return 'done'
  if (job.value) return 'running'
  if (preview.value) return 'preview'
  return 'source'
})
const isPreviewReady = computed(() => preview.value?.status === 'COMPLETED')
const isPreviewRunning = computed(() => preview.value?.status === 'PENDING' || preview.value?.status === 'RUNNING')
const previewProgressValue = computed<number | null>(() => {
  if (!preview.value) return 0
  if (!isPreviewReady.value && preview.value.progressPercent <= 0) {
    return null
  }
  return preview.value.progressPercent
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
const selectedConflictCanvases = computed(() => conflictCanvases.value.filter(canvas => selectedCanvasIds.value.includes(canvas.canvasId)))
const selectedImportableCanvasCount = computed(() => selectedCanvasIds.value.length)
const isCollectionPreview = computed(() => preview.value?.manifest?.resourceType === 'COLLECTION')
const previewRowVirtualizer = useVirtualizer<HTMLElement, HTMLElement>(computed(() => ({
  count: preview.value?.canvases.length ?? 0,
  getScrollElement: () => previewScrollerRef.value,
  estimateSize: () => 116,
  overscan: 3,
  getItemKey: index => preview.value?.canvases[index]?.canvasId ?? index
})))
const virtualPreviewRows = computed<Array<{ item: VirtualItem, canvas: PreviewCanvas }>>(() => {
  const canvases = preview.value?.canvases ?? []
  return previewRowVirtualizer.value.getVirtualItems().flatMap((item) => {
    const canvas = canvases[item.index]
    return canvas ? [{ item, canvas }] : []
  })
})
const totalPreviewSize = computed(() => previewRowVirtualizer.value.getTotalSize())

watch(() => preview.value?.previewToken, (previewToken) => {
  if (!previewToken) {
    selectedCanvasIds.value = []
    rangeStart.value = null
    rangeEnd.value = null
    return
  }

  const importable = (preview.value?.canvases ?? []).filter(canvas => canvas.importable)
  selectedCanvasIds.value = importable.map(canvas => canvas.canvasId)
  rangeStart.value = importable[0]?.index ?? null
  rangeEnd.value = importable[importable.length - 1]?.index ?? null
}, { immediate: true })

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
  stopPreviewPolling()
})

function stopPolling() {
  if (pollTimer.value) {
    clearTimeout(pollTimer.value)
    pollTimer.value = null
  }
}

function stopPreviewPolling() {
  if (previewPollTimer.value) {
    clearTimeout(previewPollTimer.value)
    previewPollTimer.value = null
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

async function requestPreview() {
  isLoadingPreview.value = true
  stopPreviewPolling()
  try {
    let data: PreviewJobResponse
    if (sourceMode.value === 'url') {
      data = await $fetch<PreviewJobResponse>(`/api/workspaces/${props.workspaceId}/projects/${props.projectId}/iiif-import/preview-jobs`, {
        method: 'POST',
        body: { manifestUrl: manifestUrl.value.trim() }
      })
    } else {
      if (!manifestFile.value) {
        throw new Error('Choose a manifest file first.')
      }
      const form = new FormData()
      form.append('file', manifestFile.value)
      data = await uploadFormDataWithProgress<PreviewJobResponse>({
        title: 'Uploading IIIF manifest',
        workspaceId: props.workspaceId,
        projectId: props.projectId,
        files: [{ file: manifestFile.value }],
        url: `/api/upload-proxy/workspaces/${props.workspaceId}/projects/${props.projectId}/iiif-import/preview-jobs`,
        formData: form
      })
    }

    preview.value = data
    job.value = null
    if (isPreviewRunning.value) {
      schedulePreviewPoll()
    }
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
  if (!preview.value || !preview.value.previewToken) return
  isStartingImport.value = true
  hasReportedJobFinished.value = false
  try {
    const response = await $fetch<JobResponse>(`/api/workspaces/${props.workspaceId}/projects/${props.projectId}/iiif-import/jobs`, {
      method: 'POST',
      body: {
        previewToken: preview.value.previewToken,
        selectedCanvasIds: selectedCanvasIds.value,
        resolutions: selectedConflictCanvases.value.map(canvas => ({
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

async function retryFailedImport() {
  if (!job.value) return
  isRetryingFailedImport.value = true
  hasReportedJobFinished.value = false
  try {
    const response = await $fetch<JobResponse>(`/api/workspaces/${props.workspaceId}/projects/${props.projectId}/iiif-import/jobs/${job.value.id}/retry-failed`, {
      method: 'POST'
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
      title: 'Retry failed',
      description: extractApiErrorMessage(error, 'Failed to retry failed IIIF canvases'),
      color: 'error'
    })
  } finally {
    isRetryingFailedImport.value = false
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

function schedulePreviewPoll() {
  stopPreviewPolling()
  if (!preview.value || isPreviewReady.value || preview.value.status === 'FAILED') {
    return
  }
  previewPollTimer.value = setTimeout(() => {
    void refreshPreviewJob()
  }, 500)
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

async function refreshPreviewJob() {
  if (!preview.value) return
  try {
    const latest = await $fetch<PreviewJobResponse>(`/api/workspaces/${props.workspaceId}/projects/${props.projectId}/iiif-import/preview-jobs/${preview.value.id}`)
    preview.value = latest
    if (latest.status === 'FAILED') {
      stopPreviewPolling()
      return
    }
    if (!isPreviewReady.value) {
      schedulePreviewPoll()
    } else {
      stopPreviewPolling()
    }
  } catch (error: unknown) {
    stopPreviewPolling()
    toast.add({
      title: 'Preview refresh failed',
      description: extractApiErrorMessage(error, 'Failed to refresh IIIF preview status'),
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
  selectedCanvasIds.value = []
  rangeStart.value = null
  rangeEnd.value = null
  hasReportedJobFinished.value = false
  stopPolling()
  stopPreviewPolling()
}

function handleFileChange(event: Event) {
  const input = event.target as HTMLInputElement
  manifestFile.value = input.files?.[0] ?? null
}

async function close(imported: boolean) {
  stopPolling()
  stopPreviewPolling()
  emit('close', imported)
}

function shouldRefreshProjectPages(): boolean {
  if (!job.value) return false
  return job.value.processedCanvases > 0
}

function isCanvasSelected(canvasId: string): boolean {
  return selectedCanvasIds.value.includes(canvasId)
}

function setCanvasSelected(canvasId: string, selected: boolean | 'indeterminate') {
  if (selected === 'indeterminate') return
  const next = new Set(selectedCanvasIds.value)
  if (selected) {
    next.add(canvasId)
  } else {
    next.delete(canvasId)
  }
  selectedCanvasIds.value = orderedSelectedCanvasIds(next)
}

function orderedSelectedCanvasIds(ids: Iterable<string>): string[] {
  const selected = new Set(ids)
  return importableCanvases.value
    .filter(canvas => selected.has(canvas.canvasId))
    .map(canvas => canvas.canvasId)
}

function selectAllImportableCanvases() {
  selectedCanvasIds.value = importableCanvases.value.map(canvas => canvas.canvasId)
  rangeStart.value = importableCanvases.value[0]?.index ?? null
  rangeEnd.value = importableCanvases.value[importableCanvases.value.length - 1]?.index ?? null
}

function clearCanvasSelection() {
  selectedCanvasIds.value = []
}

function applyCanvasRange() {
  if (rangeStart.value == null || rangeEnd.value == null) {
    toast.add({
      title: 'Range required',
      description: 'Choose a start and end canvas index first.',
      color: 'warning'
    })
    return
  }

  const start = Math.min(rangeStart.value, rangeEnd.value)
  const end = Math.max(rangeStart.value, rangeEnd.value)
  selectedCanvasIds.value = importableCanvases.value
    .filter(canvas => canvas.index >= start && canvas.index <= end)
    .map(canvas => canvas.canvasId)
}

function submitCurrentStep() {
  if (currentStep.value === 'source') {
    void requestPreview()
    return
  }
  if (currentStep.value === 'preview' && isPreviewReady.value) {
    void startImport()
  }
}
</script>

<template>
  <UiResponsiveSlideover
    side="right"
    :ui="{ content: 'w-full max-w-[96vw] sm:max-w-[92vw] xl:max-w-[1120px] flex flex-col' }"
    :close="{ onClick: () => close(shouldRefreshProjectPages()) }"
  >
    <template #header>
      <UiSlideoverHeader title="Import IIIF" icon="i-lucide-image-plus" />
    </template>

    <template #body>
      <UForm :id="formId" class="space-y-5" @submit="submitCurrentStep">
        <template v-if="currentStep === 'source'">
          <div class="space-y-2">
            <p class="text-sm text-muted">
              Import a public IIIF Presentation manifest into this project. Each importable canvas becomes a page with one local `iiif` image variant.
            </p>
            <div class="flex gap-2">
              <UButton
                type="button"
                :variant="sourceMode === 'url' ? 'solid' : 'outline'"
                color="neutral"
                @click="sourceMode = 'url'"
              >
                Manifest URL
              </UButton>
              <UButton
                type="button"
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
            :title="preview.manifest?.label || 'IIIF manifest'"
            :description="preview.manifest?.provider || preview.phase || undefined"
          />

          <UAlert
            v-if="preview.errorMessage"
            icon="i-lucide-circle-alert"
            color="error"
            variant="subtle"
            title="Preview failed"
            :description="preview.errorMessage"
          />

          <div v-else-if="isPreviewRunning" class="space-y-2 rounded-sm border border-default p-3">
            <div class="flex items-center justify-between gap-3 text-sm">
              <div class="font-medium">
                {{ preview.phase || 'Preparing preview' }}
              </div>
              <div class="text-muted">
                {{ preview.processedCanvases }} / {{ preview.totalCanvases || '…' }}
              </div>
            </div>
            <UProgress
              :model-value="previewProgressValue"
              :max="100"
            />
          </div>

          <div class="grid grid-cols-2 gap-3 text-sm">
            <div class="rounded-sm border border-default p-3">
              <div class="text-muted">
                Resource
              </div>
              <div class="font-medium">
                {{ preview.manifest?.resourceType === 'COLLECTION' ? 'Collection' : 'Manifest' }}
              </div>
            </div>
            <div class="rounded-sm border border-default p-3">
              <div class="text-muted">
                Presentation
              </div>
              <div class="font-medium">
                v{{ preview.manifest?.presentationVersion || '?' }}
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
            <div v-if="isCollectionPreview" class="rounded-sm border border-default p-3">
              <div class="text-muted">
                Manifests
              </div>
              <div class="font-medium">
                {{ preview.manifest?.manifestCount ?? 0 }}
              </div>
            </div>
            <div class="rounded-sm border border-default p-3">
              <div class="text-muted">
                Selected
              </div>
              <div class="font-medium">
                {{ selectedImportableCanvasCount }}
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

          <div class="space-y-3 rounded-sm border border-default p-3">
            <div class="text-sm font-medium">
              Canvas selection
            </div>
            <div class="flex flex-wrap gap-2">
              <UButton
                type="button"
                color="neutral"
                variant="outline"
                size="sm"
                @click="selectAllImportableCanvases"
              >
                Select all
              </UButton>
              <UButton
                type="button"
                color="neutral"
                variant="outline"
                size="sm"
                @click="clearCanvasSelection"
              >
                Clear
              </UButton>
            </div>
            <div class="grid grid-cols-3 gap-3">
              <UFormField label="Start canvas">
                <UInput v-model="rangeStart" type="number" min="1" />
              </UFormField>
              <UFormField label="End canvas">
                <UInput v-model="rangeEnd" type="number" min="1" />
              </UFormField>
              <UFormField label="Apply range">
                <UButton
                  type="button"
                  color="neutral"
                  variant="outline"
                  class="w-full justify-center"
                  @click="applyCanvasRange"
                >
                  Select range
                </UButton>
              </UFormField>
            </div>
          </div>

          <div v-if="selectedConflictCanvases.length > 0" class="space-y-3">
            <div class="text-sm font-medium">
              Resolve selected conflicts
            </div>
            <div
              v-for="canvas in selectedConflictCanvases"
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
                v-model="ensureResolutionState(canvas.canvasId).action"
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
                <UInput v-model="ensureResolutionState(canvas.canvasId).pageName" />
              </UFormField>
            </div>
          </div>

          <div class="space-y-2">
            <div class="text-sm font-medium">
              Canvas preview
            </div>
            <div ref="previewScrollerRef" class="max-h-[32rem] overflow-y-auto">
              <div
                class="relative w-full"
                :style="{ height: `${totalPreviewSize}px` }"
              >
                <div
                  v-for="{ item, canvas } in virtualPreviewRows"
                  :key="String(item.key)"
                  :data-index="item.index"
                  class="absolute left-0 top-0 w-full pb-2"
                  :style="{ transform: `translateY(${item.start}px)` }"
                >
                  <div class="rounded-sm border border-default p-3">
                    <div class="flex items-start justify-between gap-3">
                      <div class="flex items-start gap-3">
                        <UCheckbox
                          :model-value="isCanvasSelected(canvas.canvasId)"
                          :disabled="!canvas.importable"
                          @update:model-value="setCanvasSelected(canvas.canvasId, $event)"
                        />
                      </div>
                      <div class="min-w-0 flex-1">
                        <div class="font-medium">
                          {{ canvas.canvasLabel }}
                        </div>
                        <div v-if="canvas.sourceManifestLabel" class="text-xs text-muted">
                          Manifest: {{ canvas.sourceManifestLabel }}
                        </div>
                        <div class="text-xs text-muted">
                          Page: {{ canvas.pageName }}
                        </div>
                        <div class="text-xs text-muted">
                          Canvas: {{ canvas.index }}
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
            <div class="h-full overflow-y-auto space-y-2">
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
      </UForm>
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
          v-if="currentStep === 'done' && (job?.failedCanvases ?? 0) > 0"
          color="neutral"
          variant="ghost"
          :loading="isRetryingFailedImport"
          @click="retryFailedImport"
        >
          Retry Failed
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
          type="submit"
          :form="formId"
          color="primary"
          variant="solid"
          :loading="isLoadingPreview"
          :disabled="(sourceMode === 'url' && !manifestUrl.trim()) || (sourceMode === 'file' && !manifestFile)"
        >
          Preview Import
        </UButton>

        <UButton
          v-if="currentStep === 'preview' && isPreviewRunning"
          color="primary"
          variant="solid"
          :loading="true"
          disabled
        >
          Preparing Preview
        </UButton>

        <UButton
          v-if="currentStep === 'preview' && isPreviewReady"
          type="submit"
          :form="formId"
          color="primary"
          variant="solid"
          :loading="isStartingImport"
          :disabled="!hasImportableCanvases || selectedImportableCanvasCount === 0"
        >
          Start Import
        </UButton>
      </div>
    </template>
  </UiResponsiveSlideover>
</template>
