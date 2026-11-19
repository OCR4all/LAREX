import { describe, expect, it } from 'vitest'
import { assignToShapes, centroid, createGridTableModel, inferTableFromShapes, outlineCorners } from '../table-builder'
import type { GridPoint } from '../table-geometry'

function rect(left: number, top: number, right: number, bottom: number): GridPoint[] {
  return [[left, top], [right, top], [right, bottom], [left, bottom]]
}

describe('outlineCorners', () => {
  it('orders the corners of a skewed quadrilateral from the top left, with y pointing up', () => {
    const quad: GridPoint[] = [[1, -9], [-1, 1], [11, 0], [10, -10]]
    expect(outlineCorners(quad)).toEqual([[-1, 1], [11, 0], [10, -10], [1, -9]])
  })

  it('falls back to the bounding box when corners cannot be told apart', () => {
    expect(outlineCorners([[0, 0], [10, 10], [20, 0]])).toEqual([[0, 10], [20, 10], [20, 0], [0, 0]])
  })
})

describe('createGridTableModel', () => {
  it('splits the outline into evenly spaced cells and marks header rows', () => {
    const model = createGridTableModel({
      tableId: 't',
      outline: rect(0, 20, 30, 0),
      rows: 2,
      columns: 3,
      headerRows: 1,
      newCellId: (row, col) => `c${row}${col}`
    })
    expect(model.rows).toBe(2)
    expect(model.columns).toBe(3)
    expect(model.grid?.points).toEqual([
      [[0, 20], [10, 20], [20, 20], [30, 20]],
      [[0, 10], [10, 10], [20, 10], [30, 10]],
      [[0, 0], [10, 0], [20, 0], [30, 0]]
    ])
    expect(model.cells.map(cell => [cell.regionId, cell.header])).toEqual([
      ['c00', true], ['c01', true], ['c02', true], ['c10', false], ['c11', false], ['c12', false]
    ])
  })
})

describe('centroid', () => {
  it('uses the area centroid and falls back to the vertex mean for flat shapes', () => {
    expect(centroid(rect(0, 10, 10, 0))).toEqual([5, 5])
    expect(centroid([[0, 0], [4, 0]])).toEqual([2, 0])
  })
})

describe('assignToShapes', () => {
  const cells = [{ id: 'left', points: rect(0, 10, 10, 0) }, { id: 'right', points: rect(10, 10, 20, 0) }]

  it('assigns items to the shape containing their centre', () => {
    const lines = [{ id: 'a', points: rect(1, 6, 9, 4) }, { id: 'b', points: rect(12, 6, 19, 4) }]
    expect(assignToShapes(lines, cells, false)).toEqual(new Map([['a', 'left'], ['b', 'right']]))
  })

  it('leaves items outside every shape unassigned unless asked to use the nearest one', () => {
    const outside = [{ id: 'x', points: rect(21, 6, 25, 4) }]
    expect(assignToShapes(outside, cells, false).size).toBe(0)
    expect(assignToShapes(outside, cells, true).get('x')).toBe('right')
  })
})

describe('inferTableFromShapes', () => {
  it('finds rows, columns and spans from region boxes, tolerating small gaps', () => {
    const shapes = [
      { id: 'h', points: rect(0, 30, 20, 20.5) },
      { id: 'a', points: rect(0.4, 20, 10, 10) },
      { id: 'b', points: rect(10.3, 19.8, 20, 10.2) },
      { id: 'c', points: rect(0, 10, 10, 0) },
      { id: 'd', points: rect(10, 10, 19.6, 0) }
    ]
    const { model, outline } = inferTableFromShapes('t', shapes)

    expect(model.rows).toBe(3)
    expect(model.columns).toBe(2)
    expect(model.cells.map(cell => [cell.regionId, cell.row, cell.col, cell.rowSpan, cell.colSpan])).toEqual([
      ['h', 0, 0, 1, 2],
      ['a', 1, 0, 1, 1],
      ['b', 1, 1, 1, 1],
      ['c', 2, 0, 1, 1],
      ['d', 2, 1, 1, 1]
    ])
    expect(outline[0]![1]).toBe(30)
    expect(outline[2]![1]).toBe(0)
  })

  it('collapses a gap between rows that no region covers', () => {
    const shapes = [
      { id: 'a', points: rect(0, 30, 10, 20) },
      { id: 'b', points: rect(0, 14, 10, 4) }
    ]
    const { model } = inferTableFromShapes('t', shapes)
    expect(model.rows).toBe(2)
    expect(model.cells.map(cell => cell.row)).toEqual([0, 1])
  })
})
