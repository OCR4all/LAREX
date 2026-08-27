<script setup lang="ts">
import { z } from 'zod'
import type { FormSubmitEvent } from '#ui/types'
import { LazyProjectModalTransferConflict } from '#components'
import type { TransferableResourceType } from '@/types/capabilities'
import { extractApiErrorMessage, isProjectNameConflictError } from '@/utils/api-error'

interface Workspace {
  id: string
  name: string
}

const props = defineProps<{
  resourceId?: string
  resourceName?: string
  resources?: Array<{ id: string, name: string }>
  resourceType: TransferableResourceType
  currentWorkspaceId: string
}>()

const emit = defineEmits<{ close: [transferred: boolean], transferred: [] }>()

const toast = useToast()
const overlay = useOverlay()
const { refreshUserTransfers, refreshWorkspaceTransfers } = useDataRefresh()

const { data: workspaces } = await useFetch<Workspace[]>('/api/workspaces', {
  key: globalKey('workspaces', 'list'),
  default: () => []
})

const availableWorkspaces = computed(() =>
  workspaces.value?.filter(w => w.id !== props.currentWorkspaceId) || []
)

const schema = z.object({
  targetWorkspaceId: z.string().min(1, { error: 'Select a workspace' }),
  transferType: z.enum(['MOVE', 'COPY']),
  message: z.string().max(500).optional()
})

type Schema = z.output<typeof schema>

const state = ref<Schema>({
  targetWorkspaceId: '',
  transferType: 'MOVE',
  message: ''
})

const isSubmitting = ref(false)
const formId = useId()

const selectedResources = computed(() => {
  if (props.resources?.length) {
    return props.resources.filter(resource => resource.id)
  }
  return props.resourceId
    ? [{ id: props.resourceId, name: props.resourceName || props.resourceId }]
    : []
})

const isBatchProjectShare = computed(() =>
  props.resourceType === 'PROJECT' && selectedResources.value.length > 1
)

const shareTitle = computed(() => isBatchProjectShare.value
  ? `Share ${selectedResources.value.length} projects`
  : `Share ${selectedResources.value[0]?.name || props.resourceName || 'resource'}`
)

const endpoint = computed(() =>
  props.resourceType === 'PROJECT' ? '/api/project-transfers' : '/api/resource-transfers'
)

const transferConflictModal = overlay.create(LazyProjectModalTransferConflict)

function defaultProjectName(resourceName: string, transferType: 'MOVE' | 'COPY'): string {
  return transferType === 'COPY' ? `${resourceName} (Copy)` : resourceName
}

async function requestProjectTransfer(
  resource: { id: string, name: string },
  targetWorkspaceId: string,
  transferType: 'MOVE' | 'COPY',
  message?: string
): Promise<boolean> {
  let projectName: string | undefined

  while (true) {
    try {
      await $fetch('/api/project-transfers', {
        method: 'POST',
        body: {
          projectId: resource.id,
          targetWorkspaceId,
          transferType,
          message,
          ...(projectName ? { projectName } : {})
        }
      })
      return true
    } catch (error: unknown) {
      if (!isProjectNameConflictError(error)) throw error

      const targetWorkspaceName = availableWorkspaces.value.find(workspace => workspace.id === targetWorkspaceId)?.name
        || 'the target workspace'
      const renamed = await transferConflictModal.open({
        projectId: resource.id,
        projectName: projectName || defaultProjectName(resource.name, transferType),
        targetWorkspaceId,
        targetWorkspaceName,
        transferType
      }).result

      if (!renamed) return false
      projectName = renamed
    }
  }
}

async function onSubmit(event: FormSubmitEvent<Schema>) {
  if (selectedResources.value.length === 0) return

  isSubmitting.value = true
  try {
    if (isBatchProjectShare.value) {
      let successCount = 0
      let failedCount = 0
      let cancelledCount = 0

      for (const resource of selectedResources.value) {
        try {
          const transferred = await requestProjectTransfer(
            resource,
            event.data.targetWorkspaceId,
            event.data.transferType,
            event.data.message
          )
          if (transferred) successCount++
          else cancelledCount++
        } catch {
          failedCount++
        }
      }

      if (successCount === 0) {
        if (cancelledCount > 0 && failedCount === 0) return
        toast.add({
          title: 'No requests created',
          description: 'The selected projects could not be shared. Check existing transfers and permissions.',
          color: 'error'
        })
        return
      }

      await Promise.all([
        refreshUserTransfers(),
        refreshWorkspaceTransfers(props.currentWorkspaceId),
        refreshWorkspaceTransfers(event.data.targetWorkspaceId)
      ])
      toast.add({
        title: event.data.transferType === 'MOVE' ? 'Transfer requests created' : 'Copy requests created',
        description: failedCount > 0
          ? `${successCount} created; ${failedCount} could not be created.`
          : `${successCount} project${successCount === 1 ? '' : 's'} shared.`,
        color: failedCount > 0 ? 'warning' : 'success',
        icon: failedCount > 0 ? 'i-lucide-triangle-alert' : 'i-lucide-check'
      })
      emit('transferred')
      emit('close', true)
      return
    }

    const resource = selectedResources.value[0]!
    if (props.resourceType === 'PROJECT') {
      const transferred = await requestProjectTransfer(
        resource,
        event.data.targetWorkspaceId,
        event.data.transferType,
        event.data.message
      )
      if (!transferred) return
    } else {
      const body = {
        resourceId: resource.id,
        resourceType: props.resourceType,
        targetWorkspaceId: event.data.targetWorkspaceId,
        transferType: event.data.transferType,
        message: event.data.message
      }

      await $fetch(endpoint.value, { method: 'POST', body })
    }
    await Promise.all([
      refreshUserTransfers(),
      refreshWorkspaceTransfers(props.currentWorkspaceId),
      refreshWorkspaceTransfers(event.data.targetWorkspaceId)
    ])
    toast.add({ title: event.data.transferType === 'MOVE' ? 'Transfer Requested' : 'Copy Requested', color: 'success', icon: 'i-lucide-check' })
    emit('transferred')
    emit('close', true)
  } catch (error: unknown) {
    toast.add({ title: 'Request Failed', description: extractApiErrorMessage(error, 'Could not create the transfer request.'), color: 'error' })
  } finally {
    isSubmitting.value = false
  }
}

const transferTypeOptions = [
  { label: 'Move', value: 'MOVE', description: 'Transfer to another workspace' },
  { label: 'Copy', value: 'COPY', description: 'Duplicate to another workspace' }
]
</script>

<template>
  <UiResponsiveSlideover
    @close="emit('close', false)"
  >
    <template #header>
      <UiSlideoverHeader
        :title="shareTitle"
        icon="i-lucide-share-2"
        :description="isBatchProjectShare ? 'Request a move or copy for all selected projects.' : 'Request a move or copy to another workspace.'"
      />
    </template>

    <template #body>
      <UForm
        :id="formId"
        :schema="schema"
        :state="state"
        class="space-y-4"
        @submit="onSubmit"
      >
        <UiSlideoverSection
          title="Transfer Request"
          description="Choose the destination, transfer mode, and optional context."
          icon="i-lucide-arrow-right-left"
        >
          <div class="space-y-4">
            <UFormField label="Target Workspace" name="targetWorkspaceId" required>
              <USelect
                v-model="state.targetWorkspaceId"
                :items="availableWorkspaces"
                value-key="id"
                label-key="name"
                placeholder="Select workspace"
                :disabled="isSubmitting || availableWorkspaces.length === 0"
                class="w-full"
              />
            </UFormField>
            <UFormField label="Transfer Type" name="transferType" required>
              <URadioGroup v-model="state.transferType" :items="transferTypeOptions" :disabled="isSubmitting" />
            </UFormField>
            <UFormField label="Message (optional)" name="message">
              <UTextarea
                v-model="state.message"
                placeholder="Add a message for the workspace admin"
                :rows="2"
                :disabled="isSubmitting"
                class="w-full"
              />
            </UFormField>
            <UAlert
              v-if="state.transferType === 'MOVE'"
              icon="i-lucide-info"
              color="info"
              variant="subtle"
              :title="isBatchProjectShare ? 'Move will lock the projects until approved' : 'Move will lock the resource until approved'"
            />
          </div>
        </UiSlideoverSection>
      </UForm>
    </template>

    <template #footer>
      <UButton
        color="neutral"
        variant="ghost"
        :disabled="isSubmitting"
        @click="emit('close', false)"
      >
        Cancel
      </UButton>
      <UButton
        :form="formId"
        type="submit"
        icon="i-lucide-forward"
        :loading="isSubmitting"
        :disabled="!state.targetWorkspaceId || selectedResources.length === 0"
      >
        {{ state.transferType === 'MOVE' ? 'Request Move' : 'Request Copy' }}
      </UButton>
    </template>
  </UiResponsiveSlideover>
</template>
