<script setup lang="ts">
import type { DropdownMenuItem, TableColumn } from '@nuxt/ui'
import { LazyAdminSlideoverEditQuota } from '#components'

definePageMeta({ layout: 'admin', middleware: 'admin' })

const UBadge = resolveComponent('UBadge')
const UDropdownMenu = resolveComponent('UDropdownMenu')
const UButton = resolveComponent('UButton')
const UProgress = resolveComponent('UProgress')

const toast = useToast()
const overlay = useOverlay()
const { refreshAdminQuotas } = useDataRefresh()

const editSlideover = overlay.create(LazyAdminSlideoverEditQuota)

interface AdminQuota {
  workspaceId: string
  quotaLimitBytes: number
  quotaLimitFormatted: string
  currentUsageFormatted: string
  usagePercentage: number
  isCustom: boolean
  isQuotaExceeded: boolean
  [key: string]: unknown
}

interface AdminWorkspace {
  id: string
  name: string
}

type AdminQuotaRow = AdminQuota & {
  workspaceName: string
  name: string
  description: string
}

const { data: quotas, refresh, pending } = await useFetch<AdminQuota[]>('/api/storage/quotas/admin/all', {
  key: globalKey('admin', 'storage-quotas', 'all'),
  default: () => []
})

const { data: adminWorkspaces } = await useFetch<AdminWorkspace[]>('/api/admin/workspaces', {
  key: globalKey('admin', 'workspaces', 'all'),
  default: () => []
})

const { data: defaultQuota } = await useFetch<number>('/api/storage/quotas/admin/default', {
  key: globalKey('admin', 'storage-quotas', 'default')
})

type EditQuotaSlideoverPayload = {
  quota: {
    workspaceId: string
    quotaLimitBytes: number
    isCustom: boolean
  }
  defaultQuota: number
}

async function openEditQuotaSlideover(quota: AdminQuotaRow) {
  const payload: EditQuotaSlideoverPayload = {
    quota: {
      workspaceId: quota.workspaceId,
      quotaLimitBytes: quota.quotaLimitBytes,
      isCustom: quota.isCustom
    },
    defaultQuota: defaultQuota.value ?? 0
  }

  const instance = editSlideover.open(payload)
  await instance.result
}

const workspaceNameById = computed<Record<string, string>>(() => {
  return Object.fromEntries((adminWorkspaces.value ?? []).map(workspace => [workspace.id, workspace.name]))
})

const datatableUi = {
  base: 'table-fixed border-separate border-spacing-0',
  thead: '[&>tr]:bg-elevated/50 [&>tr]:after:content-none',
  tbody: '[&>tr]:last:[&>td]:border-b-0',
  th: 'py-2 first:rounded-l-lg last:rounded-r-lg border-y border-default first:border-l last:border-r',
  td: 'border-b border-default',
  separator: 'h-0'
}

const quotaRows = computed<AdminQuotaRow[]>(() => {
  return (quotas.value ?? []).map((quota) => {
    const workspaceName = workspaceNameById.value[quota.workspaceId] || quota.workspaceId

    return {
      ...quota,
      workspaceName,
      name: workspaceName,
      description: `${quota.workspaceId} ${quota.currentUsageFormatted} ${quota.quotaLimitFormatted}`
    }
  })
})

const { sort, globalFilter, columnFilters, filteredAndSortedData, activeFilters, resetAllFilters } = useTableFilters(quotaRows, { column: 'usagePercentage', direction: 'desc' })

const page = ref(1)
const itemsPerPage = ref(25)
const totalItems = computed(() => filteredAndSortedData.value.length)
const totalPages = computed(() => Math.max(1, Math.ceil(totalItems.value / itemsPerPage.value)))
const showingFrom = computed(() => totalItems.value === 0 ? 0 : (page.value - 1) * itemsPerPage.value + 1)
const showingTo = computed(() => Math.min(page.value * itemsPerPage.value, totalItems.value))
const paginatedRows = computed(() => {
  const start = (page.value - 1) * itemsPerPage.value
  return filteredAndSortedData.value.slice(start, start + itemsPerPage.value)
})

watch([globalFilter, columnFilters, itemsPerPage], () => {
  page.value = 1
}, { deep: true })

watch(totalPages, (newTotalPages) => {
  if (page.value > newTotalPages) {
    page.value = newTotalPages
  }
})

function toggleSort(column: string, initialDirection: 'asc' | 'desc' = 'asc') {
  if (sort.value.column === column) {
    sort.value.direction = sort.value.direction === 'asc' ? 'desc' : 'asc'
  } else {
    sort.value = { column, direction: initialDirection }
  }
}

function sortableHeader(label: string, column: string, initialDirection: 'asc' | 'desc' = 'asc') {
  return h('div', { class: 'flex items-center gap-2' }, [
    h('span', label),
    h(UButton, {
      icon: sort.value.column === column
        ? (sort.value.direction === 'asc' ? 'i-lucide-arrow-up' : 'i-lucide-arrow-down')
        : 'i-lucide-arrow-up-down',
      size: 'xs',
      variant: 'ghost',
      color: sort.value.column === column ? 'primary' : 'neutral',
      onClick: () => toggleSort(column, initialDirection)
    })
  ])
}

const columns: TableColumn<AdminQuotaRow>[] = [
  {
    accessorKey: 'workspaceName',
    header: () => sortableHeader('Workspace', 'workspaceName', 'asc'),
    cell: ({ row }) => h('div', { class: 'min-w-52' }, [
      h('p', { class: 'font-medium' }, row.original.workspaceName),
      h('p', { class: 'text-xs text-muted font-mono' }, row.original.workspaceId)
    ])
  },
  {
    accessorKey: 'quotaLimitFormatted',
    header: () => sortableHeader('Quota Limit', 'quotaLimitFormatted', 'asc')
  },
  {
    accessorKey: 'currentUsageFormatted',
    header: () => sortableHeader('Current Usage', 'currentUsageFormatted', 'desc')
  },
  {
    accessorKey: 'usagePercentage',
    header: () => sortableHeader('Usage', 'usagePercentage', 'desc'),
    cell: ({ row }) => {
      const pct = row.original.usagePercentage
      return h('div', { class: 'min-w-24 space-y-1' }, [
        h(UProgress, {
          modelValue: pct,
          max: 100,
          color: pct >= 90 ? 'error' : pct >= 80 ? 'warning' : 'primary',
          size: 'xs',
          animation: false,
          status: true
        })
      ])
    }
  },
  {
    accessorKey: 'isCustom',
    header: 'Custom',
    cell: ({ row }) => h(UBadge, { color: row.original.isCustom ? 'primary' : 'neutral', variant: 'soft' }, () => row.original.isCustom ? 'Yes' : 'No')
  },
  {
    accessorKey: 'isQuotaExceeded',
    header: 'Status',
    cell: ({ row }) => h(UBadge, { color: row.original.isQuotaExceeded ? 'error' : 'success', variant: 'soft' }, () => row.original.isQuotaExceeded ? 'Exceeded' : 'OK')
  },
  {
    accessorKey: 'actions',
    header: 'Actions',
    cell: ({ row }) => h(UDropdownMenu, { items: [getActions(row.original)] }, {
      default: () => h(UButton, { color: 'neutral', variant: 'ghost', icon: 'i-lucide-more-horizontal' })
    })
  }
]

const getActions = (quota: AdminQuotaRow) => [
  { label: 'Edit Quota', icon: 'i-lucide-edit', onSelect: async () => await openEditQuotaSlideover(quota) },
  { label: 'Recalculate Usage', icon: 'i-lucide-refresh-cw', onSelect: () => recalculateUsage(quota.workspaceId) },
  { label: 'Reset to Default', icon: 'i-lucide-rotate-ccw', onSelect: () => resetToDefault(quota.workspaceId) }
]

const contextMenuQuota = ref<AdminQuotaRow | null>(null)
const contextMenuItems = computed<DropdownMenuItem[][]>(() => {
  if (!contextMenuQuota.value) return []
  return [getActions(contextMenuQuota.value)]
})

function handleRowContextMenu(_event: Event, row: { original: Record<string, unknown> }) {
  contextMenuQuota.value = row.original as unknown as AdminQuotaRow
}

function clearFilters() {
  resetAllFilters()
  page.value = 1
}

async function recalculateUsage(workspaceId: string) {
  try {
    await $fetch(`/api/storage/quotas/workspace/${workspaceId}/recalculate`, { method: 'POST' })
    toast.add({ title: 'Success', description: 'Usage recalculated successfully', color: 'success' })
    await refreshAdminQuotas()
  } catch (error) {
    showApiErrorToast({ title: 'Error', error, fallback: 'Failed to recalculate usage' })
  }
}

async function resetToDefault(workspaceId: string) {
  if (!defaultQuota.value) return

  try {
    await $fetch(`/api/storage/quotas/admin/workspace/${workspaceId}`, {
      method: 'PUT',
      body: { quotaLimitBytes: defaultQuota.value, isCustom: false }
    })
    toast.add({ title: 'Success', description: 'Quota reset to default', color: 'success' })
    await refreshAdminQuotas()
  } catch (error) {
    showApiErrorToast({ title: 'Error', error, fallback: 'Failed to reset quota' })
  }
}

async function resetAllToDefault() {
  try {
    await $fetch('/api/storage/quotas/admin/reset-defaults', { method: 'POST' })
    toast.add({ title: 'Success', description: 'All non-custom quotas reset to default', color: 'success' })
    await refreshAdminQuotas()
  } catch (error) {
    showApiErrorToast({ title: 'Error', error, fallback: 'Failed to reset quotas' })
  }
}

const { data: exceededQuotas } = await useFetch<AdminQuota[]>('/api/storage/quotas/admin/exceeded', {
  key: globalKey('admin', 'storage-quotas', 'exceeded'),
  default: () => []
})

const statusOptions = [{ value: 'true', label: 'Exceeded' }, { value: 'false', label: 'OK' }]
const customOptions = [{ value: 'true', label: 'Custom' }, { value: 'false', label: 'Default' }]
const itemsPerPageOptions = [10, 25, 50, 100].map(value => ({ label: `${value} per page`, value }))
const quotaExceededFilter = computed<string | undefined>({
  get: () => typeof columnFilters.value.isQuotaExceeded === 'string' ? columnFilters.value.isQuotaExceeded : undefined,
  set: (value) => {
    if (!value) {
      const { isQuotaExceeded: _removed, ...rest } = columnFilters.value
      columnFilters.value = rest
      return
    }

    columnFilters.value = {
      ...columnFilters.value,
      isQuotaExceeded: value
    }
  }
})
const customQuotaFilter = computed<string | undefined>({
  get: () => typeof columnFilters.value.isCustom === 'string' ? columnFilters.value.isCustom : undefined,
  set: (value) => {
    if (!value) {
      const { isCustom: _removed, ...rest } = columnFilters.value
      columnFilters.value = rest
      return
    }

    columnFilters.value = {
      ...columnFilters.value,
      isCustom: value
    }
  }
})

function formatBytes(bytes: number) {
  if (bytes === 0) return '0 B'
  const k = 1024
  const sizes = ['B', 'KB', 'MB', 'GB', 'TB']
  const i = Math.floor(Math.log(bytes) / Math.log(k))
  return parseFloat((bytes / Math.pow(k, i)).toFixed(2)) + ' ' + sizes[i]
}
</script>

<template>
  <UDashboardPanel>
    <template #header>
      <UDashboardNavbar title="Storage Quota Management" :ui="{ right: 'gap-3' }">
        <template #leading>
          <UDashboardSidebarCollapse />
        </template>
        <template #right>
          <UButton
            color="neutral"
            variant="outline"
            icon="i-lucide-refresh-cw"
            label="Refresh"
            :loading="pending"
            @click="refresh()"
          />
          <UButton
            color="error"
            variant="subtle"
            icon="i-lucide-rotate-ccw"
            label="Reset All to Default"
            @click="resetAllToDefault()"
          />
        </template>
      </UDashboardNavbar>

      <UDashboardToolbar>
        <template #left>
          <UInput
            v-model="globalFilter"
            placeholder="Search by workspace name or ID..."
            icon="i-lucide-search"
            class="w-full sm:w-80"
          >
            <template v-if="globalFilter" #trailing>
              <UButton
                color="neutral"
                variant="link"
                icon="i-lucide-x"
                :padded="false"
                @click="globalFilter = ''"
              />
            </template>
          </UInput>

          <USelectMenu
            v-model="quotaExceededFilter"
            :items="statusOptions"
            value-key="value"
            placeholder="All statuses"
            class="w-full sm:w-40"
          />

          <USelectMenu
            v-model="customQuotaFilter"
            :items="customOptions"
            value-key="value"
            placeholder="All types"
            class="w-full sm:w-40"
          />

          <UButton
            v-if="activeFilters.length > 0"
            color="neutral"
            variant="ghost"
            size="sm"
            @click="clearFilters"
          >
            Clear Filters
          </UButton>
        </template>

        <template #right>
          <div v-if="activeFilters.length > 0" class="flex items-center gap-2">
            <span class="text-xs text-neutral-500">Active filters:</span>
            <UBadge
              v-for="filter in activeFilters"
              :key="`${filter.type}-${filter.column || 'global'}`"
              variant="soft"
              color="neutral"
              size="sm"
              class="flex items-center gap-1"
            >
              {{ filter.label }}
              <UButton
                size="2xs"
                color="neutral"
                variant="link"
                icon="i-lucide-x"
                :padded="false"
                @click="filter.clear()"
              />
            </UBadge>
          </div>
        </template>
      </UDashboardToolbar>
    </template>

    <template #body>
      <div class="mb-6 grid grid-cols-1 gap-3 md:grid-cols-4">
        <div class="rounded-lg bg-elevated/30 px-4 py-3">
          <p class="text-xs uppercase tracking-wide text-muted">
            Total Workspaces
          </p>
          <div class="mt-2 text-xl font-semibold text-highlighted">
            {{ quotas?.length || 0 }}
          </div>
        </div>

        <div class="rounded-lg bg-elevated/30 px-4 py-3">
          <p class="text-xs uppercase tracking-wide text-muted">
            Quota Exceeded
          </p>
          <div class="mt-2 text-xl font-semibold text-error">
            {{ exceededQuotas?.length || 0 }}
          </div>
        </div>

        <div class="rounded-lg bg-elevated/30 px-4 py-3">
          <p class="text-xs uppercase tracking-wide text-muted">
            Custom Quotas
          </p>
          <div class="mt-2 text-xl font-semibold text-primary">
            {{ quotas?.filter(q => q.isCustom).length || 0 }}
          </div>
        </div>

        <div class="rounded-lg bg-elevated/30 px-4 py-3">
          <p class="text-xs uppercase tracking-wide text-muted">
            Default Quota
          </p>
          <div class="mt-2 text-xl font-semibold text-highlighted">
            {{ formatBytes(defaultQuota || 0) }}
          </div>
        </div>
      </div>

      <div>
        <UContextMenu :items="contextMenuItems">
          <AppTable
            table-id="admin-quotas"
            :data="paginatedRows"
            :columns="columns"
            :loading="pending"
            :ui="datatableUi"
            @contextmenu="handleRowContextMenu"
          />
        </UContextMenu>

        <div class="mt-4 flex flex-col gap-4 border-t border-default pt-4 lg:flex-row lg:items-center lg:justify-between">
          <div class="text-sm text-muted">
            Showing {{ showingFrom }} to {{ showingTo }} of {{ totalItems }} quota entries
          </div>

          <div class="flex items-center gap-4">
            <USelect
              v-model="itemsPerPage"
              :items="itemsPerPageOptions"
              value-key="value"
              class="w-32"
              size="sm"
            />

            <UPagination
              v-model:page="page"
              :total="totalItems"
              :items-per-page="itemsPerPage"
              show-edges
              :sibling-count="1"
            />
          </div>
        </div>
      </div>
    </template>
  </UDashboardPanel>
</template>
