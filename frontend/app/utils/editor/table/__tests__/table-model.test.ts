import { describe, expect, it } from 'vitest'
import { Polygon } from '@/models/editor/geometry'
import type { Region } from '@/models/editor/region'
import {
  applyTableModel,
  buildTableModel,
  findParentTable,
  readGridMatrix,
  syncTableDimensions,
  tableDimensions
} from '../table-model'

function cell(id: string, rowIndex: number, columnIndex: number, extra: Record<string, unknown> = {}): Region {
  return {
    id,
    kind: 'TextRegion',
    coords: new Polygon([[0, 0], [1, 0], [1, 1]], 0.5),
    roles: { tableCellRole: { rowIndex, columnIndex, ...extra } }
  } as Region
}

function table(overrides: Partial<Region> = {}): Region {
  return {
    id: 't1',
    kind: 'TableRegion',
    coords: new Polygon([[0, 0], [20, 0], [20, 20], [0, 20]]),
    grid: {
      rows: [
        { index: 0, points: new Polygon([[0, 0], [10, 0], [20, 0]]) },
        { index: 1, points: new Polygon([[0, 10], [10, 10], [20, 10]]) },
        { index: 2, points: new Polygon([[0, 20], [10, 20], [20, 20]]) }
      ]
    },
    regions: [cell('a', 0, 0, { colSpan: 2, header: true }), cell('b', 1, 0), cell('c', 1, 1)],
    ...overrides
  } as Region
}

describe('readGridMatrix', () => {
  it('orders grid rows by index', () => {
    const shuffled = table()
    shuffled.grid!.rows = [...shuffled.grid!.rows!].reverse()
    expect(readGridMatrix(shuffled)?.[0]).toEqual([[0, 0], [10, 0], [20, 0]])
  })

  it('rejects ragged, gapped and partially indexed grids', () => {
    const ragged = table()
    ragged.grid!.rows![1]!.points = new Polygon([[0, 10], [20, 10]])
    const gapped = table()
    gapped.grid!.rows![2]!.index = 3
    const partial = table()
    partial.grid!.rows![2]!.index = undefined
    expect(readGridMatrix(ragged)).toBeUndefined()
    expect(readGridMatrix(gapped)).toBeUndefined()
    expect(readGridMatrix(partial)).toBeUndefined()
  })
})

describe('buildTableModel', () => {
  it('takes the dimensions from the grid and reads cell roles with defaults', () => {
    const model = buildTableModel(table())
    expect(model.rows).toBe(2)
    expect(model.columns).toBe(2)
    expect(model.cells).toEqual([
      { regionId: 'a', row: 0, col: 0, rowSpan: 1, colSpan: 2, header: true },
      { regionId: 'b', row: 1, col: 0, rowSpan: 1, colSpan: 1, header: false },
      { regionId: 'c', row: 1, col: 1, rowSpan: 1, colSpan: 1, header: false }
    ])
  })

  it('falls back to the cells when there is no grid', () => {
    expect(tableDimensions(table({ grid: undefined }))).toEqual({ rows: 2, columns: 2 })
    expect(buildTableModel(table({ grid: undefined })).grid).toBeUndefined()
  })

  it('falls back to the declared attributes for an empty table', () => {
    const model = buildTableModel(table({ grid: undefined, regions: [], rows: 3, columns: 4 }))
    expect(model.rows).toBe(3)
    expect(model.columns).toBe(4)
  })
})

describe('syncTableDimensions', () => {
  it('corrects drifted rows and columns', () => {
    const drifted = table({ rows: 7, columns: 1 })
    expect(syncTableDimensions(drifted)).toBe(true)
    expect(drifted.rows).toBe(2)
    expect(drifted.columns).toBe(2)
  })

  it('ignores regions that are not tables', () => {
    const region = { ...table(), kind: 'TextRegion' } as Region
    expect(syncTableDimensions(region)).toBe(false)
  })
})

describe('findParentTable', () => {
  it('finds the table a cell belongs to', () => {
    const t = table()
    expect(findParentTable([t], 'b')).toBe(t)
    expect(findParentTable([t], 't1')).toBeUndefined()
  })
})

describe('applyTableModel', () => {
  it('writes the grid, roles and cell outlines back', () => {
    const t = table()
    const model = buildTableModel(t)
    model.grid!.points[1]![1] = [12, 10]
    model.grid!.addPoints.push({ row: 1, col: 1, left: [[13, 15]], top: [] })

    const changed = applyTableModel(t, model)

    expect(changed).toEqual(['t1', 'a', 'b', 'c'])
    expect(t.grid?.rows?.[1]?.points.points).toEqual([[0, 10], [12, 10], [20, 10]])
    expect(t.grid?.addPoints).toEqual([{ row: 1, col: 1, leftPoints: new Polygon([[13, 15]]), topPoints: undefined }])
    const [a, b, c] = t.regions!
    expect(a!.coords.points).toEqual([[0, 0], [10, 0], [20, 0], [20, 10], [12, 10], [0, 10]])
    expect(b!.coords.points).toEqual([[0, 10], [12, 10], [13, 15], [10, 20], [0, 20]])
    expect(c!.coords.points).toEqual([[12, 10], [20, 10], [20, 20], [10, 20], [13, 15]])
    expect(c!.coords.conf).toBe(0.5)
  })

  it('only writes default spans and header when the source had them', () => {
    const t = table()
    applyTableModel(t, buildTableModel(t))
    expect(t.regions![0]!.roles?.tableCellRole).toEqual({ rowIndex: 0, columnIndex: 0, rowSpan: undefined, colSpan: 2, header: true })
    expect(t.regions![1]!.roles?.tableCellRole).toEqual({ rowIndex: 1, columnIndex: 0, rowSpan: undefined, colSpan: undefined, header: undefined })
  })
})

describe('readAddPoints', () => {
  it('reads add points as grid points and treats blank text as no points', () => {
    const t = table()
    t.grid!.addPoints = [{ row: 0, col: 1, leftPoints: new Polygon([[10, 5]]), top: '' }]
    expect(buildTableModel(t).grid?.addPoints).toEqual([{ row: 0, col: 1, left: [[10, 5]], top: [] }])
  })

  it('gives up the grid when add points hold text that is not a point list', () => {
    const t = table()
    t.grid!.addPoints = [{ row: 0, col: 1, left: 'text' }]
    expect(buildTableModel(t).grid).toBeUndefined()
  })
})
