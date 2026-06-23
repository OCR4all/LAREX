<script setup lang="ts">
import type { TableColumn } from '@nuxt/ui'
import type { AdminErrorEventDetail, AdminErrorEventPage, AdminErrorEventSummary, AdminErrorSummary } from '@/types/admin-errors'
import type { AdminUserPage } from '@/types/admin-users'
import { getWorkspaceDisplayName } from '@/utils/workspace-display'

definePageMeta({ layout: 'admin', middleware: 'admin' })

const UBadge = resolveComponent('UBadge')
const UButton = resolveComponent('UButton')

const page = ref(1)
const itemsPerPage = ref(25)
const dayWindow = ref(7)
const statusFilter = ref<'ALL' | '403' | '409' | '500' | '507'>('ALL')
const userFilter = ref('all')
const workspaceFilter = ref('all')
const searchInput = ref('')
const debouncedSearch = ref('')
const selectedErrorId = ref<string | null>(null)
const isDetailsOpen = ref(false)
const detailErrorMessage = ref<string | null>(null)

const datatableUi = {
  base: 'table-fixed border-separate border-spacing-0',
  thead: '[&>tr]:bg-elevated/50 [&>tr]:after:content-none',
  tbody: '[&>tr]:last:[&>td]:border-b-0',
  th: 'py-2 first:rounded-l-lg last:rounded-r-lg border-y border-default first:border-l last:border-r',
  td: 'border-b border-default',
  separator: 'h-0'
}

watch(searchInput, useDebounceFn((value: string) => {
  debouncedSearch.value = value.trim()
  page.value = 1
}, 250))

watch([dayWindow, statusFilter, userFilter, workspaceFilter], () => {
  page.value = 1
})

const query = computed(() => {
  const value: Record<string, string | number> = {
    page: page.value - 1,
    size: itemsPerPage.value,
    days: dayWindow.value
  }

  if (statusFilter.value !== 'ALL') {
    value.status = Number(statusFilter.value)
  }
  if (userFilter.value !== 'all') {
    value.userId = userFilter.value
  }
  if (workspaceFilter.value !== 'all') {
    value.workspaceId = workspaceFilter.value
  }
  if (debouncedSearch.value) {
    value.query = debouncedSearch.value
  }

  return value
})

const errorsKey = computed(() => globalKey(
  'admin',
  'errors',
  page.value,
  itemsPerPage.value,
  dayWindow.value,
  statusFilter.value,
  userFilter.value,
  workspaceFilter.value,
  debouncedSearch.value || 'none'
))

const { data: summary24h } = await useFetch<AdminErrorSummary>('/api/admin/errors/summary', {
  key: globalKey('admin', 'errors', 'summary', '1d'),
  query: { days: 1 },
  default: () => ({
    windowDays: 1,
    totalEvents: 0,
    serverErrors: 0,
    actionableClientErrors: 0,
    distinctUsers: 0,
    distinctWorkspaces: 0
  })
})

const { data: summary7d } = await useFetch<AdminErrorSummary>('/api/admin/errors/summary', {
  key: globalKey('admin', 'errors', 'summary', '7d'),
  query: { days: 7 },
  default: () => ({
    windowDays: 7,
    totalEvents: 0,
    serverErrors: 0,
    actionableClientErrors: 0,
    distinctUsers: 0,
    distinctWorkspaces: 0
  })
})

const { data: errorsPage, refresh, pending } = await useFetch<AdminErrorEventPage>('/api/admin/errors', {
  key: errorsKey,
  query,
  watch: [query],
  default: () => ({
    items: [],
    page: 0,
    size: itemsPerPage.value,
    totalElements: 0,
    totalPages: 0
  })
})

const { data: usersPage } = await useFetch<AdminUserPage>('/api/admin/users', {
  key: globalKey('admin', 'errors', 'users-filter'),
  query: { page: 0, size: 100, status: 'ALL', includeServiceAccounts: false },
  default: () => ({
    items: [],
    page: 0,
    size: 100,
    totalElements: 0,
    totalPages: 0,
    creationAllowed: true,
    setupEmailAllowed: true
  })
})

interface AdminWorkspace {
  id: string
  name: string
  isPersonal: boolean
  ownerUserId: string
  ownerUsername?: string | null
}

const { data: workspaces } = await useFetch<AdminWorkspace[]>('/api/admin/workspaces', {
  key: globalKey('admin', 'errors', 'workspaces-filter'),
  default: () => []
})

const { data: detailData, pending: detailPending, refresh: refreshDetail } = await useFetch<AdminErrorEventDetail>(
  () => selectedErrorId.value ? `/api/admin/errors/${selectedErrorId.value}` : '/api/admin/errors/none',
  {
    key: () => selectedErrorId.value ? globalKey('admin', 'errors', selectedErrorId.value) : globalKey('admin', 'errors', 'none'),
    immediate: false
  }
)

const rows = computed(() => errorsPage.value.items || [])
const totalItems = computed(() => errorsPage.value.totalElements || 0)
const totalPages = computed(() => Math.max(1, errorsPage.value.totalPages || 1))
const itemsPerPageModel = useItemsPerPageModel(page, itemsPerPage, totalItems)
const showingFrom = computed(() => totalItems.value === 0 ? 0 : (page.value - 1) * itemsPerPage.value + 1)
const showingTo = computed(() => Math.min(page.value * itemsPerPage.value, totalItems.value))
const activeErrorFilters = computed(() => {
  const filters: Array<{ key: string, label: string, clear: () => void }> = []
  if (searchInput.value.trim()) {
    filters.push({
      key: 'search',
      label: `Search: ${searchInput.value}`,
      clear: () => { searchInput.value = '' }
    })
  }
  if (dayWindow.value !== 7) {
    filters.push({
      key: 'days',
      label: `${dayWindow.value} days`,
      clear: () => { dayWindow.value = 7 }
    })
  }
  if (statusFilter.value !== 'ALL') {
    filters.push({
      key: 'status',
      label: `Status: ${statusFilter.value}`,
      clear: () => { statusFilter.value = 'ALL' }
    })
  }
  if (userFilter.value !== 'all') {
    filters.push({
      key: 'user',
      label: userOptions.value.find(option => option.value === userFilter.value)?.label ?? userFilter.value,
      clear: () => { userFilter.value = 'all' }
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

watch(totalPages, (value) => {
  if (page.value > value) {
    page.value = value
  }
})

const userOptions = computed(() => [
  { label: 'All users', value: 'all' },
  ...(usersPage.value.items || []).map(user => ({
    label: user.username,
    value: user.id
  }))
])

const workspaceOptions = computed(() => [
  { label: 'All workspaces', value: 'all' },
  ...(workspaces.value || []).map(workspace => ({
    label: getWorkspaceDisplayName(workspace),
    value: workspace.id
  }))
])

const columns = computed<TableColumn<AdminErrorEventSummary>[]>(() => [
  {
    accessorKey: 'created',
    header: 'Created',
    cell: ({ row }) => formatDate(row.original.created)
  },
  {
    accessorKey: 'status',
    header: 'Status',
    cell: ({ row }) => h(UBadge, {
      color: row.original.severity === 'ERROR' ? 'error' : 'warning',
      variant: 'soft'
    }, () => String(row.original.status))
  },
  {
    accessorKey: 'message',
    header: 'Message',
    cell: ({ row }) => h('div', { class: 'min-w-0' }, [
      h('div', { class: 'truncate font-medium' }, row.original.message),
      h('div', { class: 'truncate text-xs text-muted' }, row.original.path)
    ])
  },
  {
    accessorKey: 'username',
    header: 'User',
    cell: ({ row }) => row.original.username || row.original.userId || '-'
  },
  {
    accessorKey: 'workspaceId',
    header: 'Workspace',
    cell: ({ row }) => row.original.workspaceId || '-'
  },
  {
    accessorKey: 'actions',
    header: 'Actions',
    cell: ({ row }) => h(UButton, {
      label: 'Details',
      size: 'xs',
      variant: 'outline',
      color: 'neutral',
      onClick: () => openDetails(row.original.id)
    })
  }
])

function formatDate(value?: string | null): string {
  if (!value) {
    return '-'
  }

  return new Intl.DateTimeFormat('en-GB', {
    day: '2-digit',
    month: 'short',
    year: 'numeric',
    hour: '2-digit',
    minute: '2-digit'
  }).format(new Date(value))
}

async function openDetails(errorId: string) {
  selectedErrorId.value = errorId
  detailErrorMessage.value = null
  isDetailsOpen.value = true

  try {
    await refreshDetail()
  } catch (error) {
    detailErrorMessage.value = error instanceof Error ? error.message : 'Failed to load error details'
  }
}

function closeDetails() {
  isDetailsOpen.value = false
  selectedErrorId.value = null
  detailErrorMessage.value = null
}

function clearFilters() {
  page.value = 1
  dayWindow.value = 7
  statusFilter.value = 'ALL'
  userFilter.value = 'all'
  workspaceFilter.value = 'all'
  searchInput.value = ''
  debouncedSearch.value = ''
}
</script>

<template>
  <UDashboardPanel>
    <template #header>
      <UDashboardNavbar title="Error Events" :ui="{ right: 'gap-3' }">
        <template #right>
          <UButton
            color="neutral"
            variant="outline"
            icon="i-lucide-refresh-cw"
            label="Refresh"
            :loading="pending"
            @click="refresh()"
          />
        </template>
      </UDashboardNavbar>

      <UDashboardToolbar>
        <template #left>
          <UInput
            v-model="searchInput"
            placeholder="Search messages, codes, paths..."
            icon="i-lucide-search"
            class="w-full sm:w-80"
          />

          <USelect v-model="dayWindow" :items="[1, 7, 30]" class="w-28" />
          <USelect
            v-model="statusFilter"
            :items="[
              { label: 'All statuses', value: 'ALL' },
              { label: '403', value: '403' },
              { label: '409', value: '409' },
              { label: '500', value: '500' },
              { label: '507', value: '507' }
            ]"
            value-key="value"
            class="w-36"
          />
          <USelect
            v-model="userFilter"
            :items="userOptions"
            value-key="value"
            class="w-44"
          />
          <USelect
            v-model="workspaceFilter"
            :items="workspaceOptions"
            value-key="value"
            class="w-44"
          />
        </template>
        <template #right>
          <AppTableColumnsDropdown table-id="admin-errors" :columns="columns" />
        </template>
      </UDashboardToolbar>
    </template>

    <template #body>
      <div class="space-y-6">
        <div class="grid grid-cols-1 gap-3 md:grid-cols-2 xl:grid-cols-4">
          <div class="rounded-lg bg-elevated/30 px-4 py-3">
            <p class="text-xs uppercase tracking-wide text-muted">
              24h Events
            </p>
            <div class="mt-2 text-xl font-semibold text-highlighted">
              {{ summary24h.totalEvents }}
            </div>
            <p class="mt-1 text-sm text-muted">
              {{ summary24h.serverErrors }} server / {{ summary24h.actionableClientErrors }} client
            </p>
          </div>
          <div class="rounded-lg bg-elevated/30 px-4 py-3">
            <p class="text-xs uppercase tracking-wide text-muted">
              7d Events
            </p>
            <div class="mt-2 text-xl font-semibold text-highlighted">
              {{ summary7d.totalEvents }}
            </div>
            <p class="mt-1 text-sm text-muted">
              {{ summary7d.serverErrors }} server / {{ summary7d.actionableClientErrors }} client
            </p>
          </div>
          <div class="rounded-lg bg-elevated/30 px-4 py-3">
            <p class="text-xs uppercase tracking-wide text-muted">
              Users (7d)
            </p>
            <div class="mt-2 text-xl font-semibold text-primary">
              {{ summary7d.distinctUsers }}
            </div>
          </div>
          <div class="rounded-lg bg-elevated/30 px-4 py-3">
            <p class="text-xs uppercase tracking-wide text-muted">
              Workspaces (7d)
            </p>
            <div class="mt-2 text-xl font-semibold text-primary">
              {{ summary7d.distinctWorkspaces }}
            </div>
          </div>
        </div>

        <div>
          <AppTableActiveFilters
            :filters="activeErrorFilters"
            @clear-all="clearFilters"
          />

          <AppTable
            table-id="admin-errors"
            :data="rows"
            :columns="columns"
            :loading="pending"
            :ui="datatableUi"
          />
        </div>

        <div class="flex flex-col gap-4 border-t border-default pt-4 lg:flex-row lg:items-center lg:justify-between">
          <div class="text-sm text-muted">
            Showing {{ showingFrom }} to {{ showingTo }} of {{ totalItems }} error events
          </div>

          <div class="flex items-center gap-4">
            <USelect v-model="itemsPerPageModel" :items="[10, 25, 50, 100]" class="w-24" />
            <UPagination
              v-model:page="page"
              :items-per-page="itemsPerPage"
              :total="totalItems"
              :disabled="totalPages <= 1"
            />
          </div>
        </div>

        <LazyAdminSlideoverErrorDetails
          :open="isDetailsOpen"
          :error-event="detailData || null"
          :pending="detailPending"
          :error-message="detailErrorMessage"
          @close="closeDetails"
        />
      </div>
    </template>
  </UDashboardPanel>
</template>
