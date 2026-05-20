<script setup lang="ts">
import type { ActiveUpload } from '@/stores/upload.store'
import type { TrackedActionRun } from '@/stores/action-runs.store'

type JobKind = 'upload' | 'action'
type JobStatusColor = 'primary' | 'success' | 'error' | 'warning' | 'neutral'

type StatusJob =
  | {
      kind: 'upload'
      id: string
      title: string
      subtitle: string
      status: string
      statusLabel: string
      progress: number | null
      progressLabel: string
      color: JobStatusColor
      icon: string
      active: boolean
      terminal: boolean
      upload: ActiveUpload
    }
  | {
      kind: 'action'
      id: string
      title: string
      subtitle: string
      status: string
      statusLabel: string
      progress: number | null
      progressLabel: string
      color: JobStatusColor
      icon: string
      active: boolean
      terminal: boolean
      run: TrackedActionRun
    }

const uploadStore = useUploadStore()
const uploadSessionActions = useUploadSessionActions()
const actionRunsStore = useActionRunsStore()
const toast = useToast()

const minimized = useState('app.statusOverlay.minimized', () => false)
const collapsedJobKeys = ref<Set<string>>(new Set())
const dismissingJobKeys = ref<Set<string>>(new Set())
const clearingCompletedJobs = ref(false)
let actionPollTimer: ReturnType<typeof setInterval> | null = null

const uploadStatusLabels: Record<string, string> = {
  PENDING: 'Pending',
  UPLOADING: 'Uploading',
  PROCESSING: 'Processing',
  COMPLETED: 'Completed',
  FAILED: 'Failed',
  CANCELLED: 'Cancelled'
}

const actionStatusLabels: Record<string, string> = {
  PENDING: 'Pending',
  DISPATCHING: 'Dispatching',
  RUNNING: 'Running',
  IMPORTING_RESULTS: 'Importing',
  COMPLETED: 'Completed',
  FAILED: 'Failed',
  CANCEL_REQUESTED: 'Cancelling',
  CANCELLED: 'Cancelled'
}

const fileStatusIcons: Record<string, string> = {
  pending: 'i-lucide-clock',
  uploading: 'i-lucide-upload',
  uploaded: 'i-lucide-check',
  processing: 'i-lucide-loader',
  completed: 'i-lucide-check-circle',
  failed: 'i-lucide-x-circle',
  conflict: 'i-lucide-alert-triangle',
  skipped: 'i-lucide-skip-forward'
}

const uploadJobs = computed<StatusJob[]>(() => uploadStore.uploadsArray.map(upload => ({
  kind: 'upload',
  id: upload.sessionId,
  title: upload.projectName,
  subtitle: getUploadProgressSummary(upload),
  status: upload.status,
  statusLabel: getUploadStatusLabel(upload),
  progress: upload.status === 'PROCESSING' ? null : upload.progressPercent,
  progressLabel: upload.status === 'PROCESSING' ? 'Finalizing' : `${upload.progressPercent}%`,
  color: getUploadStatusColor(upload.status),
  icon: upload.status === 'PROCESSING' ? 'i-lucide-loader' : 'i-lucide-upload-cloud',
  active: isActiveUpload(upload.status),
  terminal: isTerminalUpload(upload.status),
  upload
})))

const actionJobs = computed<StatusJob[]>(() => actionRunsStore.runsArray.map(run => ({
  kind: 'action',
  id: run.id,
  title: run.processorName,
  subtitle: `${run.projectName} · ${run.pageIds.length} page${run.pageIds.length === 1 ? '' : 's'}`,
  status: run.status,
  statusLabel: actionStatusLabels[run.status] || run.status,
  progress: run.progressPercent,
  progressLabel: `${run.progressPercent}%`,
  color: getActionStatusColor(run.status),
  icon: 'i-lucide-circle-play',
  active: isActiveAction(run.status),
  terminal: isTerminalAction(run.status),
  run
})))

const jobs = computed<StatusJob[]>(() => [...uploadJobs.value, ...actionJobs.value]
  .sort((left, right) => {
    if (left.active !== right.active) return left.active ? -1 : 1
    return getJobTimestamp(right) - getJobTimestamp(left)
  }))

const activeJobs = computed(() => jobs.value.filter(job => job.active))
const terminalJobs = computed(() => jobs.value.filter(job => job.terminal))
const hasActiveJobs = computed(() => activeJobs.value.length > 0)
const showOverlay = computed(() => uploadStore.showProgressPanel || actionRunsStore.showProgressPanel)
const activeCountLabel = computed(() => `${activeJobs.value.length} active`)
const completedCountLabel = computed(() => `${terminalJobs.value.length} done`)
const overallProgress = computed(() => {
  if (activeJobs.value.length === 0) return 0
  const total = activeJobs.value.reduce((sum, job) => sum + (job.progress ?? 0), 0)
  return Math.round(total / activeJobs.value.length)
})
const headerTitle = computed(() => {
  if (activeJobs.value.length === 0) return 'Status'
  if (activeJobs.value.length === 1) return activeJobs.value[0]?.statusLabel || 'Status'
  return 'Status'
})

onMounted(() => {
  actionPollTimer = setInterval(() => {
    if (actionRunsStore.hasActiveRuns) {
      void actionRunsStore.refreshActiveRuns()
    }
  }, 2500)
})

watch(() => activeJobs.value.length, (count, previousCount) => {
  if (count > (previousCount ?? 0)) {
    minimized.value = false
  }
})

watch(jobs, (currentJobs) => {
  const currentKeys = new Set(currentJobs.map(getJobKey))
  const next = new Set(Array.from(collapsedJobKeys.value).filter(key => currentKeys.has(key)))
  collapsedJobKeys.value = next
})

onBeforeUnmount(() => {
  if (actionPollTimer) {
    clearInterval(actionPollTimer)
  }
})

function getJobKey(job: StatusJob): string {
  return `${job.kind}:${job.id}`
}

function getJobTimestamp(job: StatusJob): number {
  return Date.parse(job.kind === 'upload' ? job.upload.created : job.run.created)
}

function isActiveUpload(status: ActiveUpload['status']): boolean {
  return status === 'PENDING' || status === 'UPLOADING' || status === 'PROCESSING'
}

function isTerminalUpload(status: ActiveUpload['status']): boolean {
  return status === 'COMPLETED' || status === 'FAILED' || status === 'CANCELLED'
}

function isActiveAction(status: TrackedActionRun['status']): boolean {
  return status === 'PENDING'
    || status === 'DISPATCHING'
    || status === 'RUNNING'
    || status === 'IMPORTING_RESULTS'
    || status === 'CANCEL_REQUESTED'
}

function isTerminalAction(status: TrackedActionRun['status']): boolean {
  return status === 'COMPLETED' || status === 'FAILED' || status === 'CANCELLED'
}

function getUploadStatusColor(status: ActiveUpload['status']): JobStatusColor {
  if (status === 'COMPLETED') return 'success'
  if (status === 'FAILED' || status === 'CANCELLED') return 'error'
  return 'primary'
}

function getActionStatusColor(status: TrackedActionRun['status']): JobStatusColor {
  if (status === 'COMPLETED') return 'success'
  if (status === 'FAILED' || status === 'CANCELLED') return 'error'
  if (status === 'CANCEL_REQUESTED') return 'warning'
  return 'primary'
}

function getUploadStatusLabel(upload: ActiveUpload): string {
  if (upload.status === 'PROCESSING') return 'Finalizing'
  return uploadStatusLabels[upload.status] || upload.status
}

function getUploadedFileCount(upload: ActiveUpload): number {
  return upload.files.filter((file) => {
    if (file.totalChunks > 0 && file.chunksReceived >= file.totalChunks) return true
    return file.status === 'uploaded'
      || file.status === 'processing'
      || file.status === 'completed'
      || file.status === 'failed'
      || file.status === 'conflict'
      || file.status === 'skipped'
  }).length
}

function getSettledFileCount(upload: ActiveUpload): number {
  const settledByFileState = upload.files.filter(file =>
    file.status === 'completed'
    || file.status === 'failed'
    || file.status === 'conflict'
    || file.status === 'skipped'
  ).length
  const settledBySessionState = upload.processedFiles + upload.failedFiles
  const settledCount = Math.max(settledByFileState, settledBySessionState)
  return upload.status === 'COMPLETED' ? Math.max(settledCount, upload.totalFiles) : settledCount
}

function getUploadProgressSummary(upload: ActiveUpload): string {
  if (upload.status === 'PENDING' || upload.status === 'UPLOADING') {
    return `${getUploadedFileCount(upload)} / ${upload.totalFiles} files uploaded`
  }
  return `${getSettledFileCount(upload)} / ${upload.totalFiles} files processed`
}

function formatBytes(bytes: number): string {
  if (bytes === 0) return '0 B'
  const k = 1024
  const sizes = ['B', 'KB', 'MB', 'GB']
  const i = Math.floor(Math.log(bytes) / Math.log(k))
  return `${parseFloat((bytes / Math.pow(k, i)).toFixed(1))} ${sizes[i]}`
}

function canCancelJob(job: StatusJob): boolean {
  if (job.kind === 'upload') return isActiveUpload(job.upload.status)
  return job.run.status === 'PENDING'
    || job.run.status === 'DISPATCHING'
    || job.run.status === 'RUNNING'
    || job.run.status === 'IMPORTING_RESULTS'
    || job.run.status === 'CANCEL_REQUESTED'
}

function isCancellingJob(job: StatusJob): boolean {
  return job.kind === 'upload'
    ? uploadStore.isCancelling(job.id)
    : actionRunsStore.isCancelling(job.id)
}

function isJobCollapsed(job: StatusJob): boolean {
  return collapsedJobKeys.value.has(getJobKey(job))
}

function toggleJobCollapsed(job: StatusJob) {
  const key = getJobKey(job)
  const next = new Set(collapsedJobKeys.value)
  if (next.has(key)) {
    next.delete(key)
  } else {
    next.add(key)
  }
  collapsedJobKeys.value = next
}

function isDismissingJob(job: StatusJob): boolean {
  return dismissingJobKeys.value.has(getJobKey(job))
}

function setDismissingJob(job: StatusJob, value: boolean) {
  const key = getJobKey(job)
  const next = new Set(dismissingJobKeys.value)
  if (value) {
    next.add(key)
  } else {
    next.delete(key)
  }
  dismissingJobKeys.value = next
}

async function cancelJob(job: StatusJob) {
  if (!canCancelJob(job) || isCancellingJob(job)) return
  try {
    if (job.kind === 'upload') {
      await uploadSessionActions.cancelUploadBySessionId(job.id)
    } else {
      await actionRunsStore.cancelRun(job.run)
    }
  } catch (error) {
    const message = error instanceof Error ? error.message : 'Could not cancel job.'
    toast.add({
      title: 'Cancel failed',
      description: message,
      color: 'error',
      icon: 'i-lucide-alert-circle'
    })
  }
}

async function dismissJob(job: StatusJob) {
  if (isDismissingJob(job)) return
  setDismissingJob(job, true)
  try {
    if (job.kind === 'upload') {
      uploadStore.removeUpload(job.id)
    } else {
      await actionRunsStore.dismissRun(job.run)
    }
  } catch (error) {
    const message = error instanceof Error ? error.message : 'Could not persist dismissal.'
    toast.add({
      title: 'Dismissed locally',
      description: message,
      color: 'warning',
      icon: 'i-lucide-alert-triangle'
    })
  } finally {
    setDismissingJob(job, false)
  }
}

async function clearCompletedJobs() {
  if (clearingCompletedJobs.value) return
  clearingCompletedJobs.value = true
  try {
    uploadStore.clearCompletedUploads()
    await actionRunsStore.dismissCompletedRuns()
  } catch (error) {
    const message = error instanceof Error ? error.message : 'Could not persist all dismissals.'
    toast.add({
      title: 'Cleared locally',
      description: message,
      color: 'warning',
      icon: 'i-lucide-alert-triangle'
    })
  } finally {
    clearingCompletedJobs.value = false
  }
}

function toggleMinimized() {
  minimized.value = !minimized.value
}

function closeOverlay() {
  if (hasActiveJobs.value) {
    minimized.value = true
    return
  }
  uploadStore.hidePanel()
  actionRunsStore.hidePanel()
}
</script>

<template>
  <Transition
    enter-active-class="transform transition duration-300 ease-out"
    enter-from-class="translate-y-full opacity-0"
    enter-to-class="translate-y-0 opacity-100"
    leave-active-class="transform transition duration-200 ease-in"
    leave-from-class="translate-y-0 opacity-100"
    leave-to-class="translate-y-full opacity-0"
  >
    <div
      v-if="showOverlay"
      class="fixed bottom-4 right-4 z-50 w-[min(26rem,calc(100vw-2rem))] overflow-hidden rounded-sm border border-default bg-default shadow-xl"
    >
      <div class="flex items-center justify-between gap-3 border-b border-default px-4 py-3">
        <div class="flex min-w-0 items-center gap-2">
          <UIcon name="i-lucide-activity" class="size-5 shrink-0 text-primary" />
          <div class="min-w-0">
            <p class="truncate text-sm font-semibold">
              {{ headerTitle }}
            </p>
            <p class="truncate text-xs text-muted">
              {{ activeJobs.length > 0 ? activeCountLabel : completedCountLabel }}
            </p>
          </div>
        </div>
        <div class="flex shrink-0 items-center gap-1">
          <UBadge v-if="activeJobs.length > 0" color="primary" size="xs" variant="soft">
            {{ activeJobs.length }}
          </UBadge>
          <UButton
            :icon="minimized ? 'i-lucide-chevron-up' : 'i-lucide-minus'"
            variant="ghost"
            size="xs"
            :aria-label="minimized ? 'Expand status' : 'Minimize status'"
            @click="toggleMinimized"
          />
          <UButton
            icon="i-lucide-x"
            variant="ghost"
            size="xs"
            aria-label="Close status"
            @click="closeOverlay"
          />
        </div>
      </div>

      <Transition
        enter-active-class="transition-all duration-200 ease-out"
        enter-from-class="max-h-0 opacity-0"
        enter-to-class="max-h-[28rem] opacity-100"
        leave-active-class="transition-all duration-200 ease-in"
        leave-from-class="max-h-[28rem] opacity-100"
        leave-to-class="max-h-0 opacity-0"
      >
        <div v-if="!minimized" class="max-h-[28rem] overflow-y-auto">
          <div v-if="jobs.length === 0" class="px-4 py-6 text-center text-sm text-muted">
            No jobs
          </div>

          <div v-else class="divide-y divide-default">
            <div
              v-for="job in jobs"
              :key="`${job.kind}:${job.id}`"
              class="px-4 py-3"
            >
              <div class="flex items-start justify-between gap-3">
                <div class="flex min-w-0 items-start gap-1">
                  <UButton
                    :icon="isJobCollapsed(job) ? 'i-lucide-chevron-right' : 'i-lucide-chevron-down'"
                    variant="ghost"
                    size="xs"
                    class="-ml-1 mt-0.5 shrink-0"
                    :aria-label="isJobCollapsed(job) ? 'Expand job' : 'Collapse job'"
                    @click="toggleJobCollapsed(job)"
                  />
                  <UIcon :name="job.icon" class="mt-0.5 size-4 shrink-0 text-muted" />
                  <div class="min-w-0">
                    <p class="truncate text-sm font-medium">
                      {{ job.title }}
                    </p>
                    <p class="truncate text-xs text-muted">
                      {{ job.subtitle }}
                    </p>
                  </div>
                </div>
                <UBadge :color="job.color" size="xs" variant="soft">
                  {{ job.statusLabel }}
                </UBadge>
              </div>

              <Transition
                enter-active-class="transition-all duration-150 ease-out"
                enter-from-class="max-h-0 opacity-0"
                enter-to-class="max-h-80 opacity-100"
                leave-active-class="transition-all duration-150 ease-in"
                leave-from-class="max-h-80 opacity-100"
                leave-to-class="max-h-0 opacity-0"
              >
                <div v-if="!isJobCollapsed(job)" class="overflow-hidden pl-9">
                  <div class="mb-2 mt-2">
                    <div class="mb-1 flex items-center justify-between gap-2 text-xs text-muted">
                      <span class="truncate">
                        <template v-if="job.kind === 'action'">
                          {{ job.run.statusMessage || job.run.processorKey }}
                        </template>
                        <template v-else>
                          {{ job.progressLabel }}
                        </template>
                      </span>
                      <span class="shrink-0">{{ job.progressLabel }}</span>
                    </div>
                    <UProgress
                      :model-value="job.progress"
                      :color="job.color"
                      size="sm"
                    />
                  </div>

                  <p v-if="job.kind === 'upload' && job.upload.error" class="mt-1 text-xs text-error">
                    {{ job.upload.error }}
                  </p>
                  <p v-if="job.kind === 'action' && job.run.errorMessage" class="mt-1 text-xs text-error">
                    {{ job.run.errorMessage }}
                  </p>

                  <UCollapsible v-if="job.kind === 'upload' && job.upload.files.length > 0" class="mt-2">
                    <template #trigger="{ open }">
                      <UButton
                        variant="ghost"
                        size="xs"
                        class="w-full justify-between"
                        :trailing-icon="open ? 'i-lucide-chevron-up' : 'i-lucide-chevron-down'"
                      >
                        <span class="text-xs">Files</span>
                      </UButton>
                    </template>

                    <div class="mt-2 max-h-32 space-y-1 overflow-y-auto">
                      <div
                        v-for="file in job.upload.files.slice(0, 20)"
                        :key="file.id || file.fileName"
                        class="flex items-center gap-2 rounded-sm p-1 text-xs hover:bg-elevated"
                      >
                        <UIcon
                          :name="fileStatusIcons[file.status] || 'i-lucide-file'"
                          :class="file.status === 'completed' ? 'text-success' : file.status === 'failed' ? 'text-error' : 'text-muted'"
                          class="size-3.5 shrink-0"
                        />
                        <span class="min-w-0 flex-1 truncate">{{ file.fileName }}</span>
                        <span class="shrink-0 text-muted">{{ formatBytes(file.fileSize) }}</span>
                      </div>
                      <div v-if="job.upload.files.length > 20" class="p-1 text-xs text-muted">
                        ... and {{ job.upload.files.length - 20 }} more files
                      </div>
                    </div>
                  </UCollapsible>

                  <div class="mt-2 flex items-center gap-2">
                    <UButton
                      v-if="canCancelJob(job)"
                      variant="ghost"
                      size="xs"
                      icon="i-lucide-ban"
                      :loading="isCancellingJob(job)"
                      @click="cancelJob(job)"
                    >
                      Cancel
                    </UButton>
                    <UButton
                      v-else-if="job.terminal"
                      variant="ghost"
                      size="xs"
                      icon="i-lucide-trash-2"
                      :loading="isDismissingJob(job)"
                      @click="dismissJob(job)"
                    >
                      Dismiss
                    </UButton>
                  </div>
                </div>
              </Transition>
            </div>
          </div>
        </div>
      </Transition>

      <Transition
        enter-active-class="transition-all duration-200"
        enter-from-class="opacity-0"
        enter-to-class="opacity-100"
        leave-active-class="transition-all duration-200"
        leave-from-class="opacity-100"
        leave-to-class="opacity-0"
      >
        <div v-if="minimized && hasActiveJobs" class="px-4 py-2">
          <div class="flex items-center justify-between text-sm">
            <span class="text-muted">{{ activeJobs.length }} job{{ activeJobs.length === 1 ? '' : 's' }} running</span>
            <span class="font-medium">{{ overallProgress }}%</span>
          </div>
          <UProgress :model-value="overallProgress" size="xs" class="mt-1" />
        </div>
      </Transition>

      <div
        v-if="!minimized && terminalJobs.length > 0"
        class="border-t border-default px-4 py-2"
      >
        <UButton
          variant="ghost"
          size="xs"
          icon="i-lucide-check-check"
          class="w-full"
          :loading="clearingCompletedJobs"
          @click="clearCompletedJobs"
        >
          Clear completed
        </UButton>
      </div>
    </div>
  </Transition>
</template>
