<script setup lang="ts">
interface Props {
  projectId: string
  conflicts: ConflictInfo[]
  uploadResult: UploadResult
}

type ConflictType = 'IMAGE_VARIANT_EXISTS' | 'XML_FILE_EXISTS'
type ResolutionAction = 'REPLACE' | 'RENAME' | 'KEEP_EXISTING'

interface ConflictInfo {
  conflictId: string
  conflictType: ConflictType
  existingFileName: string
  newFileName: string
  existingFilePath: string
  newFilePath: string | null
  conflictTimestamp: string
  pageId: string
  pageName: string
  details: {
    existingFileSize: string | null
    newFileSize: string | null
    existingFileModified: string | null
    newFileModified: string | null
  }
}

interface UploadResult {
  totalPagesCreated: number
  totalPagesUpdated: number
  totalImagesProcessed: number
  totalXmlFilesProcessed: number
  pages: Array<{
    pageId: string
    pageName: string
    created: boolean
    imagesCount: number
    xmlUploaded: boolean
  }>
}

interface ConflictResolution {
  conflictId: string
  action: ResolutionAction
  newFileName: string
}

const props = defineProps<Props>()
const emit = defineEmits<{ close: [boolean] }>()
const formId = useId()

const workspace = useWorkspaceStore()

await workspace.validateAndSelectWorkspace()

const selectedWorkspace = computed(() => workspace.selectedWorkspaceId as string)
const projectKey = computed(() => wsKey(selectedWorkspace.value, 'projects', props.projectId))
const projectPagesKey = computed(() => wsKey(selectedWorkspace.value, 'projects', props.projectId, 'pages'))
const projectStatusKey = computed(() => wsKey(selectedWorkspace.value, 'projects', props.projectId, 'status'))

const toast = useToast()
const isResolving = ref(false)

const resolutions = ref<Record<string, { action: ResolutionAction, newFileName: string }>>({})

onMounted(() => {
  props.conflicts.forEach((conflict) => {
    resolutions.value[conflict.conflictId] = {
      action: 'KEEP_EXISTING',
      newFileName: conflict.newFileName
    }
  })
})

function updateResolution(conflictId: string, action: ResolutionAction, newFileName?: string) {
  resolutions.value[conflictId] = {
    action,
    newFileName: newFileName || resolutions.value[conflictId]?.newFileName || ''
  }
}

function formatFileSize(bytes: number): string {
  if (bytes === 0) return '0 B'
  const k = 1024
  const sizes = ['B', 'KB', 'MB', 'GB']
  const i = Math.floor(Math.log(bytes) / Math.log(k))
  return parseFloat((bytes / Math.pow(k, i)).toFixed(1)) + ' ' + sizes[i]
}

function getConflictTypeLabel(type: ConflictType): string {
  switch (type) {
    case 'IMAGE_VARIANT_EXISTS':
      return 'Image variant already exists'
    case 'XML_FILE_EXISTS':
      return 'XML file already exists'
    default:
      return 'Unknown conflict'
  }
}

async function resolveConflicts() {
  try {
    isResolving.value = true

    const conflictResolutions: ConflictResolution[] = props.conflicts.map(conflict => ({
      ...(resolutions.value[conflict.conflictId] ?? { action: 'KEEP_EXISTING' as const, newFileName: conflict.newFileName }),
      conflictId: conflict.conflictId,
      action: resolutions.value[conflict.conflictId]?.action ?? 'KEEP_EXISTING',
      newFileName: resolutions.value[conflict.conflictId]?.newFileName ?? conflict.newFileName
    }))

    await $fetch(`/api/workspaces/${selectedWorkspace.value}/projects/${props.projectId}/conflicts/resolve`, {
      method: 'POST',
      body: {
        resolutions: conflictResolutions
      }
    })

    toast.add({
      title: 'Conflicts resolved',
      description: 'All file conflicts have been resolved successfully',
      color: 'success',
      icon: 'i-lucide-check-circle'
    })

    emit('close', true)

    await $fetch(`/api/projects/${props.projectId}/pages/invalidate-cache`, { method: 'POST' })

    await Promise.all([
      refreshNuxtData(projectKey.value),
      refreshNuxtData(projectPagesKey.value),
      refreshNuxtData(projectStatusKey.value)
    ])
  } catch (error: unknown) {
    toast.add({
      title: 'Failed to resolve conflicts',
      description: extractApiErrorMessage(error, 'An error occurred while resolving conflicts'),
      color: 'error',
      icon: 'i-lucide-alert-circle'
    })
  } finally {
    isResolving.value = false
  }
}

function cancelUpload() {
  emit('close', false)
}

const successfulUploads = computed(() => {
  const { totalPagesCreated, totalPagesUpdated, totalImagesProcessed, totalXmlFilesProcessed } = props.uploadResult
  return {
    pages: totalPagesCreated + totalPagesUpdated,
    images: totalImagesProcessed,
    xml: totalXmlFilesProcessed
  }
})
</script>

<template>
  <UModal
    :close="{ onClick: () => emit('close', false) }"
    title="Resolve Upload Conflicts"
    size="xl"
  >
    <template #body>
      <UForm :id="formId" class="space-y-6" @submit="resolveConflicts">
        <div class="bg-success/10 border border-success/30 rounded-sm p-4">
          <div class="flex items-start">
            <UIcon name="i-lucide-check-circle" class="text-success mt-0.5 mr-3" />
            <div>
              <h4 class="font-medium text-success">
                Successful Uploads
              </h4>
              <p class="text-sm text-success/90 mt-1">
                {{ successfulUploads.pages }} pages processed, {{ successfulUploads.images }} images uploaded, {{ successfulUploads.xml }} XML files uploaded
              </p>
            </div>
          </div>
        </div>

        <div class="bg-warning/10 border border-warning/30 rounded-sm p-4">
          <div class="flex items-start">
            <UIcon name="i-lucide-alert-triangle" class="text-warning mt-0.5 mr-3" />
            <div class="flex-1">
              <h4 class="font-medium text-warning">
                File Conflicts Detected
              </h4>
              <p class="text-sm text-warning/90 mt-1">
                {{ conflicts.length }} files could not be uploaded due to conflicts. Please choose how to resolve each conflict:
              </p>
            </div>
          </div>
        </div>

        <div class="space-y-4">
          <div
            v-for="conflict in conflicts"
            :key="conflict.conflictId"
            class="border border-neutral-200 dark:border-neutral-700 rounded-sm p-4"
          >
            <div class="flex items-start justify-between mb-3">
              <div>
                <h5 class="font-medium text-neutral-900 dark:text-neutral-100">
                  {{ conflict.newFileName }}
                </h5>
                <p class="text-sm text-neutral-600 dark:text-neutral-400">
                  {{ getConflictTypeLabel(conflict.conflictType) }}
                </p>
                <p class="text-xs text-neutral-500 dark:text-neutral-500">
                  {{ conflict.details?.newFileSize ? formatFileSize(Number(conflict.details.newFileSize)) : '' }}
                </p>
              </div>
              <UBadge color="warning" variant="solid">
                Conflict
              </UBadge>
            </div>

            <div class="space-y-3">
              <div class="flex items-center space-x-2">
                <input
                  :id="`${conflict.conflictId}-keep`"
                  type="radio"
                  :name="`resolution-${conflict.conflictId}`"
                  :checked="resolutions[conflict.conflictId]?.action === 'KEEP_EXISTING'"
                  class="h-4 w-4 text-primary-600 focus:ring-primary-500 border-neutral-300"
                  @change="updateResolution(conflict.conflictId, 'KEEP_EXISTING')"
                >
                <label :for="`${conflict.conflictId}-keep`" class="text-sm text-neutral-700 dark:text-neutral-300">
                  Keep existing file (discard uploaded file)
                </label>
              </div>

              <div class="flex items-center space-x-2">
                <input
                  :id="`${conflict.conflictId}-replace`"
                  type="radio"
                  :name="`resolution-${conflict.conflictId}`"
                  :checked="resolutions[conflict.conflictId]?.action === 'REPLACE'"
                  class="h-4 w-4 text-primary-600 focus:ring-primary-500 border-neutral-300"
                  @change="updateResolution(conflict.conflictId, 'REPLACE')"
                >
                <label :for="`${conflict.conflictId}-replace`" class="text-sm text-neutral-700 dark:text-neutral-300">
                  Replace existing file with uploaded file
                </label>
              </div>

              <div class="space-y-2">
                <div class="flex items-center space-x-2">
                  <input
                    :id="`${conflict.conflictId}-rename`"
                    type="radio"
                    :name="`resolution-${conflict.conflictId}`"
                    :checked="resolutions[conflict.conflictId]?.action === 'RENAME'"
                    class="h-4 w-4 text-primary-600 focus:ring-primary-500 border-neutral-300"
                    @change="updateResolution(conflict.conflictId, 'RENAME')"
                  >
                  <label :for="`${conflict.conflictId}-rename`" class="text-sm text-neutral-700 dark:text-neutral-300">
                    Rename uploaded file and keep both
                  </label>
                </div>
                <div v-if="resolutions[conflict.conflictId]?.action === 'RENAME'" class="ml-6">
                  <UInput
                    v-model="resolutions[conflict.conflictId]!.newFileName"
                    placeholder="Enter new filename"
                    size="md"
                  />
                </div>
              </div>
            </div>
          </div>
        </div>
      </UForm>
    </template>

    <template #footer>
      <div class="flex gap-x-1 w-full justify-end">
        <UButton
          color="neutral"
          variant="ghost"
          @click="cancelUpload"
        >
          Cancel
        </UButton>
        <UButton
          type="submit"
          :form="formId"
          color="primary"
          variant="solid"
          :loading="isResolving"
        >
          Resolve Conflicts
        </UButton>
      </div>
    </template>
  </UModal>
</template>
