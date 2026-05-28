<script setup lang="ts">
import type { DropdownMenuItem, TableColumn } from '@nuxt/ui'
import type { Row } from '@tanstack/vue-table'
import type { ValidationRulesetSummary } from '@/types/validation-ruleset'
import { LazyUiDeleteSlideover, NuxtLink, UBadge, UButton, UDropdownMenu, UPopover } from '#components'

const toast = useToast()
const overlay = useOverlay()
const deleteSlideover = overlay.create(LazyUiDeleteSlideover)
const { allow, compactGroups } = useActionVisibility()

const { selectedWorkspace } = await useWorkspaceBootstrap()
const workspaceId = computed(() => selectedWorkspace.value ?? '')
const { capabilities: workspaceCapabilities } = useWorkspaceCapabilities(selectedWorkspace)
const canManageUtilities = computed(() => allow(workspaceCapabilities.value.canManageUtilities))
const rulesetsKey = computed(() => wsKey(workspaceId.value, 'validation-rulesets', 'list'))

const { data: rulesets } = await useFetch<ValidationRulesetSummary[]>(() => `/api/workspaces/${workspaceId.value}/validation-rulesets`, {
  key: rulesetsKey,
  default: () => []
})

const rulesetsSafe = computed(() => rulesets.value ?? [])

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
  data: rulesetsSafe,
  defaultSort: { column: 'name', direction: 'asc' }
})

const selectedRulesetIds = ref<Set<string>>(new Set())
const selectedRulesets = computed(() => rulesetsSafe.value.filter(ruleset => selectedRulesetIds.value.has(ruleset.id)))
const canDeleteSelected = computed(() =>
  selectedRulesets.value.length > 0
  && selectedRulesets.value.every(ruleset => allow(ruleset.capabilities?.canDelete))
)
const allPageSelected = computed(() =>
  paginatedData.value.length > 0
  && paginatedData.value.every(ruleset => selectedRulesetIds.value.has(ruleset.id))
)
const somePageSelected = computed(() =>
  paginatedData.value.some(ruleset => selectedRulesetIds.value.has(ruleset.id))
  && !allPageSelected.value
)

function toggleRulesetSelection(rulesetId: string) {
  const next = new Set(selectedRulesetIds.value)
  if (next.has(rulesetId)) next.delete(rulesetId)
  else next.add(rulesetId)
  selectedRulesetIds.value = next
}

function toggleCurrentPageSelection() {
  const next = new Set(selectedRulesetIds.value)
  if (allPageSelected.value) {
    paginatedData.value.forEach(ruleset => next.delete(ruleset.id))
  } else {
    paginatedData.value.forEach(ruleset => next.add(ruleset.id))
  }
  selectedRulesetIds.value = next
}

function clearSelection() {
  selectedRulesetIds.value = new Set()
}

watch(rulesetsSafe, (nextRulesets) => {
  const validIds = new Set(nextRulesets.map(ruleset => ruleset.id))
  selectedRulesetIds.value = new Set(Array.from(selectedRulesetIds.value).filter(id => validIds.has(id)))
}, { immediate: true })

const columns: TableColumn<ValidationRulesetSummary>[] = [
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
      checked: selectedRulesetIds.value.has(row.original.id),
      onChange: () => toggleRulesetSelection(row.original.id),
      onClick: (event: Event) => event.stopPropagation(),
      class: 'rounded-sm border-neutral-300 text-primary-600 focus:ring-primary-500'
    })
  },
  {
    accessorKey: 'name',
    header: createSortableHeader('Name', 'name', sort, UButton),
    cell: ({ row }) => h(NuxtLink, { to: `/validation-rulesets/${row.original.id}`, class: 'font-medium hover:underline text-primary' }, () => row.getValue('name'))
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
    accessorKey: 'ruleCount',
    header: createSortableHeader('Rules', 'ruleCount', sort, UButton, { align: 'end' }),
    cell: ({ row }) => h('div', { class: 'text-right tabular-nums' }, String(row.getValue('ruleCount') ?? 0))
  },
  {
    id: 'actions',
    cell: ({ row }) => renderDropdownActionsCell(items(row.original), { UButton, UDropdownMenu })
  }
]

const handleDelete = async (row: ValidationRulesetSummary) => {
  if (!allow(row.capabilities?.canDelete)) return
  const instance = deleteSlideover.open({
    name: row.name,
    entityType: 'Validation Ruleset',
    warningMessage: 'This action cannot be undone. Projects and workspaces using this ruleset will lose the assignment.'
  })
  const confirmed = await instance.result
  if (!confirmed) return

  try {
    await $fetch(`/api/workspaces/${workspaceId.value}/validation-rulesets/${row.id}`, { method: 'DELETE' })
    toast.add({ title: 'Validation ruleset deleted', color: 'success' })
    await refreshNuxtData(rulesetsKey.value)
  } catch {
    toast.add({ title: 'Error deleting validation ruleset', color: 'error' })
  }
}

async function handleDeleteSelected() {
  if (!canDeleteSelected.value) return

  const count = selectedRulesets.value.length
  const instance = deleteSlideover.open({
    name: `${count} validation ruleset${count === 1 ? '' : 's'}`,
    entityType: 'Validation Ruleset',
    items: selectedRulesets.value.map(ruleset => ({ id: ruleset.id, label: ruleset.name })),
    warningMessage: 'This action cannot be undone. Projects and workspaces using these rulesets will lose the assignment.'
  })
  const confirmed = await instance.result
  if (!confirmed) return

  try {
    const response = await $fetch<{ successCount: number, failedCount: number }>(
      `/api/workspaces/${workspaceId.value}/validation-rulesets/bulk`,
      {
        method: 'DELETE',
        body: { ids: selectedRulesets.value.map(ruleset => ruleset.id) }
      }
    )

    if (response.successCount > 0) {
      toast.add({ title: response.successCount === 1 ? 'Validation ruleset deleted' : 'Validation rulesets deleted', description: `${response.successCount} item${response.successCount === 1 ? '' : 's'} removed.`, color: 'success' })
    }
    if (response.failedCount > 0) {
      toast.add({ title: 'Some deletions failed', description: `${response.failedCount} item${response.failedCount === 1 ? '' : 's'} could not be deleted.`, color: 'warning' })
    }

    clearSelection()
    await refreshNuxtData(rulesetsKey.value)
  } catch (error: unknown) {
    toast.add({ title: 'Error deleting validation rulesets', description: extractApiErrorMessage(error, 'Failed to delete validation rulesets'), color: 'error' })
  }
}

const items = (row: ValidationRulesetSummary): DropdownMenuItem[][] => {
  const actions: DropdownMenuItem[] = []
  if (allow(row.capabilities?.canEdit)) {
    actions.push({ label: 'Edit', icon: 'i-lucide-edit', onSelect: () => navigateTo(`/validation-rulesets/${row.id}`) })
  }
  if (allow(row.capabilities?.canDelete)) {
    actions.push({ label: 'Delete', icon: 'i-lucide-trash', color: 'error', onSelect: () => handleDelete(row) })
  }
  return compactGroups([actions])
}

const contextMenuRuleset = ref<ValidationRulesetSummary | null>(null)
const contextMenuItems = computed(() => contextMenuRuleset.value ? items(contextMenuRuleset.value) : [])

function handleRowContextMenu(_event: Event, row: Row<ValidationRulesetSummary>) {
  contextMenuRuleset.value = row.original
}

const emptyStateActions = computed(() => {
  const actions: Array<Record<string, unknown>> = [
    { icon: 'i-lucide-refresh-cw', label: 'Refresh', color: 'neutral', variant: 'subtle', onClick: () => refreshNuxtData(rulesetsKey.value) }
  ]

  if (canManageUtilities.value) {
    actions.unshift({ icon: 'i-lucide-plus', label: 'Create new', variant: 'solid', to: '/validation-rulesets/new' })
  }

  return actions
})
</script>

<template>
  <UDashboardPanel id="validation-rulesets">
    <template #header>
      <UDashboardNavbar title="Validation Rulesets">
        <template #leading>
          <LazyUDashboardSidebarCollapse />
        </template>
        <template #right>
          <UButton
            v-if="canManageUtilities"
            label="New Ruleset"
            color="neutral"
            variant="outline"
            icon="i-lucide-plus"
            to="/validation-rulesets/new"
          />
        </template>
      </UDashboardNavbar>
      <UDashboardToolbar>
        <template #left>
          <UInput
            v-model="globalFilter"
            placeholder="Search rulesets..."
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
      </UDashboardToolbar>
    </template>

    <template #body>
      <UEmpty
        v-if="rulesets && rulesets.length === 0"
        variant="naked"
        icon="i-lucide-shield-alert"
        title="No validation rulesets found"
        description="Validation rulesets let you surface suspicious transcription patterns across project text."
        :actions="emptyStateActions"
      />
      <div v-else-if="rulesets">
        <UContextMenu :items="contextMenuItems">
          <AppTable
            table-id="workspace-validation-rulesets"
            :data="paginatedData"
            :columns="columns"
            class="flex-1"
            @contextmenu="handleRowContextMenu"
          />
        </UContextMenu>

        <UiFloatingSelectionMenu
          :selected-count="selectedRulesetIds.size"
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
            <span>Showing {{ (page - 1) * itemsPerPage + 1 }} to {{ Math.min(page * itemsPerPage, totalItems) }} of {{ totalItems }} rulesets</span>
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
