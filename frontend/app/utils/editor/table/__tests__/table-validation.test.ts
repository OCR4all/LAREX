import { describe, expect, it } from 'vitest'
import { Polygon } from '@/models/editor/geometry'
import type { Region } from '@/models/editor/region'
import { validateTable, validateTables } from '../table-validation'

function cell(id: string, rowIndex?: number, columnIndex?: number, extra: Record<string, unknown> = {}): Region {
  return {
    id,
    kind: 'TextRegion',
    coords: new Polygon([[0, 0], [1, 0], [1, 1]]),
    roles: { tableCellRole: { rowIndex, columnIndex, ...extra } }
  } as Region
}

function gridRows(rows: number, columns: number) {
  return Array.from({ length: rows + 1 }, (_, r) => ({
    index: r,
    points: new Polygon(Array.from({ length: columns + 1 }, (_, c) => [c * 10, r * 10] as [number, number]))
  }))
}

function table(regions: Region[], overrides: Partial<Region> = {}): Region {
  return {
    id: 't1',
    kind: 'TableRegion',
    coords: new Polygon([[0, 0], [20, 0], [20, 20], [0, 20]]),
    grid: { rows: gridRows(2, 2) },
    regions,
    ...overrides
  } as Region
}

const codes = (region: Region) => validateTable(region).map(issue => issue.code)

describe('validateTable', () => {
  it('accepts a complete table', () => {
    expect(validateTable(table([cell('a', 0, 0), cell('b', 0, 1), cell('c', 1, 0, { colSpan: 2 })], { rows: 2, columns: 2 }))).toEqual([])
  })

  it('reports broken grids', () => {
    const tooShort = table([], { grid: { rows: gridRows(2, 2).slice(0, 1) } })
    const ragged = table([], { grid: { rows: [...gridRows(1, 2).slice(0, 1), ...gridRows(1, 1).slice(1)] } })
    const narrow = table([], { grid: { rows: gridRows(1, 0) } })
    const misindexed = table([], { grid: { rows: gridRows(1, 1).map(row => ({ ...row, index: row.index + 1 })) } })
    expect(codes(tooShort)).toContain('grid-too-few-rows')
    expect(codes(ragged)).toContain('grid-ragged')
    expect(codes(narrow)).toContain('grid-too-few-columns')
    expect(codes(misindexed)).toContain('grid-row-indices')
  })

  it('reports add points that are malformed or outside the grid', () => {
    expect(codes(table([], { grid: { rows: gridRows(2, 2), addPoints: [{ row: 0, col: 0, left: 'nope' }] } })))
      .toContain('add-points-invalid')
    expect(codes(table([], { grid: { rows: gridRows(2, 2), addPoints: [{ row: 3, col: 0, leftPoints: new Polygon([[1, 1]]) }] } })))
      .toContain('add-points-out-of-range')
  })

  it('warns when the declared dimensions drift from the grid', () => {
    expect(codes(table([], { rows: 3, columns: 2 }))).toEqual(['dimension-mismatch'])
  })

  it('reports cells with missing, incomplete or invalid roles', () => {
    const noRole = { ...cell('x', 0, 0), roles: undefined } as Region
    const issues = codes(table([noRole, cell('y', 0), cell('z', 0, 0, { rowSpan: 0 }), cell('w', 2, 0)]))
    expect(issues).toEqual(expect.arrayContaining(['cell-missing-role', 'cell-incomplete-role', 'cell-invalid-span', 'cell-index-out-of-range']))
  })

  it('reports each overlapping pair once and the positions nobody covers', () => {
    const issues = validateTable(table([cell('a', 0, 0, { rowSpan: 2, colSpan: 2 }), cell('b', 1, 1)]))
    expect(issues.filter(issue => issue.code === 'cell-overlap')).toEqual([
      { code: 'cell-overlap', severity: 'error', tableId: 't1', regionIds: ['a', 'b'], row: 1, col: 1 }
    ])
    expect(codes(table([cell('a', 0, 0)])).filter(code => code === 'uncovered-position')).toHaveLength(3)
  })

  it('does not report uncovered positions for a table without cells', () => {
    expect(codes(table([]))).toEqual([])
  })
})

describe('validateTables', () => {
  it('flags cell roles outside a table and validates nested tables', () => {
    const stray = cell('stray', 0, 0)
    const nested = table([cell('n', 0, 0)], { id: 'inner' })
    const outer = table([{ ...cell('host', 0, 0), regions: [nested] } as Region], { id: 'outer', grid: undefined })
    const issues = validateTables([stray, outer])
    expect(issues).toContainEqual({ code: 'role-outside-table', severity: 'warning', regionIds: ['stray'] })
    expect(issues.some(issue => issue.tableId === 'inner' && issue.code === 'uncovered-position')).toBe(true)
  })
})
