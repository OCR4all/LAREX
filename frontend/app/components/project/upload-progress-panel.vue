<script setup lang="ts">
const uploadStore = useUploadStore()
const uploadSessionActions = useUploadSessionActions()
const toast = useToast()

const statusLabels: Record<string, string> = {
  PENDING: 'Pending',
  UPLOADING: 'Uploading',
  PROCESSING: 'Processing',
  COMPLETED: 'Completed',
  FAILED: 'Failed',
  CANCELLED: 'Cancelled'
}

const headerTitle = computed(() => {
  const uploads = uploadStore.uploadsArray
  if (uploads.length === 0) return 'Upload Complete'

  const hasProcessing = uploads.some(u => u.status === 'PROCESSING')
  const hasUploading = uploads.some(u => u.status === 'UPLOADING' || u.status === 'PENDING')
  const allComplete = uploads.every(u => u.status === 'COMPLETED' || u.status === 'FAILED' || u.status === 'CANCELLED')

  if (allComplete) return 'Upload Complete'
  if (hasProcessing) return 'Processing Files'
  if (hasUploading) return 'Uploading Files'
  return 'Upload Complete'
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

function formatBytes(bytes: number): string {
  if (bytes === 0) return '0 B'
  const k = 1024
  const sizes = ['B', 'KB', 'MB', 'GB']
  const i = Math.floor(Math.log(bytes) / Math.log(k))
  return `${parseFloat((bytes / Math.pow(k, i)).toFixed(1))} ${sizes[i]}`
}

function isFinalizing(upload: { status: string, progressPercent: number }): boolean {
  return upload.status === 'PROCESSING' && upload.progressPercent >= 100
}

function getStatusLabel(upload: { status: string, progressPercent: number }): string {
  if (isFinalizing(upload)) return 'Finalizing'
  return statusLabels[upload.status] || upload.status
}

function getUploadedFileCount(upload: { files: Array<{ status: string, chunksReceived: number, totalChunks: number }> }): number {
  return upload.files.filter((file) => {
    if (file.totalChunks > 0 && file.chunksReceived >= file.totalChunks) {
      return true
    }

    return file.status === 'uploaded'
      || file.status === 'processing'
      || file.status === 'completed'
      || file.status === 'failed'
      || file.status === 'conflict'
      || file.status === 'skipped'
  }).length
}

function getSettledFileCount(upload: { files: Array<{ status: string }>, processedFiles: number, failedFiles: number, totalFiles: number, status: string }): number {
  const settledByFileState = upload.files.filter(file =>
    file.status === 'completed'
    || file.status === 'failed'
    || file.status === 'conflict'
    || file.status === 'skipped'
  ).length

  const settledBySessionState = upload.processedFiles + upload.failedFiles
  const settledCount = Math.max(settledByFileState, settledBySessionState)

  if (upload.status === 'COMPLETED') {
    return Math.max(settledCount, upload.totalFiles)
  }

  return settledCount
}

function getProgressSummary(upload: {
  status: string
  totalFiles: number
  processedFiles: number
  failedFiles: number
  files: Array<{ status: string, chunksReceived: number, totalChunks: number }>
}): string {
  if (upload.status === 'PENDING' || upload.status === 'UPLOADING') {
    return `${getUploadedFileCount(upload)} / ${upload.totalFiles} files uploaded`
  }

  return `${getSettledFileCount(upload)} / ${upload.totalFiles} files processed`
}

function canCancelUpload(upload: { status: string }): boolean {
  return upload.status === 'PENDING' || upload.status === 'UPLOADING' || upload.status === 'PROCESSING'
}

async function handleCancelUpload(upload: { sessionId: string, status: string }) {
  if (!canCancelUpload(upload)) return
  if (uploadStore.isCancelling(upload.sessionId)) return

  try {
    await uploadSessionActions.cancelUploadBySessionId(upload.sessionId)
  } catch (error) {
    const message = error instanceof Error ? error.message : 'Failed to cancel upload'
    toast.add({
      title: 'Cancel failed',
      description: message,
      color: 'error',
      icon: 'i-lucide-alert-circle'
    })
  }
}

function handleClose() {
  if (uploadStore.hasActiveUploads) {
    uploadStore.toggleMinimized()
  } else {
    uploadStore.hidePanel()
  }
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
      v-if="uploadStore.showProgressPanel"
      class="fixed bottom-4 right-4 z-50 w-96 bg-(--ui-bg) border border-(--ui-border) rounded-sm shadow-xl"
    >
      <div class="flex items-center justify-between px-4 py-3 border-b border-(--ui-border)">
        <div class="flex items-center gap-2">
          <UIcon name="i-lucide-upload-cloud" class="text-(--ui-primary)" />
          <span class="font-medium">
            {{ headerTitle }}
          </span>
          <UBadge v-if="uploadStore.totalActiveUploads > 0" color="primary" size="xs">
            {{ uploadStore.totalActiveUploads }}
          </UBadge>
        </div>
        <div class="flex items-center gap-1">
          <UButton
            icon="i-lucide-minus"
            variant="ghost"
            size="xs"
            @click="uploadStore.toggleMinimized()"
          />
          <UButton
            icon="i-lucide-x"
            variant="ghost"
            size="xs"
            @click="handleClose"
          />
        </div>
      </div>

      <Transition
        enter-active-class="transition-all duration-200 ease-out"
        enter-from-class="max-h-0 opacity-0"
        enter-to-class="max-h-96 opacity-100"
        leave-active-class="transition-all duration-200 ease-in"
        leave-from-class="max-h-96 opacity-100"
        leave-to-class="max-h-0 opacity-0"
      >
        <div v-if="!uploadStore.minimized" class="max-h-80 overflow-y-auto">
          <div v-if="uploadStore.uploadsArray.length === 0" class="p-4 text-center text-(--ui-text-muted)">
            No active uploads
          </div>

          <div v-else class="divide-y divide-(--ui-border)">
            <div
              v-for="upload in uploadStore.uploadsArray"
              :key="upload.sessionId"
              class="p-3"
            >
              <div class="flex items-center justify-between mb-2">
                <div class="flex items-center gap-2 min-w-0">
                  <UIcon name="i-lucide-folder" class="text-(--ui-text-muted) shrink-0" />
                  <span class="text-sm font-medium truncate">{{ upload.projectName }}</span>
                </div>
                <UBadge :color="upload.status === 'COMPLETED' ? 'success' : upload.status === 'FAILED' ? 'error' : 'primary'" size="xs">
                  {{ getStatusLabel(upload) }}
                </UBadge>
              </div>

              <div class="mb-2">
                <div class="flex justify-between text-xs text-muted mb-1">
                  <span>{{ getProgressSummary(upload) }}</span>
                  <span>{{ isFinalizing(upload) ? 'Finalizing' : `${upload.progressPercent}%` }}</span>
                </div>
                <UProgress
                  :model-value="isFinalizing(upload) ? null : upload.progressPercent"
                  :color="upload.status === 'FAILED' ? 'error' : upload.status === 'COMPLETED' ? 'success' : 'primary'"
                  size="sm"
                />
              </div>

              <div v-if="upload.error" class="text-xs text-error mt-1">
                {{ upload.error }}
              </div>

              <div v-if="canCancelUpload(upload)" class="mt-2">
                <UButton
                  variant="ghost"
                  size="xs"
                  icon="i-lucide-ban"
                  :loading="uploadStore.isCancelling(upload.sessionId)"
                  @click="handleCancelUpload(upload)"
                >
                  Cancel upload
                </UButton>
              </div>

              <UCollapsible class="mt-2">
                <template #trigger="{ open }">
                  <UButton
                    variant="ghost"
                    size="xs"
                    class="w-full justify-between"
                    :trailing-icon="open ? 'i-lucide-chevron-up' : 'i-lucide-chevron-down'"
                  >
                    <span class="text-xs">Show files</span>
                  </UButton>
                </template>

                <div class="mt-2 space-y-1 max-h-32 overflow-y-auto">
                  <div
                    v-for="file in upload.files.slice(0, 20)"
                    :key="file.id"
                    class="flex items-center gap-2 text-xs p-1 rounded-sm hover:bg-elevated"
                  >
                    <UIcon
                      :name="fileStatusIcons[file.status] || 'i-lucide-file'"
                      :class="file.status === 'completed' ? 'text-success' : file.status === 'failed' ? 'text-error' : 'text-muted'"
                      size="xs"
                    />
                    <span class="truncate flex-1">{{ file.fileName }}</span>
                    <span class="text-muted shrink-0">{{ formatBytes(file.fileSize) }}</span>
                  </div>
                  <div v-if="upload.files.length > 20" class="text-xs text-muted p-1">
                    ... and {{ upload.files.length - 20 }} more files
                  </div>
                </div>
              </UCollapsible>

              <div v-if="upload.status === 'COMPLETED' || upload.status === 'FAILED' || upload.status === 'CANCELLED'" class="mt-2">
                <UButton
                  variant="ghost"
                  size="xs"
                  icon="i-lucide-trash-2"
                  @click="uploadStore.removeUpload(upload.sessionId)"
                >
                  Dismiss
                </UButton>
              </div>
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
        <div v-if="uploadStore.minimized && uploadStore.hasActiveUploads" class="px-4 py-2">
          <div class="flex items-center justify-between text-sm">
            <span class="text-muted">{{ uploadStore.totalActiveUploads }} upload(s) in progress</span>
            <span class="font-medium">{{ uploadStore.overallProgress }}%</span>
          </div>
          <UProgress v-model="uploadStore.overallProgress" size="xs" class="mt-1" />
        </div>
      </Transition>

      <div
        v-if="!uploadStore.minimized && uploadStore.uploadsArray.some(u => u.status === 'COMPLETED' || u.status === 'FAILED' || u.status === 'CANCELLED')"
        class="px-4 py-2 border-t border-default"
      >
        <UButton
          variant="ghost"
          size="xs"
          icon="i-lucide-check-check"
          class="w-full"
          @click="uploadStore.clearCompletedUploads()"
        >
          Clear completed
        </UButton>
      </div>
    </div>
  </Transition>
</template>
