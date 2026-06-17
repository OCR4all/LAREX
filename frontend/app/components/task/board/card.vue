<script setup lang="ts">
import type { Task } from '~/types/index'
import { formatDistanceToNow, isPast, parseISO } from 'date-fns'

const props = defineProps<{
  task: Task
  isUpdating?: boolean
}>()

const emit = defineEmits<{
  click: [task: Task]
}>()

const priorityColor = computed(() => {
  switch (props.task.priority) {
    case 'LOW': return 'neutral'
    case 'MEDIUM': return 'info'
    case 'HIGH': return 'warning'
    case 'URGENT': return 'error'
  }
})

const isOverdue = computed(() => {
  if (!props.task.dueDate) return false
  if (props.task.status === 'COMPLETED' || props.task.status === 'CANCELLED') return false
  return isPast(parseISO(props.task.dueDate))
})

const dueDateText = computed(() => {
  if (!props.task.dueDate) return null
  const date = parseISO(props.task.dueDate)
  return formatDistanceToNow(date, { addSuffix: true })
})

const displayedAssignees = computed(() => {
  const users = props.task.assignedUsers || []
  return users.slice(0, 3)
})

const additionalCount = computed(() => {
  const users = props.task.assignedUsers || []
  return Math.max(0, users.length - 3)
})
</script>

<template>
  <div
    class="group relative bg-default border border-default rounded-sm p-3 cursor-pointer hover:border-primary transition-colors"
    :class="{ 'opacity-60': isUpdating }"
    @click="emit('click', task)"
  >
    <div v-if="isUpdating" class="absolute inset-0 flex items-center justify-center bg-default/50 rounded-sm">
      <UIcon name="i-lucide-loader-2" class="size-4 animate-spin" />
    </div>

    <div class="space-y-2">
      <div class="flex items-start justify-between gap-2">
        <h4 class="text-sm font-medium line-clamp-2">
          {{ task.title }}
        </h4>
        <UBadge
          :color="priorityColor"
          variant="subtle"
          size="xs"
        >
          {{ task.priority }}
        </UBadge>
      </div>

      <div v-if="task.dueDate" class="flex items-center gap-1 text-xs" :class="isOverdue ? 'text-error' : 'text-muted'">
        <UIcon :name="isOverdue ? 'i-lucide-alert-circle' : 'i-lucide-calendar'" class="size-3" />
        <span>{{ dueDateText }}</span>
      </div>

      <div v-if="displayedAssignees.length > 0" class="flex items-center gap-1">
        <div class="flex -space-x-1">
          <UAvatar
            v-for="user in displayedAssignees"
            :key="user.id"
            :alt="user.username"
            size="xs"
            class="ring-2 ring-default"
          />
        </div>
        <span v-if="additionalCount > 0" class="text-xs text-muted ml-1">
          +{{ additionalCount }}
        </span>
      </div>

      <TaskSubtaskProgress :task-id="task.id" />
    </div>
  </div>
</template>
