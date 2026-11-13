<script setup lang="ts">
import type { Task } from '~/types/index'

const props = withDefaults(defineProps<{
  tasks?: Task[]
}>(), {
  tasks: () => []
})

const emit = defineEmits<{
  'task-click': [task: Task]
  'task-delete': [task: Task]
}>()

const tasksRef = computed(() => props.tasks ?? [])
const { columns } = useTaskKanban(tasksRef)
</script>

<template>
  <div class="flex h-full items-start gap-5 overflow-x-auto px-4 py-5 lg:px-6">
    <TaskBoardColumn
      v-for="column in columns"
      :id="column.id"
      :key="column.id"
      :title="column.title"
      :tasks="column.tasks"
      @task-click="emit('task-click', $event)"
      @task-delete="emit('task-delete', $event)"
    />
  </div>
</template>
