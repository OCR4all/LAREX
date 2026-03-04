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

const { sort, globalFilter, filteredAndSortedData, activeFilters, resetAllFilters } = useTableFilters(workspaces, { column: 'created', direction: 'desc' })

const page = ref(1)
const itemsPerPage = ref(25)
const totalItems = computed(() => filteredAndSortedData.value.length)
const totalPages = computed(() => Math.max(1, Math.ceil(totalItems.value / itemsPerPage.value)))
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

const personalCount = computed(() => workspaces.value.filter(w => w.isPersonal).length)
const teamCount = computed(() => workspaces.value.filter(w => !w.isPersonal).length)
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
    </template>

    <template #body>
      <div class="grid grid-cols-1 md:grid-cols-3 gap-4 mb-6">
        <UCard>
          <div class="text-center">
            <h3 class="text-2xl font-bold">
              {{ workspaces.length }}
            </h3>
            <p class="text-sm text-muted">
              Total Workspaces
            </p>
          </div>
        </UCard>
        <UCard>
          <div class="text-center">
            <h3 class="text-2xl font-bold">
              {{ personalCount }}
            </h3>
            <p class="text-sm text-muted">
              Personal
            </p>
          </div>
        </UCard>
        <UCard>
          <div class="text-center">
            <h3 class="text-2xl font-bold text-primary">
              {{ teamCount }}
            </h3>
            <p class="text-sm text-muted">
              Team
            </p>
          </div>
        </UCard>
      </div>

      <UCard>
        <template #header>
          <div class="flex flex-col sm:flex-row gap-4 items-start sm:items-center justify-between">
            <div class="flex-1 max-w-md">
              <UInput v-model="globalFilter" placeholder="Search workspaces..." icon="i-lucide-search">
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
            </div>
            <UButton
              v-if="activeFilters.length > 0"
              color="neutral"
              variant="outline"
              size="sm"
              @click="resetAllFilters"
            >
              Clear Filters
            </UButton>
          </div>
        </template>

        <UTable
          :data="paginatedRows"
          :columns="columns"
          :loading="pending"
          :ui="datatableUi"
        />

        <template #footer>
          <div class="flex flex-col gap-4 lg:flex-row lg:items-center lg:justify-between">
            <div class="text-sm text-muted">
              Showing {{ totalItems === 0 ? 0 : (page - 1) * itemsPerPage + 1 }} to {{ Math.min(page * itemsPerPage, totalItems) }} of {{ totalItems }} workspaces
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
        </template>
      </UCard>
    </template>
  </UDashboardPanel>
</template>
