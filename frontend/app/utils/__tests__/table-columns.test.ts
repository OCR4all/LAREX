import { describe, expect, it } from 'vitest'
import { filterVisibleTableColumns, normalizeTableColumns } from '../table-columns'

describe('table-columns', () => {
  it('uses meta labels for column visibility labels', () => {
    const columns = [
      { id: 'select' },
      { accessorKey: 'projectOrderPosition', header: () => null, meta: { label: 'Order' } },
      { accessorKey: 'name', header: 'Name' }
    ]

    expect(normalizeTableColumns(columns)).toEqual([
      { id: 'select', label: 'Select', canHide: false },
      { id: 'projectOrderPosition', label: 'Order', canHide: true },
      { id: 'name', label: 'Name', canHide: true }
    ])
  })

  it('keeps fixed columns visible while hiding optional columns', () => {
    const columns = [
      { id: 'select' },
      { accessorKey: 'projectOrderPosition' },
      { accessorKey: 'name' }
    ]

    expect(filterVisibleTableColumns(columns, { projectOrderPosition: false, name: true }))
      .toEqual([
        { id: 'select' },
        { accessorKey: 'name' }
      ])
  })
})
