<script setup lang="ts">
import type { DropdownMenuItem } from '@nuxt/ui'
import type { Task } from '~/types/index'
import { DEFAULT_TASK_CAPABILITIES } from '@/types/capabilities'
import { format, isPast, parseISO } from 'date-fns'

const props = defineProps<{
  task: Task
  isUpdating?: boolean
}>()

const emit = defineEmits<{
  click: [task: Task]
  delete: [task: Task]
}>()

const { allow } = useActionVisibility()
const canDelete = computed(() => allow({
  ...DEFAULT_TASK_CAPABILITIES,
  ...(props.task.capabilities ?? {})
}.canDelete))

const actionItems = computed<DropdownMenuItem[][]>(() => {
  const groups: DropdownMenuItem[][] = [[{
    label: 'Open',
    icon: 'i-lucide-external-link',
    onSelect: () => emit('click', props.task)
  }]]

  if (canDelete.value) {
    groups.push([{
      label: 'Delete',
      icon: 'i-lucide-trash',
      color: 'error' as const,
      onSelect: () => emit('delete', props.task)
    }])
  }

  return groups
})

const priorityColor = computed(() => {
  switch (props.task.priority) {
    case 'LOW': return 'neutral'
    case 'MEDIUM': return 'info'
    case 'HIGH': return 'warning'
    case 'URGENT': return 'error'
    default: return 'neutral'
  }
})

const priorityLabel = computed(() => {
  const value = props.task.priority.toLowerCase()
  return value.charAt(0).toUpperCase() + value.slice(1)
})

const isOverdue = computed(() => {
  if (!props.task.dueDate) return false
  if (props.task.status === 'COMPLETED' || props.task.status === 'CANCELLED') return false
  return isPast(parseISO(props.task.dueDate))
})

const dueDateText = computed(() => {
  if (!props.task.dueDate) return null
  const date = parseISO(props.task.dueDate)
  return format(date, 'd MMM yyyy')
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
    class="group relative cursor-pointer overflow-hidden rounded-xl border border-default bg-default shadow-xs transition duration-200 hover:-translate-y-0.5 hover:border-accented hover:shadow-md"
    :class="{ 'opacity-60': isUpdating }"
    @click="emit('click', task)"
  >
    <div v-if="isUpdating" class="absolute inset-0 z-10 flex items-center justify-center rounded-xl bg-default/70 backdrop-blur-[1px]">
      <UIcon name="i-lucide-loader-2" class="size-4 animate-spin" />
    </div>

    <div class="p-4">
      <div class="flex items-start justify-between gap-3">
        <h4 class="line-clamp-2 text-base font-semibold leading-5 text-highlighted">
          {{ task.title }}
        </h4>
        <div class="flex shrink-0 items-center gap-1">
          <UBadge
            :color="priorityColor"
            variant="subtle"
            size="xs"
            class="capitalize"
          >
            {{ priorityLabel }}
          </UBadge>
          <UDropdownMenu :items="actionItems" :content="{ align: 'end' }">
            <UButton
              class="task-card-action"
              icon="i-lucide-ellipsis-vertical"
              color="neutral"
              variant="ghost"
              size="xs"
              aria-label="Task actions"
              title="Task actions"
              @pointerdown.stop
              @click.stop
            />
          </UDropdownMenu>
        </div>
      </div>

      <p
        v-if="task.description"
        class="mt-1.5 line-clamp-2 text-sm leading-5 text-muted"
      >
        {{ task.description }}
      </p>

      <div class="mt-5 flex min-h-7 items-center justify-between gap-3">
        <div
          v-if="task.dueDate"
          class="flex items-center gap-1.5 text-xs font-medium"
          :class="isOverdue ? 'text-error' : 'text-muted'"
        >
          <UIcon :name="isOverdue ? 'i-lucide-calendar-x-2' : 'i-lucide-calendar-days'" class="size-4" />
          <span>{{ dueDateText }}</span>
        </div>

        <TaskSubtaskProgress :task-id="task.id" compact />
      </div>
    </div>

    <div class="flex min-h-14 items-center justify-between border-t border-default px-4 py-3">
      <div v-if="displayedAssignees.length > 0" class="flex items-center">
        <div class="flex -space-x-2">
          <UTooltip
            v-for="user in displayedAssignees"
            :key="user.id"
            :text="user.username"
          >
            <UAvatar
              :alt="user.username"
              size="sm"
              class="ring-2 ring-default"
            />
          </UTooltip>
        </div>
        <span v-if="additionalCount > 0" class="ml-2 text-xs font-medium text-muted">
          +{{ additionalCount }}
        </span>
      </div>
      <span v-else class="flex items-center gap-1.5 text-xs text-dimmed">
        <UIcon name="i-lucide-user-round" class="size-3.5" />
        Unassigned
      </span>

      <UIcon name="i-lucide-chevron-right" class="size-4 text-dimmed transition-transform group-hover:translate-x-0.5 group-hover:text-muted" />
    </div>
  </div>
</template>
