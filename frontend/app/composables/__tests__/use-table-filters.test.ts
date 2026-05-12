import { computed, ref } from 'vue'
import { describe, expect, it, vi } from 'vitest'

describe('useTableFilters', () => {
  it('includes searchText in the global filter', async () => {
    vi.stubGlobal('ref', ref)
    vi.stubGlobal('computed', computed)
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
