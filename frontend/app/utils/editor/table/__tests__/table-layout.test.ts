import { describe, expect, it } from 'vitest'
import type { ReadingOrder } from '@/models/editor'
import type { TableModel } from '../table-model'
import { orderedTableCellIds, withTableReadingOrder } from '../table-reading-order'
import { tableLayoutRows } from '../table-layout'

const model: TableModel = {
  tableId: 't',
  rows: 2,
  columns: 2,
  cells: [
    { regionId: 'h', row: 0, col: 0, rowSpan: 1, colSpan: 2, header: true },
    { regionId: 'a', row: 1, col: 0, rowSpan: 1, colSpan: 1, header: false }
  ]
}

describe('tableLayoutRows', () => {
  it('lists each cell once at its origin and fills uncovered positions', () => {
    expect(tableLayoutRows(model).map(row => row.map(slot => slot.kind === 'cell' ? slot.cell.regionId : `-${slot.row}${slot.col}`)))
      .toEqual([['h'], ['a', '-11']])
  })
})

describe('withTableReadingOrder', () => {
  const model2x2: TableModel = {
    tableId: 't',
    rows: 2,
    columns: 2,
    cells: [
      { regionId: 'a', row: 0, col: 0, rowSpan: 1, colSpan: 1, header: false },
      { regionId: 'b', row: 0, col: 1, rowSpan: 1, colSpan: 1, header: false },
      { regionId: 'c', row: 1, col: 0, rowSpan: 1, colSpan: 1, header: false },
      { regionId: 'd', row: 1, col: 1, rowSpan: 1, colSpan: 1, header: false }
    ]
  }

  it('orders cells by rows or by columns', () => {
    expect(orderedTableCellIds(model2x2, 'rows')).toEqual(['a', 'b', 'c', 'd'])
    expect(orderedTableCellIds(model2x2, 'columns')).toEqual(['a', 'c', 'b', 'd'])
  })

  it('replaces the table reference in place and drops loose cell references', () => {
    const readingOrder: ReadingOrder = {
      root: {
        kind: 'OrderedGroup',
        id: 'root',
        elements: [
          { kind: 'RegionRefIndexed', id: 'r1', regionRef: 'x', index: 0 },
          { kind: 'RegionRefIndexed', id: 'r2', regionRef: 't', index: 1 },
          { kind: 'RegionRefIndexed', id: 'r3', regionRef: 'c', index: 2 }
        ]
      }
    }
    const next = withTableReadingOrder(readingOrder, model2x2, 'columns')
    expect(next.root.elements.map(node => [node.kind, 'regionRef' in node ? node.regionRef : undefined])).toEqual([
      ['RegionRefIndexed', 'x'],
      ['OrderedGroupIndexed', 't']
    ])
    const group = next.root.elements[1] as { index?: number, elements: { regionRef: string }[] }
    expect(group.index).toBe(1)
    expect(group.elements.map(node => node.regionRef)).toEqual(['a', 'c', 'b', 'd'])
    expect(readingOrder.root.elements).toHaveLength(3)
  })

  it('appends a group when the table is not in the reading order yet', () => {
    const next = withTableReadingOrder(undefined, model2x2, 'rows')
    expect(next.root.elements).toEqual([expect.objectContaining({ kind: 'OrderedGroup', regionRef: 't' })])
  })
})
