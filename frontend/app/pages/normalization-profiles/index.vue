<script setup lang="ts">
import { h } from 'vue'
import type { DropdownMenuItem, TableColumn } from '@nuxt/ui'
import type { Row } from '@tanstack/vue-table'
import type { NormalizationProfileSummary } from '@/types/normalization-profile'
import { wsKey } from '@/utils/fetch-keys'
import { LazyUiDeleteSlideover } from '#components'
import { useWorkspaceBootstrap } from '@/composables/use-workspace-bootstrap'
import { useResourceListPage } from '@/composables/use-resource-list-page'
import { createSortableHeader, renderDropdownActionsCell, renderSimpleTagCell, renderTruncatedText, resolveUiComponent } from '@/utils/resource-list-columns'

const UButton = resolveUiComponent('UButton')
const UBadge = resolveUiComponent('UBadge')
const UPopover = resolveUiComponent('UPopover')
const UDropdownMenu = resolveUiComponent('UDropdownMenu')
const NuxtLink = resolveUiComponent('NuxtLink')

const toast = useToast()
const overlay = useOverlay()
const deleteSlideover = overlay.create(LazyUiDeleteSlideover)
const { allow, compactGroups } = useActionVisibility()

const { selectedWorkspace } = await useWorkspaceBootstrap()
const workspaceId = computed(() => selectedWorkspace.value ?? '')
const { capabilities: workspaceCapabilities } = useWorkspaceCapabilities(selectedWorkspace)
const canManageUtilities = computed(() => allow(workspaceCapabilities.value.canManageUtilities))
const profilesKey = computed(() => wsKey(workspaceId.value, 'normalization-profiles', 'list'))

const { data: profiles } = await useFetch<NormalizationProfileSummary[]>(() => `/api/workspaces/${workspaceId.value}/normalization-profiles`, {
  key: profilesKey,
  default: () => []
})

const profilesSafe = computed(() => profiles.value ?? [])

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
  data: profilesSafe,
  defaultSort: { column: 'name', direction: 'asc' }
})

const columns: TableColumn<NormalizationProfileSummary>[] = [
  {
    accessorKey: 'name',
    header: createSortableHeader('Name', 'name', sort, UButton),
    cell: ({ row }) => h(NuxtLink, { to: `/normalization-profiles/${row.original.id}`, class: 'font-medium hover:underline text-primary' }, () => row.getValue('name'))
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
    accessorKey: 'unicodeNormalization',
    header: createSortableHeader('Unicode', 'unicodeNormalization', sort, UButton),
    cell: ({ row }) => h(UBadge, { color: 'neutral', variant: 'soft' }, () => String(row.getValue('unicodeNormalization') ?? 'NFC'))
  },
  {
    id: 'actions',
    cell: ({ row }) => renderDropdownActionsCell(items(row.original), { UButton, UDropdownMenu })
  }
]

const handleDelete = async (row: NormalizationProfileSummary) => {
  if (!allow(row.capabilities?.canDelete)) return
  const instance = deleteSlideover.open({
    name: row.name,
    entityType: 'Normalization Profile',
    warningMessage: 'This action cannot be undone. Projects and workspaces using this profile will lose the assignment.'
  })
  const confirmed = await instance.result
  if (!confirmed) return

  try {
    await $fetch(`/api/workspaces/${workspaceId.value}/normalization-profiles/${row.id}`, { method: 'DELETE' })
    toast.add({ title: 'Normalization profile deleted', color: 'success' })
    await refreshNuxtData(profilesKey.value)
  } catch {
    toast.add({ title: 'Error deleting normalization profile', color: 'error' })
  }
}

const items = (row: NormalizationProfileSummary): DropdownMenuItem[][] => {
  const actions: DropdownMenuItem[] = []
  if (allow(row.capabilities?.canEdit)) {
    actions.push({ label: 'Edit', icon: 'i-lucide-edit', onSelect: () => navigateTo(`/normalization-profiles/${row.id}`) })
  }
  if (allow(row.capabilities?.canDelete)) {
    actions.push({ label: 'Delete', icon: 'i-lucide-trash', color: 'error', onSelect: () => handleDelete(row) })
  }
  return compactGroups([actions])
}

const contextMenuProfile = ref<NormalizationProfileSummary | null>(null)
const contextMenuItems = computed(() => contextMenuProfile.value ? items(contextMenuProfile.value) : [])

function handleRowContextMenu(_event: Event, row: Row<NormalizationProfileSummary>) {
  contextMenuProfile.value = row.original
}

const emptyStateActions = computed(() => {
  const actions: Array<Record<string, unknown>> = [
    { icon: 'i-lucide-refresh-cw', label: 'Refresh', color: 'neutral', variant: 'subtle', onClick: () => refreshNuxtData(profilesKey.value) }
  ]

  if (canManageUtilities.value) {
    actions.unshift({ icon: 'i-lucide-plus', label: 'Create new', variant: 'solid', to: '/normalization-profiles/new' })
  }

  return actions
})
</script>

<template>
  <UDashboardPanel id="normalization-profiles">
    <template #header>
      <UDashboardNavbar title="Normalization Profiles">
        <template #leading>
          <LazyUDashboardSidebarCollapse />
        </template>
        <template #right>
          <UButton
            v-if="canManageUtilities"
            label="New Profile"
            color="neutral"
            variant="outline"
            icon="i-lucide-plus"
            to="/normalization-profiles/new"
          />
        </template>
      </UDashboardNavbar>

      <UDashboardToolbar>
        <template #left>
          <UInput
            v-model="globalFilter"
            placeholder="Search profiles..."
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
        v-if="profiles && profiles.length === 0"
        variant="naked"
        icon="i-lucide-wand-sparkles"
        title="No normalization profiles found"
        description="Normalization profiles let you standardize transcription text before QA and export."
        :actions="emptyStateActions"
      />
      <div v-else-if="profiles">
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

        <div v-if="totalPages > 1" class="flex justify-between items-center p-4 border-t border-neutral-200 dark:border-neutral-800">
          <div class="flex items-center gap-2 text-sm text-neutral-600 dark:text-neutral-400">
            <span>Showing {{ (page - 1) * itemsPerPage + 1 }} to {{ Math.min(page * itemsPerPage, totalItems) }} of {{ totalItems }} profiles</span>
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
