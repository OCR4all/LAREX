<script setup lang="ts">
import type { Subtask } from '~/types'

defineProps<{
  openSubtasks: Subtask[]
  completingSubtaskId: string | null
  canCompleteActivePageSubtasks: boolean
  isCompletingOpenSubtasks: boolean
  isSavingActiveCanvas: boolean
  canEditActiveCanvas: boolean
  isActivePageLocked: boolean
}>()

const emit = defineEmits<{
  completeSubtask: [subtask: Subtask]
  saveAndContinue: []
}>()

function getTaskDescription(subtask: Subtask) {
  return subtask.description || subtask.taskDescription || null
}
</script>

<template>
  <div class="absolute top-5 right-4 z-30 w-80 max-w-[calc(100vw-2rem)] rounded-xl border border-default bg-default/95 p-3 shadow-xl backdrop-blur">
    <h2 class="mb-2 text-xs font-semibold text-muted">
      Assigned tasks
    </h2>

    <div class="max-h-48 space-y-1 overflow-y-auto">
      <div v-for="subtask in openSubtasks" :key="subtask.id" class="flex items-start gap-2">
        <div class="flex min-w-0 flex-1 items-start gap-2">
          <UIcon name="i-lucide-circle-check" class="mt-0.5 size-3.5 shrink-0 text-muted" />
          <div class="min-w-0">
            <p class="truncate text-xs font-medium">
              {{ subtask.title }}
            </p>
            <p v-if="getTaskDescription(subtask)" class="line-clamp-2 text-[11px] leading-4 text-muted">
              {{ getTaskDescription(subtask) }}
            </p>
          </div>
        </div>
        <UTooltip :delay-duration="0" text="Mark task done" :content="{ side: 'top' }">
          <UButton
            size="xs"
            color="success"
            variant="ghost"
            icon="i-lucide-check"
            square
            :loading="completingSubtaskId === subtask.id"
            :disabled="isActivePageLocked || Boolean(completingSubtaskId) || isCompletingOpenSubtasks"
            :aria-label="`Mark ${subtask.title} done`"
            @click="emit('completeSubtask', subtask)"
          />
        </UTooltip>
      </div>
    </div>

    <p v-if="isActivePageLocked" class="mt-2 text-xs text-warning">
      This page is locked.
    </p>

    <UButton
      class="mt-3 w-full justify-center"
      size="sm"
      color="success"
      variant="soft"
      icon="i-lucide-check-check"
      label="Save & complete all"
      :loading="isSavingActiveCanvas || isCompletingOpenSubtasks"
      :disabled="!canCompleteActivePageSubtasks || !canEditActiveCanvas || isSavingActiveCanvas || isCompletingOpenSubtasks || Boolean(completingSubtaskId)"
      @click="emit('saveAndContinue')"
    />
  </div>
</template>
