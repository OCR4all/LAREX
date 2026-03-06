<script setup lang="ts">
import { h, resolveComponent } from 'vue'
import type { TableColumn } from '@nuxt/ui'
import type { CodecSummary } from '@/types/codec'
import { wsKey } from '@/utils/fetch-keys'
import { LazyUiDeleteSlideover } from '#components'

const UButton = resolveComponent('UButton')
const UBadge = resolveComponent('UBadge')
const UPopover = resolveComponent('UPopover')
const UDropdownMenu = resolveComponent('UDropdownMenu')
const NuxtLink = resolveComponent('NuxtLink')

const toast = useToast()
const overlay = useOverlay()
const deleteSlideover = overlay.create(LazyUiDeleteSlideover)
const { allow, compactGroups } = useActionVisibility()

const workspace = useWorkspaceStore()

if (!workspace.hasFetched) {
  await workspace.fetchWorkspaces()
}

const selectedWorkspace = computed(() => workspace.selectedWorkspaceId as string)
const { capabilities: workspaceCapabilities } = useWorkspaceCapabilities(selectedWorkspace)
const canManageUtilities = computed(() => allow(workspaceCapabilities.value.canManageUtilities))
const codecsKey = computed(() => wsKey(selectedWorkspace.value, 'codecs', 'list'))

const { data: codecs } = await useFetch<CodecSummary[]>(() => `/api/workspaces/${selectedWorkspace.value}/codecs`, {
  key: codecsKey,
  default: () => []
})

const codecsSafe = computed(() => codecs.value ?? [])

const {
  sort,
  globalFilter,
  columnFilters,
  tagFilterOperator,
  filteredAndSortedData,
  activeFilters,
  setColumnFilter,
  clearColumnFilter,
  resetAllFilters
} = useTableFilters(codecsSafe, { column: 'name', direction: 'asc' })

const uniqueTags = computed(() => {
  const tagCounts = new Map<string, number>()
  codecsSafe.value.forEach((codec) => {
    const tags = codec.tags ?? []
    if (Array.isArray(tags)) {
      tags.forEach((tag) => {
        if (tag && typeof tag === 'string') {
          tagCounts.set(tag, (tagCounts.get(tag) || 0) + 1)
        }
      })
    }
  })

  return Array.from(tagCounts.entries())
    .sort((a, b) => a[0].localeCompare(b[0]))
    .map(([tag, count]) => ({
      label: tag,
      value: tag,
      count
    }))
})

const selectedTags = computed({
  get: () => {
    const tags = columnFilters.value['tags']
    if (Array.isArray(tags)) return tags
    return []
  },
  set: (value: string[]) => {
    if (value.length === 0) {
      clearColumnFilter('tags')
    } else {
      setColumnFilter('tags', value)
    }
  }
})

const tagOperatorOptions = [
  { label: 'Match any (OR)', value: 'or' },
  { label: 'Match all (AND)', value: 'and' }
]

const page = ref(1)
const itemsPerPage = ref(10)

const totalItems = computed(() => filteredAndSortedData.value.length)
const totalPages = computed(() => Math.ceil(totalItems.value / itemsPerPage.value))
const paginatedData = computed(() => {
  const start = (page.value - 1) * itemsPerPage.value
  const end = start + itemsPerPage.value
  return filteredAndSortedData.value.slice(start, end)
})

watch([globalFilter, columnFilters], () => {
  page.value = 1
}, { deep: true })

const columns: TableColumn<any>[] = [
  {
    accessorKey: 'name',
    header: () => {
      return h('div', { class: 'flex items-center gap-2' }, [
        h('span', 'Name'),
        h(UButton, {
          icon: sort.value.column === 'name'
            ? (sort.value.direction === 'asc' ? 'i-lucide-arrow-up' : 'i-lucide-arrow-down')
            : 'i-lucide-arrow-up-down',
          size: 'xs',
          variant: 'ghost',
          color: sort.value.column === 'name' ? 'primary' : 'neutral',
          onClick: () => {
            if (sort.value.column === 'name') {
              sort.value.direction = sort.value.direction === 'asc' ? 'desc' : 'asc'
            } else {
              sort.value = { column: 'name', direction: 'asc' }
            }
          }
        })
      ])
    },
    cell: ({ row }) => h(NuxtLink, { to: `/codecs/${row.original.id}`, class: 'font-medium hover:underline text-primary' }, () => row.getValue('name'))
  },
  {
    accessorKey: 'description',
    header: () => {
      return h('div', { class: 'flex items-center gap-2' }, [
        h('span', 'Description'),
        h(UButton, {
          icon: sort.value.column === 'description'
            ? (sort.value.direction === 'asc' ? 'i-lucide-arrow-up' : 'i-lucide-arrow-down')
            : 'i-lucide-arrow-up-down',
          size: 'xs',
          variant: 'ghost',
          color: sort.value.column === 'description' ? 'primary' : 'neutral',
          onClick: () => {
            if (sort.value.column === 'description') {
              sort.value.direction = sort.value.direction === 'asc' ? 'desc' : 'asc'
            } else {
              sort.value = { column: 'description', direction: 'asc' }
            }
          }
        })
      ])
    },
    cell: ({ row }) => {
      const description = row.getValue('description') as string
      if (!description) return h('div', { class: 'text-neutral-400 dark:text-neutral-500 text-sm' }, '—')

      return h('div', {
        class: 'text-neutral-700 dark:text-neutral-400 max-w-32 sm:max-w-48 lg:max-w-64 xl:max-w-80 truncate',
        title: description
      }, description)
    }
  },
  {
    accessorKey: 'tags',
    header: 'Tags',
    cell: ({ row }) => {
      const tags = row.getValue('tags') as string[] | undefined

      if (!tags || tags.length === 0) return null

      if (tags.length <= 3) {
        return h('div', { class: 'flex flex-wrap gap-1' },
          tags.map(tag =>
            h(UBadge, {
              variant: 'subtle',
              color: 'primary',
              size: 'md',
              key: tag
            }, () => tag)
          )
        )
      }

      const visibleTags = tags.slice(0, 2)
      const hiddenTags = tags.slice(2)
      const hiddenTagsCount = hiddenTags.length

      return h('div', { class: 'flex flex-wrap items-center gap-1' }, [
        ...visibleTags.map(tag =>
          h(UBadge, {
            variant: 'soft',
            color: 'neutral',
            size: 'sm',
            key: tag
          }, () => tag)
        ),
        h(UPopover, { mode: 'hover' }, {
          default: () => h(UButton, {
            variant: 'soft',
            color: 'primary',
            size: 'sm',
            class: 'h-[22px]'
          }, () => `+${hiddenTagsCount}`),
          content: () => h('div', { class: 'p-2 flex flex-col gap-1' },
            hiddenTags.map(tag =>
              h(UBadge, {
                variant: 'soft',
                color: 'neutral',
                size: 'sm',
                key: tag
              }, () => tag)
            )
          )
        })
      ])
    }
  },
  {
    accessorKey: 'characterCount',
    header: () => {
      return h('div', { class: 'flex items-center gap-2 justify-end' }, [
        h('span', 'Chars'),
        h(UButton, {
          icon: sort.value.column === 'characterCount'
            ? (sort.value.direction === 'asc' ? 'i-lucide-arrow-up' : 'i-lucide-arrow-down')
            : 'i-lucide-arrow-up-down',
          size: 'xs',
          variant: 'ghost',
          color: sort.value.column === 'characterCount' ? 'primary' : 'neutral',
          onClick: () => {
            if (sort.value.column === 'characterCount') {
              sort.value.direction = sort.value.direction === 'asc' ? 'desc' : 'asc'
            } else {
              sort.value = { column: 'characterCount', direction: 'asc' }
            }
          }
        })
      ])
    },
    cell: ({ row }) => h('div', { class: 'text-right tabular-nums' }, String(row.getValue('characterCount') ?? 0))
  },
  {
    id: 'actions',
    cell: ({ row }) => {
      const rowItems = items(row.original)
      if (!rowItems.length) return null
      return h(
        'div',
        { class: 'text-right' },
        h(
          UDropdownMenu,
          {
            'content': { align: 'end' },
            'items': rowItems,
            'aria-label': 'Actions dropdown'
          },
          () => h(UButton, {
            'icon': 'i-lucide-ellipsis-vertical',
            'color': 'neutral',
            'variant': 'ghost',
            'class': 'ml-auto',
            'aria-label': 'Actions dropdown'
          })
        )
      )
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
    await $fetch(`/api/workspaces/${selectedWorkspace.value}/codecs/${row.id}`, { method: 'DELETE' })
    toast.add({ title: 'Codec deleted', color: 'success' })
    await refreshNuxtData(codecsKey.value)
  } catch {
    toast.add({ title: 'Error deleting codec', color: 'error' })
  }
}

const items = (row: CodecSummary) => {
  const actions: any[] = []

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
const contextMenuItems = computed(() => {
  if (!contextMenuCodec.value) return []
  return items(contextMenuCodec.value)
})

function handleRowContextMenu(_event: Event, row: { original: Record<string, unknown> }) {
  contextMenuCodec.value = row.original as unknown as CodecSummary
}

const emptyStateActions = computed(() => {
  const actions: Array<Record<string, any>> = [
    {
      icon: 'i-lucide-refresh-cw',
      label: 'Refresh',
      color: 'neutral',
      variant: 'subtle',
      onClick: () => refreshNuxtData(codecsKey.value)
    }
  ]

  if (canManageUtilities.value) {
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
        <template #leading>
          <LazyUDashboardSidebarCollapse />
        </template>
        <template #right>
          <UButton
            v-if="canManageUtilities"
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
            data-tour="codecs-search"
            v-model="globalFilter"
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
            <span class="text-xs text-gray-500">Active filters:</span>
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
        v-if="codecs && codecs.length === 0"
        variant="naked"
        icon="i-lucide-file-code"
        title="No codecs found"
        description="Codecs define character mappings for text normalization. Create one to get started."
        :actions="emptyStateActions"
      />
      <div v-else-if="codecs">
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

        <div v-if="totalPages > 1" class="flex justify-between items-center p-4 border-t border-gray-200 dark:border-gray-800">
          <div class="flex items-center gap-2 text-sm text-gray-600 dark:text-gray-400">
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
              show-edges
              :sibling-count="1"
            />
          </div>
        </div>
      </div>
    </template>
  </UDashboardPanel>
</template>
