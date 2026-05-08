<script setup lang="ts">
import type { TrackedActionRun } from '@/stores/action-runs.store'

const actionRunsStore = useActionRunsStore()
const toast = useToast()

let pollTimer: ReturnType<typeof setInterval> | null = null

const headerTitle = computed(() => {
  if (actionRunsStore.hasActiveRuns) return 'LAREX Actions Running'
  return 'LAREX Actions Complete'
})

onMounted(() => {
  pollTimer = setInterval(() => {
    if (actionRunsStore.hasActiveRuns) {
      void actionRunsStore.refreshActiveRuns()
    }
  }, 2500)
})

onBeforeUnmount(() => {
  if (pollTimer) {
    clearInterval(pollTimer)
  }
})

function statusColor(status: TrackedActionRun['status']) {
  if (status === 'COMPLETED') return 'success'
  if (status === 'FAILED' || status === 'CANCELLED') return 'error'
  if (status === 'CANCEL_REQUESTED') return 'warning'
  return 'primary'
}

function isActiveRun(run: TrackedActionRun) {
  return ['PENDING', 'DISPATCHING', 'RUNNING', 'IMPORTING_RESULTS'].includes(run.status)
}

function canCancelRun(run: TrackedActionRun) {
  return ['PENDING', 'DISPATCHING', 'RUNNING', 'IMPORTING_RESULTS', 'CANCEL_REQUESTED'].includes(run.status)
}

async function handleCancel(run: TrackedActionRun) {
  if (!canCancelRun(run)) return
  try {
    await actionRunsStore.cancelRun(run)
  } catch (error) {
    const message = error instanceof Error ? error.message : 'Could not cancel Action run.'
    toast.add({
      title: 'Cancel failed',
      description: message,
      color: 'error',
      icon: 'i-lucide-alert-circle'
    })
  }
}

function handleClose() {
  if (actionRunsStore.hasActiveRuns) {
    actionRunsStore.toggleMinimized()
  } else {
    actionRunsStore.hidePanel()
  }
}
</script>

<template>
  <Transition
    enter-active-class="transform transition duration-300 ease-out"
    enter-from-class="translate-y-full opacity-0"
    enter-to-class="translate-y-0 opacity-100"
    leave-active-class="transform transition duration-200 ease-in"
    leave-from-class="translate-y-0 opacity-100"
    leave-to-class="translate-y-full opacity-0"
  >
    <div
      v-if="actionRunsStore.showProgressPanel"
      class="fixed bottom-4 right-[26rem] z-50 w-96 bg-(--ui-bg) border border-(--ui-border) rounded-sm shadow-xl"
    >
      <div class="flex items-center justify-between px-4 py-3 border-b border-(--ui-border)">
        <div class="flex items-center gap-2">
          <UIcon name="i-lucide-bolt" class="text-(--ui-primary)" />
          <span class="font-medium">{{ headerTitle }}</span>
          <UBadge v-if="actionRunsStore.totalActiveRuns > 0" color="primary" size="xs">
            {{ actionRunsStore.totalActiveRuns }}
          </UBadge>
        </div>
        <div class="flex items-center gap-1">
          <UButton icon="i-lucide-minus" variant="ghost" size="xs" @click="actionRunsStore.toggleMinimized()" />
          <UButton icon="i-lucide-x" variant="ghost" size="xs" @click="handleClose" />
        </div>
      </div>

      <Transition
        enter-active-class="transition-all duration-200 ease-out"
        enter-from-class="max-h-0 opacity-0"
        enter-to-class="max-h-96 opacity-100"
        leave-active-class="transition-all duration-200 ease-in"
        leave-from-class="max-h-96 opacity-100"
        leave-to-class="max-h-0 opacity-0"
      >
        <div v-if="!actionRunsStore.minimized" class="max-h-80 overflow-y-auto">
          <div v-if="actionRunsStore.runsArray.length === 0" class="p-4 text-center text-(--ui-text-muted)">
            No Action runs
          </div>

          <div v-else class="divide-y divide-(--ui-border)">
            <div
              v-for="run in actionRunsStore.runsArray"
              :key="run.id"
              class="p-3"
            >
              <div class="mb-2 flex items-center justify-between gap-2">
                <div class="min-w-0">
                  <p class="truncate text-sm font-medium">
                    {{ run.processorName }}
                  </p>
                  <p class="truncate text-xs text-muted">
                    {{ run.projectName }} · {{ run.pageIds.length }} page{{ run.pageIds.length === 1 ? '' : 's' }}
                  </p>
                </div>
                <UBadge :color="statusColor(run.status)" size="xs">
                  {{ run.status }}
                </UBadge>
              </div>

              <div class="mb-2">
                <div class="mb-1 flex justify-between text-xs text-muted">
                  <span class="truncate">{{ run.statusMessage || run.processorKey }}</span>
                  <span>{{ run.progressPercent }}%</span>
                </div>
                <UProgress
                  :model-value="run.progressPercent"
                  :color="statusColor(run.status)"
                  size="sm"
                />
              </div>

              <p v-if="run.errorMessage" class="mt-1 text-xs text-error">
                {{ run.errorMessage }}
              </p>

              <div class="mt-2 flex items-center gap-2">
                <UButton
                  v-if="canCancelRun(run)"
                  variant="ghost"
                  size="xs"
                  icon="i-lucide-ban"
                  :loading="actionRunsStore.isCancelling(run.id)"
                  @click="handleCancel(run)"
                >
                  Cancel Action
                </UButton>
                <UButton
                  v-if="!canCancelRun(run)"
                  variant="ghost"
                  size="xs"
                  icon="i-lucide-trash-2"
                  @click="actionRunsStore.removeRun(run.id)"
                >
                  Dismiss
                </UButton>
              </div>
            </div>
          </div>
        </div>
      </Transition>

      <Transition
        enter-active-class="transition-all duration-200"
        enter-from-class="opacity-0"
        enter-to-class="opacity-100"
        leave-active-class="transition-all duration-200"
        leave-from-class="opacity-100"
        leave-to-class="opacity-0"
      >
        <div v-if="actionRunsStore.minimized && actionRunsStore.hasActiveRuns" class="px-4 py-2">
          <div class="flex items-center justify-between text-sm">
            <span class="text-muted">{{ actionRunsStore.totalActiveRuns }} Action run(s)</span>
            <span class="font-medium">{{ actionRunsStore.overallProgress }}%</span>
          </div>
          <UProgress :model-value="actionRunsStore.overallProgress" size="xs" class="mt-1" />
        </div>
      </Transition>

      <div
        v-if="!actionRunsStore.minimized && actionRunsStore.runsArray.some(run => ['COMPLETED', 'FAILED', 'CANCELLED'].includes(run.status))"
        class="px-4 py-2 border-t border-default"
      >
        <UButton
          variant="ghost"
          size="xs"
          icon="i-lucide-check-check"
          class="w-full"
          @click="actionRunsStore.clearCompletedRuns()"
        >
          Clear completed
        </UButton>
      </div>
    </div>
  </Transition>
</template>
