<script setup lang="ts">
import { h, resolveComponent } from 'vue'
import type { TableColumn } from '@nuxt/ui'
import type { TagSetSummary } from '@/types/tag-set'
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
const tagSetsKey = computed(() => wsKey(selectedWorkspace.value, 'tag-sets', 'list'))

const { data: tagSets, refresh } = await useFetch<TagSetSummary[]>(() => `/api/workspaces/${selectedWorkspace.value}/tag-sets`, {
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
  columnFilters,
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

const columns: TableColumn<TagSetRow>[] = [
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
    await $fetch(`/api/workspaces/${selectedWorkspace.value}/tag-sets/${row.id}`, { method: 'DELETE' })
    toast.add({ title: 'Tag set deleted', color: 'success' })
    await refresh()
  } catch {
    toast.add({ title: 'Error deleting tag set', color: 'error' })
  }
}

const items = (row: TagSetRow) => {
  const actions: any[] = []

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
const contextMenuItems = computed(() => {
  if (!contextMenuTagSet.value) return []
  return items(contextMenuTagSet.value)
})

function handleRowContextMenu(_event: Event, row: { original: Record<string, unknown> }) {
  contextMenuTagSet.value = row.original as unknown as TagSetRow
}

const emptyStateActions = computed(() => {
  const actions: Array<Record<string, any>> = [
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
            data-tour="tag-sets-search"
            v-model="globalFilter"
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
                size="2xs"
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
