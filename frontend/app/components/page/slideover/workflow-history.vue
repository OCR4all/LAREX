<script setup lang="ts">
import type { PageWorkflowAction, PageWorkflowEvent, PageWorkflowEventAction } from '@/types/project-page'

const props = defineProps<{
  projectId: string
  pageId: string
  pageName: string
}>()

const emit = defineEmits<{ close: [] }>()

const isOpen = ref(true)
const events = ref<PageWorkflowEvent[]>([])
const isLoading = ref(true)
const loadError = ref<string | null>(null)

const eventLabels: Record<PageWorkflowEventAction, string> = {
  SUBMIT: 'Submitted for review',
  APPROVE: 'Approved',
  REQUEST_CHANGES: 'Requested changes',
  WITHDRAW: 'Withdrew from review',
  MARK_DONE: 'Marked as done',
  REOPEN: 'Reopened',
  ASSIGNMENT_SYNC: 'Updated by Assignment'
}

function eventIcon(event: PageWorkflowEvent) {
  return event.action === 'ASSIGNMENT_SYNC'
    ? PAGE_WORKFLOW_STATE_META[event.toState].icon
    : PAGE_WORKFLOW_ACTION_META[event.action as PageWorkflowAction].icon
}

function actorName(event: PageWorkflowEvent) {
  if (event.actor?.firstName && event.actor.lastName) return `${event.actor.firstName} ${event.actor.lastName}`
  return event.actor?.username ?? 'System'
}

onMounted(async () => {
  try {
    events.value = await fetchPageWorkflowEvents(props.projectId, props.pageId)
  } catch {
    loadError.value = 'Could not load the review history.'
  } finally {
    isLoading.value = false
  }
})

watch(isOpen, (open) => {
  if (!open) emit('close')
})
</script>

<template>
  <USlideover
    v-model:open="isOpen"
    title="Review history"
    :description="pageName"
  >
    <template #body>
      <p v-if="isLoading" class="text-sm text-muted">
        Loading…
      </p>
      <UAlert
        v-else-if="loadError"
        color="error"
        variant="subtle"
        icon="i-lucide-alert-circle"
        :title="loadError"
      />
      <p v-else-if="events.length === 0" class="text-sm text-muted">
        No state changes recorded yet.
      </p>
      <ol v-else class="space-y-4">
        <li v-for="event in events" :key="event.id" class="flex gap-3">
          <UIcon :name="eventIcon(event)" class="mt-0.5 size-4 shrink-0 text-muted" />
          <div class="min-w-0">
            <p class="text-sm font-medium text-highlighted">
              {{ eventLabels[event.action] }}
            </p>
            <p class="text-xs text-muted">
              {{ actorName(event) }} · {{ new Date(event.created).toLocaleString() }}
            </p>
            <p v-if="event.comment" class="mt-1 whitespace-pre-wrap break-words text-sm">
              {{ event.comment }}
            </p>
          </div>
        </li>
      </ol>
    </template>
  </USlideover>
</template>
