<script setup lang="ts">
import type { TableColumn, TableRow } from '@nuxt/ui'
import type { DictionarySummary } from '@/types/dictionary'
import { LazyUiDeleteSlideover, NuxtLink, UBadge, UButton, UDropdownMenu, UPopover } from '#components'

const toast = useToast()
const overlay = useOverlay()
const deleteSlideover = overlay.create(LazyUiDeleteSlideover)
const { allow, compactGroups } = useActionVisibility()
const deletingDictionaryIds = ref<Set<string>>(new Set())

const { selectedWorkspace } = await useWorkspaceBootstrap()
const { capabilities: workspaceCapabilities } = useWorkspaceCapabilities(selectedWorkspace)
const canManageToolkit = computed(() => allow(workspaceCapabilities.value.canManageToolkit))
const dictionariesKey = computed(() =>
  selectedWorkspace.value
    ? wsKey(selectedWorkspace.value, 'dictionaries', 'list')
    : 'pending:dictionaries:list'
)

const { data: dictionaries } = await useFetch<DictionarySummary[]>(() => `/api/workspaces/${selectedWorkspace.value}/dictionaries`, {
  key: dictionariesKey,
  default: () => []
})

const dictionariesSafe = computed(() => dictionaries.value ?? [])

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
  data: dictionariesSafe,
  defaultSort: { column: 'name', direction: 'asc' },
  tableId: 'workspace-dictionaries'
})

const selectedDictionaryIds = ref<Set<string>>(new Set())
const selectedDictionaries = computed(() => dictionariesSafe.value.filter(dictionary => selectedDictionaryIds.value.has(dictionary.id)))
const canDeleteSelected = computed(() =>
  selectedDictionaries.value.length > 0
  && selectedDictionaries.value.every(dictionary => allow(dictionary.capabilities?.canDelete))
  && !selectedDictionaries.value.some(dictionary => deletingDictionaryIds.value.has(dictionary.id))
)

const allPageSelected = computed(() =>
  paginatedData.value.length > 0
  && paginatedData.value.every(dictionary => selectedDictionaryIds.value.has(dictionary.id))
)

const somePageSelected = computed(() =>
  paginatedData.value.some(dictionary => selectedDictionaryIds.value.has(dictionary.id))
  && !allPageSelected.value
)

function toggleDictionarySelection(dictionaryId: string) {
  const next = new Set(selectedDictionaryIds.value)
  if (next.has(dictionaryId)) next.delete(dictionaryId)
  else next.add(dictionaryId)
  selectedDictionaryIds.value = next
}

function toggleCurrentPageSelection() {
  const next = new Set(selectedDictionaryIds.value)
  if (allPageSelected.value) {
    paginatedData.value.forEach(dictionary => next.delete(dictionary.id))
  } else {
    paginatedData.value.forEach(dictionary => next.add(dictionary.id))
  }
  selectedDictionaryIds.value = next
}

function clearSelection() {
  selectedDictionaryIds.value = new Set()
}

watch(dictionariesSafe, (nextDictionaries) => {
  const validIds = new Set(nextDictionaries.map(dictionary => dictionary.id))
  selectedDictionaryIds.value = new Set(Array.from(selectedDictionaryIds.value).filter(id => validIds.has(id)))
}, { immediate: true })

const columns: TableColumn<DictionarySummary>[] = [
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
      checked: selectedDictionaryIds.value.has(row.original.id),
      onChange: () => toggleDictionarySelection(row.original.id),
      onClick: (event: Event) => event.stopPropagation(),
      class: 'rounded-sm border-neutral-300 text-primary-600 focus:ring-primary-500'
    })
  },
  {
    accessorKey: 'name',
    header: createSortableHeader('Name', 'name', sort, UButton),
    cell: ({ row }) => h(NuxtLink, { to: `/dictionaries/${row.original.id}`, class: 'font-medium hover:underline text-primary' }, () => row.getValue('name'))
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
    accessorKey: 'entryCount',
    header: createSortableHeader('Forms', 'entryCount', sort, UButton, { align: 'end' }),
    cell: ({ row }) => h('div', { class: 'text-right tabular-nums' }, String(row.getValue('entryCount') ?? 0))
  },
  {
    id: 'actions',
    cell: ({ row }) => renderDropdownActionsCell(items(row.original), { UButton, UDropdownMenu })
  }
]

const handleDelete = async (row: DictionarySummary) => {
  if (!allow(row.capabilities?.canDelete)) return
  if (deletingDictionaryIds.value.has(row.id)) return
  const instance = deleteSlideover.open({
    name: row.name,
    entityType: 'Dictionary',
    warningMessage: 'This action cannot be undone. Projects using this dictionary will lose the dictionary reference.'
  })
  const confirmed = await instance.result
  if (!confirmed) return

  const progressToast = toast.add({
    title: 'Deleting dictionary',
    description: row.name,
    color: 'neutral',
    icon: 'i-lucide-loader-circle',
    ui: { icon: 'animate-spin' },
    close: false,
    progress: false,
    duration: 0
  })

  deletingDictionaryIds.value = new Set(deletingDictionaryIds.value).add(row.id)

  try {
    await $fetch(`/api/workspaces/${selectedWorkspace.value}/dictionaries/${row.id}`, { method: 'DELETE' })
    toast.add({ title: 'Dictionary deleted', color: 'success' })
    await refreshNuxtData(dictionariesKey.value)
  } catch (error: unknown) {
    toast.add({ title: 'Error deleting dictionary', description: extractApiErrorMessage(error, 'Failed to delete dictionary'), color: 'error' })
  } finally {
    toast.remove(progressToast.id)
    const next = new Set(deletingDictionaryIds.value)
    next.delete(row.id)
    deletingDictionaryIds.value = next
  }
}

async function handleDeleteSelected() {
  if (!selectedWorkspace.value || !canDeleteSelected.value) return

  const count = selectedDictionaries.value.length
  const instance = deleteSlideover.open({
    name: `${count} dictionar${count === 1 ? 'y' : 'ies'}`,
    entityType: 'Dictionary',
    items: selectedDictionaries.value.map(dictionary => ({ id: dictionary.id, label: dictionary.name })),
    warningMessage: 'This action cannot be undone. Projects using these dictionaries will lose the dictionary reference.'
  })
  const confirmed = await instance.result
  if (!confirmed) return

  const ids = selectedDictionaries.value.map(dictionary => dictionary.id)
  deletingDictionaryIds.value = new Set([...deletingDictionaryIds.value, ...ids])

  try {
    const response = await $fetch<{ successCount: number, failedCount: number }>(
      `/api/workspaces/${selectedWorkspace.value}/dictionaries/bulk`,
      {
        method: 'DELETE',
        body: { ids }
      }
    )

    if (response.successCount > 0) {
      toast.add({
        title: response.successCount === 1 ? 'Dictionary deleted' : 'Dictionaries deleted',
        description: `${response.successCount} item${response.successCount === 1 ? '' : 's'} removed.`,
        color: 'success'
      })
    }

    if (response.failedCount > 0) {
      toast.add({
        title: 'Some deletions failed',
        description: `${response.failedCount} item${response.failedCount === 1 ? '' : 's'} could not be deleted.`,
        color: 'warning'
      })
    }

    clearSelection()
    await refreshNuxtData(dictionariesKey.value)
  } catch (error: unknown) {
    toast.add({
      title: 'Error deleting dictionaries',
      description: extractApiErrorMessage(error, 'Failed to delete dictionaries'),
      color: 'error'
    })
  } finally {
    deletingDictionaryIds.value = new Set()
  }
}

const items = (row: DictionarySummary) => {
  const actions: Array<Record<string, unknown>> = []

  if (allow(row.capabilities?.canEdit)) {
    actions.push({
      label: 'Edit',
      icon: 'i-lucide-edit',
      disabled: deletingDictionaryIds.value.has(row.id),
      onSelect: () => navigateTo(`/dictionaries/${row.id}`)
    })
  }

  if (allow(row.capabilities?.canDelete)) {
    actions.push({
      label: deletingDictionaryIds.value.has(row.id) ? 'Deleting...' : 'Delete',
      icon: deletingDictionaryIds.value.has(row.id) ? 'i-lucide-loader-circle' : 'i-lucide-trash',
      color: 'error',
      disabled: deletingDictionaryIds.value.has(row.id),
      onSelect: () => handleDelete(row)
    })
  }

  return compactGroups([actions])
}

const contextMenuDictionary = ref<DictionarySummary | null>(null)
const contextMenuItems = computed(() => contextMenuDictionary.value ? items(contextMenuDictionary.value) : [])

function handleRowContextMenu(_event: Event, row: TableRow<DictionarySummary>) {
  contextMenuDictionary.value = row.original
}

const emptyStateActions = computed(() => {
  const actions: Array<Record<string, unknown>> = [
    {
      icon: 'i-lucide-refresh-cw',
      label: 'Refresh',
      color: 'neutral',
      variant: 'subtle',
      onClick: () => refreshNuxtData(dictionariesKey.value)
    }
  ]

  if (canManageToolkit.value) {
    actions.unshift({
      icon: 'i-lucide-plus',
      label: 'Create new',
      variant: 'solid',
      to: '/dictionaries/new'
    })
  }

  return actions
})
</script>

<template>
  <UDashboardPanel id="dictionaries">
    <template #header>
      <UDashboardNavbar title="Dictionaries">
        <template #right>
          <UButton
            v-if="canManageToolkit"
            label="New Dictionary"
            color="neutral"
            variant="outline"
            icon="i-lucide-plus"
            to="/dictionaries/new"
          />
        </template>
      </UDashboardNavbar>

      <UDashboardToolbar>
        <template #left>
          <UInput
            v-model="globalFilter"
            placeholder="Search dictionaries..."
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
          <AppTableClearFiltersButton
            :active="activeFilters.length > 0"
            @clear="resetAllFilters"
          />
        </template>
        <template #right>
          <AppTableColumnsDropdown table-id="workspace-dictionaries" :columns="columns" />
        </template>
      </UDashboardToolbar>
    </template>

    <template #body>
      <UEmpty
        v-if="dictionaries && dictionaries.length === 0"
        variant="naked"
        icon="i-lucide-book-copy"
        title="No dictionaries found"
        description="Controlled dictionaries let you validate GT text and suggest accepted surface forms."
        :actions="emptyStateActions"
      />
      <div v-else-if="dictionaries">
        <UContextMenu :items="contextMenuItems as any">
          <AppTable
            table-id="workspace-dictionaries"
            :data="paginatedData"
            :columns="columns"
            class="flex-1"
            @contextmenu="handleRowContextMenu"
          />
        </UContextMenu>

        <UiFloatingSelectionMenu
          :selected-count="selectedDictionaryIds.size"
          @clear="clearSelection"
        >
          <UButton
            icon="i-lucide-trash"
            color="error"
            variant="ghost"
            size="sm"
            class="hover:bg-error/20"
            :disabled="!canDeleteSelected"
            @click="handleDeleteSelected"
          >
            Delete
          </UButton>
        </UiFloatingSelectionMenu>

        <div v-if="totalItems > 0" class="flex justify-between items-center p-4 border-t border-neutral-200 dark:border-neutral-800">
          <div class="flex items-center gap-2 text-sm text-neutral-600 dark:text-neutral-400">
            <span>Showing {{ (page - 1) * itemsPerPage + 1 }} to {{ Math.min(page * itemsPerPage, totalItems) }} of {{ totalItems }} dictionaries</span>
          </div>

          <div class="flex items-center gap-4">
            <USelect
              v-model="itemsPerPage"
              :items="[5, 10, 15, 20, 50, 100]"
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
    </template>
  </UDashboardPanel>
</template>
