<script setup lang="ts">
import type { ActionDefinition } from '~/types/action'
import type { ActionWorkflow } from '~/types/action-workflow'
import { extractApiErrorDetails } from '~/utils/api-error'

const props = defineProps<{ workflowId?: string, processors: ActionDefinition[], initialName?: string, initialDescription?: string, toolbarTarget?: string, metadataTarget?: string }>()
const emit = defineEmits<{ saved: [workflow: ActionWorkflow], dirty: [value: boolean], busy: [value: boolean], metadata: [value: { name: string, description: string }] }>()
const workflow = ref<ActionWorkflow | null>(null)
const error = ref('')
const loading = ref(true)
let generation = 0
async function load() {
  const request = ++generation
  loading.value = true
  error.value = ''
  if (!props.workflowId) {
    workflow.value = null
    loading.value = false
    return
  }
  try {
    const result = await $fetch<ActionWorkflow>(`/api/admin/actions/workflows/${props.workflowId}`)
    if (request === generation) workflow.value = result
  } catch (cause) {
    if (request === generation) error.value = extractApiErrorDetails(cause, 'Could not load workflow.').message
  } finally {
    if (request === generation) loading.value = false
  }
}
function saved(result: ActionWorkflow) {
  workflow.value = result
  emit('saved', result)
}
watch(() => props.workflowId, load, { immediate: true })
onBeforeUnmount(() => {
  generation++
})
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
        <UButton label="Retry" @click="load" />
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
