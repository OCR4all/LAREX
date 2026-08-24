import { ref } from 'vue'
import { describe, expect, it } from 'vitest'

describe('useTableFilters', () => {
  it('includes searchText in the global filter', async () => {
    const { useTableFilters } = await import('../use-table-filters')
    const rows = ref([
      { name: 'Personal Workspace', description: '', searchText: 'Personal Workspace alice' },
      { name: 'Manuscripts', description: '', searchText: 'Manuscripts curator' }
    ])
    const { globalFilter, filteredAndSortedData } = useTableFilters(rows, { column: 'name', direction: 'asc' })

    globalFilter.value = 'alice'

    expect(filteredAndSortedData.value).toHaveLength(1)
    expect(filteredAndSortedData.value[0]?.name).toBe('Personal Workspace')
  })
})
