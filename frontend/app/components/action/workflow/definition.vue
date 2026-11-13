<script setup lang="ts">
import type { ActionDefinition } from '~/types/action'
import type { ActionWorkflow } from '~/types/action-workflow'

const props = defineProps<{ workflowId?: string, processors: ActionDefinition[], initialName?: string, initialDescription?: string, toolbarTarget?: string, metadataTarget?: string }>()
const emit = defineEmits<{ saved: [workflow: ActionWorkflow], dirty: [value: boolean], busy: [value: boolean], metadata: [value: { name: string, description: string }] }>()
const requestFetch = useRequestFetch()
const workflowKey = computed(() => globalKey('admin', 'action-workflows', props.workflowId ?? 'new'))
const { data: workflow, pending: loading, error: loadError, refresh: load } = useAsyncData(
  workflowKey,
  () => props.workflowId ? requestFetch<ActionWorkflow>(`/api/admin/actions/workflows/${props.workflowId}`) : Promise.resolve(null),
  { default: () => null }
)
const error = computed(() => loadError.value ? extractApiErrorDetails(loadError.value, 'Could not load workflow.').message : '')
function saved(result: ActionWorkflow) {
  workflow.value = result
  emit('saved', result)
}
</script>

<template>
  <section class="space-y-4" aria-label="Workflow definition">
    <UAlert
      v-if="error"
      title="Workflow unavailable"
      :description="error"
      color="error"
    >
      <template #actions>
        <UButton label="Retry" @click="load()" />
      </template>
    </UAlert>
    <USkeleton v-if="loading" class="h-96" />
    <ActionWorkflowEditor
      v-if="!loading && (workflow || !workflowId)"
      :workflow="workflow"
      :processors="processors"
      :initial-name="initialName"
      :initial-description="initialDescription"
      :toolbar-target="toolbarTarget"
      :metadata-target="metadataTarget"
      @saved="saved"
      @dirty="emit('dirty', $event)"
      @busy="emit('busy', $event)"
      @metadata="emit('metadata', $event)"
    />
  </section>
</template>
