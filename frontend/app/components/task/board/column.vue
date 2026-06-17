<script setup lang="ts">
import { VueDraggable } from 'vue-draggable-plus'
import type { Task, TaskStatus } from '~/types/index'

const props = defineProps<{
  id: TaskStatus
  title: string
  color: 'neutral' | 'info' | 'success' | 'error'
  tasks: Task[]
  isUpdating: (taskId: string) => boolean
}>()

const emit = defineEmits<{
  'task-moved': [taskId: string, newStatus: TaskStatus]
  'task-click': [task: Task]
}>()

const localTasks = ref<Task[]>([])

watch(() => props.tasks, (newTasks) => {
  localTasks.value = [...newTasks]
}, { immediate: true, deep: true })

function onDragEnd(event: any) {
  if (event.to !== event.from) {
    return
  }
}

function onAdd(event: any) {
  const taskId = event.item?.dataset?.taskId
  if (taskId) {
    emit('task-moved', taskId, props.id)
  }
}
</script>

<template>
  <div class="flex flex-col h-full min-w-[280px] max-w-[320px] flex-1 bg-elevated/30 rounded-sm">
    <div class="flex items-center gap-2 px-3 py-3 border-b border-default">
      <UBadge :color="color" variant="subtle" size="sm">
        {{ tasks.length }}
      </UBadge>
      <h3 class="text-sm font-medium">
        {{ title }}
      </h3>
    </div>

    <VueDraggable
      v-model="localTasks"
      :group="{ name: 'tasks', pull: true, put: true }"
      item-key="id"
      :animation="150"
      ghost-class="opacity-50"
      drag-class="cursor-grabbing"
      class="flex-1 overflow-y-auto p-2 space-y-2 min-h-[200px]"
      @end="onDragEnd"
      @add="onAdd"
    >
      <div
        v-for="task in localTasks"
        :key="task.id"
        :data-task-id="task.id"
      >
        <TaskBoardCard
          :task="task"
          :is-updating="isUpdating(task.id)"
          @click="emit('task-click', task)"
        />
      </div>

      <div
        v-if="localTasks.length === 0"
        class="h-full flex items-center justify-center p-4 text-center text-muted"
      >
        <p class="text-xs">
          Drop tasks here
        </p>
      </div>
    </VueDraggable>
  </div>
</template>
