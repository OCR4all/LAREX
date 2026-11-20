import { describe, expect, it } from 'vitest'
import { createGridTableModel } from '../table-builder'
import {
  applyTableOperation,
  canApplyTableOperation,
  gridHasPoint,
  gridLineAt,
  gridLinePoints,
  insertGridAddPoint,
  moveGridLine,
  moveGridPoint,
  removeGridAddPoints,
  splitPolylineInHalf
} from '../table-structure'
import type { TableModel } from '../table-model'

function grid(rows: number, columns: number, headerRows = 0): TableModel {
  return createGridTableModel({
    tableId: 't',
    outline: [[0, rows * 10], [columns * 10, rows * 10], [columns * 10, 0], [0, 0]],
    rows,
    columns,
    headerRows,
    newCellId: (row, col) => `c${row}${col}`
  })
}

function round<T>(value: T): T {
  return JSON.parse(JSON.stringify(value, (_, v) => typeof v === 'number' ? Math.round(v * 1e6) / 1e6 : v))
}

function ys(model: TableModel): number[] {
  return round(model.grid!.points.map(line => line[0]![1]))
}

function counter() {
  let next = 0
  return () => `n${next++}`
}

function layout(model: TableModel): string[] {
  return model.cells.map(cell => `${cell.regionId}@${cell.row},${cell.col}+${cell.rowSpan}x${cell.colSpan}`)
}

function expectValid(model: TableModel) {
  expect(model.grid!.points).toHaveLength(model.rows + 1)
  expect(model.grid!.points.every(line => line.length === model.columns + 1)).toBe(true)
  const covered = new Map<string, number>()
  for (const cell of model.cells) {
    for (let row = cell.row; row < cell.row + cell.rowSpan; row++) {
      for (let col = cell.col; col < cell.col + cell.colSpan; col++) {
        covered.set(`${row},${col}`, (covered.get(`${row},${col}`) ?? 0) + 1)
      }
    }
  }
  expect(covered.size).toBe(model.rows * model.columns)
  expect([...covered.values()].every(count => count === 1)).toBe(true)
}

describe('splitPolylineInHalf', () => {
  it('finds the middle by length and keeps the intermediate points on their side', () => {
    expect(splitPolylineInHalf([0, 0], [[0, 2], [0, 8]], [0, 10])).toEqual({ middle: [0, 5], before: [[0, 2]], after: [[0, 8]] })
  })
})

describe('applyTableOperation', () => {
  it('splits a row in half, adding cells for unspanned cells and stretching spanned ones', () => {
    const merged = applyTableOperation(grid(2, 2), { kind: 'merge-cells', cellIds: ['c00', 'c10'] }, counter()).model
    const { model, created, redistribute } = applyTableOperation(merged, { kind: 'split-row', row: 1 }, counter())

    expect(model.rows).toBe(3)
    expect(ys(model)).toEqual([20, 10, 5, 0])
    expect(layout(model)).toEqual(['c00@0,0+3x1', 'c01@0,1+1x1', 'c11@1,1+1x1', 'n0@2,1+1x1'])
    expect(created).toEqual(['n0'])
    expect(redistribute).toEqual(['c11'])
    expectValid(model)
  })

  it('splits a column through the transposed grid', () => {
    const { model } = applyTableOperation(grid(1, 2, 1), { kind: 'split-column', col: 0 }, counter())
    expect(model.columns).toBe(3)
    expect(round(model.grid!.points[0]!.map(point => point[0]))).toEqual([0, 5, 10, 20])
    expect(model.cells.map(cell => [cell.regionId, cell.col, cell.header])).toEqual([['c00', 0, true], ['n0', 1, true], ['c01', 2, true]])
    expectValid(model)
  })

  it('merges a row with the next one when the cells line up', () => {
    const { model, absorbed } = applyTableOperation(grid(3, 2), { kind: 'merge-row-with-next', row: 0 }, counter())
    expect(model.rows).toBe(2)
    expect(layout(model)).toEqual(['c00@0,0+1x1', 'c01@0,1+1x1', 'c20@1,0+1x1', 'c21@1,1+1x1'])
    expect(absorbed).toEqual(['c10', 'c11'])
    expect(ys(model)).toEqual([30, 10, 0])
    expect(round(model.grid!.addPoints)).toEqual([
      { row: 0, col: 0, left: [[0, 20]], top: [] },
      { row: 0, col: 1, left: [[10, 20]], top: [] },
      { row: 0, col: 2, left: [[20, 20]], top: [] }
    ])
    expectValid(model)
  })

  it('refuses to merge rows whose cells do not line up', () => {
    const spanned = applyTableOperation(grid(2, 2), { kind: 'merge-cells', cellIds: ['c00', 'c01'] }, counter()).model
    expect(canApplyTableOperation(spanned, { kind: 'merge-row-with-next', row: 0 })).toBe(false)
  })

  it('deletes edge rows by shrinking the table and inner rows by widening the row above', () => {
    const edge = applyTableOperation(grid(3, 1), { kind: 'delete-row', row: 0 }, counter())
    expect(edge.removed).toEqual(['c00'])
    expect(ys(edge.model)).toEqual([20, 10, 0])
    expect(layout(edge.model)).toEqual(['c10@0,0+1x1', 'c20@1,0+1x1'])

    const inner = applyTableOperation(grid(3, 1), { kind: 'delete-row', row: 1 }, counter())
    expect(ys(inner.model)).toEqual([30, 10, 0])
    expect(layout(inner.model)).toEqual(['c00@0,0+1x1', 'c20@1,0+1x1'])
    expectValid(inner.model)

    const last = applyTableOperation(grid(3, 1), { kind: 'delete-row', row: 2 }, counter())
    expect(ys(last.model)).toEqual([30, 20, 10])
  })

  it('shrinks cells spanning a deleted column', () => {
    const spanned = applyTableOperation(grid(2, 3), { kind: 'merge-cells', cellIds: ['c00', 'c01'] }, counter()).model
    const { model, removed } = applyTableOperation(spanned, { kind: 'delete-column', col: 1 }, counter())
    expect(removed).toEqual(['c11'])
    expect(layout(model)).toEqual(['c00@0,0+1x1', 'c02@0,1+1x1', 'c10@1,0+1x1', 'c12@1,1+1x1'])
    expectValid(model)
  })

  it('merges a rectangle of cells into the top left one and splits it again', () => {
    const merged = applyTableOperation(grid(2, 2), { kind: 'merge-cells', cellIds: ['c11', 'c00', 'c01', 'c10'] }, counter())
    expect(layout(merged.model)).toEqual(['c00@0,0+2x2'])
    expect(merged.absorbed).toEqual(['c01', 'c10', 'c11'])

    const split = applyTableOperation(merged.model, { kind: 'split-cell', cellId: 'c00' }, counter())
    expect(layout(split.model)).toEqual(['c00@0,0+1x1', 'n0@0,1+1x1', 'n1@1,0+1x1', 'n2@1,1+1x1'])
    expectValid(split.model)
  })

  it('refuses to merge cells that do not form a rectangle', () => {
    expect(canApplyTableOperation(grid(2, 2), { kind: 'merge-cells', cellIds: ['c00', 'c11'] })).toBe(false)
  })

  it('marks every cell touching a row as header', () => {
    const { model } = applyTableOperation(grid(2, 2), { kind: 'set-header-row', row: 1, header: true }, counter())
    expect(model.cells.map(cell => cell.header)).toEqual([false, false, true, true])
  })

  it('leaves the original model untouched', () => {
    const original = grid(2, 2)
    const before = JSON.stringify(original)
    applyTableOperation(original, { kind: 'split-row', row: 0 }, counter())
    expect(JSON.stringify(original)).toBe(before)
  })
})

describe('moveGridPoint', () => {
  it('moves the grid point everywhere it occurs and ignores unknown points', () => {
    const model = grid(2, 2)
    const moved = moveGridPoint(model, [10, 10], [12, 9])!
    expect(moved.grid!.points[1]![1]).toEqual([12, 9])
    expect(gridHasPoint(model, [10, 10])).toBe(true)
    expect(moveGridPoint(model, [3, 3], [4, 4])).toBeUndefined()
  })

  it('moves add points too', () => {
    const merged = applyTableOperation(grid(3, 1), { kind: 'merge-row-with-next', row: 0 }, counter()).model
    const addPoint = merged.grid!.addPoints[0]!.left[0]!
    const moved = moveGridPoint(merged, addPoint, [1, 20])!
    expect(moved.grid!.addPoints[0]!.left).toEqual([[1, 20]])
  })
})

describe('insertGridAddPoint', () => {
  it('adds the point to the grid segment it lies on', () => {
    const model = insertGridAddPoint(grid(2, 2), [5, 10], [5, 11])!
    expect(model.grid!.addPoints).toEqual([{ row: 1, col: 0, left: [], top: [[5, 11]] }])
    expect(insertGridAddPoint(grid(2, 2), [5, 5], [5, 5])).toBeUndefined()
    expect(insertGridAddPoint(grid(2, 2), [10, 10], [10, 10])).toBeUndefined()
  })

  it('removes add points but refuses grid corners', () => {
    const model = insertGridAddPoint(grid(2, 2), [5, 10], [5, 11])!
    expect(removeGridAddPoints(model, [[5, 11]])!.grid!.addPoints).toEqual([])
    expect(() => removeGridAddPoints(model, [[10, 10]])).toThrow()
  })
})

describe('grid lines', () => {
  it('finds the row or column line a point lies on', () => {
    const model = grid(2, 2)
    expect(gridLineAt(model, [5, 10])).toEqual({ axis: 'row', index: 1 })
    expect(gridLineAt(model, [20, 15])).toEqual({ axis: 'column', index: 2 })
    expect(gridLineAt(model, [5, 5])).toBeUndefined()
  })

  it('lists the corners and add points of a line in order', () => {
    const model = insertGridAddPoint(grid(2, 2), [5, 10], [5, 11])!
    expect(gridLinePoints(model, { axis: 'row', index: 1 })).toEqual([[0, 10], [5, 11], [10, 10], [20, 10]])
    expect(gridLineAt(model, [2.5, 10.5])).toEqual({ axis: 'row', index: 1 })
  })

  it('moves a row line with its add points and leaves the other lines alone', () => {
    const model = insertGridAddPoint(grid(2, 2), [5, 10], [5, 11])!
    const moved = moveGridLine(model, { axis: 'row', index: 1 }, -3)!
    expect(ys(moved)).toEqual([20, 7, 0])
    expect(moved.grid!.addPoints[0]!.top).toEqual([[5, 8]])
    expect(model.grid!.points[1]![0]).toEqual([0, 10])
  })

  it('moves a column line through the transposed grid', () => {
    const moved = moveGridLine(grid(1, 3), { axis: 'column', index: 1 }, 4)!
    expect(moved.grid!.points[0]!.map(point => point[0])).toEqual([0, 14, 20, 30])
    expect(layout(moved)).toEqual(layout(grid(1, 3)))
  })

  it('refuses moves that would cross a neighbouring line or its add points', () => {
    expect(moveGridLine(grid(2, 1), { axis: 'row', index: 1 }, 10)).toBeUndefined()
    expect(moveGridLine(grid(2, 1), { axis: 'row', index: 0 }, -10)).toBeUndefined()
    expect(moveGridLine(grid(2, 1), { axis: 'row', index: 0 }, 5)).toBeDefined()
    const withPoint = insertGridAddPoint(grid(2, 1), [0, 15], [0, 15])!
    expect(moveGridLine(withPoint, { axis: 'row', index: 1 }, 6)).toBeUndefined()
  })
})
