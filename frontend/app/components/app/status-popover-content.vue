<script setup lang="ts">
import {
  getJobKey,
  isActiveAction,
  isActiveIiifImport,
  isActiveUpload,
  type StatusJob
} from '@/utils/status-center'

withDefaults(defineProps<{
  showClose?: boolean
  showMinimize?: boolean
  minimized?: boolean
  compact?: boolean
}>(), {
  showClose: false,
  showMinimize: false,
  minimized: false,
  compact: false
})

const emit = defineEmits<{
  close: []
  toggleMinimized: []
}>()

const uploadStore = useUploadStore()
const uploadSessionActions = useUploadSessionActions()
const actionRunsStore = useActionRunsStore()
const backgroundJobsStore = useBackgroundJobsStore()
const iiifImportJobsStore = useIiifImportJobsStore()
const toast = useToast()
const realtime = useRealtimeSocket()
const UChatShimmer = resolveComponent('UChatShimmer')
const {
  issues,
  retryIssue,
  resolveIssue,
  isRetrying
} = useStatusIssues()

const {
  jobs,
  activeJobs,
  terminalJobs,
  activeCountLabel,
  completedCountLabel,
  issueCountLabel,
  headerTitle,
  overallProgress
} = useStatusCenter()

const collapsedJobKeys = ref<Set<string>>(new Set())
const dismissingJobKeys = ref<Set<string>>(new Set())
const clearingCompletedJobs = ref(false)

const realtimeStatus = computed(() => {
  if (realtime.connectionStatus.value === 'connected') {
    return { label: 'Realtime connected', icon: 'i-lucide-wifi', color: 'text-success' }
  }
  if (realtime.connectionStatus.value === 'connecting') {
    return { label: 'Reconnecting…', icon: 'i-lucide-loader-circle', color: 'text-warning' }
  }
  return { label: 'Using HTTP fallback', icon: 'i-lucide-wifi-off', color: 'text-warning' }
})

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

const issueSeverityMeta: Record<string, { color: 'error' | 'warning' | 'neutral', icon: string }> = {
  error: { color: 'error', icon: 'i-lucide-alert-circle' },
  warning: { color: 'warning', icon: 'i-lucide-triangle-alert' },
  info: { color: 'neutral', icon: 'i-lucide-info' }
}
const fallbackIssueSeverityMeta = issueSeverityMeta.warning!

watch(jobs, (currentJobs) => {
  const currentKeys = new Set(currentJobs.map(getJobKey))
  collapsedJobKeys.value = new Set(Array.from(collapsedJobKeys.value).filter(key => currentKeys.has(key)))
})

function formatBytes(bytes: number): string {
  if (bytes === 0) return '0 B'
  const k = 1024
  const sizes = ['B', 'KB', 'MB', 'GB']
  const i = Math.floor(Math.log(bytes) / Math.log(k))
  return `${parseFloat((bytes / Math.pow(k, i)).toFixed(1))} ${sizes[i]}`
}

function canCancelJob(job: StatusJob): boolean {
  if (job.kind === 'background') return false
  if (job.kind === 'upload') {
    return job.upload.cancelable !== false && isActiveUpload(job.upload.status)
  }
  if (job.kind === 'action') {
    return job.run.canCancel && isActiveAction(job.run.status)
  }
  return isActiveIiifImport(job.iiifJob.status)
}

function isCancellingJob(job: StatusJob): boolean {
  if (job.kind === 'background') return false
  if (job.kind === 'upload') return uploadStore.isCancelling(job.id)
  if (job.kind === 'action') return actionRunsStore.isCancelling(job.id)
  return iiifImportJobsStore.isCancelling(job.id)
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

function canRetryJob(job: StatusJob): boolean {
  return job.kind === 'background' && job.status === 'FAILED' && backgroundJobsStore.canRetryJob(job.id)
}

function isRetryingJob(job: StatusJob): boolean {
  return job.kind === 'background' && backgroundJobsStore.isRetrying(job.id)
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
    } else if (job.kind === 'action') {
      await actionRunsStore.cancelRun(job.run)
    } else if (job.kind === 'iiif') {
      await iiifImportJobsStore.cancelJob(job.iiifJob)
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
    } else if (job.kind === 'action') {
      await actionRunsStore.dismissRun(job.run)
    } else if (job.kind === 'background') {
      backgroundJobsStore.removeJob(job.id)
    } else {
      await iiifImportJobsStore.dismissJob(job.iiifJob)
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

async function retryJob(job: StatusJob) {
  if (!canRetryJob(job) || isRetryingJob(job)) return
  try {
    await backgroundJobsStore.retryJob(job.id)
  } catch (error) {
    const message = error instanceof Error ? error.message : 'Could not retry job.'
    toast.add({
      title: 'Retry failed',
      description: message,
      color: 'error',
      icon: 'i-lucide-alert-circle'
    })
  }
}

async function clearCompletedJobs() {
  if (clearingCompletedJobs.value) return
  clearingCompletedJobs.value = true
  try {
    uploadStore.clearCompletedUploads()
    await actionRunsStore.dismissCompletedRuns()
    backgroundJobsStore.clearCompletedJobs()
    await iiifImportJobsStore.dismissCompletedJobs()
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

function closePanel() {
  emit('close')
}

function toggleMinimized() {
  emit('toggleMinimized')
}

function actionStatusDetail(job: Extract<StatusJob, { kind: 'action' }>) {
  if (job.run.queuePosition && job.run.queuePosition > 0) {
    return `Queue position ${job.run.queuePosition}`
  }
  return job.run.statusMessage || job.run.processorKey
}

function shouldUseJobShimmer(job: StatusJob) {
  return (job.kind === 'action' || job.kind === 'background' || job.kind === 'iiif') && job.active
}

function backgroundJobDetail(job: Extract<StatusJob, { kind: 'background' }>) {
  return job.backgroundJob.error || job.backgroundJob.detail || job.subtitle
}

function iiifJobDetail(job: Extract<StatusJob, { kind: 'iiif' }>) {
  if (job.iiifJob.queuePosition && job.iiifJob.queuePosition > 0) {
    return `Queue position ${job.iiifJob.queuePosition}`
  }
  if (job.iiifJob.status === 'IMPORTING') {
    return `${job.iiifJob.processedCanvases} / ${job.iiifJob.totalCanvases} canvases processed`
  }
  return job.iiifJob.errorMessage || job.subtitle
}
</script>

<template>
  <div
    :class="[
      minimized ? 'w-[min(16rem,calc(100vw-2rem))]' : compact ? 'w-[min(22rem,calc(100vw-2rem))]' : 'w-[min(28rem,calc(100vw-2rem))]',
      'overflow-hidden'
    ]"
  >
    <div :class="['flex items-center justify-between gap-3 border-b border-default', minimized || compact ? 'px-3 py-2' : 'px-4 py-3']">
      <div class="flex min-w-0 items-center gap-2">
        <UIcon :class="minimized ? 'size-4' : 'size-5'" name="i-lucide-activity" class="shrink-0 text-primary" />
        <div class="min-w-0">
          <p :class="minimized ? 'text-xs' : 'text-sm'" class="truncate font-semibold">
            {{ headerTitle }}
          </p>
          <p v-if="!minimized" class="truncate text-xs text-muted">
            {{ activeJobs.length > 0 ? activeCountLabel : issues.length > 0 ? issueCountLabel : completedCountLabel }}
          </p>
        </div>
      </div>
      <div class="flex items-center gap-1">
        <UBadge
          v-if="activeJobs.length > 0"
          color="primary"
          size="xs"
          variant="soft"
        >
          {{ activeJobs.length }}
        </UBadge>
        <UButton
          v-if="showMinimize"
          :icon="minimized ? 'i-lucide-chevron-up' : 'i-lucide-minus'"
          variant="ghost"
          size="xs"
          :aria-label="minimized ? 'Expand jobs panel' : 'Minimize jobs panel'"
          @click="toggleMinimized"
        />
        <UButton
          v-if="showClose"
          icon="i-lucide-x"
          variant="ghost"
          size="xs"
          aria-label="Close jobs panel"
          @click="closePanel"
        />
      </div>
    </div>

    <div v-if="minimized" class="px-3 py-2">
      <div class="flex items-center justify-between text-xs">
        <span class="text-muted">
          <template v-if="issues.length > 0">
            {{ issueCountLabel }}
          </template>
          <template v-else-if="activeJobs.length > 0">
            {{ activeJobs.length }} job{{ activeJobs.length === 1 ? '' : 's' }} running
          </template>
          <template v-else>
            {{ completedCountLabel }}
          </template>
        </span>
        <span v-if="activeJobs.length > 0" class="font-medium text-xs">
          {{ overallProgress }}%
        </span>
      </div>
      <UProgress
        v-if="activeJobs.length > 0"
        :model-value="overallProgress"
        size="xs"
        class="mt-0.5"
      />
    </div>

    <div v-else :class="[compact ? 'max-h-[min(60vh,24rem)]' : 'max-h-110', 'overflow-y-auto']">
      <div class="flex items-center gap-2 border-b border-default px-4 py-2 text-xs text-muted">
        <UIcon :name="realtimeStatus.icon" :class="realtimeStatus.color" class="size-3.5" />
        <span>{{ realtimeStatus.label }}</span>
      </div>
      <div v-if="issues.length === 0 && jobs.length === 0" class="px-4 py-6 text-center text-sm text-muted">
        All clear
      </div>

      <div v-else class="divide-y divide-default">
        <div
          v-for="issue in issues"
          :key="issue.id"
          class="px-4 py-3"
        >
          <div class="flex items-start justify-between gap-3">
            <div class="flex min-w-0 items-start gap-2">
              <UIcon
                :name="issueSeverityMeta[issue.severity]?.icon || fallbackIssueSeverityMeta.icon"
                :class="issue.severity === 'error' ? 'text-error' : issue.severity === 'warning' ? 'text-warning' : 'text-muted'"
                class="mt-0.5 size-4 shrink-0"
              />
              <div class="min-w-0">
                <p class="truncate text-sm font-medium">
                  {{ issue.title }}
                </p>
                <p class="mt-1 text-xs text-muted">
                  {{ issue.message }}
                </p>
                <p class="mt-1 text-[11px] uppercase tracking-wide text-muted">
                  {{ issue.source.replace(/-/g, ' ') }}
                </p>
              </div>
            </div>
            <UBadge
              :color="issueSeverityMeta[issue.severity]?.color || fallbackIssueSeverityMeta.color"
              size="xs"
              variant="soft"
            >
              {{ issue.severity }}
            </UBadge>
          </div>

          <div class="mt-2 flex items-center gap-2 pl-6">
            <UButton
              v-if="issue.retryLabel"
              variant="ghost"
              size="xs"
              icon="i-lucide-rotate-cw"
              :loading="isRetrying(issue.id)"
              @click="() => { void retryIssue(issue.id) }"
            >
              {{ issue.retryLabel }}
            </UButton>
            <UButton
              variant="ghost"
              size="xs"
              icon="i-lucide-x"
              @click="resolveIssue(issue.id)"
            >
              Dismiss
            </UButton>
          </div>
        </div>

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
                <UChatShimmer
                  v-if="shouldUseJobShimmer(job)"
                  :text="job.title"
                  class="max-w-full text-sm font-medium"
                />
                <p v-else class="truncate text-sm font-medium">
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
                  <span v-if="job.kind !== 'upload'" class="truncate">
                    <template v-if="job.kind === 'action'">
                      {{ actionStatusDetail(job) }}
                    </template>
                    <template v-else-if="job.kind === 'background'">
                      {{ backgroundJobDetail(job) }}
                    </template>
                    <template v-else-if="job.kind === 'iiif'">
                      {{ iiifJobDetail(job) }}
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
              <p v-if="job.kind === 'background' && job.backgroundJob.error" class="mt-1 text-xs text-error">
                {{ job.backgroundJob.error }}
              </p>
              <p v-if="job.kind === 'iiif' && job.iiifJob.errorMessage" class="mt-1 text-xs text-error">
                {{ job.iiifJob.errorMessage }}
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
                  <div v-if="job.upload.totalFiles > job.upload.files.length" class="p-1 text-xs text-muted">
                    ... and {{ job.upload.totalFiles - job.upload.files.length }} more files
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
                  v-if="canRetryJob(job)"
                  variant="ghost"
                  size="xs"
                  icon="i-lucide-rotate-cw"
                  :loading="isRetryingJob(job)"
                  @click="retryJob(job)"
                >
                  Retry
                </UButton>
                <UButton
                  v-if="job.terminal"
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
</template>
