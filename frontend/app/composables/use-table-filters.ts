/**
 * Reusable composable for table sorting, filtering, and searching
 */
export type TagFilterOperator = 'and' | 'or'
type ActiveFilter =
  | {
    type: 'global'
    label: string
    value: string
    clear: () => void
    column?: undefined
  }
  | {
    type: 'column'
    column: string
    label: string
    value: string | string[] | boolean
    clear: () => void
  }

export const useTableFilters = <T extends object>(
  data: Ref<T[]> | ComputedRef<T[]>,
  defaultSort: { column: string, direction: 'asc' | 'desc' } = { column: 'created', direction: 'desc' }
) => {
  const sort = ref(defaultSort)
  const globalFilter = ref('')
  const columnFilters = ref<Record<string, string | string[] | boolean>>({})
  const tagFilterOperator = ref<TagFilterOperator>('or')
  const globalSearchFields = ['name', 'description'] as const

  const getValueByPath = (obj: unknown, path: string): unknown => {
    return path.split('.').reduce<unknown>((current, key) => {
      if (current && typeof current === 'object') {
        return (current as Record<string, unknown>)[key]
      }
      return undefined
    }, obj)
  }

  const getUniqueColumnValues = (columnKey: string) => {
    if (!data.value) return []
    const values = [...new Set(data.value.map(item => getValueByPath(item, columnKey)))]
      .filter(Boolean)
      .sort((a, b) => String(a).localeCompare(String(b)))
    return values.map(value => ({ value, label: String(value) }))
  }

  const filteredAndSortedData = computed(() => {
    if (!data.value) return []

    let items = [...data.value]

    if (globalFilter.value) {
      const searchTerm = globalFilter.value.toLowerCase().trim()
      items = items.filter((item) => {
        return globalSearchFields.some((field) => {
          const value = getValueByPath(item, field)
          if (value === undefined || value === null) return false

          if (Array.isArray(value)) {
            return value.some(v => String(v).toLowerCase().includes(searchTerm))
          }
          return String(value).toLowerCase().includes(searchTerm)
        })
      })
    }

    Object.entries(columnFilters.value).forEach(([column, filterValue]) => {
      if (filterValue !== undefined && filterValue !== null && filterValue !== '') {
        if (Array.isArray(filterValue) && filterValue.length > 0) {
          items = items.filter((item) => {
            const value = getValueByPath(item, column)
            if (Array.isArray(value)) {
              if (tagFilterOperator.value === 'and') {
                return filterValue.every(tag =>
                  value.some(v => String(v).toLowerCase() === String(tag).toLowerCase())
                )
              } else {
                return filterValue.some(tag =>
                  value.some(v => String(v).toLowerCase() === String(tag).toLowerCase())
                )
              }
            }
            return filterValue.some(tag =>
              String(value).toLowerCase() === String(tag).toLowerCase()
            )
          })
        } else if (typeof filterValue === 'boolean') {
          items = items.filter((item) => {
            const value = getValueByPath(item, column)
            return value === filterValue
          })
        } else if (typeof filterValue === 'string') {
          const filterTerm = filterValue.toLowerCase()
          items = items.filter((item) => {
            const value = getValueByPath(item, column)
            if (Array.isArray(value)) {
              return value.some(v => String(v).toLowerCase().includes(filterTerm))
            }
            return String(value).toLowerCase().includes(filterTerm)
          })
        }
      }
    })

    if (sort.value.column) {
      items.sort((a, b) => {
        const aVal = getValueByPath(a, sort.value.column)
        const bVal = getValueByPath(b, sort.value.column)

        let comparison = 0

        if (aVal instanceof Date && bVal instanceof Date) {
          comparison = aVal.getTime() - bVal.getTime()
        } else if (typeof aVal === 'string' && typeof bVal === 'string') {
          const aDate = new Date(aVal)
          const bDate = new Date(bVal)
          if (!isNaN(aDate.getTime()) && !isNaN(bDate.getTime())) {
            comparison = aDate.getTime() - bDate.getTime()
          } else {
            comparison = aVal.localeCompare(bVal)
          }
        } else if (typeof aVal === 'number' && typeof bVal === 'number') {
          comparison = aVal - bVal
        } else {
          comparison = String(aVal).localeCompare(String(bVal))
        }

        return sort.value.direction === 'desc' ? -comparison : comparison
      })
    }

    return items
  })

  const setColumnFilter = (column: string, value: string | string[] | boolean) => {
    columnFilters.value[column] = value
  }

  const clearColumnFilter = (column: string) => {
    const { [column]: _removed, ...rest } = columnFilters.value
    columnFilters.value = rest
  }

  const resetAllFilters = () => {
    globalFilter.value = ''
    columnFilters.value = {}
    sort.value = defaultSort
    tagFilterOperator.value = 'or'
  }

  const activeFilters = computed<ActiveFilter[]>(() => {
    const filters: ActiveFilter[] = []

    if (globalFilter.value) {
      filters.push({
        type: 'global',
        label: `Search: ${globalFilter.value}`,
        value: globalFilter.value,
        clear: () => { globalFilter.value = '' }
      })
    }

    Object.entries(columnFilters.value).forEach(([column, value]) => {
      if (value !== undefined && value !== null && value !== '' && !(Array.isArray(value) && value.length === 0)) {
        let displayValue: string
        if (Array.isArray(value)) {
          displayValue = value.join(` ${tagFilterOperator.value.toUpperCase()} `)
        } else if (typeof value === 'boolean') {
          displayValue = value ? 'Yes' : 'No'
        } else {
          displayValue = String(value)
        }
        filters.push({
          type: 'column',
          column,
          label: `${column}: ${displayValue}`,
          value,
          clear: () => clearColumnFilter(column)
        })
      }
    })

    return filters
  })

  return {
    sort,
    globalFilter,
    columnFilters,
    tagFilterOperator,

    filteredAndSortedData,
    activeFilters,

    getUniqueColumnValues,
    setColumnFilter,
    clearColumnFilter,
    resetAllFilters
  }
}
