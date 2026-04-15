<script setup lang="ts">
import { h } from 'vue'
import type { DropdownMenuItem, TableColumn } from '@nuxt/ui'
import type { Row } from '@tanstack/vue-table'
import type { TagSetSummary } from '@/types/tag-set'
import { wsKey } from '@/utils/fetch-keys'
import { LazyUiDeleteSlideover, NuxtLink, UBadge, UButton, UDropdownMenu, UPopover } from '#components'
import { useWorkspaceBootstrap } from '@/composables/use-workspace-bootstrap'
import { useResourceListPage } from '@/composables/use-resource-list-page'
import { createSortableHeader, renderDropdownActionsCell, renderSimpleTagCell, renderTruncatedText } from '@/utils/resource-list-columns'

const toast = useToast()
const overlay = useOverlay()
const deleteSlideover = overlay.create(LazyUiDeleteSlideover)
const { allow, compactGroups } = useActionVisibility()

const { selectedWorkspace } = await useWorkspaceBootstrap()
const workspaceId = computed(() => selectedWorkspace.value ?? '')
const { capabilities: workspaceCapabilities } = useWorkspaceCapabilities(selectedWorkspace)
const canManageUtilities = computed(() => allow(workspaceCapabilities.value.canManageUtilities))
const tagSetsKey = computed(() => wsKey(workspaceId.value, 'tag-sets', 'list'))

const { data: tagSets, refresh } = await useFetch<TagSetSummary[]>(() => `/api/workspaces/${workspaceId.value}/tag-sets`, {
  key: tagSetsKey,
  default: () => []
})

const tagSetsSafe = computed(() => tagSets.value ?? [])

type TagSetRow = TagSetSummary & {
  name: string
  description: string
  tags: string[]
}

const rows = computed<TagSetRow[]>(() => {
  return tagSetsSafe.value.map(t => ({
    ...t,
    name: t.meta?.name ?? '',
    description: t.meta?.description ?? '',
    tags: t.meta?.tags ?? []
  }))
})

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
  data: rows,
  defaultSort: { column: 'name', direction: 'asc' }
})

const selectedTagSetIds = ref<Set<string>>(new Set())
const selectedTagSets = computed(() => rows.value.filter(tagSet => selectedTagSetIds.value.has(tagSet.id)))
const canDeleteSelected = computed(() =>
  selectedTagSets.value.length > 0
  && selectedTagSets.value.every(tagSet => allow(tagSet.capabilities?.canDelete))
)
const allPageSelected = computed(() =>
  paginatedData.value.length > 0
  && paginatedData.value.every(tagSet => selectedTagSetIds.value.has(tagSet.id))
)
const somePageSelected = computed(() =>
  paginatedData.value.some(tagSet => selectedTagSetIds.value.has(tagSet.id))
  && !allPageSelected.value
)

function toggleTagSetSelection(tagSetId: string) {
  const next = new Set(selectedTagSetIds.value)
  if (next.has(tagSetId)) next.delete(tagSetId)
  else next.add(tagSetId)
  selectedTagSetIds.value = next
}

function toggleCurrentPageSelection() {
  const next = new Set(selectedTagSetIds.value)
  if (allPageSelected.value) {
    paginatedData.value.forEach(tagSet => next.delete(tagSet.id))
  } else {
    paginatedData.value.forEach(tagSet => next.add(tagSet.id))
  }
  selectedTagSetIds.value = next
}

function clearSelection() {
  selectedTagSetIds.value = new Set()
}

watch(rows, (nextRows) => {
  const validIds = new Set(nextRows.map(tagSet => tagSet.id))
  selectedTagSetIds.value = new Set(Array.from(selectedTagSetIds.value).filter(id => validIds.has(id)))
}, { immediate: true })

const columns: TableColumn<TagSetRow>[] = [
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
      checked: selectedTagSetIds.value.has(row.original.id),
      onChange: () => toggleTagSetSelection(row.original.id),
      onClick: (event: Event) => event.stopPropagation(),
      class: 'rounded-sm border-neutral-300 text-primary-600 focus:ring-primary-500'
    })
  },
  {
    accessorKey: 'name',
    header: createSortableHeader('Name', 'name', sort, UButton),
    cell: ({ row }) => h(NuxtLink, { to: `/tag-sets/${row.original.id}`, class: 'font-medium hover:underline text-primary' }, () => (row.getValue('name') as string) || '-')
  },
  {
    accessorKey: 'description',
    header: createSortableHeader('Description', 'description', sort, UButton),
    cell: ({ row }) => renderTruncatedText((row.getValue('description') as string) ?? '', '-')
  },
  {
    accessorKey: 'tags',
    header: 'Tags',
    cell: ({ row }) => renderSimpleTagCell(row.getValue('tags') as string[] | undefined, { UBadge, UButton, UPopover })
  },
  {
    accessorKey: 'tagCount',
    header: () => h('div', { class: 'flex items-center gap-2 justify-end' }, [h('span', 'Tags Count')]),
    cell: ({ row }) => h('div', { class: 'text-right tabular-nums' }, String(row.original.tagCount ?? 0))
  },
  {
    id: 'actions',
    cell: ({ row }) => {
      const rowItems = items(row.original)
      return renderDropdownActionsCell(rowItems, { UButton, UDropdownMenu })
    }
  }
]

const handleDelete = async (row: TagSetRow) => {
  if (!allow(row.capabilities?.canDelete)) return
  const instance = deleteSlideover.open({
    name: row.name,
    entityType: 'Tag Set',
    warningMessage: 'This action cannot be undone! All projects using this tag set will lose their tag structure reference.'
  })
  const confirmed = await instance.result
  if (!confirmed) return

  try {
    await $fetch(`/api/workspaces/${workspaceId.value}/tag-sets/${row.id}`, { method: 'DELETE' })
    toast.add({ title: 'Tag set deleted', color: 'success' })
    await refresh()
  } catch {
    toast.add({ title: 'Error deleting tag set', color: 'error' })
  }
}

async function handleDeleteSelected() {
  if (!canDeleteSelected.value) return

  const count = selectedTagSets.value.length
  const instance = deleteSlideover.open({
    name: `${count} tag set${count === 1 ? '' : 's'}`,
    entityType: 'Tag Set',
    warningMessage: 'This action cannot be undone. All projects using these tag sets will lose their tag structure reference.'
  })
  const confirmed = await instance.result
  if (!confirmed) return

  try {
    const response = await $fetch<{ successCount: number, failedCount: number }>(
      `/api/workspaces/${workspaceId.value}/tag-sets/bulk`,
      {
        method: 'DELETE',
        body: { ids: selectedTagSets.value.map(tagSet => tagSet.id) }
      }
    )

    if (response.successCount > 0) {
      toast.add({ title: response.successCount === 1 ? 'Tag set deleted' : 'Tag sets deleted', description: `${response.successCount} item${response.successCount === 1 ? '' : 's'} removed.`, color: 'success' })
    }
    if (response.failedCount > 0) {
      toast.add({ title: 'Some deletions failed', description: `${response.failedCount} item${response.failedCount === 1 ? '' : 's'} could not be deleted.`, color: 'warning' })
    }

    clearSelection()
    await refresh()
  } catch (error: unknown) {
    toast.add({ title: 'Error deleting tag sets', description: extractApiErrorMessage(error, 'Failed to delete tag sets'), color: 'error' })
  }
}

const items = (row: TagSetRow): DropdownMenuItem[][] => {
  const actions: DropdownMenuItem[] = []

  if (allow(row.capabilities?.canEdit)) {
    actions.push({
      label: 'Edit',
      icon: 'i-lucide-edit',
      onSelect: () => navigateTo(`/tag-sets/${row.id}`)
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

const contextMenuTagSet = ref<TagSetRow | null>(null)
const contextMenuItems = computed<DropdownMenuItem[][]>(() => {
  if (!contextMenuTagSet.value) return []
  return items(contextMenuTagSet.value)
})

function handleRowContextMenu(_event: Event, row: Row<TagSetRow>) {
  contextMenuTagSet.value = row.original
}

const emptyStateActions = computed(() => {
  const actions: Array<Record<string, unknown>> = [
    {
      icon: 'i-lucide-refresh-cw',
      label: 'Refresh',
      color: 'neutral',
      variant: 'subtle',
      onClick: () => refresh()
    }
  ]

  if (canManageUtilities.value) {
    actions.unshift({
      icon: 'i-lucide-plus',
      label: 'Create new',
      variant: 'solid',
      to: '/tag-sets/new'
    })
  }

  return actions
})
</script>

<template>
  <UDashboardPanel id="tag-sets" data-tour="tag-sets-panel">
    <template #header>
      <UDashboardNavbar title="Tags">
        <template #leading>
          <LazyUDashboardSidebarCollapse />
        </template>
        <template #right>
          <UButton
            v-if="canManageUtilities"
            data-tour="tag-sets-new"
            label="New Tag Set"
            color="neutral"
            variant="outline"
            icon="i-lucide-plus"
            to="/tag-sets/new"
          />
        </template>
      </UDashboardNavbar>

      <UDashboardToolbar>
        <template #left>
          <UInput
            v-model="globalFilter"
            data-tour="tag-sets-search"
            placeholder="Search tag sets..."
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
          <div v-if="activeFilters.length > 0" class="flex items-center gap-2">
            <span class="text-xs text-neutral-500">Active filters:</span>
            <component
              :is="UBadge"
              v-for="filter in activeFilters"
              :key="`${filter.type}-${filter.value}`"
              variant="soft"
              color="neutral"
              size="sm"
              class="flex items-center gap-1"
            >
              {{ filter.label }}
              <component
                :is="UButton"
                size="xs"
                color="neutral"
                variant="link"
                icon="i-lucide-x"
                :padded="false"
                @click="filter.clear()"
              />
            </component>
          </div>
        </template>
      </UDashboardToolbar>
    </template>

    <template #body>
      <UEmpty
        v-if="tagSets && tagSets.length === 0"
        variant="naked"
        icon="i-lucide-network"
        title="No tag sets found"
        description="Tag sets define hierarchical tag structures for categorizing projects and pages. Create one to get started."
        :actions="emptyStateActions"
      />
      <div v-else-if="tagSets">
        <UContextMenu :items="contextMenuItems">
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

        <UiFloatingSelectionMenu
          :selected-count="selectedTagSetIds.size"
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
            <span>Showing {{ (page - 1) * itemsPerPage + 1 }} to {{ Math.min(page * itemsPerPage, totalItems) }} of {{ totalItems }} tag sets</span>
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
