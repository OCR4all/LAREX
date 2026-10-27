<script setup lang="ts">
import type { ActionWorkflowRun } from '~/types/action-workflow'
import { actionRunStatusLabel } from '~/utils/action-run-status'
import { extractApiErrorDetails } from '~/utils/api-error'

const props = defineProps<{ workspaceId: string, projectId: string, projectName?: string | null }>()
const store = useActionRunsStore()
const runs = ref<ActionWorkflowRun[]>([])
const busy = ref(false)
const historyError = ref('')
const base = computed(() => `/api/workspaces/${props.workspaceId}/actions/projects/${props.projectId}/workflow-runs`)
async function refreshRuns() {
  try {
    const [result] = await Promise.all([$fetch<ActionWorkflowRun[]>(base.value), store.refreshProjectRuns(props.workspaceId, props.projectId, props.projectName)])
    runs.value = result
    historyError.value = ''
  } catch (cause) {
    historyError.value = extractApiErrorDetails(cause, 'Could not refresh workflow runs.').message
  }
}
async function cancel(run: ActionWorkflowRun) {
  busy.value = true
  try {
    await $fetch(`${base.value}/${run.id}/cancel`, { method: 'POST' })
    await refreshRuns()
  } catch (cause) {
    historyError.value = extractApiErrorDetails(cause, 'Could not cancel workflow.').message
  } finally { busy.value = false }
}
function stepStatus(run: ActionWorkflowRun, index: number) {
  if (run.status === 'COMPLETED' || index < run.stepRunIds.length - 1) return 'Completed'
  const id = run.stepRunIds[index]
  if (!id) return run.completedAt ? 'Not run' : 'Waiting'
  const child = store.runsById.get(id)
  if (child) return actionRunStatusLabel(child.status)
  if (run.status === 'CANCELLED') return 'Cancelled'
  if (run.status === 'FAILED') return 'Stopped'
  return 'Loading…'
}
function progress(run: ActionWorkflowRun) {
  if (run.status === 'COMPLETED') return run.steps.length
  return Math.max(0, run.stepRunIds.length - 1) + (store.runsById.get(run.stepRunIds.at(-1) ?? '')?.status === 'COMPLETED' ? 1 : 0)
}
watch(() => store.workflowRunsArray, (updates) => {
  const byId = new Map(updates.map(run => [run.id, run]))
  runs.value = runs.value.map(run => byId.get(run.id) ?? run)
})
onMounted(refreshRuns)
</script>

<template>
  <section class="space-y-3" aria-label="Workflow run history">
    <div class="flex items-center justify-between gap-3">
      <h3 class="font-semibold">
        Workflow runs
      </h3>
      <UButton
        label="Refresh"
        icon="i-lucide-refresh-cw"
        size="sm"
        variant="ghost"
        :disabled="busy"
        @click="refreshRuns"
      />
    </div>
    <UAlert
      v-if="historyError"
      title="History unavailable"
      :description="historyError"
      color="error"
    />
    <p v-if="!runs.length" class="text-sm text-muted">
      No workflow runs yet.
    </p>
    <div v-for="run in runs.slice(0, 10)" :key="run.id" class="space-y-2 rounded-lg border border-default p-4">
      <div class="flex items-center justify-between gap-3">
        <p class="font-medium">
          {{ run.workflowName }}
        </p>
        <UBadge :color="run.status === 'COMPLETED' ? 'success' : run.status === 'FAILED' ? 'error' : 'neutral'" variant="subtle">
          {{ actionRunStatusLabel(run.status) }}
        </UBadge>
      </div>
      <p class="text-sm text-muted">
        {{ progress(run) }} / {{ run.steps.length }} steps completed · {{ run.pageIds.length }} page{{ run.pageIds.length === 1 ? '' : 's' }}
      </p>
      <ol class="space-y-1 text-xs text-muted">
        <li v-for="(step, index) in run.steps" :key="index">
          {{ index + 1 }}. {{ run.stepNames[index] || 'Processor' }}
          · {{ stepStatus(run, index) }}
        </li>
      </ol>
      <p v-if="run.errorMessage" class="text-sm text-error">
        {{ run.errorMessage }}
      </p>
      <UButton
        v-if="run.canCancel"
        label="Cancel workflow"
        color="error"
        variant="outline"
        size="sm"
        :loading="busy"
        :disabled="run.status === 'CANCEL_REQUESTED'"
        @click="cancel(run)"
      />
    </div>
    <p v-if="runs.length" class="text-xs text-muted">
      Individual step logs and reports are available in Action history.
    </p>
  </section>
</template>
