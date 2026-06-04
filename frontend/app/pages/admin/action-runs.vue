<script setup lang="ts">
import type { TableColumn } from '@nuxt/ui'
import type { AdminActionRun, ActionRunStatus } from '@/types/action'
import { extractApiErrorMessage } from '@/utils/api-error'

definePageMeta({ layout: 'admin', middleware: 'admin' })

const route = useRoute()
const toast = useToast()
const UBadgeComponent = resolveComponent('UBadge')
const UButtonComponent = resolveComponent('UButton')

const searchInput = ref('')
const debouncedSearch = ref('')
const statusFilter = ref<'ALL' | ActionRunStatus>('ALL')
const processorFilter = ref(typeof route.query.definitionId === 'string' ? route.query.definitionId : 'all')
const workspaceFilter = ref('all')
const page = ref(1)
const itemsPerPage = ref(25)
const cancellingRunIds = ref<Set<string>>(new Set())
const runs = ref<AdminActionRun[]>([])
const adminRunsKey = globalKey('admin', 'action-runs', 'all')

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

watch([statusFilter, processorFilter, workspaceFilter], () => {
  page.value = 1
})

watch(() => route.query.definitionId, (value) => {
  processorFilter.value = typeof value === 'string' ? value : 'all'
})

const processorOptions = computed(() => [
  { label: 'All Actions', value: 'all' },
  ...Array.from(new Map(runs.value.map(run => [run.processorDefinitionId, {
    label: run.processorName,
    value: run.processorDefinitionId
  }])).values())
])

const workspaceOptions = computed(() => [
  { label: 'All workspaces', value: 'all' },
  ...Array.from(new Map(runs.value.map(run => [run.workspaceId, {
    label: `${run.workspaceLabel} · ${run.workspaceId}`,
    value: run.workspaceId
  }])).values())
])

const hasActiveFilters = computed(() =>
  debouncedSearch.value.length > 0
  || statusFilter.value !== 'ALL'
  || processorFilter.value !== 'all'
  || workspaceFilter.value !== 'all'
)
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
  if (processorFilter.value !== 'all') {
    filters.push({
      key: 'processor',
      label: processorOptions.value.find(option => option.value === processorFilter.value)?.label ?? processorFilter.value,
      clear: () => { processorFilter.value = 'all' }
    })
  }
  if (workspaceFilter.value !== 'all') {
    filters.push({
      key: 'workspace',
      label: workspaceOptions.value.find(option => option.value === workspaceFilter.value)?.label ?? workspaceFilter.value,
      clear: () => { workspaceFilter.value = 'all' }
    })
  }
  return filters
})

const filteredRuns = computed(() => runs.value
  .filter((run) => {
    if (statusFilter.value !== 'ALL' && run.status !== statusFilter.value) return false
    if (processorFilter.value !== 'all' && run.processorDefinitionId !== processorFilter.value) return false
    if (workspaceFilter.value !== 'all' && run.workspaceId !== workspaceFilter.value) return false
    const needle = debouncedSearch.value.toLowerCase()
    if (!needle) return true
    return [
      run.processorName,
      run.processorKey,
      run.workspaceLabel,
      run.projectLabel,
      run.status,
      run.statusMessage,
      run.errorMessage
    ].some(value => value?.toLowerCase().includes(needle))
  })
  .sort(compareRuns))

const totalItems = computed(() => filteredRuns.value.length)
const totalPages = computed(() => Math.max(1, Math.ceil(totalItems.value / itemsPerPage.value)))
const itemsPerPageModel = useItemsPerPageModel(page, itemsPerPage, totalItems)
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

const columns = computed<TableColumn<AdminActionRun>[]>(() => [
  {
    id: 'action',
    header: 'Action',
    cell: ({ row }) => h('div', { class: 'min-w-0' }, [
      h('div', { class: 'truncate font-medium' }, row.original.processorName),
      h('div', { class: 'truncate text-xs text-muted' }, row.original.processorKey)
    ])
  },
  {
    id: 'target',
    header: 'Workspace / Project',
    cell: ({ row }) => h('div', { class: 'min-w-0' }, [
      h('div', { class: 'truncate font-medium' }, row.original.workspaceLabel),
      h('div', { class: 'truncate text-xs text-muted' }, row.original.workspaceId),
      h('div', { class: 'truncate text-xs text-muted' }, row.original.projectLabel)
    ])
  },
  {
    id: 'status',
    header: 'Status',
    cell: ({ row }) => h('div', { class: 'min-w-0 space-y-1' }, [
      h(UBadgeComponent, {
        size: 'sm',
        variant: 'soft',
        color: statusColor(row.original.status)
      }, () => statusBadgeLabel(row.original)),
      h('div', { class: 'truncate text-xs text-muted' }, row.original.statusMessage || formatStatus(row.original.status))
    ])
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
    id: 'actions',
    header: '',
    cell: ({ row }) => h('div', { class: 'flex items-center justify-end gap-2' }, [
      canCancelRun(row.original)
        ? h(UButtonComponent, {
            color: 'warning',
            variant: 'ghost',
            size: 'xs',
            icon: 'i-lucide-ban',
            type: 'button',
            loading: isCancellingRun(row.original.id),
            onClick: async (event: MouseEvent) => {
              event.stopPropagation()
              await cancelRun(row.original)
            }
          })
        : null,
      h(UButtonComponent, {
        label: 'Action',
        color: 'neutral',
        variant: 'ghost',
        size: 'xs',
        type: 'button',
        onClick: (event: MouseEvent) => {
          event.stopPropagation()
          void navigateTo(`/admin/actions?definitionId=${row.original.processorDefinitionId}`)
        }
      })
    ].filter(Boolean))
  }
])

const {
  data: adminRunsData,
  pending: loading,
  refresh: refreshRuns
} = await useFetch<AdminActionRun[]>(() => `/api/admin/actions/runs`,
  {
    key: adminRunsKey,
    default: () => []
  }
)

watch(adminRunsData, (value) => {
  runs.value = value ?? []
}, { immediate: true })

async function loadRuns() {
  try {
    await refreshRuns()
  } catch (error: unknown) {
    toast.add({
      title: 'Run load failed',
      description: extractApiErrorMessage(error, 'Could not load Action runs.'),
      color: 'error'
    })
  }
}

async function cancelRun(run: AdminActionRun) {
  if (!canCancelRun(run) || isCancellingRun(run.id)) return
  setCancellingRun(run.id, true)
  try {
    await $fetch(`/api/workspaces/${run.workspaceId}/actions/projects/${run.projectId}/runs/${run.id}/cancel`, {
      method: 'POST'
    })
    await loadRuns()
    toast.add({
      title: run.status === 'QUEUED' || run.status === 'PENDING' ? 'Run cancelled' : 'Cancellation requested',
      color: 'success',
      icon: 'i-lucide-ban'
    })
  } catch (error: unknown) {
    toast.add({
      title: 'Cancel failed',
      description: extractApiErrorMessage(error, 'Could not cancel Action run.'),
      color: 'error'
    })
  } finally {
    setCancellingRun(run.id, false)
  }
}

function clearFilters() {
  searchInput.value = ''
  debouncedSearch.value = ''
  statusFilter.value = 'ALL'
  processorFilter.value = typeof route.query.definitionId === 'string' ? route.query.definitionId : 'all'
  workspaceFilter.value = 'all'
}

function compareRuns(left: AdminActionRun, right: AdminActionRun) {
  const leftRank = runSortRank(left)
  const rightRank = runSortRank(right)
  if (leftRank !== rightRank) return leftRank - rightRank
  if (left.status === 'QUEUED' && right.status === 'QUEUED') {
    return (left.queuePosition ?? Number.MAX_SAFE_INTEGER) - (right.queuePosition ?? Number.MAX_SAFE_INTEGER)
  }
  return Date.parse(right.updated) - Date.parse(left.updated)
}

function runSortRank(run: AdminActionRun) {
  if (run.status === 'QUEUED') return 0
  if (isActiveRun(run.status)) return 1
  return 2
}

function isTerminalRun(status: AdminActionRun['status']) {
  return status === 'COMPLETED' || status === 'FAILED' || status === 'CANCELLED'
}

function isActiveRun(status: AdminActionRun['status']) {
  return !isTerminalRun(status)
}

function formatStatus(status: AdminActionRun['status']) {
  return status.replaceAll('_', ' ')
}

function statusBadgeLabel(run: AdminActionRun) {
  return run.status === 'QUEUED' && run.queuePosition
    ? `Queued #${run.queuePosition}`
    : formatStatus(run.status)
}

function statusColor(status: AdminActionRun['status']): 'success' | 'error' | 'neutral' | 'warning' | 'primary' {
  if (status === 'COMPLETED') return 'success'
  if (status === 'FAILED') return 'error'
  if (status === 'CANCELLED') return 'neutral'
  if (status === 'QUEUED' || status === 'CANCEL_REQUESTED') return 'warning'
  return 'primary'
}

function canCancelRun(run: AdminActionRun) {
  return run.canCancel && !isTerminalRun(run.status)
}

function isCancellingRun(runId: string) {
  return cancellingRunIds.value.has(runId)
}

function setCancellingRun(runId: string, value: boolean) {
  const next = new Set(cancellingRunIds.value)
  if (value) {
    next.add(runId)
  } else {
    next.delete(runId)
  }
  cancellingRunIds.value = next
}
</script>

<template>
  <UDashboardPanel id="admin-action-runs">
    <template #header>
      <UDashboardNavbar title="Action Runs">
        <template #leading>
          <UDashboardSidebarCollapse />
        </template>
        <template #right>
          <UButton
            color="neutral"
            variant="outline"
            icon="i-lucide-refresh-cw"
            :loading="loading"
            @click="loadRuns"
          >
            Refresh
          </UButton>
        </template>
      </UDashboardNavbar>

      <UDashboardToolbar>
        <template #left>
          <UInput
            v-model="searchInput"
            icon="i-lucide-search"
            placeholder="Search action, workspace, project, or status..."
            class="w-full sm:w-80"
          />
          <USelect
            v-model="statusFilter"
            :items="statusOptions"
            value-key="value"
            class="w-full sm:w-52"
          />
          <USelect
            v-model="processorFilter"
            :items="processorOptions"
            value-key="value"
            class="w-full sm:w-56"
          />
          <USelect
            v-model="workspaceFilter"
            :items="workspaceOptions"
            value-key="value"
            class="w-full sm:w-56"
          />
        </template>
        <template #right>
          <AppTableColumnsDropdown table-id="admin-action-runs" :columns="columns" />
        </template>
      </UDashboardToolbar>
    </template>

    <template #body>
      <UEmpty
        v-if="!loading && runs.length === 0"
        variant="naked"
        icon="i-lucide-circle-play"
        title="No Action runs yet"
        description="Queued, active, and finished Action runs across all workspaces will appear here."
      />

      <div v-else class="flex h-full min-h-0 flex-col">
        <div v-if="loading && runs.length === 0" class="space-y-2 p-4">
          <USkeleton class="h-14 w-full" />
          <USkeleton class="h-14 w-full" />
          <USkeleton class="h-14 w-full" />
        </div>

        <template v-else>
          <AppTableActiveFilters
            :filters="activeRunFilters"
            @clear-all="clearFilters"
          />

          <UEmpty
            v-if="filteredRuns.length === 0"
            variant="naked"
            icon="i-lucide-filter-x"
            title="No matching Action runs"
            :description="hasActiveFilters ? 'Try adjusting the current filters.' : 'No Action runs match the current view.'"
          />

          <AppTable
            v-else
            table-id="admin-action-runs"
            :data="paginatedRuns"
            :columns="columns"
            :default-visible-column-ids="['action', 'target', 'status', 'pageCount', 'updated', 'actions']"
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
