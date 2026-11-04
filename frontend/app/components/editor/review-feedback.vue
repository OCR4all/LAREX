<script setup lang="ts">
import type { PageWorkflowEvent } from '@/types/project-page'

const props = defineProps<{
  projectId: string
  pageId: string
}>()

const emit = defineEmits<{ history: [] }>()

const feedback = ref<PageWorkflowEvent | null>(null)
const title = computed(() => {
  const actor = feedback.value?.actor
  if (actor?.firstName && actor.lastName) return `${actor.firstName} ${actor.lastName} requested changes`
  return actor?.username ? `${actor.username} requested changes` : 'Changes requested'
})

watch(() => [props.projectId, props.pageId], async ([projectId, pageId]) => {
  feedback.value = null
  if (!projectId || !pageId) return
  try {
    const events = await fetchPageWorkflowEvents(projectId, pageId)
    if (pageId !== props.pageId) return
    feedback.value = events.find(event => event.action === 'REQUEST_CHANGES') ?? null
  } catch (error) {
    console.warn('Failed to load review feedback:', error)
  }
}, { immediate: true })
</script>

<template>
  <div class="flex items-start gap-2">
    <UIcon name="i-lucide-message-square-warning" class="mt-0.5 size-3.5 shrink-0 text-warning" />
    <div class="min-w-0 flex-1">
      <p class="text-xs font-medium text-highlighted">
        {{ title }}
      </p>
      <p v-if="feedback?.comment" class="mt-0.5 max-h-24 overflow-y-auto whitespace-pre-wrap break-words text-xs leading-4 text-default">
        {{ feedback.comment }}
      </p>
      <UButton
        class="mt-1 p-0"
        size="xs"
        color="neutral"
        variant="link"
        label="Review history"
        @click="emit('history')"
      />
    </div>
  </div>
</template>
