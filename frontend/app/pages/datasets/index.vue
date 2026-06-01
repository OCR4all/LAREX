<script setup lang="ts">
import type { TableColumn } from '@nuxt/ui'
import { LazyDatasetSlideoverCreate, LazyUiDeleteSlideover, NuxtLink, UBadge, UButton, UDropdownMenu, UPopover } from '#components'
import type { DatasetSummary } from '@/types/dataset'

const toast = useToast()
const overlay = useOverlay()
const deleteSlideover = overlay.create(LazyUiDeleteSlideover)
const createSlideover = overlay.create(LazyDatasetSlideoverCreate)
const { allow, compactGroups } = useActionVisibility()

const { selectedWorkspace } = await useWorkspaceBootstrap()
const { capabilities: workspaceCapabilities } = useWorkspaceCapabilities(selectedWorkspace)
const canManageDatasets = computed(() => allow(workspaceCapabilities.value.canManageProjects))
const datasetsKey = computed(() =>
  selectedWorkspace.value
    ? wsKey(selectedWorkspace.value, 'datasets', 'list')
    : 'pending:datasets:list'
)

const { data: datasets } = await useFetch<DatasetSummary[]>(() => `/api/workspaces/${selectedWorkspace.value}/datasets`, {
  key: datasetsKey,
  default: () => []
})

const datasetsSafe = computed(() => datasets.value ?? [])
const {
  sort,
  globalFilter,
  tagFilterOperator,
  activeFilters,
  resetAllFilters,
  uniqueTags,
  selectedTags,
  tagOperatorOptions,
  page,
  itemsPerPage,
  totalItems,
  totalPages,
  paginatedData
} = useResourceListPage({
  data: datasetsSafe,
  defaultSort: { column: 'updated', direction: 'desc' }
})

const selectedDatasetIds = ref<Set<string>>(new Set())
const selectedDatasets = computed(() => datasetsSafe.value.filter(dataset => selectedDatasetIds.value.has(dataset.id)))
const canDeleteSelected = computed(() =>
  selectedDatasets.value.length > 0
  && selectedDatasets.value.every(dataset => allow(dataset.capabilities?.canDelete))
)
const allPageSelected = computed(() =>
  paginatedData.value.length > 0
  && paginatedData.value.every(dataset => selectedDatasetIds.value.has(dataset.id))
)
const somePageSelected = computed(() =>
  paginatedData.value.some(dataset => selectedDatasetIds.value.has(dataset.id))
  && !allPageSelected.value
)

function toggleDatasetSelection(datasetId: string) {
  const next = new Set(selectedDatasetIds.value)
  if (next.has(datasetId)) next.delete(datasetId)
  else next.add(datasetId)
  selectedDatasetIds.value = next
}

function toggleCurrentPageSelection() {
  const next = new Set(selectedDatasetIds.value)
  if (allPageSelected.value) {
    paginatedData.value.forEach(dataset => next.delete(dataset.id))
  } else {
    paginatedData.value.forEach(dataset => next.add(dataset.id))
  }
  selectedDatasetIds.value = next
}

function clearSelection() {
  selectedDatasetIds.value = new Set()
}

watch(datasetsSafe, (nextDatasets) => {
  const validIds = new Set(nextDatasets.map(dataset => dataset.id))
  selectedDatasetIds.value = new Set(Array.from(selectedDatasetIds.value).filter(id => validIds.has(id)))
}, { immediate: true })

const columns: TableColumn<DatasetSummary>[] = [
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
      checked: selectedDatasetIds.value.has(row.original.id),
      onChange: () => toggleDatasetSelection(row.original.id),
      onClick: (event: Event) => event.stopPropagation(),
      class: 'rounded-sm border-neutral-300 text-primary-600 focus:ring-primary-500'
    })
  },
  {
    accessorKey: 'name',
    header: createSortableHeader('Name', 'name', sort, UButton),
    cell: ({ row }) => h(NuxtLink, { to: `/datasets/${row.original.id}`, class: 'font-medium hover:underline text-primary' }, () => row.getValue('name'))
  },
  {
    accessorKey: 'description',
    header: createSortableHeader('Description', 'description', sort, UButton),
    cell: ({ row }) => renderTruncatedText(row.getValue('description') as string)
  },
  {
    accessorKey: 'tags',
    header: 'Tags',
    cell: ({ row }) => renderSimpleTagCell(row.getValue('tags') as string[] | undefined, { UBadge, UButton, UPopover })
  },
  {
    accessorKey: 'itemCount',
    header: createSortableHeader('Items', 'itemCount', sort, UButton, { align: 'end' }),
    cell: ({ row }) => h('div', { class: 'text-right tabular-nums' }, String(row.original.itemCount))
  },
  {
    accessorKey: 'brokenItems',
    header: createSortableHeader('Broken', 'brokenItems', sort, UButton, { align: 'end' }),
    cell: ({ row }) => h('div', { class: 'text-right tabular-nums' }, String(row.original.stats?.brokenItems ?? 0))
  },
  {
    accessorKey: 'lastValidationStatus',
    header: createSortableHeader('Validation', 'lastValidationStatus', sort, UButton),
    cell: ({ row }) => h(UBadge, {
      color: row.original.lastValidationStatus === 'INVALID' ? 'error' : row.original.lastValidationStatus === 'VALID' ? 'success' : 'neutral',
      variant: 'soft'
    }, () => row.original.lastValidationStatus.replaceAll('_', ' '))
  },
  {
    id: 'actions',
    cell: ({ row }) => renderDropdownActionsCell(items(row.original), { UButton, UDropdownMenu })
  }
]

async function openCreateDataset() {
  const instance = createSlideover.open()
  const createdId = await instance.result as string | null
  if (!createdId) return

  await refreshNuxtData(datasetsKey.value)
  await navigateTo(`/datasets/${createdId}`)
}

async function handleDelete(row: DatasetSummary) {
  if (!selectedWorkspace.value || !allow(row.capabilities?.canDelete)) return
  const instance = deleteSlideover.open({
    name: row.name,
    entityType: 'Dataset',
    warningMessage: 'This action cannot be undone. Frozen dataset copies will be deleted from storage.'
  })
  const confirmed = await instance.result
  if (!confirmed) return

  try {
    await $fetch(`/api/workspaces/${selectedWorkspace.value}/datasets/${row.id}`, { method: 'DELETE' })
    toast.add({ title: 'Dataset deleted', color: 'success' })
    await refreshNuxtData(datasetsKey.value)
  } catch (error: unknown) {
    toast.add({ title: 'Delete failed', description: extractApiErrorMessage(error, 'Failed to delete dataset'), color: 'error' })
  }
}

async function handleDeleteSelected() {
  if (!selectedWorkspace.value || !canDeleteSelected.value) return

  const count = selectedDatasets.value.length
  const instance = deleteSlideover.open({
    name: `${count} dataset${count === 1 ? '' : 's'}`,
    entityType: 'Dataset',
    items: selectedDatasets.value.map(dataset => ({ id: dataset.id, label: dataset.name })),
    warningMessage: 'This action cannot be undone. Frozen dataset copies will be deleted from storage.'
  })
  const confirmed = await instance.result
  if (!confirmed) return

  try {
    const response = await $fetch<{ successCount: number, failedCount: number }>(
      `/api/workspaces/${selectedWorkspace.value}/datasets/bulk`,
      {
        method: 'DELETE',
        body: { ids: selectedDatasets.value.map(dataset => dataset.id) }
      }
    )

    if (response.successCount > 0) {
      toast.add({ title: response.successCount === 1 ? 'Dataset deleted' : 'Datasets deleted', description: `${response.successCount} item${response.successCount === 1 ? '' : 's'} removed.`, color: 'success' })
    }
    if (response.failedCount > 0) {
      toast.add({ title: 'Some deletions failed', description: `${response.failedCount} item${response.failedCount === 1 ? '' : 's'} could not be deleted.`, color: 'warning' })
    }

    clearSelection()
    await refreshNuxtData(datasetsKey.value)
  } catch (error: unknown) {
    toast.add({ title: 'Delete failed', description: extractApiErrorMessage(error, 'Failed to delete datasets'), color: 'error' })
  }
}

const items = (row: DatasetSummary) => compactGroups([[
  {
    label: 'Open',
    icon: 'i-lucide-arrow-right',
    onSelect: () => navigateTo(`/datasets/${row.id}`)
  },
  allow(row.capabilities?.canDelete)
    ? {
        label: 'Delete',
        icon: 'i-lucide-trash',
        color: 'error',
        onSelect: () => handleDelete(row)
      }
    : null
].filter(Boolean) as Array<Record<string, unknown>>])

const emptyStateActions = computed(() => {
  const actions: Array<Record<string, unknown>> = [
    {
      icon: 'i-lucide-refresh-cw',
      label: 'Refresh',
      color: 'neutral',
      variant: 'subtle',
      onClick: () => refreshNuxtData(datasetsKey.value)
    }
  ]

  if (canManageDatasets.value) {
    actions.unshift({
      icon: 'i-lucide-plus',
      label: 'Create new',
      variant: 'solid',
      onClick: () => { void openCreateDataset() }
    })
  }

  return actions
})
</script>

<template>
  <UDashboardPanel id="datasets">
    <template #header>
      <UDashboardNavbar title="Datasets">
        <template #leading>
          <LazyUDashboardSidebarCollapse />
        </template>
        <template #right>
          <UButton
            v-if="canManageDatasets"
            label="New Dataset"
            color="neutral"
            variant="outline"
            icon="i-lucide-plus"
            @click="openCreateDataset"
          />
        </template>
      </UDashboardNavbar>

      <UDashboardToolbar>
        <template #left>
          <UInput
            v-model="globalFilter"
            placeholder="Search datasets..."
            icon="i-lucide-search"
            class="w-64"
          />
          <USelectMenu
            v-model="selectedTags"
            :items="uniqueTags"
            value-key="value"
            placeholder="Filter by tag"
            multiple
            searchable
            clear-search-on-close
            class="w-48"
          />
          <USelectMenu
            v-if="selectedTags.length > 1"
            v-model="tagFilterOperator"
            :items="tagOperatorOptions"
            value-key="value"
            class="w-36"
          />
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
          <AppTableColumnsDropdown table-id="workspace-datasets" :columns="columns" />
        </template>
      </UDashboardToolbar>
    </template>

    <template #body>
      <UEmpty
        v-if="datasets && datasets.length === 0"
        variant="naked"
        icon="i-lucide-database-zap"
        title="No datasets found"
        description="Workspace datasets collect curated page annotations and image variants for training and evaluation."
        :actions="emptyStateActions"
      />
      <div v-else-if="datasets">
        <AppTable
          table-id="workspace-datasets"
          :data="paginatedData"
          :columns="columns"
          class="flex-1"
        />

        <UiFloatingSelectionMenu
          :selected-count="selectedDatasetIds.size"
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

        <div v-if="totalPages > 1" class="flex justify-between items-center p-4 border-t border-neutral-200 dark:border-neutral-800">
          <div class="flex items-center gap-2 text-sm text-neutral-600 dark:text-neutral-400">
            <span>Showing {{ (page - 1) * itemsPerPage + 1 }} to {{ Math.min(page * itemsPerPage, totalItems) }} of {{ totalItems }} datasets</span>
          </div>
          <div class="flex items-center gap-4">
            <USelect
              v-model="itemsPerPage"
              :items="[5, 10, 15, 20, 50, 100]"
              class="w-32"
              size="sm"
            />
            <UPagination v-model:page="page" :total="totalItems" :items-per-page="itemsPerPage" />
          </div>
        </div>
      </div>
    </template>
  </UDashboardPanel>
</template>
