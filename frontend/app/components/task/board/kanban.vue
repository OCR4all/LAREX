<script setup lang="ts">
import type { Task, TaskStatus } from '~/types/index'

const props = withDefaults(defineProps<{
  tasks?: Task[]
}>(), {
  tasks: () => []
})

const emit = defineEmits<{
  'refresh': []
  'task-click': [task: Task]
  'task-delete': [task: Task]
}>()

const tasksRef = computed(() => props.tasks ?? [])
const { columns, updateTaskStatus, isUpdating } = useTaskKanban(tasksRef)

async function onTaskMoved(taskId: string, newStatus: TaskStatus) {
  const success = await updateTaskStatus(taskId, newStatus)
  if (success) {
    emit('refresh')
  }
}
</script>

<template>
  <div class="flex h-full items-start gap-5 overflow-x-auto px-4 py-5 lg:px-6">
    <TaskBoardColumn
      v-for="column in columns"
      :id="column.id"
      :key="column.id"
      :title="column.title"
      :tasks="column.tasks"
      :is-updating="isUpdating"
      @task-moved="onTaskMoved"
      @task-click="emit('task-click', $event)"
      @task-delete="emit('task-delete', $event)"
    />
  </div>
</template>
