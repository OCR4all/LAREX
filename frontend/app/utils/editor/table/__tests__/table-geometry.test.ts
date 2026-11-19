import { describe, expect, it } from 'vitest'
import { cellPolygonFromGrid, columnLine, interpolateGrid, rowLine, sharesRowOrColumn, type GridMatrix } from '../table-geometry'

const grid: GridMatrix = [
  [[0, 0], [10, 0], [20, 0]],
  [[0, 10], [10, 10], [20, 10]],
  [[0, 20], [10, 20], [20, 20]]
]

describe('interpolateGrid', () => {
  it('spaces grid points evenly in a rectangle', () => {
    expect(interpolateGrid([[0, 0], [20, 0], [20, 20], [0, 20]], 2, 2)).toEqual(grid)
  })

  it('follows a skewed quadrilateral', () => {
    const skewed = interpolateGrid([[0, 0], [20, 4], [24, 24], [4, 20]], 1, 2)
    expect(skewed).toEqual([
      [[0, 0], [10, 2], [20, 4]],
      [[4, 20], [14, 22], [24, 24]]
    ])
  })
})

describe('cellPolygonFromGrid', () => {
  it('outlines a single cell clockwise', () => {
    expect(cellPolygonFromGrid(grid, [], { row: 1, col: 0, rowSpan: 1, colSpan: 1 }))
      .toEqual([[0, 10], [10, 10], [10, 20], [0, 20]])
  })

  it('skips interior grid points of a merged cell', () => {
    expect(cellPolygonFromGrid(grid, [], { row: 0, col: 0, rowSpan: 2, colSpan: 2 }))
      .toEqual([[0, 0], [10, 0], [20, 0], [20, 10], [20, 20], [10, 20], [0, 20], [0, 10]])
  })

  it('bends the outline through add points on every side', () => {
    const addPoints = [
      { row: 0, col: 0, left: [[1, 5] as [number, number]], top: [[6, 1], [3, -1]] as [number, number][] },
      { row: 0, col: 1, left: [[11, 5] as [number, number]], top: [] },
      { row: 1, col: 0, left: [], top: [[5, 11] as [number, number]] }
    ]
    expect(cellPolygonFromGrid(grid, addPoints, { row: 0, col: 0, rowSpan: 1, colSpan: 1 }))
      .toEqual([[0, 0], [3, -1], [6, 1], [10, 0], [11, 5], [10, 10], [5, 11], [0, 10], [1, 5]])
  })
})

describe('grid lines', () => {
  const addPoints = [{ row: 1, col: 0, left: [[1, 15] as [number, number]], top: [[5, 9] as [number, number]] }]

  it('traces a row separator with its add points', () => {
    expect(rowLine(grid, addPoints, 1)).toEqual([[0, 10], [5, 9], [10, 10], [20, 10]])
  })

  it('traces a column separator with its add points', () => {
    expect(columnLine(grid, addPoints, 0)).toEqual([[0, 0], [0, 10], [1, 15], [0, 20]])
  })
})

describe('sharesRowOrColumn', () => {
  const merged = { row: 0, col: 0, rowSpan: 2, colSpan: 1 }

  it('matches cells whose rows or columns overlap, including spans', () => {
    expect(sharesRowOrColumn(merged, { row: 1, col: 3, rowSpan: 1, colSpan: 1 })).toBe(true)
    expect(sharesRowOrColumn(merged, { row: 4, col: 0, rowSpan: 1, colSpan: 1 })).toBe(true)
  })

  it('rejects cells in other rows and columns', () => {
    expect(sharesRowOrColumn(merged, { row: 2, col: 1, rowSpan: 1, colSpan: 1 })).toBe(false)
  })
})
