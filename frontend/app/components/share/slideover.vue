<script setup lang="ts">
import { z } from 'zod'
import type { FormSubmitEvent } from '#ui/types'
import type { TransferableResourceType } from '@/types/capabilities'

interface Workspace {
  id: string
  name: string
}

const props = defineProps<{
  resourceId: string
  resourceName: string
  resourceType: TransferableResourceType
  currentWorkspaceId: string
}>()

const emit = defineEmits<{ close: [transferred: boolean], transferred: [] }>()

const toast = useToast()
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

const endpoint = computed(() =>
  props.resourceType === 'PROJECT' ? '/api/project-transfers' : '/api/resource-transfers'
)

async function onSubmit(event: FormSubmitEvent<Schema>) {
  isSubmitting.value = true
  try {
    const body = props.resourceType === 'PROJECT'
      ? { projectId: props.resourceId, targetWorkspaceId: event.data.targetWorkspaceId, transferType: event.data.transferType, message: event.data.message }
      : { resourceId: props.resourceId, resourceType: props.resourceType, targetWorkspaceId: event.data.targetWorkspaceId, transferType: event.data.transferType, message: event.data.message }

    await $fetch(endpoint.value, { method: 'POST', body })
    await Promise.all([
      refreshUserTransfers(),
      refreshWorkspaceTransfers(props.currentWorkspaceId),
      refreshWorkspaceTransfers(event.data.targetWorkspaceId)
    ])
    toast.add({ title: event.data.transferType === 'MOVE' ? 'Transfer Requested' : 'Copy Requested', color: 'success', icon: 'i-lucide-check' })
    emit('transferred')
    emit('close', true)
  } catch {
    toast.add({ title: 'Request Failed', color: 'error' })
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
        :title="`Share ${resourceName}`"
        icon="i-lucide-share-2"
        :description="resourceType"
      />
    </template>

    <template #body>
      <UForm
        :schema="schema"
        :state="state"
        class="space-y-4"
        @submit="onSubmit"
      >
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
          title="Move will lock the resource until approved"
        />
        <UButton
          type="submit"
          icon="i-lucide-forward"
          variant="solid"
          :loading="isSubmitting"
          :disabled="!state.targetWorkspaceId"
        >
          {{ state.transferType === 'MOVE' ? 'Request Move' : 'Request Copy' }}
        </UButton>
      </UForm>
    </template>
  </UiResponsiveSlideover>
</template>
