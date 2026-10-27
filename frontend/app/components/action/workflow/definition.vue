<script setup lang="ts">
import type { ActionDefinition } from '~/types/action'
import type { ActionWorkflow, ActionWorkflowRun } from '~/types/action-workflow'
import { actionRunStatusLabel } from '~/utils/action-run-status'
import { extractApiErrorDetails } from '~/utils/api-error'

const props = defineProps<{ workflowId?: string, processors: ActionDefinition[] }>()
const emit = defineEmits<{ saved: [workflow: ActionWorkflow], dirty: [value: boolean], busy: [value: boolean] }>()
const workflow = ref<ActionWorkflow | null>(null)
const runs = ref<ActionWorkflowRun[]>([])
const cancelling = ref('')
const error = ref('')
const loading = ref(true)
let generation = 0
async function load() {
  const request = ++generation
  loading.value = true
  error.value = ''
  if (!props.workflowId) {
    workflow.value = null
    runs.value = []
    loading.value = false
    return
  }
  try {
    const [result, history] = await Promise.all([
      $fetch<ActionWorkflow>(`/api/admin/actions/workflows/${props.workflowId}`),
      $fetch<ActionWorkflowRun[]>(`/api/admin/actions/workflows/${props.workflowId}/runs`)
    ])
    if (request === generation) {
      workflow.value = result
      runs.value = history
    }
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
async function refreshRuns() {
  if (!props.workflowId) return
  try {
    runs.value = await $fetch<ActionWorkflowRun[]>(`/api/admin/actions/workflows/${props.workflowId}/runs`)
  } catch (cause) {
    error.value = extractApiErrorDetails(cause, 'Could not load workflow runs.').message
  }
}
async function cancel(run: ActionWorkflowRun) {
  cancelling.value = run.id
  try {
    await $fetch(`/api/admin/actions/workflows/${props.workflowId}/runs/${run.id}/cancel`, { method: 'POST' })
    await refreshRuns()
  } catch (cause) {
    error.value = extractApiErrorDetails(cause, 'Could not cancel workflow.').message
  } finally {
    cancelling.value = ''
  }
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
      @saved="saved"
      @dirty="emit('dirty', $event)"
      @busy="emit('busy', $event)"
    />
    <section v-if="workflow" class="space-y-3 rounded-lg border border-default bg-default p-4" aria-label="Workflow runs">
      <div class="flex justify-between gap-4">
        <h3 class="font-semibold">
          Workflow runs
        </h3>
        <UButton
          label="Refresh"
          icon="i-lucide-refresh-cw"
          variant="ghost"
          size="sm"
          :loading="loading"
          @click="refreshRuns"
        />
      </div>
      <p v-if="!runs.length" class="text-sm text-muted">
        No runs yet.
      </p>
      <div v-for="run in runs.slice(0, 20)" :key="run.id" class="flex flex-wrap items-center justify-between gap-3 border-t border-default pt-3">
        <div class="space-y-1">
          <p class="text-sm font-medium">
            {{ actionRunStatusLabel(run.status) }} · Step {{ run.stepRunIds.length }} of {{ run.steps.length }} · {{ run.pageIds.length }} pages
          </p>
          <p class="text-xs text-muted">
            {{ run.created }} · {{ run.id }}
          </p>
          <p v-if="run.errorMessage" class="text-sm text-error">
            {{ run.errorMessage }}
          </p>
        </div>
        <UButton
          v-if="run.canCancel"
          label="Cancel workflow"
          color="error"
          variant="outline"
          size="sm"
          :loading="cancelling === run.id"
          :disabled="!!cancelling || run.status === 'CANCEL_REQUESTED'"
          @click="cancel(run)"
        />
      </div>
    </section>
  </section>
</template>
