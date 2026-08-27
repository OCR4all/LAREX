<script setup lang="ts">
import type { DropdownMenuItem, TableColumn } from '@nuxt/ui'
import type { Row } from '@tanstack/vue-table'
import type { NormalizationProfileSummary } from '@/types/normalization-profile'
import { LazyUiDeleteSlideover, NuxtLink, UBadge, UButton, UDropdownMenu, UPopover } from '#components'

const toast = useToast()
const overlay = useOverlay()
const deleteSlideover = overlay.create(LazyUiDeleteSlideover)
const { allow, compactGroups } = useActionVisibility()

const { selectedWorkspace } = await useWorkspaceBootstrap()
const workspaceId = computed(() => selectedWorkspace.value ?? '')
const { capabilities: workspaceCapabilities } = useWorkspaceCapabilities(selectedWorkspace)
const canManageToolkit = computed(() => allow(workspaceCapabilities.value.canManageToolkit))
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
  defaultSort: { column: 'name', direction: 'asc' },
  tableId: 'workspace-normalization-profiles'
})

const selectedProfileIds = ref<Set<string>>(new Set())
const selectedProfiles = computed(() => profilesSafe.value.filter(profile => selectedProfileIds.value.has(profile.id)))
const canDeleteSelected = computed(() =>
  selectedProfiles.value.length > 0
  && selectedProfiles.value.every(profile => allow(profile.capabilities?.canDelete))
)
const allPageSelected = computed(() =>
  paginatedData.value.length > 0
  && paginatedData.value.every(profile => selectedProfileIds.value.has(profile.id))
)
const somePageSelected = computed(() =>
  paginatedData.value.some(profile => selectedProfileIds.value.has(profile.id))
  && !allPageSelected.value
)

function toggleProfileSelection(profileId: string) {
  const next = new Set(selectedProfileIds.value)
  if (next.has(profileId)) next.delete(profileId)
  else next.add(profileId)
  selectedProfileIds.value = next
}

function toggleCurrentPageSelection() {
  const next = new Set(selectedProfileIds.value)
  if (allPageSelected.value) {
    paginatedData.value.forEach(profile => next.delete(profile.id))
  } else {
    paginatedData.value.forEach(profile => next.add(profile.id))
  }
  selectedProfileIds.value = next
}

function clearSelection() {
  selectedProfileIds.value = new Set()
}

watch(profilesSafe, (nextProfiles) => {
  const validIds = new Set(nextProfiles.map(profile => profile.id))
  selectedProfileIds.value = new Set(Array.from(selectedProfileIds.value).filter(id => validIds.has(id)))
}, { immediate: true })

const columns: TableColumn<NormalizationProfileSummary>[] = [
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
      checked: selectedProfileIds.value.has(row.original.id),
      onChange: () => toggleProfileSelection(row.original.id),
      onClick: (event: Event) => event.stopPropagation(),
      class: 'rounded-sm border-neutral-300 text-primary-600 focus:ring-primary-500'
    })
  },
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

async function handleDeleteSelected() {
  if (!canDeleteSelected.value) return

  const count = selectedProfiles.value.length
  const instance = deleteSlideover.open({
    name: `${count} normalization profile${count === 1 ? '' : 's'}`,
    entityType: 'Normalization Profile',
    items: selectedProfiles.value.map(profile => ({ id: profile.id, label: profile.name })),
    warningMessage: 'This action cannot be undone. Projects and workspaces using these profiles will lose the assignment.'
  })
  const confirmed = await instance.result
  if (!confirmed) return

  try {
    const response = await $fetch<{ successCount: number, failedCount: number }>(
      `/api/workspaces/${workspaceId.value}/normalization-profiles/bulk`,
      {
        method: 'DELETE',
        body: { ids: selectedProfiles.value.map(profile => profile.id) }
      }
    )

    if (response.successCount > 0) {
      toast.add({ title: response.successCount === 1 ? 'Normalization profile deleted' : 'Normalization profiles deleted', description: `${response.successCount} item${response.successCount === 1 ? '' : 's'} removed.`, color: 'success' })
    }
    if (response.failedCount > 0) {
      toast.add({ title: 'Some deletions failed', description: `${response.failedCount} item${response.failedCount === 1 ? '' : 's'} could not be deleted.`, color: 'warning' })
    }

    clearSelection()
    await refreshNuxtData(profilesKey.value)
  } catch (error: unknown) {
    toast.add({ title: 'Error deleting normalization profiles', description: extractApiErrorMessage(error, 'Failed to delete normalization profiles'), color: 'error' })
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

  if (canManageToolkit.value) {
    actions.unshift({ icon: 'i-lucide-plus', label: 'Create new', variant: 'solid', to: '/normalization-profiles/new' })
  }

  return actions
})
</script>

<template>
  <UDashboardPanel id="normalization-profiles">
    <template #header>
      <UDashboardNavbar title="Normalization Profiles">
        <template #right>
          <UButton
            v-if="canManageToolkit"
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
          <AppTableClearFiltersButton
            :active="activeFilters.length > 0"
            @clear="resetAllFilters"
          />
        </template>
        <template #right>
          <AppTableColumnsDropdown table-id="workspace-normalization-profiles" :columns="columns" />
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
          <AppTable
            table-id="workspace-normalization-profiles"
            :data="paginatedData"
            :columns="columns"
            class="flex-1"
            @contextmenu="handleRowContextMenu"
          />
        </UContextMenu>

        <UiFloatingSelectionMenu
          :selected-count="selectedProfileIds.size"
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
