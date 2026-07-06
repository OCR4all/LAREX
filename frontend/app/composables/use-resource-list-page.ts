import { computed, ref, watch, type ComputedRef, type Ref } from 'vue'
import { useTableFilters, type TagFilterOperator } from '@/composables/use-table-filters'

type TagOption = {
  label: string
  value: string
  count: number
}

type TagDescriptor = string | {
  label?: string | null
  value: string
}

type ResourceListPageOptions<T extends object> = {
  data: Ref<T[]> | ComputedRef<T[]>
  defaultSort: { column: string, direction: 'asc' | 'desc' }
  tableId: string
  tagColumn?: string
  getTags?: (item: T) => TagDescriptor[]
}

export const SIMPLE_TAG_OPERATOR_OPTIONS: Array<{ label: string, value: TagFilterOperator }> = [
  { label: 'Match any (OR)', value: 'or' },
  { label: 'Match all (AND)', value: 'and' }
]

export function useResourceListPage<T extends object>(options: ResourceListPageOptions<T>) {
  const tagColumn = options.tagColumn ?? 'tags'
  const getTags = options.getTags ?? ((item: T) => {
    const value = (item as Record<string, unknown>)[tagColumn]
    return Array.isArray(value) ? value as string[] : []
  })

  const filters = useTableFilters(options.data, options.defaultSort, options.tableId)

  const uniqueTags = computed<TagOption[]>(() => {
    const tagCounts = new Map<string, { label: string, count: number }>()

    for (const item of options.data.value) {
      for (const tag of getTags(item)) {
        const value = typeof tag === 'string' ? tag : tag.value
        const label = typeof tag === 'string' ? tag : (tag.label || tag.value)
        if (!value) continue
        const existing = tagCounts.get(value)
        if (existing) {
          existing.count += 1
        } else {
          tagCounts.set(value, { label, count: 1 })
        }
      }
    }

    return Array.from(tagCounts.entries())
      .sort((a, b) => a[1].label.localeCompare(b[1].label))
      .map(([value, { label, count }]) => ({ label, value, count }))
  })

  const selectedTags = computed<string[]>({
    get: () => {
      const tags = filters.columnFilters.value[tagColumn]
      return Array.isArray(tags) ? tags : []
    },
    set: (value) => {
      if (value.length === 0) {
        filters.clearColumnFilter(tagColumn)
      } else {
        filters.setColumnFilter(tagColumn, value)
      }
    }
  })

  const page = ref(1)
  const itemsPerPageRef = ref(10)

  const totalItems = computed(() => filters.filteredAndSortedData.value.length)
  const totalPages = computed(() => Math.max(1, Math.ceil(totalItems.value / itemsPerPageRef.value)))
  const itemsPerPage = useItemsPerPageModel(page, itemsPerPageRef, totalItems)
  const paginatedData = computed(() => {
    const start = (page.value - 1) * itemsPerPageRef.value
    const end = start + itemsPerPageRef.value
    return filters.filteredAndSortedData.value.slice(start, end)
  })

  watch([filters.globalFilter, filters.columnFilters], () => {
    page.value = 1
  }, { deep: true })

  watch(totalPages, (value) => {
    if (page.value > value) {
      page.value = value
    }
  })

  return {
    ...filters,
    uniqueTags,
    selectedTags,
    tagOperatorOptions: SIMPLE_TAG_OPERATOR_OPTIONS,
    page,
    itemsPerPage,
    totalItems,
    totalPages,
    paginatedData
  }
}
