<script setup lang="ts">
import { globalKey } from '@/utils/fetch-keys'

definePageMeta({ layout: 'admin', middleware: 'admin' })

const router = useRouter()
const workspaceStore = useWorkspaceStore()
const toast = useToast()

const UBadge = resolveComponent('UBadge')
const UButton = resolveComponent('UButton')

interface AdminWorkspace {
  id: string
  name: string
  description?: string
  isPersonal: boolean
  ownerUserId: string
  ownerUsername?: string
  memberCount: number
  projectCount: number
  created: string
}

const { data: workspaces, refresh, pending } = await useFetch<AdminWorkspace[]>('/api/admin/workspaces', {
  key: globalKey('admin', 'workspaces', 'all'),
  default: () => []
})

const datatableUi = {
  base: 'table-fixed border-separate border-spacing-0',
  thead: '[&>tr]:bg-elevated/50 [&>tr]:after:content-none',
  tbody: '[&>tr]:last:[&>td]:border-b-0',
  th: 'py-2 first:rounded-l-lg last:rounded-r-lg border-y border-default first:border-l last:border-r',
  td: 'border-b border-default',
  separator: 'h-0'
}

const { sort, globalFilter, filteredAndSortedData } = useTableFilters(workspaces, { column: 'created', direction: 'desc' })

const page = ref(1)
const itemsPerPage = ref(25)
const totalItems = computed(() => filteredAndSortedData.value.length)
const totalPages = computed(() => Math.max(1, Math.ceil(totalItems.value / itemsPerPage.value)))
const showingFrom = computed(() => totalItems.value === 0 ? 0 : (page.value - 1) * itemsPerPage.value + 1)
const showingTo = computed(() => Math.min(page.value * itemsPerPage.value, totalItems.value))
const hasActiveFilters = computed(() => Boolean(globalFilter.value.trim()))
const paginatedRows = computed(() => {
  const start = (page.value - 1) * itemsPerPage.value
  return filteredAndSortedData.value.slice(start, start + itemsPerPage.value)
})

watch([globalFilter, itemsPerPage], () => {
  page.value = 1
})

watch(totalPages, (newTotalPages) => {
  if (page.value > newTotalPages) {
    page.value = newTotalPages
  }
})

function openWorkspace(workspace: AdminWorkspace) {
  workspaceStore.selectWorkspaceAsAdmin({
    id: workspace.id,
    name: workspace.name,
    description: workspace.description,
    isPersonal: workspace.isPersonal,
    ownerUserId: workspace.ownerUserId
  })
  toast.add({
    title: 'Admin Mode',
    description: `Now viewing "${workspace.name}" as administrator`,
    color: 'warning',
    icon: 'i-lucide-shield-alert'
  })
  router.push('/')
}

function getRowActions(workspace: AdminWorkspace) {
  return [{
    label: 'Open',
    icon: 'i-lucide-external-link',
    onSelect: () => openWorkspace(workspace)
  }]
}

const columns = [
  {
    accessorKey: 'name',
    header: () => h('div', { class: 'flex items-center gap-2' }, [
      h('span', 'Name'),
      h(UButton, { icon: sort.value.column === 'name' ? (sort.value.direction === 'asc' ? 'i-lucide-arrow-up' : 'i-lucide-arrow-down') : 'i-lucide-arrow-up-down', size: 'xs', variant: 'ghost', color: sort.value.column === 'name' ? 'primary' : 'neutral', onClick: () => { sort.value = sort.value.column === 'name' ? { column: 'name', direction: sort.value.direction === 'asc' ? 'desc' : 'asc' } : { column: 'name', direction: 'asc' } } })
    ])
  },
  {
    accessorKey: 'isPersonal',
    header: 'Type',
    cell: ({ row }) => h(UBadge, { color: row.original.isPersonal ? 'neutral' : 'primary', variant: 'soft' }, () => row.original.isPersonal ? 'Personal' : 'Team')
  },
  {
    accessorKey: 'ownerUsername',
    header: 'Owner'
  },
  {
    accessorKey: 'memberCount',
    header: () => h('div', { class: 'flex items-center gap-2' }, [
      h('span', 'Members'),
      h(UButton, { icon: sort.value.column === 'memberCount' ? (sort.value.direction === 'asc' ? 'i-lucide-arrow-up' : 'i-lucide-arrow-down') : 'i-lucide-arrow-up-down', size: 'xs', variant: 'ghost', color: sort.value.column === 'memberCount' ? 'primary' : 'neutral', onClick: () => { sort.value = sort.value.column === 'memberCount' ? { column: 'memberCount', direction: sort.value.direction === 'asc' ? 'desc' : 'asc' } : { column: 'memberCount', direction: 'asc' } } })
    ])
  },
  {
    accessorKey: 'projectCount',
    header: () => h('div', { class: 'flex items-center gap-2' }, [
      h('span', 'Projects'),
      h(UButton, { icon: sort.value.column === 'projectCount' ? (sort.value.direction === 'asc' ? 'i-lucide-arrow-up' : 'i-lucide-arrow-down') : 'i-lucide-arrow-up-down', size: 'xs', variant: 'ghost', color: sort.value.column === 'projectCount' ? 'primary' : 'neutral', onClick: () => { sort.value = sort.value.column === 'projectCount' ? { column: 'projectCount', direction: sort.value.direction === 'asc' ? 'desc' : 'asc' } : { column: 'projectCount', direction: 'asc' } } })
    ])
  },
  {
    accessorKey: 'created',
    header: () => h('div', { class: 'flex items-center gap-2' }, [
      h('span', 'Created'),
      h(UButton, { icon: sort.value.column === 'created' ? (sort.value.direction === 'asc' ? 'i-lucide-arrow-up' : 'i-lucide-arrow-down') : 'i-lucide-arrow-up-down', size: 'xs', variant: 'ghost', color: sort.value.column === 'created' ? 'primary' : 'neutral', onClick: () => { sort.value = sort.value.column === 'created' ? { column: 'created', direction: sort.value.direction === 'asc' ? 'desc' : 'asc' } : { column: 'created', direction: 'asc' } } })
    ]),
    cell: ({ row }) => new Date(row.original.created).toLocaleDateString()
  },
  {
    accessorKey: 'actions',
    header: 'Actions',
    cell: ({ row }) => h(UButton, {
      icon: 'i-lucide-external-link',
      size: 'xs',
      variant: 'soft',
      color: 'primary',
      label: 'Open',
      onClick: () => openWorkspace(row.original)
    })
  }
]

const contextMenuWorkspace = ref<AdminWorkspace | null>(null)
const contextMenuItems = computed(() => {
  if (!contextMenuWorkspace.value) return []
  return getRowActions(contextMenuWorkspace.value)
})

function handleRowContextMenu(_event: Event, row: { original: AdminWorkspace }) {
  contextMenuWorkspace.value = row.original
}

const personalCount = computed(() => workspaces.value.filter(w => w.isPersonal).length)
const teamCount = computed(() => workspaces.value.filter(w => !w.isPersonal).length)

function clearFilters() {
  globalFilter.value = ''
  page.value = 1
}
</script>

<template>
  <UDashboardPanel>
    <template #header>
      <UDashboardNavbar title="Workspace Management" :ui="{ right: 'gap-3' }">
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
        </template>
      </UDashboardNavbar>

      <UDashboardToolbar>
        <template #left>
          <UInput
            v-model="globalFilter"
            placeholder="Search workspaces..."
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

          <UButton
            v-if="hasActiveFilters"
            color="neutral"
            variant="ghost"
            size="sm"
            @click="clearFilters"
          >
            Clear Filters
          </UButton>
        </template>
      </UDashboardToolbar>
    </template>

    <template #body>
      <div class="mb-6 grid grid-cols-1 gap-3 md:grid-cols-3">
        <div class="rounded-lg bg-elevated/30 px-4 py-3">
          <p class="text-xs uppercase tracking-wide text-muted">
            Total Workspaces
          </p>
          <div class="mt-2 text-xl font-semibold text-highlighted">
            {{ workspaces.length }}
          </div>
        </div>

        <div class="rounded-lg bg-elevated/30 px-4 py-3">
          <p class="text-xs uppercase tracking-wide text-muted">
            Personal
          </p>
          <div class="mt-2 text-xl font-semibold text-highlighted">
            {{ personalCount }}
          </div>
        </div>

        <div class="rounded-lg bg-elevated/30 px-4 py-3">
          <p class="text-xs uppercase tracking-wide text-muted">
            Team
          </p>
          <div class="mt-2 text-xl font-semibold text-primary">
            {{ teamCount }}
          </div>
        </div>
      </div>

      <div>
        <UContextMenu :items="contextMenuItems as any">
          <UTable
            :data="paginatedRows"
            :columns="columns"
            :loading="pending"
            :ui="datatableUi"
            @contextmenu="handleRowContextMenu"
          />
        </UContextMenu>

        <div class="mt-4 flex flex-col gap-4 border-t border-default pt-4 lg:flex-row lg:items-center lg:justify-between">
          <div class="text-sm text-muted">
            Showing {{ showingFrom }} to {{ showingTo }} of {{ totalItems }} workspaces
          </div>

          <div class="flex items-center gap-4">
            <USelect
              v-model="itemsPerPage"
              :items="[10, 25, 50, 100]"
              class="w-32"
              size="sm"
            >
              <template #label>
                {{ itemsPerPage }} per page
              </template>
            </USelect>

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
