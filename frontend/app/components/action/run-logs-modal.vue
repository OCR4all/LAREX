<script setup lang="ts">
import type { ActionRunDetail } from '@/types/action'
import { extractApiErrorMessage } from '@/utils/api-error'

const props = defineProps<{
  workspaceId: string
  runId: string
  title: string
  description: string
}>()
const emit = defineEmits<{ close: [value: boolean] }>()
const actionRunsStore = useActionRunsStore()
const updated = computed(() => actionRunsStore.runsById.get(props.runId)?.updated)
const { data, pending, error, refresh } = useFetch<ActionRunDetail>(
  `/api/workspaces/${props.workspaceId}/actions/runs/${props.runId}`,
  {
    key: wsKey(props.workspaceId, 'action-run-logs', props.runId),
    watch: [updated]
  }
)
const logs = computed(() => {
  if (data.value?.logEvents?.length) {
    const formatter = new Intl.DateTimeFormat(undefined, { dateStyle: 'medium', timeStyle: 'medium' })
    return data.value.logEvents.map(event =>
      `[${formatter.format(new Date(event.created))}] ${event.level}: ${event.message}`
    ).join('\n')
  }
  return data.value?.logText || 'No logs recorded yet.'
})
</script>

<template>
  <UModal
    :title="title"
    :description="description"
    :ui="{ content: 'sm:max-w-4xl' }"
    @after:leave="emit('close', false)"
  >
    <template #body>
      <UAlert
        v-if="error"
        color="error"
        variant="soft"
        title="Could not load logs"
        :description="extractApiErrorMessage(error, 'Please try again.')"
      />
      <p v-else-if="pending && !data" role="status" class="text-sm text-muted">
        Loading logs…
      </p>
      <pre
        v-else
        class="max-h-[65vh] overflow-auto whitespace-pre-wrap break-words rounded-sm bg-elevated p-4 text-xs"
      >{{ logs }}</pre>
    </template>
    <template #footer>
      <UButton
        color="neutral"
        variant="outline"
        icon="i-lucide-refresh-cw"
        :loading="pending"
        @click="refresh()"
      >
        Refresh
      </UButton>
    </template>
  </UModal>
</template>
