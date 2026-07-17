<script setup lang="ts">
import type { TableColumn } from '@nuxt/ui'
import type { ActionRun, ActionRunStatus, ClearActionRunsResponse } from '@/types/action'

await useWorkspaceBootstrap()

const toast = useToast()
const workspaceStore = useWorkspaceStore()
const actionRunsStore = useActionRunsStore()
const realtime = useRealtimeSocket()
const UButtonComponent = resolveComponent('UButton')
const UBadgeComponent = resolveComponent('UBadge')

const selectedWorkspace = computed(() => workspaceStore.selectedWorkspaceId)
const currentWorkspace = computed(() => workspaceStore.currentWorkspace)

const runs = ref<ActionRun[]>([])
const clearingFinished = ref(false)
const cancellingRunId = ref<string | null>(null)
const searchInput = ref('')
const debouncedSearch = ref('')
const statusFilter = ref<'ALL' | ActionRunStatus>('ALL')
const page = ref(1)
const itemsPerPage = ref(25)
const itemsPerPageModel = useItemsPerPageModel(page, itemsPerPage, computed(() => filteredRuns.value.length))
let pollTimer: ReturnType<typeof setInterval> | null = null
let realtimeRefreshTimer: ReturnType<typeof setTimeout> | null = null
let realtimeUnsubscribe: (() => void) | null = null
let lastRealtimeAuditAt = Date.now()

const workspaceRunsKey = computed(() => globalKey('workspace', 'action-runs', selectedWorkspace.value || 'none'))

const statusOptions = [
  { label: 'All statuses', value: 'ALL' as const },
  { label: 'Queued', value: 'QUEUED' as const },
  { label: 'Pending', value: 'PENDING' as const },
  { label: 'Dispatching', value: 'DISPATCHING' as const },
  { label: 'Running', value: 'RUNNING' as const },
  { label: 'Importing Results', value: 'IMPORTING_RESULTS' as const },
  { label: 'Cancel Requested', value: 'CANCEL_REQUESTED' as const },
  { label: 'Completed', value: 'COMPLETED' as const },
  { label: 'Failed', value: 'FAILED' as const },
  { label: 'Cancelled', value: 'CANCELLED' as const }
]

watch(searchInput, useDebounceFn((value: string) => {
  debouncedSearch.value = value.trim()
  page.value = 1
}, 250))

watch(statusFilter, () => {
  page.value = 1
})

const terminalCount = computed(() => runs.value.filter(run => isTerminalRun(run.status)).length)
const hasActiveFilters = computed(() => debouncedSearch.value.length > 0 || statusFilter.value !== 'ALL')
const activeRunFilters = computed(() => {
  const filters: Array<{ key: string, label: string, clear: () => void }> = []
  if (debouncedSearch.value) {
    filters.push({
      key: 'search',
      label: `Search: ${debouncedSearch.value}`,
      clear: () => { searchInput.value = '' }
    })
  }
  if (statusFilter.value !== 'ALL') {
    filters.push({
      key: 'status',
      label: statusOptions.find(option => option.value === statusFilter.value)?.label ?? statusFilter.value,
      clear: () => { statusFilter.value = 'ALL' }
    })
  }
  return filters
})

const filteredRuns = computed(() => runs.value
  .filter((run) => {
    if (statusFilter.value !== 'ALL' && run.status !== statusFilter.value) return false
    const needle = debouncedSearch.value.toLowerCase()
    if (!needle) return true
    return [
      run.projectLabel,
      run.processorName,
      run.processorKey,
      run.status,
      run.statusMessage,
      run.errorMessage
    ].some(value => value?.toLowerCase().includes(needle))
  })
  .sort(compareRuns))

const totalItems = computed(() => filteredRuns.value.length)
const totalPages = computed(() => Math.max(1, Math.ceil(totalItems.value / itemsPerPage.value)))
const paginatedRuns = computed(() => {
  const start = (page.value - 1) * itemsPerPage.value
  return filteredRuns.value.slice(start, start + itemsPerPage.value)
})
const showingFrom = computed(() => totalItems.value === 0 ? 0 : (page.value - 1) * itemsPerPage.value + 1)
const showingTo = computed(() => Math.min(page.value * itemsPerPage.value, totalItems.value))

watch(totalPages, (value) => {
  if (page.value > value) {
    page.value = value
  }
})

const columns = computed<TableColumn<ActionRun>[]>(() => [
  {
    id: 'project',
    header: 'Project',
    cell: ({ row }) => h('div', { class: 'min-w-0' }, [
      h('div', { class: 'truncate font-medium' }, row.original.projectLabel),
      h('div', { class: 'truncate text-xs text-muted' }, row.original.processorName)
    ])
  },
  {
    id: 'status',
    header: 'Status',
    cell: ({ row }) => {
      const run = row.original
      return h('div', { class: 'min-w-0 space-y-1' }, [
        h(UBadgeComponent, {
          size: 'sm',
          variant: 'soft',
          color: statusColor(run.status)
        }, () => statusBadgeLabel(run)),
        h('div', { class: 'truncate text-xs text-muted' }, run.statusMessage || formatStatus(run.status))
      ])
    }
  },
  {
    accessorKey: 'pageCount',
    header: 'Pages',
    cell: ({ row }) => h('span', { class: 'tabular-nums text-sm' }, String(row.original.pageCount))
  },
  {
    accessorKey: 'updated',
    header: 'Last Activity'
  },
  {
    id: 'duration',
    header: 'Duration',
    cell: ({ row }) => h('span', { class: 'text-sm text-muted' }, formatDurationFromRun(row.original))
  },
  {
    id: 'actions',
    header: '',
    cell: ({ row }) => h('div', { class: 'flex items-center justify-end gap-2' }, [
      row.original.canCancel && isActiveRun(row.original.status)
        ? h(UButtonComponent, {
            color: 'warning',
            variant: 'ghost',
            size: 'xs',
            icon: 'i-lucide-ban',
            type: 'button',
            loading: cancellingRunId.value === row.original.id,
            onClick: async (event: MouseEvent) => {
              event.stopPropagation()
              await cancelRun(row.original)
            }
          })
        : null
    ].filter(Boolean))
  }
])

const { data: workspaceRunsData, pending: loading, refresh: refreshRuns } = await useAsyncData<ActionRun[]>(
  workspaceRunsKey.value,
  async () => {
    if (!selectedWorkspace.value) return []
    return await $fetch<ActionRun[]>(`/api/workspaces/${selectedWorkspace.value}/actions/runs`)
  },
  {
    watch: [selectedWorkspace],
    default: () => []
  }
)

watch(workspaceRunsData, (value) => {
  runs.value = value ?? []
  runs.value.forEach(run => actionRunsStore.upsertRun(run, run.projectLabel))
}, { immediate: true })

onMounted(() => {
  realtimeUnsubscribe = realtime.subscribe((message) => {
    if (message.type !== 'ACTION_RUN_UPDATED' && message.type !== 'ACTION_PAGE_RESULT_IMPORTED') return
    const workspaceId = (message.payload as { workspaceId?: unknown } | null)?.workspaceId
    if (workspaceId !== selectedWorkspace.value || realtimeRefreshTimer) return
    realtimeRefreshTimer = setTimeout(() => {
      realtimeRefreshTimer = null
      void loadRuns()
    }, 50)
  })

  pollTimer = setInterval(() => {
    if (!realtime.isPageVisible.value) return
    const realtimeConnected = realtime.connectionStatus.value === 'connected'
    const auditDue = !realtimeConnected || Date.now() - lastRealtimeAuditAt >= 60_000
    if (runs.value.some(run => isActiveRun(run.status)) && auditDue) {
      if (realtimeConnected) {
        lastRealtimeAuditAt = Date.now()
      }
      void loadRuns()
    }
  }, 5000)
})

watch(() => realtime.isPageVisible.value, (pageVisible) => {
  if (pageVisible && realtime.connectionStatus.value !== 'connected' && runs.value.some(run => isActiveRun(run.status))) {
    void loadRuns()
  }
})

onBeforeUnmount(() => {
  if (pollTimer) {
    clearInterval(pollTimer)
    pollTimer = null
  }
  if (realtimeRefreshTimer) {
    clearTimeout(realtimeRefreshTimer)
    realtimeRefreshTimer = null
  }
  realtimeUnsubscribe?.()
  realtimeUnsubscribe = null
})

watch(selectedWorkspace, () => {
  page.value = 1
})

async function loadRuns() {
  try {
    await refreshRuns()
  } catch (error: unknown) {
    const message = error instanceof Error ? error.message : 'Could not load workspace Action runs.'
    toast.add({ title: 'Queue load failed', description: message, color: 'error' })
  }
}

async function clearFinishedRuns() {
  if (!selectedWorkspace.value || terminalCount.value === 0) return
  clearingFinished.value = true
  try {
    const result = await $fetch<ClearActionRunsResponse>(`/api/workspaces/${selectedWorkspace.value}/actions/runs/history/dismiss`, {
      method: 'POST'
    })
    await loadRuns()
    toast.add({
      title: `Cleared ${result.deletedCount} finished job${result.deletedCount === 1 ? '' : 's'}`,
      description: 'Finished Action runs were removed from your queue view.',
      color: 'success'
    })
  } catch (error: unknown) {
    const message = error instanceof Error ? error.message : 'Could not clear finished Action runs.'
    toast.add({ title: 'Clear failed', description: message, color: 'error' })
  } finally {
    clearingFinished.value = false
  }
}

async function cancelRun(run: ActionRun) {
  if (!run.canCancel || !isActiveRun(run.status) || cancellingRunId.value === run.id) return
  cancellingRunId.value = run.id
  try {
    const updated = await $fetch<ActionRun>(
      `/api/workspaces/${run.workspaceId}/actions/projects/${run.projectId}/runs/${run.id}/cancel`,
      { method: 'POST' }
    )
    updateRun(updated)
    actionRunsStore.upsertRun(updated, updated.projectLabel)
    toast.add({
      title: run.status === 'QUEUED' || run.status === 'PENDING' ? 'Run cancelled' : 'Cancellation requested',
      color: 'success',
      icon: 'i-lucide-ban'
    })
  } catch (error: unknown) {
    const message = error instanceof Error ? error.message : 'Could not cancel Action run.'
    toast.add({ title: 'Cancel failed', description: message, color: 'error' })
  } finally {
    cancellingRunId.value = null
  }
}

function clearFilters() {
  searchInput.value = ''
  debouncedSearch.value = ''
  statusFilter.value = 'ALL'
}

function updateRun(updated: ActionRun) {
  runs.value = runs.value.map(run => run.id === updated.id ? updated : run)
}

function compareRuns(left: ActionRun, right: ActionRun) {
  const leftRank = runSortRank(left)
  const rightRank = runSortRank(right)
  if (leftRank !== rightRank) return leftRank - rightRank
  if (left.status === 'QUEUED' && right.status === 'QUEUED') {
    return (left.queuePosition ?? Number.MAX_SAFE_INTEGER) - (right.queuePosition ?? Number.MAX_SAFE_INTEGER)
  }
  return Date.parse(right.updated) - Date.parse(left.updated)
}

function runSortRank(run: ActionRun) {
  if (run.status === 'QUEUED') return 0
  if (isActiveRun(run.status)) return 1
  return 2
}

function isTerminalRun(status: ActionRun['status']) {
  return status === 'COMPLETED' || status === 'FAILED' || status === 'CANCELLED'
}

function isActiveRun(status: ActionRun['status']) {
  return !isTerminalRun(status)
}

function formatStatus(status: ActionRun['status']) {
  return status.replaceAll('_', ' ')
}

function statusBadgeLabel(run: ActionRun) {
  return run.status === 'QUEUED' && run.queuePosition
    ? `Queued #${run.queuePosition}`
    : formatStatus(run.status)
}

function statusColor(status: ActionRun['status']): 'success' | 'error' | 'neutral' | 'warning' | 'primary' {
  if (status === 'COMPLETED') return 'success'
  if (status === 'FAILED') return 'error'
  if (status === 'CANCELLED') return 'neutral'
  if (status === 'QUEUED' || status === 'CANCEL_REQUESTED') return 'warning'
  return 'primary'
}

function formatDuration(seconds: number | null | undefined) {
  if (seconds === null || seconds === undefined) return 'Running'
  if (seconds < 60) return `${seconds}s`
  const minutes = Math.floor(seconds / 60)
  const remainder = seconds % 60
  return `${minutes}m ${remainder}s`
}

function formatDurationFromRun(run: ActionRun) {
  if (!run.completedAt) return run.status === 'QUEUED' && run.queuePosition ? `Position ${run.queuePosition}` : 'In progress'
  return formatDuration(Math.max(0, Math.round((Date.parse(run.completedAt) - Date.parse(run.created)) / 1000)))
}
</script>

<template>
  <UDashboardPanel id="workspace-queue">
    <template #header>
      <UDashboardNavbar :title="`${currentWorkspace?.name || 'Workspace'} Queue`">
        <template #right>
          <UFieldGroup>
            <UButton
              color="neutral"
              variant="outline"
              icon="i-lucide-list-x"
              :disabled="terminalCount === 0"
              :loading="clearingFinished"
              @click="clearFinishedRuns"
            >
              Clear Finished Jobs
            </UButton>
            <UButton
              color="neutral"
              variant="outline"
              icon="i-lucide-refresh-cw"
              :loading="loading"
              @click="loadRuns"
            >
              Refresh
            </UButton>
          </UFieldGroup>
        </template>
      </UDashboardNavbar>

      <UDashboardToolbar>
        <template #left>
          <UInput
            v-model="searchInput"
            placeholder="Search project, processor, or status..."
            icon="i-lucide-search"
            class="w-full sm:w-80"
          />
          <USelect
            v-model="statusFilter"
            :items="statusOptions"
            value-key="value"
            class="w-full sm:w-52"
          />
          <AppTableClearFiltersButton
            :active="activeRunFilters.length > 0"
            @clear="clearFilters"
          />
        </template>
        <template #right>
          <AppTableColumnsDropdown table-id="workspace-action-runs" :columns="columns" />
        </template>
      </UDashboardToolbar>
    </template>

    <template #body>
      <UEmpty
        v-if="!loading && runs.length === 0"
        variant="naked"
        icon="i-lucide-list-ordered"
        title="No Action runs yet"
        description="Queued, active, and finished Action runs for this workspace will appear here."
      />

      <div v-else class="flex h-full min-h-0 flex-col">
        <div v-if="loading && runs.length === 0" class="space-y-2 p-4">
          <USkeleton class="h-14 w-full" />
          <USkeleton class="h-14 w-full" />
          <USkeleton class="h-14 w-full" />
        </div>

        <template v-else>
          <UEmpty
            v-if="filteredRuns.length === 0"
            variant="naked"
            icon="i-lucide-filter-x"
            title="No matching Action runs"
            :description="hasActiveFilters ? 'Try adjusting the current filters.' : 'This workspace has no matching Action runs.'"
          />

          <AppTable
            v-else
            table-id="workspace-action-runs"
            :columns="columns"
            :data="paginatedRuns"
            :default-visible-column-ids="['project', 'status', 'pageCount', 'updated', 'duration', 'actions']"
            class="flex-1 px-4 pb-4"
          />

          <div v-if="filteredRuns.length > 0" class="flex items-center justify-between border-t border-default px-4 py-4">
            <div class="text-sm text-muted">
              Showing {{ showingFrom }} to {{ showingTo }} of {{ totalItems }} runs
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
                :total="totalItems"
                :items-per-page="itemsPerPage"
                :disabled="totalPages <= 1"
              />
            </div>
          </div>
        </template>
      </div>
    </template>
  </UDashboardPanel>
</template>
