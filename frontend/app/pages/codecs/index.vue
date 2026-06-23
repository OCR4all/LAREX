<script setup lang="ts">
import type { DropdownMenuItem, TableColumn } from '@nuxt/ui'
import type { Row } from '@tanstack/vue-table'
import type { CodecSummary } from '@/types/codec'
import { LazyUiDeleteSlideover, NuxtLink, UBadge, UButton, UDropdownMenu, UPopover } from '#components'

const toast = useToast()
const overlay = useOverlay()
const deleteSlideover = overlay.create(LazyUiDeleteSlideover)
const { allow, compactGroups } = useActionVisibility()

const { selectedWorkspace } = await useWorkspaceBootstrap()
const workspaceId = computed(() => selectedWorkspace.value ?? '')
const { capabilities: workspaceCapabilities } = useWorkspaceCapabilities(selectedWorkspace)
const canManageToolkit = computed(() => allow(workspaceCapabilities.value.canManageToolkit))
const codecsKey = computed(() => wsKey(workspaceId.value, 'codecs', 'list'))

const { data: codecs } = await useFetch<CodecSummary[]>(() => `/api/workspaces/${workspaceId.value}/codecs`, {
  key: codecsKey,
  default: () => []
})

const codecsSafe = computed(() => codecs.value ?? [])

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
  data: codecsSafe,
  defaultSort: { column: 'name', direction: 'asc' }
})

const selectedCodecIds = ref<Set<string>>(new Set())
const selectedCodecs = computed(() => codecsSafe.value.filter(codec => selectedCodecIds.value.has(codec.id)))
const canDeleteSelected = computed(() =>
  selectedCodecs.value.length > 0
  && selectedCodecs.value.every(codec => allow(codec.capabilities?.canDelete))
)
const allPageSelected = computed(() =>
  paginatedData.value.length > 0
  && paginatedData.value.every(codec => selectedCodecIds.value.has(codec.id))
)
const somePageSelected = computed(() =>
  paginatedData.value.some(codec => selectedCodecIds.value.has(codec.id))
  && !allPageSelected.value
)

function toggleCodecSelection(codecId: string) {
  const next = new Set(selectedCodecIds.value)
  if (next.has(codecId)) next.delete(codecId)
  else next.add(codecId)
  selectedCodecIds.value = next
}

function toggleCurrentPageSelection() {
  const next = new Set(selectedCodecIds.value)
  if (allPageSelected.value) {
    paginatedData.value.forEach(codec => next.delete(codec.id))
  } else {
    paginatedData.value.forEach(codec => next.add(codec.id))
  }
  selectedCodecIds.value = next
}

function clearSelection() {
  selectedCodecIds.value = new Set()
}

watch(codecsSafe, (nextCodecs) => {
  const validIds = new Set(nextCodecs.map(codec => codec.id))
  selectedCodecIds.value = new Set(Array.from(selectedCodecIds.value).filter(id => validIds.has(id)))
}, { immediate: true })

const columns: TableColumn<CodecSummary>[] = [
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
      checked: selectedCodecIds.value.has(row.original.id),
      onChange: () => toggleCodecSelection(row.original.id),
      onClick: (event: Event) => event.stopPropagation(),
      class: 'rounded-sm border-neutral-300 text-primary-600 focus:ring-primary-500'
    })
  },
  {
    accessorKey: 'name',
    header: createSortableHeader('Name', 'name', sort, UButton),
    cell: ({ row }) => h(NuxtLink, { to: `/codecs/${row.original.id}`, class: 'font-medium hover:underline text-primary' }, () => row.getValue('name'))
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
    accessorKey: 'characterCount',
    header: createSortableHeader('Chars', 'characterCount', sort, UButton, { align: 'end' }),
    cell: ({ row }) => h('div', { class: 'text-right tabular-nums' }, String(row.getValue('characterCount') ?? 0))
  },
  {
    id: 'actions',
    cell: ({ row }) => {
      const rowItems = items(row.original)
      return renderDropdownActionsCell(rowItems, { UButton, UDropdownMenu })
    }
  }
]

const handleDelete = async (row: CodecSummary) => {
  if (!allow(row.capabilities?.canDelete)) return
  const instance = deleteSlideover.open({
    name: row.name,
    entityType: 'Codec',
    warningMessage: 'This action cannot be undone! All projects using this codec will lose their codec reference.'
  })
  const confirmed = await instance.result
  if (!confirmed) return

  try {
    await $fetch(`/api/workspaces/${workspaceId.value}/codecs/${row.id}`, { method: 'DELETE' })
    toast.add({ title: 'Codec deleted', color: 'success' })
    await refreshNuxtData(codecsKey.value)
  } catch {
    toast.add({ title: 'Error deleting codec', color: 'error' })
  }
}

async function handleDeleteSelected() {
  if (!canDeleteSelected.value) return

  const count = selectedCodecs.value.length
  const instance = deleteSlideover.open({
    name: `${count} codec${count === 1 ? '' : 's'}`,
    entityType: 'Codec',
    items: selectedCodecs.value.map(codec => ({ id: codec.id, label: codec.name })),
    warningMessage: 'This action cannot be undone. All projects using these codecs will lose their codec reference.'
  })
  const confirmed = await instance.result
  if (!confirmed) return

  try {
    const response = await $fetch<{ successCount: number, failedCount: number }>(
      `/api/workspaces/${workspaceId.value}/codecs/bulk`,
      {
        method: 'DELETE',
        body: { ids: selectedCodecs.value.map(codec => codec.id) }
      }
    )

    if (response.successCount > 0) {
      toast.add({ title: response.successCount === 1 ? 'Codec deleted' : 'Codecs deleted', description: `${response.successCount} item${response.successCount === 1 ? '' : 's'} removed.`, color: 'success' })
    }
    if (response.failedCount > 0) {
      toast.add({ title: 'Some deletions failed', description: `${response.failedCount} item${response.failedCount === 1 ? '' : 's'} could not be deleted.`, color: 'warning' })
    }

    clearSelection()
    await refreshNuxtData(codecsKey.value)
  } catch (error: unknown) {
    toast.add({ title: 'Error deleting codecs', description: extractApiErrorMessage(error, 'Failed to delete codecs'), color: 'error' })
  }
}

const items = (row: CodecSummary): DropdownMenuItem[][] => {
  const actions: DropdownMenuItem[] = []

  if (allow(row.capabilities?.canEdit)) {
    actions.push({
      label: 'Edit',
      icon: 'i-lucide-edit',
      onSelect: () => navigateTo(`/codecs/${row.id}`)
    })
  }

  if (allow(row.capabilities?.canDelete)) {
    actions.push({
      label: 'Delete',
      icon: 'i-lucide-trash',
      color: 'error',
      onSelect: () => handleDelete(row)
    })
  }

  return compactGroups([actions])
}

const contextMenuCodec = ref<CodecSummary | null>(null)
const contextMenuItems = computed<DropdownMenuItem[][]>(() => {
  if (!contextMenuCodec.value) return []
  return items(contextMenuCodec.value)
})

function handleRowContextMenu(_event: Event, row: Row<CodecSummary>) {
  contextMenuCodec.value = row.original
}

const emptyStateActions = computed(() => {
  const actions: Array<Record<string, unknown>> = [
    {
      icon: 'i-lucide-refresh-cw',
      label: 'Refresh',
      color: 'neutral',
      variant: 'subtle',
      onClick: () => refreshNuxtData(codecsKey.value)
    }
  ]

  if (canManageToolkit.value) {
    actions.unshift({
      icon: 'i-lucide-plus',
      label: 'Create new',
      variant: 'solid',
      to: '/codecs/new'
    })
  }

  return actions
})
</script>

<template>
  <UDashboardPanel id="codecs" data-tour="codecs-panel">
    <template #header>
      <UDashboardNavbar title="Codecs">
        <template #right>
          <UButton
            v-if="canManageToolkit"
            data-tour="codecs-new"
            label="New Codec"
            color="neutral"
            variant="outline"
            icon="i-lucide-plus"
            to="/codecs/new"
          />
        </template>
      </UDashboardNavbar>

      <UDashboardToolbar>
        <template #left>
          <UInput
            v-model="globalFilter"
            data-tour="codecs-search"
            placeholder="Search codecs..."
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

          <USelectMenu
            v-model="selectedTags"
            :items="uniqueTags"
            value-key="value"
            placeholder="Filter by tag"
            searchable
            multiple
            clear-search-on-close
            searchable-placeholder="Search tags..."
            class="w-48"
          >
            <template #item="{ item }">
              <div class="flex items-center justify-between w-full gap-2">
                <div class="flex items-center gap-2">
                  <UIcon
                    v-if="selectedTags.includes(item.value)"
                    name="i-lucide-check"
                    class="text-primary w-4 h-4"
                  />
                  <span v-else class="w-4 h-4" />
                  <span>{{ item.label }}</span>
                </div>
                <UBadge variant="solid" color="neutral" size="xs">
                  {{ item.count }}
                </UBadge>
              </div>
            </template>
          </USelectMenu>

          <USelectMenu
            v-if="selectedTags.length > 1"
            v-model="tagFilterOperator"
            :items="tagOperatorOptions"
            value-key="value"
            class="w-36"
          >
            <template #leading>
              <UIcon name="i-lucide-git-merge" class="w-4 h-4" />
            </template>
          </USelectMenu>
          <AppTableClearFiltersButton
            :active="activeFilters.length > 0"
            @clear="resetAllFilters"
          />
        </template>

        <template #right>
          <AppTableColumnsDropdown table-id="workspace-codecs" :columns="columns" />
        </template>
      </UDashboardToolbar>
    </template>

    <template #body>
      <UEmpty
        v-if="codecs && codecs.length === 0"
        variant="naked"
        icon="i-lucide-file-code"
        title="No codecs found"
        description="Codecs define character mappings for text normalization. Create one to get started."
        :actions="emptyStateActions"
      />
      <div v-else-if="codecs">
        <UContextMenu :items="contextMenuItems">
          <AppTable
            table-id="workspace-codecs"
            :data="paginatedData"
            :columns="columns"
            class="flex-1"
            @contextmenu="handleRowContextMenu"
          />
        </UContextMenu>

        <UiFloatingSelectionMenu
          :selected-count="selectedCodecIds.size"
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
          <div class="flex items-center gap-2 text-sm text-neutral-600 dark:text-neutral-400">
            <span>Showing {{ (page - 1) * itemsPerPage + 1 }} to {{ Math.min(page * itemsPerPage, totalItems) }} of {{ totalItems }} codecs</span>
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
