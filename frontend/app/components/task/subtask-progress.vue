<script setup lang="ts">
import type { SubtaskProgress } from '~/types/index'

const props = defineProps<{
  taskId: string
  compact?: boolean
}>()

const { data: progress } = await useFetch<SubtaskProgress>(
  () => `/api/tasks/${props.taskId}/subtasks/progress`,
  {
    key: computed(() => globalKey('tasks', props.taskId, 'subtasks-progress')),
    default: () => ({ total: 0, completed: 0, percentage: 0 })
  }
)

const hasProgress = computed(() => (progress.value?.total ?? 0) > 0)

const circleDashOffset = computed(() => 100 - (progress.value?.percentage ?? 0))
</script>

<template>
  <div
    v-if="hasProgress && compact"
    class="ml-auto flex shrink-0 items-center gap-1.5 rounded-lg bg-elevated px-2 py-1 text-xs font-semibold text-muted ring-1 ring-inset ring-default"
    :aria-label="`${progress!.percentage}% of subtasks completed`"
  >
    <svg class="size-4 -rotate-90" viewBox="0 0 36 36" aria-hidden="true">
      <circle
        class="stroke-accented"
        cx="18"
        cy="18"
        r="15.9"
        fill="none"
        stroke-width="3"
      />
      <circle
        class="stroke-primary transition-all"
        cx="18"
        cy="18"
        r="15.9"
        fill="none"
        stroke-width="3"
        stroke-linecap="round"
        stroke-dasharray="100"
        :stroke-dashoffset="circleDashOffset"
      />
    </svg>
    {{ progress!.percentage }}%
  </div>

  <div v-else-if="hasProgress" class="space-y-1">
    <UProgress v-model="progress!.percentage" size="xs" />
    <div class="text-xs text-muted">
      {{ progress!.completed }}/{{ progress!.total }}
    </div>
  </div>
</template>
