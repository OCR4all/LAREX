<script setup lang="ts">
import type { DropdownMenuItem, TableColumn, TableRow } from '@nuxt/ui'
import type { WorkspaceCapabilities } from '@/types/capabilities'
import { LazyWorkspaceSlideoverCreate, LazyUiDeleteSlideover } from '#components'

const UButton = resolveComponent('UButton')
const UBadge = resolveComponent('UBadge')
const UDropdownMenu = resolveComponent('UDropdownMenu')

const toast = useToast()
const overlay = useOverlay()
const workspaceStore = useWorkspaceStore()
const { allow, compactGroups } = useActionVisibility()
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
  capabilities?: WorkspaceCapabilities
}

const { data: workspaces } = await useFetch<Workspace[]>('/api/workspaces', {
  key: globalKey('workspaces', 'list'),
  default: () => []
})

const workspacesSafe = computed(() => workspaces.value ?? [])

type WorkspaceRow = Workspace & { role: 'OWNER' | 'CURATOR' | 'EDITOR' }
const canCreateTeamWorkspace = computed(() => {
  const roles = user.value?.roles || []
  return roles.includes('GLOBAL_ADMIN') || roles.includes('GLOBAL_CURATOR')
})

const rows = computed<WorkspaceRow[]>(() => {
  return workspacesSafe.value.map(ws => ({
    ...ws,
    role: ws.isPersonal || ws.ownerUserId === user.value?.id
      ? 'OWNER'
      : (ws.capabilities?.canManageMembers ? 'CURATOR' : 'EDITOR')
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
const itemsPerPageRef = ref(10)

const totalItems = computed(() => filteredAndSortedData.value.length)
const totalPages = computed(() => Math.max(1, Math.ceil(totalItems.value / itemsPerPageRef.value)))
const itemsPerPage = useItemsPerPageModel(page, itemsPerPageRef, totalItems)
const paginatedData = computed(() => {
  const start = (page.value - 1) * itemsPerPageRef.value
  return filteredAndSortedData.value.slice(start, start + itemsPerPageRef.value)
})

const selectedWorkspaceIds = ref<Set<string>>(new Set())
const selectedWorkspaces = computed(() => rows.value.filter(workspace => selectedWorkspaceIds.value.has(workspace.id)))
const canDeleteSelected = computed(() =>
  selectedWorkspaces.value.length > 0
  && selectedWorkspaces.value.every(workspace => !workspace.isPersonal && allow(workspace.capabilities?.canAdminWorkspace))
)
const allPageSelected = computed(() =>
  paginatedData.value.length > 0
  && paginatedData.value.every(workspace => selectedWorkspaceIds.value.has(workspace.id))
)
const somePageSelected = computed(() =>
  paginatedData.value.some(workspace => selectedWorkspaceIds.value.has(workspace.id))
  && !allPageSelected.value
)

watch(globalFilter, () => {
  page.value = 1
})
watch(totalPages, (value) => {
  if (page.value > value) {
    page.value = value
  }
})
watch(rows, (nextRows) => {
  const validIds = new Set(nextRows.map(workspace => workspace.id))
  selectedWorkspaceIds.value = new Set(Array.from(selectedWorkspaceIds.value).filter(id => validIds.has(id)))
}, { immediate: true })

function toggleWorkspaceSelection(workspaceId: string) {
  const next = new Set(selectedWorkspaceIds.value)
  if (next.has(workspaceId)) next.delete(workspaceId)
  else next.add(workspaceId)
  selectedWorkspaceIds.value = next
}

function toggleCurrentPageSelection() {
  const next = new Set(selectedWorkspaceIds.value)
  if (allPageSelected.value) {
    paginatedData.value.forEach(workspace => next.delete(workspace.id))
  } else {
    paginatedData.value.forEach(workspace => next.add(workspace.id))
  }
  selectedWorkspaceIds.value = next
}

function clearSelection() {
  selectedWorkspaceIds.value = new Set()
}

const columns: TableColumn<WorkspaceRow>[] = [
  {
    id: 'select',
    header: () => h('input', {
      type: 'checkbox',
      checked: allPageSelected.value,
      indeterminate: somePageSelected.value,
      onChange: toggleCurrentPageSelection,
      class: 'rounded-sm border-neutral-300 text-primary-600 focus:ring-primary-500'
    }),
    cell: ({ row }) => h('input', {
      type: 'checkbox',
      checked: selectedWorkspaceIds.value.has(row.original.id),
      onChange: () => toggleWorkspaceSelection(row.original.id),
      onClick: (event: Event) => event.stopPropagation(),
      class: 'rounded-sm border-neutral-300 text-primary-600 focus:ring-primary-500'
    })
  },
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
      const color = role === 'OWNER' ? 'primary' : role === 'CURATOR' ? 'info' : 'neutral'
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

function getActions(ws: WorkspaceRow): DropdownMenuItem[][] {
  const actions: DropdownMenuItem[][] = [[
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

  if (!ws.isPersonal && allow(ws.capabilities?.canEditWorkspace)) {
    actions.push([
      { label: 'Settings', icon: 'i-lucide-settings', onSelect: () => navigateTo(`/workspace/settings?workspaceId=${ws.id}`) }
    ])
  }

  if (!ws.isPersonal && allow(ws.capabilities?.canAdminWorkspace)) {
    actions.push([
      { label: 'Delete', icon: 'i-lucide-trash', color: 'error', onSelect: () => openDeleteSlideover(ws) }
    ])
  } else if (!ws.isPersonal && ws.role !== 'OWNER') {
    actions.push([
      { label: 'Leave', icon: 'i-lucide-log-out', color: 'warning', onSelect: () => leaveWorkspace(ws) }
    ])
  }

  return compactGroups(actions)
}

const contextMenuWorkspace = ref<WorkspaceRow | null>(null)
const contextMenuItems = computed(() => {
  if (!contextMenuWorkspace.value) return []
  return getActions(contextMenuWorkspace.value)
})

function handleRowContextMenu(_event: Event, row: TableRow<WorkspaceRow>) {
  contextMenuWorkspace.value = row.original
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
  } catch (error: unknown) {
    toast.add({ title: 'Failed to delete', description: extractApiErrorMessage(error, 'An error occurred'), color: 'error' })
  }
}

async function handleDeleteSelected() {
  if (!canDeleteSelected.value) return

  const count = selectedWorkspaces.value.length
  const instance = deleteSlideover.open({
    name: `${count} workspace${count === 1 ? '' : 's'}`,
    entityType: 'Workspace',
    items: selectedWorkspaces.value.map(workspace => ({ id: workspace.id, label: workspace.name })),
    warningMessage: 'This action cannot be undone. This will permanently delete the selected workspaces, all projects, and remove all member associations.'
  })
  const confirmed = await instance.result
  if (!confirmed) return

  try {
    const response = await $fetch<{ successCount: number, failedCount: number }>(
      '/api/workspaces/bulk',
      {
        method: 'DELETE',
        body: { ids: selectedWorkspaces.value.map(workspace => workspace.id) }
      }
    )

    if (response.successCount > 0) {
      toast.add({ title: response.successCount === 1 ? 'Workspace deleted' : 'Workspaces deleted', description: `${response.successCount} item${response.successCount === 1 ? '' : 's'} removed.`, color: 'success' })
    }
    if (response.failedCount > 0) {
      toast.add({ title: 'Some deletions failed', description: `${response.failedCount} item${response.failedCount === 1 ? '' : 's'} could not be deleted.`, color: 'warning' })
    }

    clearSelection()
    await refreshWorkspaceList()
  } catch (error: unknown) {
    toast.add({ title: 'Failed to delete', description: extractApiErrorMessage(error, 'Failed to delete workspaces'), color: 'error' })
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
            v-if="canCreateTeamWorkspace"
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
        <template #right>
          <AppTableColumnsDropdown table-id="workspaces-list" :columns="columns" />
        </template>
      </UDashboardToolbar>
    </template>

    <template #body>
      <div v-if="workspaces?.length">
        <UContextMenu :items="contextMenuItems as any">
          <AppTable
            table-id="workspaces-list"
            :data="paginatedData"
            :columns="columns"
            class="flex-1"
            @contextmenu="handleRowContextMenu"
          />
        </UContextMenu>

        <UiFloatingSelectionMenu
          :selected-count="selectedWorkspaceIds.size"
          @clear="clearSelection"
        >
          <UButton
            icon="i-lucide-trash"
            color="error"
            variant="ghost"
            size="sm"
            class="hover:bg-white/10"
            :disabled="!canDeleteSelected"
            @click="handleDeleteSelected"
          >
            Delete
          </UButton>
        </UiFloatingSelectionMenu>

        <div v-if="totalItems > 0" class="flex justify-between items-center p-4 border-t border-neutral-200 dark:border-neutral-800">
          <span class="text-sm text-neutral-600 dark:text-neutral-400">
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
              :disabled="totalPages <= 1"
              show-edges
              :sibling-count="1"
            />
          </div>
        </div>
      </div>

      <div v-else class="text-center py-12">
        <UIcon name="i-lucide-folder" class="w-12 h-12 text-neutral-400 mx-auto mb-4" />
        <h3 class="text-lg font-semibold mb-2">
          No workspaces found
        </h3>
        <p class="text-neutral-500 mb-6">
          Create your first workspace to get started
        </p>
        <UButton
          v-if="canCreateTeamWorkspace"
          icon="i-lucide-plus"
          label="Create Workspace"
          @click="createWorkspaceSlideover.open()"
        />
      </div>
    </template>
  </UDashboardPanel>
</template>
