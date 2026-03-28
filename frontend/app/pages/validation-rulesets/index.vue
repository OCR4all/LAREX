<script setup lang="ts">
import { h, resolveComponent } from 'vue'
import type { TableColumn } from '@nuxt/ui'
import type { ValidationRulesetSummary } from '@/types/validation-ruleset'
import { wsKey } from '@/utils/fetch-keys'
import { LazyUiDeleteSlideover } from '#components'
import { useWorkspaceBootstrap } from '@/composables/use-workspace-bootstrap'
import { useResourceListPage } from '@/composables/use-resource-list-page'
import { createSortableHeader, renderDropdownActionsCell, renderSimpleTagCell, renderTruncatedText } from '@/utils/resource-list-columns'

const UButton = resolveComponent('UButton')
const UBadge = resolveComponent('UBadge')
const UPopover = resolveComponent('UPopover')
const UDropdownMenu = resolveComponent('UDropdownMenu')
const NuxtLink = resolveComponent('NuxtLink')

const toast = useToast()
const overlay = useOverlay()
const deleteSlideover = overlay.create(LazyUiDeleteSlideover)
const { allow, compactGroups } = useActionVisibility()

const { selectedWorkspace } = await useWorkspaceBootstrap()
const { capabilities: workspaceCapabilities } = useWorkspaceCapabilities(selectedWorkspace)
const canManageUtilities = computed(() => allow(workspaceCapabilities.value.canManageUtilities))
const rulesetsKey = computed(() => wsKey(selectedWorkspace.value, 'validation-rulesets', 'list'))

const { data: rulesets } = await useFetch<ValidationRulesetSummary[]>(() => `/api/workspaces/${selectedWorkspace.value}/validation-rulesets`, {
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

const columns: TableColumn<ValidationRulesetSummary>[] = [
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
    await $fetch(`/api/workspaces/${selectedWorkspace.value}/validation-rulesets/${row.id}`, { method: 'DELETE' })
    toast.add({ title: 'Validation ruleset deleted', color: 'success' })
    await refreshNuxtData(rulesetsKey.value)
  } catch {
    toast.add({ title: 'Error deleting validation ruleset', color: 'error' })
  }
}

const items = (row: ValidationRulesetSummary) => compactGroups([[
  allow(row.capabilities?.canEdit)
    ? { label: 'Edit', icon: 'i-lucide-edit', onSelect: () => navigateTo(`/validation-rulesets/${row.id}`) }
    : null,
  allow(row.capabilities?.canDelete)
    ? { label: 'Delete', icon: 'i-lucide-trash', color: 'error', onSelect: () => handleDelete(row) }
    : null
].filter(Boolean) as Array<Record<string, unknown>>])

const contextMenuRuleset = ref<ValidationRulesetSummary | null>(null)
const contextMenuItems = computed(() => contextMenuRuleset.value ? items(contextMenuRuleset.value) : [])

function handleRowContextMenu(_event: Event, row: { original: ValidationRulesetSummary }) {
  contextMenuRuleset.value = row.original as ValidationRulesetSummary
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
