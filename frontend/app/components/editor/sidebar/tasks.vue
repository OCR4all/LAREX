<script setup lang="ts">
import { formatDistanceToNow, parseISO } from 'date-fns'
import type { LinkedTask, Subtask } from '~/types/index'

const props = defineProps<{
  openTasks: Subtask[]
  taskById: Record<string, LinkedTask>
  isPageLocked: boolean
  isLoading: boolean
  onCompleteSubtask: (subtask: Subtask) => void
}>()

function getTaskTitle(taskId: string) {
  return props.taskById[taskId]?.title ?? 'Task'
}

function getTaskDueDate(taskId: string) {
  return props.taskById[taskId]?.dueDate ?? null
}

function formatDueDate(dateString: string) {
  return formatDistanceToNow(parseISO(dateString), { addSuffix: true })
}

function getTaskDescription(subtask: Subtask) {
  return subtask.description || subtask.taskDescription || null
}
</script>

<template>
  <div>
    <div v-if="openTasks.length === 0" class="text-xs text-muted">
      No open tasks for this page.
    </div>

    <div v-else class="space-y-2">
      <div
        v-for="subtask in openTasks"
        :key="subtask.id"
        class="rounded-sm border border-default/60 bg-elevated/40 p-2"
      >
        <div class="flex items-start justify-between gap-2">
          <div class="min-w-0">
            <p class="text-sm font-medium truncate">{{ subtask.title }}</p>
            <p v-if="getTaskDescription(subtask)" class="text-xs text-muted mt-0.5 line-clamp-2">
              {{ getTaskDescription(subtask) }}
            </p>
            <p class="text-xs text-muted mt-1 truncate">
              Task: {{ getTaskTitle(subtask.taskId) }}
            </p>
            <p
              v-if="getTaskDueDate(subtask.taskId)"
              class="text-xs text-muted"
            >
              Due {{ formatDueDate(getTaskDueDate(subtask.taskId)!) }}
            </p>
          </div>

          <UButton
            size="xs"
            color="success"
            variant="soft"
            icon="i-lucide-check"
            :disabled="isPageLocked"
            @click="onCompleteSubtask(subtask)"
          />
        </div>
      </div>
    </div>

    <div v-if="isPageLocked" class="text-xs text-warning mt-2">
      Page is locked. Completing tasks is disabled.
    </div>

    <div v-if="isLoading" class="text-xs text-muted mt-2">
      Loading…
    </div>
  </div>
</template>
