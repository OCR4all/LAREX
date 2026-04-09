<script setup lang="ts">
import { h } from 'vue'
import type { TableColumn } from '@nuxt/ui'
import type { DictionarySummary } from '@/types/dictionary'
import { wsKey } from '@/utils/fetch-keys'
import { extractApiErrorMessage } from '@/utils/api-error'
import { LazyUiDeleteSlideover, NuxtLink, UBadge, UButton, UDropdownMenu, UPopover } from '#components'
import { useWorkspaceBootstrap } from '@/composables/use-workspace-bootstrap'
import { useResourceListPage } from '@/composables/use-resource-list-page'
import { createSortableHeader, renderDropdownActionsCell, renderSimpleTagCell, renderTruncatedText } from '@/utils/resource-list-columns'

const toast = useToast()
const overlay = useOverlay()
const deleteSlideover = overlay.create(LazyUiDeleteSlideover)
const { allow, compactGroups } = useActionVisibility()
const deletingDictionaryIds = ref<Set<string>>(new Set())

const { selectedWorkspace } = await useWorkspaceBootstrap()
const { capabilities: workspaceCapabilities } = useWorkspaceCapabilities(selectedWorkspace)
const canManageUtilities = computed(() => allow(workspaceCapabilities.value.canManageUtilities))
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
  defaultSort: { column: 'name', direction: 'asc' }
})

const columns: TableColumn<DictionarySummary>[] = [
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

const items = (row: DictionarySummary) => compactGroups([[
  allow(row.capabilities?.canEdit) ? {
    label: 'Edit',
    icon: 'i-lucide-edit',
    disabled: deletingDictionaryIds.value.has(row.id),
    onSelect: () => navigateTo(`/dictionaries/${row.id}`)
  } : null,
  allow(row.capabilities?.canDelete) ? {
    label: deletingDictionaryIds.value.has(row.id) ? 'Deleting...' : 'Delete',
    icon: deletingDictionaryIds.value.has(row.id) ? 'i-lucide-loader-circle' : 'i-lucide-trash',
    color: 'error',
    disabled: deletingDictionaryIds.value.has(row.id),
    onSelect: () => handleDelete(row)
  } : null
].filter(Boolean) as any[]])

const contextMenuDictionary = ref<DictionarySummary | null>(null)
const contextMenuItems = computed(() => contextMenuDictionary.value ? items(contextMenuDictionary.value) : [])

function handleRowContextMenu(_event: Event, row: any) {
  contextMenuDictionary.value = row.original as DictionarySummary
}

const emptyStateActions = computed(() => {
  const actions: Array<Record<string, any>> = [
    {
      icon: 'i-lucide-refresh-cw',
      label: 'Refresh',
      color: 'neutral',
      variant: 'subtle',
      onClick: () => refreshNuxtData(dictionariesKey.value)
    }
  ]

  if (canManageUtilities.value) {
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
        <template #leading>
          <LazyUDashboardSidebarCollapse />
        </template>
        <template #right>
          <UButton
            v-if="canManageUtilities"
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
          <UButton v-if="activeFilters.length > 0" color="neutral" variant="ghost" size="sm" @click="resetAllFilters()">
            Clear Filters
          </UButton>
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
            @contextmenu="handleRowContextMenu"
          />
        </UContextMenu>

        <div v-if="totalPages > 1" class="flex justify-between items-center p-4 border-t border-neutral-200 dark:border-neutral-800">
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
              show-edges
              :sibling-count="1"
            />
          </div>
        </div>
      </div>
    </template>
  </UDashboardPanel>
</template>
