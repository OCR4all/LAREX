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
</script>

<template>
  <div v-if="hasProgress" class="space-y-1">
    <UProgress v-model="progress!.percentage" size="xs" />
    <div class="text-xs text-muted">
      {{ progress!.completed }}/{{ progress!.total }}
    </div>
  </div>
</template>
