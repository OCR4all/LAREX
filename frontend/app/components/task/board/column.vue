<script setup lang="ts">
import type { Task, TaskStatus } from '~/types/index'

const props = defineProps<{
  id: TaskStatus
  title: string
  tasks: Task[]
}>()

const emit = defineEmits<{
  'task-click': [task: Task]
  'task-delete': [task: Task]
}>()

const COUNT_CLASSES: Record<TaskStatus, string> = {
  OPEN: 'bg-muted text-muted ring-default',
  IN_PROGRESS: 'bg-info/10 text-info ring-info/20',
  COMPLETED: 'bg-success/10 text-success ring-success/20',
  PAUSED: 'bg-warning/10 text-warning ring-warning/20'
}

const ICON_CLASSES: Record<TaskStatus, string> = {
  OPEN: 'text-muted',
  IN_PROGRESS: 'text-info',
  COMPLETED: 'text-success',
  PAUSED: 'text-warning'
}

const EMPTY_TEXT: Record<TaskStatus, string> = {
  OPEN: 'Assignments with no finished Tasks appear here',
  IN_PROGRESS: 'Assignments with some finished Tasks appear here',
  COMPLETED: 'Assignments whose Tasks are all done appear here',
  PAUSED: 'Paused Assignments appear here'
}

const meta = computed(() => TASK_STATUS_META[props.id])
</script>

<template>
  <section
    class="flex min-w-[19rem] max-w-[22rem] flex-1 flex-col overflow-hidden rounded-2xl border border-default bg-elevated/50 shadow-xs"
    :aria-label="`${title} assignments`"
  >
    <header class="flex items-center gap-3 px-4 py-4">
      <span class="flex size-8 items-center justify-center rounded-full bg-default shadow-xs ring-1 ring-default">
        <UIcon :name="meta.icon" class="size-4.5" :class="ICON_CLASSES[id]" />
      </span>
      <h3 class="flex-1 text-base font-semibold tracking-tight text-highlighted">
        {{ title }}
      </h3>
      <span
        class="flex min-w-6 items-center justify-center rounded-full px-2 py-0.5 text-xs font-semibold ring-1 ring-inset"
        :class="COUNT_CLASSES[id]"
      >
        {{ tasks.length }}
      </span>
    </header>

    <div class="min-h-40 space-y-3 px-3 pb-3">
      <TaskBoardCard
        v-for="task in tasks"
        :key="task.id"
        :task="task"
        @click="emit('task-click', task)"
        @delete="emit('task-delete', task)"
      />

      <div
        v-if="tasks.length === 0"
        class="flex min-h-32 items-center justify-center rounded-xl border border-dashed border-default bg-default/40 p-4 text-center text-xs text-muted"
      >
        {{ EMPTY_TEXT[id] }}
      </div>
    </div>
  </section>
</template>
