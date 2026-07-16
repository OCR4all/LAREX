<script setup lang="ts">
import { VueDraggable } from 'vue-draggable-plus'
import type { Task, TaskStatus } from '~/types/index'

const props = defineProps<{
  id: TaskStatus
  title: string
  tasks: Task[]
  isUpdating: (taskId: string) => boolean
}>()

const emit = defineEmits<{
  'task-moved': [taskId: string, newStatus: TaskStatus]
  'task-click': [task: Task]
  'task-delete': [task: Task]
}>()

const localTasks = ref<Task[]>([])

const columnAppearance = computed(() => {
  switch (props.id) {
    case 'IN_PROGRESS':
      return {
        icon: 'i-lucide-loader-circle',
        iconClass: 'text-info',
        countClass: 'bg-info/10 text-info ring-info/20'
      }
    case 'COMPLETED':
      return {
        icon: 'i-lucide-circle-check',
        iconClass: 'text-success',
        countClass: 'bg-success/10 text-success ring-success/20'
      }
    case 'CANCELLED':
      return {
        icon: 'i-lucide-circle-x',
        iconClass: 'text-error',
        countClass: 'bg-error/10 text-error ring-error/20'
      }
    default:
      return {
        icon: 'i-lucide-circle-dot',
        iconClass: 'text-muted',
        countClass: 'bg-muted text-muted ring-default'
      }
  }
})

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
  <section
    class="flex min-w-[19rem] max-w-[22rem] flex-1 flex-col overflow-hidden rounded-2xl border border-default bg-elevated/50 shadow-xs"
    :aria-label="`${title} tasks`"
  >
    <header class="flex items-center gap-3 px-4 py-4">
      <span class="flex size-8 items-center justify-center rounded-full bg-default shadow-xs ring-1 ring-default">
        <UIcon
          :name="columnAppearance.icon"
          class="size-4.5"
          :class="columnAppearance.iconClass"
        />
      </span>
      <h3 class="flex-1 text-base font-semibold tracking-tight text-highlighted">
        {{ title }}
      </h3>
      <span
        class="flex min-w-6 items-center justify-center rounded-full px-2 py-0.5 text-xs font-semibold ring-1 ring-inset"
        :class="columnAppearance.countClass"
      >
        {{ tasks.length }}
      </span>
    </header>

    <VueDraggable
      v-model="localTasks"
      :group="{ name: 'tasks', pull: true, put: true }"
      item-key="id"
      :animation="150"
      ghost-class="task-card-ghost"
      drag-class="cursor-grabbing"
      class="min-h-40 space-y-3 px-3 pb-3"
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
          @delete="emit('task-delete', task)"
        />
      </div>

      <div
        v-if="localTasks.length === 0"
        class="flex min-h-32 items-center justify-center rounded-xl border border-dashed border-default bg-default/40 p-4 text-center text-muted"
      >
        <div class="space-y-2">
          <UIcon name="i-lucide-move-down" class="mx-auto size-4" />
          <p class="text-xs">
            Drop tasks here
          </p>
        </div>
      </div>
    </VueDraggable>
  </section>
</template>

<style scoped>
:deep(.task-card-ghost) {
  opacity: 0.45;
}
</style>
