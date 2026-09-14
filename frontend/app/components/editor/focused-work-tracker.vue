<script setup lang="ts">
import type { EditorQueuePage, Subtask } from '~/types'

const props = defineProps<{
  pages: EditorQueuePage[]
  currentPageId: string | null
  remainingTaskCount: number
  remainingPageCount: number
  blockedPageCount: number
  completedTaskCount: number
  isComplete: boolean
  canGoPrevious: boolean
  canGoNext: boolean
  openSubtasks: Subtask[]
  completingSubtaskId: string | null
  canCompleteActivePageSubtasks: boolean
  isCompletingOpenSubtasks: boolean
  isSavingActiveCanvas: boolean
  canEditActiveCanvas: boolean
  isActivePageLocked: boolean
}>()

const emit = defineEmits<{
  previous: []
  next: []
  select: [page: EditorQueuePage]
  exit: []
  completeSubtask: [subtask: Subtask]
  saveAndContinue: []
}>()
const listOpen = ref(false)
const totalTaskCount = computed(() => props.remainingTaskCount + props.completedTaskCount)
const progressPercentage = computed(() => totalTaskCount.value === 0
  ? 100
  : Math.round((props.completedTaskCount / totalTaskCount.value) * 100))

function getTaskDescription(subtask: Subtask) {
  return subtask.description || subtask.taskDescription || null
}
</script>

<template>
  <div class="absolute top-14 right-4 z-30 w-96 max-w-[calc(100vw-2rem)] rounded-xl border border-default bg-default/95 p-3 shadow-xl backdrop-blur">
    <h2 class="mb-2 text-xs font-semibold text-muted">
      Task
    </h2>
    <div v-if="isComplete" class="flex items-center gap-3 whitespace-nowrap">
      <UIcon name="i-lucide-party-popper" class="size-4 text-success" />
      <span class="text-sm font-medium">All assigned tasks complete</span>
      <UBadge color="success" variant="subtle">
        {{ completedTaskCount }} completed
      </UBadge>
      <UButton
        size="xs"
        label="Back to Tasks"
        @click="emit('exit')"
      />
    </div>
    <div v-else>
      <div class="flex items-center gap-2">
        <UIcon name="i-lucide-list-checks" class="size-4 shrink-0 text-primary" />
        <UProgress
          :model-value="progressPercentage"
          :max="100"
          color="primary"
          size="sm"
          class="min-w-0 flex-1"
          :aria-label="`Focused task progress: ${progressPercentage}%`"
        />
        <span class="text-xs font-medium tabular-nums" aria-live="polite">{{ progressPercentage }}%</span>
      </div>

      <div v-if="openSubtasks.length" class="mt-2 max-h-28 space-y-1 overflow-y-auto border-t border-default/60 pt-2">
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

      <div class="mt-2 flex items-center justify-between gap-2">
        <UButton
          v-if="openSubtasks.length"
          size="xs"
          color="success"
          variant="soft"
          icon="i-lucide-check-check"
          label="Complete all & save"
          :loading="isSavingActiveCanvas || isCompletingOpenSubtasks"
          :disabled="!canCompleteActivePageSubtasks || !canEditActiveCanvas || isSavingActiveCanvas || isCompletingOpenSubtasks || Boolean(completingSubtaskId)"
          @click="emit('saveAndContinue')"
        />

        <div class="ml-auto flex items-center gap-1">
          <UBadge
            v-if="blockedPageCount"
            size="xs"
            color="warning"
            variant="subtle"
          >
            {{ blockedPageCount }} blocked
          </UBadge>
          <span class="text-xs text-muted">{{ remainingPageCount }} pages</span>
          <UTooltip :delay-duration="0" text="Previous page" :content="{ side: 'top' }">
            <UButton
              size="xs"
              color="neutral"
              variant="ghost"
              icon="i-lucide-chevron-left"
              :disabled="!canGoPrevious"
              aria-label="Previous page"
              @click="emit('previous')"
            />
          </UTooltip>
          <UTooltip :delay-duration="0" text="Next page" :content="{ side: 'top' }">
            <UButton
              size="xs"
              color="neutral"
              variant="ghost"
              icon="i-lucide-chevron-right"
              :disabled="!canGoNext"
              aria-label="Next page"
              @click="emit('next')"
            />
          </UTooltip>
          <UPopover v-model:open="listOpen">
            <UTooltip :delay-duration="0" text="Open task queue" :content="{ side: 'top' }">
              <UButton
                size="xs"
                color="neutral"
                variant="ghost"
                icon="i-lucide-list"
                aria-label="Open queue"
              />
            </UTooltip>
            <template #content>
              <div class="w-80 max-h-80 overflow-y-auto p-2">
                <button
                  v-for="page in pages"
                  :key="page.pageId"
                  type="button"
                  class="flex w-full items-center gap-2 rounded-md px-2 py-2 text-left text-sm hover:bg-elevated disabled:opacity-50"
                  :class="page.pageId === currentPageId ? 'bg-elevated' : ''"
                  :disabled="page.blocked"
                  @click="emit('select', page); listOpen = false"
                >
                  <UIcon :name="page.blocked ? 'i-lucide-lock' : 'i-lucide-file-text'" class="size-4 shrink-0" />
                  <span class="min-w-0 flex-1 truncate">{{ page.projectName }} / {{ page.pageName }}</span>
                  <UBadge size="xs" :color="page.blocked ? 'warning' : 'neutral'" variant="subtle">
                    {{ page.subtasks.length }}
                  </UBadge>
                </button>
              </div>
            </template>
          </UPopover>
          <UTooltip :delay-duration="0" text="Exit focused work" :content="{ side: 'top' }">
            <UButton
              size="xs"
              color="neutral"
              variant="ghost"
              icon="i-lucide-log-out"
              aria-label="Exit focused work"
              @click="emit('exit')"
            />
          </UTooltip>
        </div>
      </div>
    </div>
  </div>
</template>
