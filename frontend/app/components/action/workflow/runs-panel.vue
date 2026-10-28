<script setup lang="ts">
import type { ActionWorkflowRun } from '~/types/action-workflow'
import { actionRunStatusColor, actionRunStatusLabel } from '~/utils/action-run-status'
import { extractApiErrorDetails } from '~/utils/api-error'

const props = defineProps<{ workflowId: string }>()
const emit = defineEmits<{ close: [] }>()
const runs = ref<ActionWorkflowRun[]>([])
const loading = ref(false)
const cancelling = ref('')
const error = ref('')
let generation = 0
const base = computed(() => `/api/admin/actions/workflows/${props.workflowId}/runs`)
const activeCount = computed(() => runs.value.filter(isActive).length)
const summary = computed(() => runs.value.length
  ? `${activeCount.value} active · ${runs.value.length - activeCount.value} history`
  : 'No runs recorded for this workflow.')

function isActive(run: ActionWorkflowRun) {
  return run.status === 'RUNNING' || run.status === 'CANCEL_REQUESTED'
}
async function load() {
  const request = ++generation
  loading.value = true
  error.value = ''
  try {
    const result = await $fetch<ActionWorkflowRun[]>(base.value)
    if (request === generation) runs.value = result
  } catch (cause) {
    if (request === generation) error.value = extractApiErrorDetails(cause, 'Could not load workflow runs.').message
  } finally {
    if (request === generation) loading.value = false
  }
}
async function cancel(run: ActionWorkflowRun) {
  cancelling.value = run.id
  error.value = ''
  try {
    await $fetch(`${base.value}/${run.id}/cancel`, { method: 'POST' })
    await load()
  } catch (cause) {
    error.value = extractApiErrorDetails(cause, 'Could not cancel workflow.').message
  } finally {
    cancelling.value = ''
  }
}
watch(() => props.workflowId, () => {
  runs.value = []
  void load()
}, { immediate: true })
onBeforeUnmount(() => {
  generation++
})

function statusIcon(status: ActionWorkflowRun['status']) {
  if (status === 'COMPLETED') return 'i-lucide-check-circle'
  if (status === 'FAILED') return 'i-lucide-x-circle'
  if (status === 'CANCELLED') return 'i-lucide-circle-slash'
  if (status === 'CANCEL_REQUESTED') return 'i-lucide-ban'
  return 'i-lucide-loader-circle'
}
function statusTextClass(status: ActionWorkflowRun['status']) {
  if (status === 'COMPLETED') return 'text-success'
  if (status === 'FAILED') return 'text-error'
  if (status === 'CANCELLED') return 'text-muted'
  if (status === 'CANCEL_REQUESTED') return 'text-warning'
  return 'text-primary'
}
function statusColor(status: ActionWorkflowRun['status']) {
  return status === 'CANCELLED' ? 'neutral' : actionRunStatusColor(status)
}
function formatDate(value: string) {
  return new Intl.DateTimeFormat(undefined, { month: 'short', day: 'numeric', hour: '2-digit', minute: '2-digit' }).format(new Date(value))
}
function duration(run: ActionWorkflowRun) {
  if (!run.completedAt) return 'In progress'
  const seconds = Math.max(0, Math.round((Date.parse(run.completedAt) - Date.parse(run.created)) / 1000))
  return seconds < 60 ? `${seconds}s` : `${Math.floor(seconds / 60)}m ${seconds % 60}s`
}
</script>

<template>
  <aside
    class="w-96 shrink-0 overflow-y-auto border-l border-neutral-200 bg-neutral-50/30 dark:border-neutral-700 dark:bg-neutral-800/50"
    aria-label="Workflow runs"
  >
    <div class="border-b border-neutral-200 p-4 dark:border-neutral-700 lg:p-5">
      <div class="mb-3 flex items-center justify-between gap-2">
        <div>
          <h2 class="text-sm font-semibold">
            Workflow Runs
          </h2>
          <p class="text-xs text-muted">
            {{ summary }}
          </p>
        </div>
        <UButton
          color="neutral"
          variant="ghost"
          icon="i-lucide-x"
          size="sm"
          aria-label="Close workflow runs sidebar"
          @click="emit('close')"
        />
      </div>
      <UButton
        color="neutral"
        variant="outline"
        icon="i-lucide-refresh-cw"
        size="sm"
        :loading="loading"
        @click="load"
      >
        Refresh
      </UButton>
    </div>

    <div class="p-3 lg:p-4">
      <UAlert
        v-if="error"
        class="mb-3"
        color="error"
        variant="subtle"
        title="Workflow runs unavailable"
        :description="error"
      />
      <div v-if="loading && !runs.length" class="space-y-2">
        <USkeleton class="h-20 w-full" />
        <USkeleton class="h-20 w-full" />
      </div>
      <div v-else-if="!runs.length && !error" class="rounded-sm border border-default p-3 text-sm text-muted">
        No runs recorded for this workflow.
      </div>
      <div v-else-if="runs.length" class="divide-y divide-default rounded-sm border border-default bg-default">
        <div v-for="run in runs" :key="run.id" class="flex items-start gap-3 p-3">
          <UIcon
            :name="statusIcon(run.status)"
            class="mt-0.5 size-4 shrink-0"
            :class="statusTextClass(run.status)"
          />
          <div class="min-w-0 flex-1">
            <div class="flex items-center justify-between gap-2">
              <p class="truncate text-sm font-medium">
                {{ actionRunStatusLabel(run.status) }}
              </p>
              <UBadge
                size="sm"
                variant="soft"
                :color="statusColor(run.status)"
                :title="`${run.stepRunIds.length} of ${run.steps.length} steps started`"
              >
                Step {{ run.stepRunIds.length }}/{{ run.steps.length }}
              </UBadge>
            </div>
            <p class="mt-1 text-xs text-muted">
              {{ run.pageIds.length }} page{{ run.pageIds.length === 1 ? '' : 's' }} · {{ formatDate(run.created) }} · {{ duration(run) }}
            </p>
            <p class="mt-1 truncate font-mono text-xs text-dimmed" :title="run.id">
              {{ run.id }}
            </p>
            <p v-if="run.errorMessage" class="mt-1 break-words text-xs text-error">
              {{ run.errorMessage }}
            </p>
          </div>
          <UButton
            v-if="run.canCancel"
            color="warning"
            variant="ghost"
            icon="i-lucide-ban"
            size="sm"
            aria-label="Cancel workflow run"
            title="Cancel workflow run"
            :loading="cancelling === run.id"
            :disabled="!!cancelling || run.status === 'CANCEL_REQUESTED'"
            @click="cancel(run)"
          />
        </div>
      </div>
    </div>
  </aside>
</template>
