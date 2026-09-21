<script setup lang="ts">
import type { TableColumn } from '@nuxt/ui'
import type { ActionRun, ActionRunStatus } from '@/types/action'

await useWorkspaceBootstrap()

const workspaceStore = useWorkspaceStore()
const toast = useToast()
const realtime = useRealtimeSocket()
const UButtonComponent = resolveComponent('UButton')
const UBadgeComponent = resolveComponent('UBadge')
const UProgressComponent = resolveComponent('UProgress')
const UTooltipComponent = resolveComponent('UTooltip')

const workspaceId = computed(() => workspaceStore.selectedWorkspaceId)
const runs = ref<ActionRun[]>([])
const loading = ref(false)
const searchInput = ref('')
const statusFilter = ref<'ALL' | ActionRunStatus>('ALL')
const cancellingRunId = ref<string | null>(null)
const page = ref(1)
const itemsPerPage = ref(25)
let realtimeUnsubscribe: (() => void) | null = null
let refreshTimer: ReturnType<typeof setTimeout> | null = null
let pollTimer: ReturnType<typeof setInterval> | null = null

const statusOptions = [
  { label: 'All statuses', value: 'ALL' as const },
  { label: 'Queued', value: 'QUEUED' as const },
  { label: 'Pending', value: 'PENDING' as const },
  { label: 'Dispatching', value: 'DISPATCHING' as const },
  { label: 'Running', value: 'RUNNING' as const },
  { label: 'Importing results', value: 'IMPORTING_RESULTS' as const },
  { label: 'Cancel requested', value: 'CANCEL_REQUESTED' as const },
  { label: 'Completed', value: 'COMPLETED' as const },
  { label: 'Failed', value: 'FAILED' as const },
  { label: 'Cancelled', value: 'CANCELLED' as const }
]

const filteredRuns = computed(() => {
  const needle = searchInput.value.trim().toLowerCase()
  return runs.value
    .filter(run => statusFilter.value === 'ALL' || run.status === statusFilter.value)
    .filter((run) => {
      if (!needle) return true
      return [run.datasetLabel, run.processorName, run.processorKey, run.status, run.statusMessage, run.errorMessage]
        .some(value => value?.toLowerCase().includes(needle))
    })
    .sort(compareRuns)
})

const totalPages = computed(() => Math.max(1, Math.ceil(filteredRuns.value.length / itemsPerPage.value)))
const paginatedRuns = computed(() => filteredRuns.value.slice((page.value - 1) * itemsPerPage.value, page.value * itemsPerPage.value))
const showingFrom = computed(() => filteredRuns.value.length ? (page.value - 1) * itemsPerPage.value + 1 : 0)
const showingTo = computed(() => Math.min(page.value * itemsPerPage.value, filteredRuns.value.length))

watch([searchInput, statusFilter], () => {
  page.value = 1
})
watch(totalPages, (value) => {
  if (page.value > value) page.value = value
})

const itemsPerPageModel = useItemsPerPageModel(page, itemsPerPage, computed(() => filteredRuns.value.length))

const columns = computed<TableColumn<ActionRun>[]>(() => [
  {
    id: 'dataset',
    header: 'Dataset',
    cell: ({ row }) => h('span', { class: 'truncate font-medium' }, row.original.datasetLabel || 'Dataset')
  },
  {
    id: 'action',
    header: 'Action',
    cell: ({ row }) => h('span', { class: 'truncate' }, row.original.processorName)
  },
  {
    id: 'status',
    header: 'Status',
    cell: ({ row }) => h(UBadgeComponent, { size: 'sm', variant: 'soft', color: statusColor(row.original.status) }, () => statusLabel(row.original))
  },
  {
    id: 'message',
    header: 'Message',
    cell: ({ row }) => h('span', { class: 'truncate text-sm text-muted' }, row.original.statusMessage || row.original.errorMessage || '—')
  },
  {
    id: 'inputs',
    header: 'Inputs',
    cell: ({ row }) => h('span', { class: 'tabular-nums' }, String(row.original.inputCount ?? 0))
  },
  {
    id: 'train',
    header: 'Train',
    cell: ({ row }) => h('span', { class: 'tabular-nums' }, String(row.original.splitCounts?.TRAIN ?? row.original.splitCounts?.train ?? 0))
  },
  {
    id: 'val',
    header: 'Val',
    cell: ({ row }) => h('span', { class: 'tabular-nums' }, String(row.original.splitCounts?.VAL ?? row.original.splitCounts?.val ?? 0))
  },
  {
    id: 'test',
    header: 'Test',
    cell: ({ row }) => h('span', { class: 'tabular-nums' }, String(row.original.splitCounts?.TEST ?? row.original.splitCounts?.test ?? 0))
  },
  {
    id: 'progress',
    header: 'Progress',
    cell: ({ row }) => {
      const run = row.original
      const progressLabel = `${statusLabel(run)} · ${run.progressPercent}%`
      return h(UTooltipComponent, { text: progressLabel }, () => h(UProgressComponent, {
        'modelValue': run.progressPercent,
        'max': 100,
        'size': 'xs',
        'color': run.status === 'COMPLETED' ? 'success' : 'primary',
        'class': 'w-20',
        'aria-label': `Progress: ${progressLabel}`
      }))
    }
  },
  {
    accessorKey: 'updated',
    header: 'Updated'
  },
  {
    id: 'duration',
    header: 'Duration',
    cell: ({ row }) => h('span', { class: 'text-sm text-muted' }, formatDurationFromRun(row.original))
  },
  {
    id: 'actions',
    header: '',
    cell: ({ row }) => row.original.canCancel && isActiveRun(row.original.status)
      ? h('div', { class: 'flex justify-end' }, [
          h(UButtonComponent, {
            'color': 'warning',
            'variant': 'ghost',
            'size': 'xs',
            'icon': 'i-lucide-ban',
            'aria-label': row.original.status === 'CANCEL_REQUESTED' ? 'Force cancel training run' : 'Cancel training run',
            'title': row.original.status === 'CANCEL_REQUESTED' ? 'Force cancel training run' : 'Cancel training run',
            'loading': cancellingRunId.value === row.original.id,
            'onClick': (event: MouseEvent) => {
              event.stopPropagation()
              void cancelRun(row.original)
            }
          })
        ])
      : null
  }
])

async function loadRuns() {
  if (!workspaceId.value) {
    runs.value = []
    return
  }
  loading.value = true
  try {
    const allRuns = await $fetch<ActionRun[]>(`/api/workspaces/${workspaceId.value}/actions/runs`)
    runs.value = allRuns.filter(run => run.kind === 'TRAINING')
  } catch (error: unknown) {
    toast.add({ title: 'Could not load training runs', description: error instanceof Error ? error.message : undefined, color: 'error' })
  } finally {
    loading.value = false
  }
}

async function cancelRun(run: ActionRun) {
  if (!run.canCancel || !isActiveRun(run.status) || cancellingRunId.value === run.id) return
  cancellingRunId.value = run.id
  try {
    const updated = await $fetch<ActionRun>(`/api/workspaces/${workspaceId.value}/actions/runs/${run.id}/cancel${run.status === 'CANCEL_REQUESTED' ? '?force=true' : ''}`, { method: 'POST' })
    runs.value = runs.value.map(item => item.id === updated.id ? updated : item)
    toast.add({ title: updated.status === 'CANCELLED' ? 'Run cancelled' : 'Cancellation requested', color: 'success', icon: 'i-lucide-ban' })
  } catch (error: unknown) {
    toast.add({ title: 'Could not cancel training', description: error instanceof Error ? error.message : undefined, color: 'error' })
  } finally {
    cancellingRunId.value = null
  }
}

function compareRuns(left: ActionRun, right: ActionRun) {
  const leftRank = runSortRank(left)
  const rightRank = runSortRank(right)
  if (leftRank !== rightRank) return leftRank - rightRank
  if (left.status === 'QUEUED' && right.status === 'QUEUED') return (left.queuePosition ?? Number.MAX_SAFE_INTEGER) - (right.queuePosition ?? Number.MAX_SAFE_INTEGER)
  return Date.parse(right.updated) - Date.parse(left.updated)
}

function runSortRank(run: ActionRun) {
  if (run.status === 'QUEUED') return 0
  if (isActiveRun(run.status)) return 1
  return 2
}

function isTerminalRun(status: ActionRunStatus) {
  return status === 'COMPLETED' || status === 'FAILED' || status === 'CANCELLED'
}

function isActiveRun(status: ActionRunStatus) {
  return !isTerminalRun(status)
}

function formatStatus(status: ActionRunStatus) {
  return status.replaceAll('_', ' ')
}

function statusLabel(run: ActionRun) {
  return run.status === 'QUEUED' && run.queuePosition ? `Queued #${run.queuePosition}` : formatStatus(run.status)
}

function statusColor(status: ActionRunStatus): 'success' | 'error' | 'neutral' | 'warning' | 'primary' {
  if (status === 'COMPLETED') return 'success'
  if (status === 'FAILED') return 'error'
  if (status === 'CANCELLED') return 'neutral'
  if (status === 'QUEUED' || status === 'CANCEL_REQUESTED') return 'warning'
  return 'primary'
}

function formatDuration(seconds: number | null | undefined) {
  if (seconds === null || seconds === undefined) return 'Running'
  if (seconds < 60) return `${seconds}s`
  return `${Math.floor(seconds / 60)}m ${seconds % 60}s`
}

function formatDurationFromRun(run: ActionRun) {
  if (!run.completedAt) return run.status === 'QUEUED' && run.queuePosition ? `Position ${run.queuePosition}` : 'In progress'
  return formatDuration(Math.max(0, Math.round((Date.parse(run.completedAt) - Date.parse(run.created)) / 1000)))
}

watch(workspaceId, () => {
  void loadRuns()
}, { immediate: true })

onMounted(() => {
  realtimeUnsubscribe = realtime.subscribe((message) => {
    if (message.type !== 'ACTION_RUN_UPDATED') return
    const messageWorkspaceId = (message.payload as { workspaceId?: unknown } | null)?.workspaceId
    if (messageWorkspaceId !== workspaceId.value || refreshTimer) return
    refreshTimer = setTimeout(() => {
      refreshTimer = null
      void loadRuns()
    }, 100)
  })
  pollTimer = setInterval(() => {
    if (realtime.isPageVisible.value && runs.value.some(run => isActiveRun(run.status))) {
      void loadRuns()
    }
  }, 10000)
})

onBeforeUnmount(() => {
  realtimeUnsubscribe?.()
  realtimeUnsubscribe = null
  if (refreshTimer) clearTimeout(refreshTimer)
  if (pollTimer) clearInterval(pollTimer)
})
</script>

<template>
  <UDashboardPanel id="training-runs">
    <template #header>
      <UDashboardNavbar title="Training">
        <template #right>
          <UButton to="/training/new" icon="i-lucide-plus">
            New Training
          </UButton>
        </template>
      </UDashboardNavbar>
      <UDashboardToolbar>
        <template #left>
          <UInput
            v-model="searchInput"
            icon="i-lucide-search"
            placeholder="Search dataset or Action…"
            class="w-full sm:w-64"
          />
          <USelectMenu
            v-model="statusFilter"
            :items="statusOptions"
            value-key="value"
            placeholder="Filter by status"
            class="w-full sm:w-48"
          />
        </template>
        <template #right>
          <AppTableColumnsDropdown table-id="training-runs" :columns="columns" />
          <UButton
            color="neutral"
            variant="ghost"
            icon="i-lucide-refresh-cw"
            size="sm"
            square
            aria-label="Refresh training runs"
            title="Refresh"
            :loading="loading"
            @click="loadRuns"
          />
        </template>
      </UDashboardToolbar>
    </template>
    <template #body>
      <UEmpty
        v-if="!runs.length"
        variant="naked"
        icon="i-lucide-brain-circuit"
        title="No training runs yet"
        description="Start a training run to see queued, active, and completed work here."
      >
        <template #actions>
          <UButton to="/training/new" icon="i-lucide-plus">
            New Training
          </UButton>
        </template>
      </UEmpty>
      <div v-else class="flex h-full min-h-0 flex-col">
        <UEmpty
          v-if="!filteredRuns.length"
          variant="naked"
          icon="i-lucide-filter-x"
          title="No matching training runs"
          description="Adjust the current filters."
        />
        <AppTable
          v-else
          table-id="training-runs"
          :columns="columns"
          :data="paginatedRuns"
          :default-visible-column-ids="['dataset', 'action', 'status', 'message', 'inputs', 'train', 'val', 'test', 'progress', 'updated', 'duration', 'actions']"
          class="flex-1 px-4 pb-4"
        />
        <div v-if="filteredRuns.length" class="flex items-center justify-between border-t border-default px-4 py-4">
          <div class="text-sm text-muted">
            Showing {{ showingFrom }} to {{ showingTo }} of {{ filteredRuns.length }} runs
          </div>
          <div class="flex items-center gap-4">
            <USelect
              v-model="itemsPerPageModel"
              :items="[10, 25, 50, 100]"
              class="w-24"
              size="sm"
            />
            <UPagination
              v-model:page="page"
              :total="filteredRuns.length"
              :items-per-page="itemsPerPage"
              :disabled="totalPages <= 1"
            />
          </div>
        </div>
      </div>
    </template>
  </UDashboardPanel>
</template>
