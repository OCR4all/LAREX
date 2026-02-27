<script setup lang="ts">
import type { Task, TaskStatus } from '~/types/index'
import { useTaskKanban, KANBAN_COLUMNS } from '~/composables/use-task-kanban'

const props = defineProps<{
  tasks: Task[]
}>()

const emit = defineEmits<{
  refresh: []
  'task-click': [task: Task]
}>()

const tasksRef = computed(() => props.tasks)
const { columns, updateTaskStatus, isUpdating } = useTaskKanban(tasksRef)

async function onTaskMoved(taskId: string, newStatus: TaskStatus) {
  const success = await updateTaskStatus(taskId, newStatus)
  if (success) {
    emit('refresh')
  }
}
</script>

<template>
  <div class="flex gap-4 h-full overflow-x-auto p-4">
    <TaskBoardColumn
      v-for="column in columns"
      :key="column.id"
      :id="column.id"
      :title="column.title"
      :color="column.color"
      :tasks="column.tasks"
      :is-updating="isUpdating"
      @task-moved="onTaskMoved"
      @task-click="emit('task-click', $event)"
    />
  </div>
</template>
