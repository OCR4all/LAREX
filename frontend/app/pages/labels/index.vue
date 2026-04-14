<script setup lang="ts">
import { h } from 'vue'
import type { DropdownMenuItem, TableColumn } from '@nuxt/ui'
import type { Row } from '@tanstack/vue-table'
import type { LabelSet, LabelSetSummary } from '@/types/label-set'
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
const labelSetsKey = computed(() => wsKey(workspaceId.value, 'label-sets', 'list'))

const { data: labelSets } = await useFetch<LabelSetSummary[]>(() => `/api/workspaces/${workspaceId.value}/label-sets`, {
  key: labelSetsKey,
  default: () => []
})

const labelSetsSafe = computed(() => labelSets.value ?? [])

type LabelSetRow = LabelSetSummary & {
  name: string
  description: string
  tags: string[]
}

const rows = computed<LabelSetRow[]>(() => {
  return labelSetsSafe.value.map(ls => ({
    ...ls,
    name: ls.meta?.name ?? '',
    description: ls.meta?.description ?? '',
    tags: ls.meta?.tags ?? []
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

const selectedLabelSetIds = ref<Set<string>>(new Set())
const selectedLabelSets = computed(() => rows.value.filter(labelSet => selectedLabelSetIds.value.has(labelSet.id)))
const canDeleteSelected = computed(() =>
  selectedLabelSets.value.length > 0
  && selectedLabelSets.value.every(labelSet => allow(labelSet.capabilities?.canDelete))
)
const allPageSelected = computed(() =>
  paginatedData.value.length > 0
  && paginatedData.value.every(labelSet => selectedLabelSetIds.value.has(labelSet.id))
)
const somePageSelected = computed(() =>
  paginatedData.value.some(labelSet => selectedLabelSetIds.value.has(labelSet.id))
  && !allPageSelected.value
)

function toggleLabelSetSelection(labelSetId: string) {
  const next = new Set(selectedLabelSetIds.value)
  if (next.has(labelSetId)) next.delete(labelSetId)
  else next.add(labelSetId)
  selectedLabelSetIds.value = next
}

function toggleCurrentPageSelection() {
  const next = new Set(selectedLabelSetIds.value)
  if (allPageSelected.value) {
    paginatedData.value.forEach(labelSet => next.delete(labelSet.id))
  } else {
    paginatedData.value.forEach(labelSet => next.add(labelSet.id))
  }
  selectedLabelSetIds.value = next
}

function clearSelection() {
  selectedLabelSetIds.value = new Set()
}

watch(rows, (nextRows) => {
  const validIds = new Set(nextRows.map(labelSet => labelSet.id))
  selectedLabelSetIds.value = new Set(Array.from(selectedLabelSetIds.value).filter(id => validIds.has(id)))
}, { immediate: true })

const columns: TableColumn<LabelSetRow>[] = [
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
      checked: selectedLabelSetIds.value.has(row.original.id),
      onChange: () => toggleLabelSetSelection(row.original.id),
      onClick: (event: Event) => event.stopPropagation(),
      class: 'rounded-sm border-neutral-300 text-primary-600 focus:ring-primary-500'
    })
  },
  {
    accessorKey: 'name',
    header: createSortableHeader('Name', 'name', sort, UButton),
    cell: ({ row }) => {
      const name = row.getValue('name') as string
      const isSystem = row.original.meta?.isSystem ?? false

      return h('div', { class: 'flex items-center gap-2' }, [
        h(NuxtLink, { to: `/labels/${row.original.id}`, class: `font-medium hover:underline ${isSystem ? 'text-muted' : 'text-primary'}` }, () => name || '—'),
        isSystem ? h(UBadge, { variant: 'subtle', color: 'neutral', size: 'xs', icon: 'i-lucide-lock' }, () => 'System') : null
      ])
    }
  },
  {
    accessorKey: 'description',
    header: createSortableHeader('Description', 'description', sort, UButton),
    cell: ({ row }) => renderTruncatedText((row.getValue('description') as string) ?? '')
  },
  {
    accessorKey: 'tags',
    header: 'Tags',
    cell: ({ row }) => renderSimpleTagCell(row.getValue('tags') as string[] | undefined, { UBadge, UButton, UPopover })
  },
  {
    accessorKey: 'labelCount',
    header: () => h('div', { class: 'flex items-center gap-2 justify-end' }, [h('span', 'Labels')]),
    cell: ({ row }) => h('div', { class: 'text-right tabular-nums' }, String(row.original.labelCount ?? 0))
  },
  {
    id: 'actions',
    cell: ({ row }) => {
      const rowItems = items(row.original)
      return renderDropdownActionsCell(rowItems, { UButton, UDropdownMenu })
    }
  }
]

const handleDelete = async (row: LabelSetRow) => {
  if (!allow(row.capabilities?.canDelete)) return
  const instance = deleteSlideover.open({
    name: row.name,
    entityType: 'Label Set',
    warningMessage: 'This action cannot be undone! All projects using this label set will lose their label configuration.'
  })
  const confirmed = await instance.result
  if (!confirmed) return

  try {
    await $fetch(`/api/workspaces/${workspaceId.value}/label-sets/${row.id}`, { method: 'DELETE' })
    toast.add({ title: 'Label set deleted', color: 'success' })
    await refreshNuxtData(labelSetsKey.value)
  } catch {
    toast.add({ title: 'Error deleting label set', color: 'error' })
  }
}

async function handleDeleteSelected() {
  if (!canDeleteSelected.value) return

  const count = selectedLabelSets.value.length
  const instance = deleteSlideover.open({
    name: `${count} label set${count === 1 ? '' : 's'}`,
    entityType: 'Label Set',
    warningMessage: 'This action cannot be undone. All projects using these label sets will lose their label configuration.'
  })
  const confirmed = await instance.result
  if (!confirmed) return

  const results = await Promise.allSettled(selectedLabelSets.value.map(labelSet =>
    $fetch(`/api/workspaces/${workspaceId.value}/label-sets/${labelSet.id}`, { method: 'DELETE' })
  ))

  const deletedCount = results.filter(result => result.status === 'fulfilled').length
  const failedCount = results.length - deletedCount

  if (deletedCount > 0) {
    toast.add({ title: deletedCount === 1 ? 'Label set deleted' : 'Label sets deleted', description: `${deletedCount} item${deletedCount === 1 ? '' : 's'} removed.`, color: 'success' })
  }
  if (failedCount > 0) {
    toast.add({ title: 'Some deletions failed', description: `${failedCount} item${failedCount === 1 ? '' : 's'} could not be deleted.`, color: 'warning' })
  }

  clearSelection()
  await refreshNuxtData(labelSetsKey.value)
}

const getDuplicateName = (baseName: string) => {
  const trimmed = baseName.trim() || 'Label Set'
  const existing = new Set(rows.value.map(row => row.name))
  const baseCopy = `${trimmed} (Copy)`
  if (!existing.has(baseCopy)) return baseCopy
  let index = 2
  while (existing.has(`${trimmed} (Copy ${index})`)) {
    index++
  }
  return `${trimmed} (Copy ${index})`
}

const handleDuplicate = async (row: LabelSetRow) => {
  if (!allow(row.capabilities?.canEdit)) return
  try {
    const source = await $fetch<LabelSet>(`/api/workspaces/${workspaceId.value}/label-sets/${row.id}`)
    const name = getDuplicateName(source.meta?.name ?? row.name)
    const payload = {
      meta: {
        name,
        description: source.meta?.description ?? '',
        tags: source.meta?.tags ?? [],
        altoEnabled: source.meta?.altoEnabled ?? false
      },
      labels: source.labels ?? []
    }
    await $fetch(`/api/workspaces/${workspaceId.value}/label-sets`, {
      method: 'POST',
      body: payload
    })
    toast.add({ title: 'Label set duplicated', color: 'success' })
    await refreshNuxtData(labelSetsKey.value)
  } catch {
    toast.add({ title: 'Error duplicating label set', color: 'error' })
  }
}

const items = (row: LabelSetRow): DropdownMenuItem[][] => {
  const canEdit = allow(row.capabilities?.canEdit)
  const canDelete = allow(row.capabilities?.canDelete)
  const actions: DropdownMenuItem[] = []

  if (canEdit) {
    actions.push({
      label: 'Edit',
      icon: 'i-lucide-edit',
      onSelect: () => navigateTo(`/labels/${row.id}`)
    })
    actions.push({
      label: 'Duplicate',
      icon: 'i-lucide-copy-plus',
      onSelect: () => handleDuplicate(row)
    })
  }

  if (canDelete) {
    actions.push({
      label: 'Delete',
      icon: 'i-lucide-trash',
      color: 'error',
      onSelect: () => handleDelete(row)
    })
  }

  return compactGroups([actions])
}

const contextMenuLabelSet = ref<LabelSetRow | null>(null)
const contextMenuItems = computed<DropdownMenuItem[][]>(() => {
  if (!contextMenuLabelSet.value) return []
  return items(contextMenuLabelSet.value)
})

function handleRowContextMenu(_event: Event, row: Row<LabelSetRow>) {
  contextMenuLabelSet.value = row.original
}

const emptyStateActions = computed(() => {
  const actions: Array<Record<string, unknown>> = [
    {
      icon: 'i-lucide-refresh-cw',
      label: 'Refresh',
      color: 'neutral',
      variant: 'subtle',
      onClick: () => refreshNuxtData(labelSetsKey.value)
    }
  ]

  if (canManageUtilities.value) {
    actions.unshift({
      icon: 'i-lucide-plus',
      label: 'Create new',
      variant: 'solid',
      to: '/labels/new'
    })
  }

  return actions
})
</script>

<template>
  <UDashboardPanel id="labels" data-tour="labels-panel">
    <template #header>
      <UDashboardNavbar title="Labels">
        <template #leading>
          <LazyUDashboardSidebarCollapse />
        </template>
        <template #right>
          <UButton
            v-if="canManageUtilities"
            data-tour="labels-new"
            label="New Label Set"
            color="neutral"
            variant="outline"
            icon="i-lucide-plus"
            to="/labels/new"
          />
        </template>
      </UDashboardNavbar>

      <UDashboardToolbar>
        <template #left>
          <UInput
            v-model="globalFilter"
            data-tour="labels-search"
            placeholder="Search label sets..."
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
        v-if="labelSets && labelSets.length === 0"
        variant="naked"
        icon="i-lucide-tags"
        title="No label sets found"
        description="Label sets define categories for annotating document regions. Create one to get started."
        :actions="emptyStateActions"
      />
      <div v-else-if="labelSets">
        <UContextMenu :items="contextMenuItems">
          <UTable
            data-tour="labels-table"
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
          :selected-count="selectedLabelSetIds.size"
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
            <span>Showing {{ (page - 1) * itemsPerPage + 1 }} to {{ Math.min(page * itemsPerPage, totalItems) }} of {{ totalItems }} label sets</span>
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
