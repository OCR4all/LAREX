<script setup lang="ts">
import { h, resolveComponent } from 'vue'
import type { TableColumn } from '@nuxt/ui'
import { globalKey } from '~/utils/fetch-keys'
import { LazyWorkspaceSlideoverCreate, LazyUiDeleteSlideover } from '#components'

const UButton = resolveComponent('UButton')
const UBadge = resolveComponent('UBadge')
const UDropdownMenu = resolveComponent('UDropdownMenu')

const toast = useToast()
const overlay = useOverlay()
const workspaceStore = useWorkspaceStore()
const { refreshWorkspaceList } = useDataRefresh()
const { user } = useUserSession()

const createWorkspaceSlideover = overlay.create(LazyWorkspaceSlideoverCreate)
const deleteSlideover = overlay.create(LazyUiDeleteSlideover)

interface Workspace {
  id: string
  name: string
  description?: string
  isPersonal: boolean
  ownerUserId: string
  type: 'personal' | 'team'
}

const { data: workspaces } = await useFetch<Workspace[]>('/api/workspaces', {
  key: globalKey('workspaces', 'list'),
  default: () => []
})

const workspacesSafe = computed(() => workspaces.value ?? [])

type WorkspaceRow = Workspace & { role: 'OWNER' | 'ADMINISTRATOR' | 'MEMBER' }

const rows = computed<WorkspaceRow[]>(() => {
  return workspacesSafe.value.map(ws => ({
    ...ws,
    role: ws.isPersonal || ws.ownerUserId === user.value?.id ? 'OWNER' : 'MEMBER'
  }))
})

const {
  sort,
  globalFilter,
  filteredAndSortedData,
  activeFilters,
  resetAllFilters
} = useTableFilters(rows, { column: 'name', direction: 'asc' })

const page = ref(1)
const itemsPerPage = ref(10)

const totalItems = computed(() => filteredAndSortedData.value.length)
const totalPages = computed(() => Math.ceil(totalItems.value / itemsPerPage.value))
const paginatedData = computed(() => {
  const start = (page.value - 1) * itemsPerPage.value
  return filteredAndSortedData.value.slice(start, start + itemsPerPage.value)
})

watch(globalFilter, () => { page.value = 1 })

const columns: TableColumn<WorkspaceRow>[] = [
  {
    accessorKey: 'name',
    header: () => h('div', { class: 'flex items-center gap-2' }, [
      h('span', 'Name'),
      h(UButton, {
        icon: sort.value.column === 'name'
          ? (sort.value.direction === 'asc' ? 'i-lucide-arrow-up' : 'i-lucide-arrow-down')
          : 'i-lucide-arrow-up-down',
        size: 'xs',
        variant: 'ghost',
        color: sort.value.column === 'name' ? 'primary' : 'neutral',
        onClick: () => {
          if (sort.value.column === 'name') {
            sort.value.direction = sort.value.direction === 'asc' ? 'desc' : 'asc'
          } else {
            sort.value = { column: 'name', direction: 'asc' }
          }
        }
      })
    ]),
    cell: ({ row }) => h('button', {
      class: 'font-medium hover:underline text-primary text-left',
      onClick: () => {
        workspaceStore.selectWorkspace(row.original.id)
        navigateTo('/')
      }
    }, row.getValue('name'))
  },
  {
    accessorKey: 'description',
    header: 'Description',
    cell: ({ row }) => {
      const description = row.getValue('description') as string
      if (!description) return h('span', { class: 'text-neutral-400' }, '—')
      return h('div', { class: 'max-w-64 truncate', title: description }, description)
    }
  },
  {
    accessorKey: 'role',
    header: 'Role',
    cell: ({ row }) => {
      const role = row.getValue('role') as string
      const color = role === 'OWNER' ? 'primary' : role === 'ADMINISTRATOR' ? 'info' : 'neutral'
      return h(UBadge, { variant: 'subtle', color, size: 'sm' }, () => role)
    }
  },
  {
    id: 'storage',
    header: 'Storage',
    cell: ({ row }) => h(resolveComponent('WorkspaceStorageQuotaCell'), { workspaceId: row.original.id })
  },
  {
    id: 'actions',
    header: 'Actions',
    cell: ({ row }) => h('div', { class: 'text-right' },
      h(UDropdownMenu, {
        content: { align: 'end' },
        items: getActions(row.original)
      }, () => h(UButton, {
        icon: 'i-lucide-ellipsis-vertical',
        color: 'neutral',
        variant: 'ghost'
      }))
    )
  }
]

function getActions(ws: WorkspaceRow) {
  const actions: any[][] = [[
    {
      label: 'Select',
      icon: 'i-lucide-check-circle',
      disabled: workspaceStore.selectedWorkspaceId === ws.id,
      onSelect: () => {
        workspaceStore.selectWorkspace(ws.id)
        toast.add({ title: 'Workspace selected', description: ws.name, color: 'success' })
      }
    }
  ]]

  if (!ws.isPersonal) {
    if (ws.role === 'OWNER') {
      actions.push([
        { label: 'Settings', icon: 'i-lucide-settings', onSelect: () => navigateTo(`/workspace/settings?workspaceId=${ws.id}`) },
        { label: 'Delete', icon: 'i-lucide-trash', color: 'error', onSelect: () => openDeleteSlideover(ws) }
      ])
    } else {
      actions.push([
        { label: 'Leave', icon: 'i-lucide-log-out', color: 'warning', onSelect: () => leaveWorkspace(ws) }
      ])
    }
  }

  return actions
}

async function openDeleteSlideover(ws: WorkspaceRow) {
  const instance = deleteSlideover.open({
    name: ws.name,
    entityType: 'Workspace',
    warningMessage: 'This action cannot be undone. This will permanently delete the workspace, all projects, and remove all member associations.'
  })
  const confirmed = await instance.result
  if (!confirmed) return

  try {
    await $fetch(`/api/workspaces/${ws.id}`, { method: 'DELETE' })
    toast.add({ title: 'Workspace deleted', color: 'success' })
    await refreshWorkspaceList()
  } catch (err: any) {
    toast.add({ title: 'Failed to delete', description: err?.data?.message || 'An error occurred', color: 'error' })
  }
}

async function leaveWorkspace(ws: WorkspaceRow) {
  const instance = deleteSlideover.open({
    name: ws.name,
    entityType: 'Workspace',
    warningMessage: `Are you sure you want to leave "${ws.name}"? You will lose access to all resources in this workspace.`
  })
  const confirmed = await instance.result
  if (!confirmed) return

  try {
    await $fetch(`/api/workspaces/${ws.id}/leave`, { method: 'POST' })
    toast.add({ title: 'Left workspace', color: 'success' })
    await refreshWorkspaceList()
  } catch {
    toast.add({ title: 'Failed to leave workspace', color: 'error' })
  }
}
</script>

<template>
  <UDashboardPanel id="workspaces">
    <template #header>
      <UDashboardNavbar title="Workspaces">
        <template #leading>
          <LazyUDashboardSidebarCollapse />
        </template>
        <template #right>
          <UButton
            label="New Workspace"
            color="primary"
            variant="solid"
            icon="i-lucide-plus"
            @click="createWorkspaceSlideover.open()"
          />
        </template>
      </UDashboardNavbar>
      <UDashboardToolbar>
        <template #left>
          <UInput
            v-model="globalFilter"
            placeholder="Search workspaces..."
            icon="i-lucide-search"
            class="w-64"
            size="md"
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
            v-if="activeFilters.length > 0"
            color="neutral"
            variant="ghost"
            size="sm"
            @click="resetAllFilters()"
          >
            Clear Filters
          </UButton>
        </template>
      </UDashboardToolbar>
    </template>

    <template #body>
      <div v-if="workspaces?.length">
        <UTable
          :data="paginatedData"
          :columns="columns"
          class="flex-1"
          :ui="{
            base: 'table-fixed border-separate border-spacing-0',
            thead: '[&>tr]:bg-elevated/50 [&>tr]:after:content-none',
            tbody: '[&>tr]:last:[&>td]:border-b-0',
            th: 'py-2 first:rounded-l-lg last:rounded-r-lg border-y border-default first:border-l last:border-r',
            td: 'border-b border-default',
            separator: 'h-0'
          }"
        />

        <div v-if="totalPages > 1" class="flex justify-between items-center p-4 border-t border-gray-200 dark:border-gray-800">
          <span class="text-sm text-gray-600 dark:text-gray-400">
            Showing {{ (page - 1) * itemsPerPage + 1 }} to {{ Math.min(page * itemsPerPage, totalItems) }} of {{ totalItems }} workspaces
          </span>
          <div class="flex items-center gap-4">
            <USelect
              v-model="itemsPerPage"
              :items="[5, 10, 15, 20, 50]"
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

      <div v-else class="text-center py-12">
        <UIcon name="i-lucide-folder" class="w-12 h-12 text-gray-400 mx-auto mb-4" />
        <h3 class="text-lg font-semibold mb-2">
          No workspaces found
        </h3>
        <p class="text-gray-500 mb-6">
          Create your first workspace to get started
        </p>
        <UButton icon="i-lucide-plus" label="Create Workspace" @click="createWorkspaceModal.open()" />
      </div>
    </template>
  </UDashboardPanel>
</template>
