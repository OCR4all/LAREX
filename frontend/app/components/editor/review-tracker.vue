<script setup lang="ts">
import type { EditorQueuePage } from '~/types'
import type { PageWorkflowAction } from '@/types/project-page'
import { useShortcutBindings } from '@/composables/editor/use-shortcut-bindings'

const props = defineProps<{
  pages: EditorQueuePage[]
  currentPageId: string | null
  remainingPageCount: number
  blockedPageCount: number
  reviewedPageCount: number
  isComplete: boolean
  canGoPrevious: boolean
  canGoNext: boolean
  actions: PageWorkflowAction[]
  updating: boolean
}>()

const emit = defineEmits<{
  previous: []
  next: []
  select: [page: EditorQueuePage]
  exit: []
  approve: []
  requestChanges: []
}>()

const { getTooltipProps } = useShortcutBindings()
const listOpen = ref(false)
const currentPage = computed(() => props.pages.find(page => page.pageId === props.currentPageId) ?? null)
const canReview = computed(() => props.actions.includes('APPROVE'))
const totalPageCount = computed(() => props.remainingPageCount + props.reviewedPageCount)
const progressPercentage = computed(() => totalPageCount.value === 0
  ? 100
  : Math.round((props.reviewedPageCount / totalPageCount.value) * 100))
const submitterName = computed(() => {
  const submitter = currentPage.value?.submittedBy
  if (submitter?.firstName && submitter.lastName) return `${submitter.firstName} ${submitter.lastName}`
  return submitter?.username ?? null
})
</script>

<template>
  <div class="absolute top-4 right-4 z-30 w-96 max-w-[calc(100vw-2rem)] rounded-xl border border-default bg-default/95 p-3 shadow-xl backdrop-blur">
    <h2 class="mb-2 text-xs font-semibold text-muted">
      Review
    </h2>

    <div v-if="isComplete" class="flex items-center gap-3 whitespace-nowrap">
      <UIcon name="i-lucide-party-popper" class="size-4 text-success" />
      <span class="text-sm font-medium">No pages left to review</span>
      <UBadge color="success" variant="subtle">
        {{ reviewedPageCount }} reviewed
      </UBadge>
      <UButton size="xs" label="Back to queue" @click="emit('exit')" />
    </div>

    <div v-else>
      <div class="flex items-center gap-2">
        <UIcon name="i-lucide-eye" class="size-4 shrink-0 text-primary" />
        <UProgress
          :model-value="progressPercentage"
          :max="100"
          color="primary"
          size="sm"
          class="min-w-0 flex-1"
          :aria-label="`Review progress: ${progressPercentage}%`"
        />
        <span class="text-xs font-medium tabular-nums" aria-live="polite">{{ progressPercentage }}%</span>
      </div>

      <div v-if="currentPage" class="mt-2 border-t border-default/60 pt-2">
        <p class="truncate text-xs font-medium">
          {{ currentPage.pageName }}
        </p>
        <p class="truncate text-[11px] leading-4 text-muted">
          {{ currentPage.projectName }}<template v-if="submitterName">
            · Submitted by {{ submitterName }}
          </template>
        </p>
        <p v-if="currentPage.assignments?.length" class="truncate text-[11px] leading-4 text-muted">
          {{ currentPage.assignments.map(assignment => assignment.title).join(', ') }}
        </p>
      </div>

      <div class="mt-2 flex items-center gap-2">
        <UTooltip
          :delay-duration="0"
          text="Approve and go to the next page"
          :kbds="getTooltipProps('reviewApprove').kbds"
          :content="{ side: 'bottom' }"
        >
          <UButton
            size="xs"
            color="success"
            variant="soft"
            icon="i-lucide-circle-check"
            label="Approve"
            :loading="updating"
            :disabled="!canReview || updating"
            @click="emit('approve')"
          />
        </UTooltip>
        <UTooltip
          :delay-duration="0"
          text="Send back to the editor"
          :kbds="getTooltipProps('reviewRequestChanges').kbds"
          :content="{ side: 'bottom' }"
        >
          <UButton
            size="xs"
            color="warning"
            variant="soft"
            icon="i-lucide-message-square-warning"
            label="Request changes"
            :disabled="!canReview || updating"
            @click="emit('requestChanges')"
          />
        </UTooltip>
      </div>

      <div class="mt-2 flex items-center justify-end gap-1">
        <UBadge
          v-if="blockedPageCount"
          size="xs"
          color="warning"
          variant="subtle"
          class="mr-auto"
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
            square
            aria-label="Previous page"
            :disabled="!canGoPrevious"
            @click="emit('previous')"
          />
        </UTooltip>
        <UTooltip :delay-duration="0" text="Next page" :content="{ side: 'top' }">
          <UButton
            size="xs"
            color="neutral"
            variant="ghost"
            icon="i-lucide-chevron-right"
            square
            aria-label="Next page"
            :disabled="!canGoNext"
            @click="emit('next')"
          />
        </UTooltip>
        <UPopover v-model:open="listOpen">
          <UButton
            size="xs"
            color="neutral"
            variant="ghost"
            icon="i-lucide-list"
            square
            aria-label="Show review queue"
          />
          <template #content>
            <div class="max-h-72 w-72 overflow-y-auto p-1">
              <button
                v-for="page in pages"
                :key="page.pageId"
                type="button"
                class="flex w-full items-center gap-2 rounded px-2 py-1.5 text-left text-xs hover:bg-elevated disabled:cursor-not-allowed disabled:opacity-50"
                :disabled="page.blocked"
                :title="page.blockedReason ?? undefined"
                @click="listOpen = false; emit('select', page)"
              >
                <UIcon :name="page.blocked ? 'i-lucide-lock' : 'i-lucide-file-text'" class="size-3.5 shrink-0 text-muted" />
                <span class="min-w-0 flex-1 truncate">{{ page.pageName }}</span>
              </button>
            </div>
          </template>
        </UPopover>
        <UButton
          size="xs"
          color="neutral"
          variant="ghost"
          icon="i-lucide-x"
          square
          aria-label="Exit review"
          @click="emit('exit')"
        />
      </div>
    </div>
  </div>
</template>
