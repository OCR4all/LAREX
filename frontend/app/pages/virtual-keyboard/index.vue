<script setup lang="ts">
import { h } from 'vue'
import type { DropdownMenuItem, TableColumn } from '@nuxt/ui'
import type { Row } from '@tanstack/vue-table'
import type { KeyboardLayout } from '@/types/virtual-keyboard'
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
const keyboardsKey = computed(() => wsKey(workspaceId.value, 'virtual-keyboards', 'list'))

const { data: keyboards } = await useFetch<KeyboardLayout[]>(() => `/api/workspaces/${workspaceId.value}/virtual-keyboards`, {
  key: keyboardsKey,
  default: () => []
})

const keyboardsSafe = computed(() => keyboards.value ?? [])

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
  data: keyboardsSafe,
  defaultSort: { column: 'name', direction: 'asc' }
})

const selectedKeyboardIds = ref<Set<string>>(new Set())
const selectedKeyboards = computed(() => keyboardsSafe.value.filter(keyboard => selectedKeyboardIds.value.has(keyboard.id)))
const canDeleteSelected = computed(() =>
  selectedKeyboards.value.length > 0
  && selectedKeyboards.value.every(keyboard => allow(keyboard.capabilities?.canDelete))
)
const allPageSelected = computed(() =>
  paginatedData.value.length > 0
  && paginatedData.value.every(keyboard => selectedKeyboardIds.value.has(keyboard.id))
)
const somePageSelected = computed(() =>
  paginatedData.value.some(keyboard => selectedKeyboardIds.value.has(keyboard.id))
  && !allPageSelected.value
)

function toggleKeyboardSelection(keyboardId: string) {
  const next = new Set(selectedKeyboardIds.value)
  if (next.has(keyboardId)) next.delete(keyboardId)
  else next.add(keyboardId)
  selectedKeyboardIds.value = next
}

function toggleCurrentPageSelection() {
  const next = new Set(selectedKeyboardIds.value)
  if (allPageSelected.value) {
    paginatedData.value.forEach(keyboard => next.delete(keyboard.id))
  } else {
    paginatedData.value.forEach(keyboard => next.add(keyboard.id))
  }
  selectedKeyboardIds.value = next
}

function clearSelection() {
  selectedKeyboardIds.value = new Set()
}

watch(keyboardsSafe, (nextKeyboards) => {
  const validIds = new Set(nextKeyboards.map(keyboard => keyboard.id))
  selectedKeyboardIds.value = new Set(Array.from(selectedKeyboardIds.value).filter(id => validIds.has(id)))
}, { immediate: true })

const columns: TableColumn<KeyboardLayout>[] = [
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
      checked: selectedKeyboardIds.value.has(row.original.id),
      onChange: () => toggleKeyboardSelection(row.original.id),
      onClick: (event: Event) => event.stopPropagation(),
      class: 'rounded-sm border-neutral-300 text-primary-600 focus:ring-primary-500'
    })
  },
  {
    accessorKey: 'name',
    header: createSortableHeader('Name', 'name', sort, UButton),
    cell: ({ row }) => h(NuxtLink, { to: `/virtual-keyboard/${row.original.id}`, class: 'font-medium hover:underline text-primary' }, () => row.getValue('name'))
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
    id: 'actions',
    cell: ({ row }) => {
      const rowItems = items(row.original)
      return renderDropdownActionsCell(rowItems, { UButton, UDropdownMenu })
    }
  }
]

const handleDelete = async (row: KeyboardLayout) => {
  if (!allow(row.capabilities?.canDelete)) return
  const instance = deleteSlideover.open({
    name: row.name,
    entityType: 'Keyboard'
  })
  const confirmed = await instance.result
  if (!confirmed) return

  try {
    await $fetch(`/api/workspaces/${workspaceId.value}/virtual-keyboards/${row.id}`, {
      method: 'DELETE'
    })
    toast.add({ title: 'Keyboard deleted', color: 'success' })
    await refreshNuxtData(keyboardsKey.value)
  } catch {
    toast.add({ title: 'Error deleting keyboard', color: 'error' })
  }
}

async function handleDeleteSelected() {
  if (!canDeleteSelected.value) return

  const count = selectedKeyboards.value.length
  const instance = deleteSlideover.open({
    name: `${count} keyboard${count === 1 ? '' : 's'}`,
    entityType: 'Keyboard'
  })
  const confirmed = await instance.result
  if (!confirmed) return

  const results = await Promise.allSettled(selectedKeyboards.value.map(keyboard =>
    $fetch(`/api/workspaces/${workspaceId.value}/virtual-keyboards/${keyboard.id}`, { method: 'DELETE' })
  ))

  const deletedCount = results.filter(result => result.status === 'fulfilled').length
  const failedCount = results.length - deletedCount

  if (deletedCount > 0) {
    toast.add({ title: deletedCount === 1 ? 'Keyboard deleted' : 'Keyboards deleted', description: `${deletedCount} item${deletedCount === 1 ? '' : 's'} removed.`, color: 'success' })
  }
  if (failedCount > 0) {
    toast.add({ title: 'Some deletions failed', description: `${failedCount} item${failedCount === 1 ? '' : 's'} could not be deleted.`, color: 'warning' })
  }

  clearSelection()
  await refreshNuxtData(keyboardsKey.value)
}

const items = (row: KeyboardLayout): DropdownMenuItem[][] => {
  const actions: DropdownMenuItem[] = []

  if (allow(row.capabilities?.canEdit)) {
    actions.push({
      label: 'Edit',
      icon: 'i-lucide-edit',
      onSelect: () => navigateTo(`/virtual-keyboard/${row.id}`)
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

const contextMenuKeyboard = ref<KeyboardLayout | null>(null)
const contextMenuItems = computed<DropdownMenuItem[][]>(() => {
  if (!contextMenuKeyboard.value) return []
  return items(contextMenuKeyboard.value)
})

function handleRowContextMenu(_event: Event, row: Row<KeyboardLayout>) {
  contextMenuKeyboard.value = row.original
}

const emptyStateActions = computed(() => {
  const actions: Array<Record<string, unknown>> = [
    {
      icon: 'i-lucide-refresh-cw',
      label: 'Refresh',
      color: 'neutral',
      variant: 'subtle',
      onClick: () => refreshNuxtData(keyboardsKey.value)
    }
  ]

  if (canManageUtilities.value) {
    actions.unshift({
      icon: 'i-lucide-plus',
      label: 'Create new',
      variant: 'solid',
      to: '/virtual-keyboard/new'
    })
  }

  return actions
})
</script>

<template>
  <UDashboardPanel id="virtual-keyboards" data-tour="vk-panel">
    <template #header>
      <UDashboardNavbar title="Virtual Keyboards">
        <template #leading>
          <LazyUDashboardSidebarCollapse />
        </template>
        <template #right>
          <UButton
            v-if="canManageUtilities"
            data-tour="vk-new"
            label="New Virtual Keyboard"
            color="neutral"
            variant="outline"
            icon="i-lucide-keyboard"
            to="/virtual-keyboard/new"
          />
        </template>
      </UDashboardNavbar>
      <UDashboardToolbar>
        <template #left>
          <UInput
            v-model="globalFilter"
            data-tour="vk-search"
            placeholder="Search virtual keyboards..."
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
        v-if="keyboards && keyboards.length === 0"
        variant="naked"
        icon="i-lucide-keyboard"
        title="No virtual keyboards found"
        description="Virtual keyboards provide custom character input layouts. Create one to get started."
        :actions="emptyStateActions"
      />
      <div v-else-if="keyboards">
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
          :selected-count="selectedKeyboardIds.size"
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
            <span>Showing {{ (page - 1) * itemsPerPage + 1 }} to {{ Math.min(page * itemsPerPage, totalItems) }} of {{ totalItems }} keyboards</span>
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
