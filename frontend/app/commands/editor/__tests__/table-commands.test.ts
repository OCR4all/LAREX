import { describe, expect, it, vi } from 'vitest'
import { Polygon, TextLine } from '@/models/editor'
import type { Region, TextRegion } from '@/models/editor'
import { buildTableModel } from '@/utils/editor/table/table-model'
import { validateTable } from '@/utils/editor/table/table-validation'
import { AssignTextLinesToCellsCommand } from '../assign-text-lines-to-cells-command'
import { BuildTableFromRegionsCommand } from '../build-table-from-regions-command'
import { CreateTableGridCommand } from '../create-table-grid-command'
import { TableStructureCommand } from '../table-structure-command'
import { TableGridPointCommand } from '../table-grid-point-command'
import { CreateGridFromCellsCommand } from '../create-grid-from-cells-command'
import { MoveElementCommand } from '../move-element-command'
import { RepairTableCommand } from '../repair-table-command'
import { createMockSession, createTestContext, createTestDocument, createTestReadingOrder, findRegionById } from './test-utils'

vi.mock('@/stores/editor/editor.ui.store', () => ({ useEditorUiStore: () => ({ bumpReadingOrderVersion: vi.fn() }) }))

function rect(left: number, top: number, right: number, bottom: number): [number, number][] {
  return [[left, top], [right, top], [right, bottom], [left, bottom]]
}

function line(id: string, left: number, top: number, right: number, bottom: number): TextLine {
  return new TextLine({ id, coords: new Polygon(rect(left, top, right, bottom)) })
}

function textRegion(id: string, points: [number, number][], textLines: TextLine[] = []): TextRegion {
  return { id, kind: 'TextRegion', coords: new Polygon(points), regions: [], textLines, textContentVariants: [] }
}

function setup(regions: Region[]) {
  const mock = createMockSession(createTestDocument({ regions }))
  return { ...mock, ctx: createTestContext(mock.session) }
}

function region(get: () => { page: { regions: Region[] } } | null, id: string): Region {
  const found = findRegionById(get()!.page.regions, id)
  if (!found) throw new Error(`missing ${id}`)
  return found
}

function lineIds(target: Region): string[] {
  return ((target as TextRegion).textLines ?? []).map(textLine => textLine.id)
}

describe('CreateTableGridCommand', () => {
  it('turns a text region into a grid table and moves its lines into the cells', () => {
    const original = textRegion('r', rect(0, 20, 30, 0), [line('top', 1, 18, 9, 12), line('bottom', 21, 8, 29, 2)])
    const { ctx, getDocument } = setup([original])

    const result = new CreateTableGridCommand({ regionId: 'r', rows: 2, columns: 3, headerRows: 1 }).execute(ctx)!

    const table = region(getDocument, 'r')
    expect(table.kind).toBe('TableRegion')
    const model = buildTableModel(table)!
    expect([model.rows, model.columns, model.cells.length]).toEqual([2, 3, 6])
    expect(model.cells.filter(cell => cell.header)).toHaveLength(3)
    expect(validateTable(table).filter(issue => issue.severity === 'error')).toEqual([])
    expect(lineIds(region(getDocument, result.cellIds[0]!))).toEqual(['top'])
    expect(lineIds(region(getDocument, result.cellIds[5]!))).toEqual(['bottom'])
    expect((table as TextRegion).textLines).toBeUndefined()
  })

  it('keeps the lines on the table when asked not to move them', () => {
    const { ctx, getDocument } = setup([textRegion('r', rect(0, 20, 30, 0), [line('l', 1, 18, 9, 12)])])
    const result = new CreateTableGridCommand({ regionId: 'r', rows: 1, columns: 2, moveTextLines: false }).execute(ctx)!
    expect(result.cellIds.flatMap(id => lineIds(region(getDocument, id)))).toEqual([])
  })

  it('restores the original region on undo and reuses cell ids on redo', () => {
    const original = textRegion('r', rect(0, 20, 30, 0), [line('l', 1, 18, 9, 12)])
    const { ctx, getDocument } = setup([original])
    const command = new CreateTableGridCommand({ regionId: 'r', rows: 2, columns: 2 })

    const first = command.execute(ctx)!
    command.undo(ctx)
    expect(region(getDocument, 'r')).toBe(original)
    expect(lineIds(original)).toEqual(['l'])

    const second = command.execute(ctx)!
    expect(second.cellIds).toEqual(first.cellIds)
  })

  it('refuses tables that already have cells and invalid sizes', () => {
    const { ctx } = setup([textRegion('r', rect(0, 20, 30, 0))])
    new CreateTableGridCommand({ regionId: 'r', rows: 1, columns: 2 }).execute(ctx)
    expect(() => new CreateTableGridCommand({ regionId: 'r', rows: 1, columns: 2 }).execute(ctx)).toThrow()
    expect(() => new CreateTableGridCommand({ regionId: 'r', rows: 0, columns: 2 })).toThrow()
  })
})

describe('BuildTableFromRegionsCommand', () => {
  const cells = () => [
    textRegion('a', rect(0, 20, 10, 10)),
    textRegion('b', rect(10, 20, 20, 10)),
    textRegion('c', rect(0, 10, 10, 0)),
    textRegion('d', rect(10, 10, 20, 0))
  ]

  it('wraps the regions in a table at the position of the first one', () => {
    const { ctx, getDocument } = setup([textRegion('before', rect(0, 40, 20, 30)), ...cells(), textRegion('after', rect(0, -5, 20, -10))])

    const { id } = new BuildTableFromRegionsCommand({ regionIds: ['a', 'b', 'c', 'd'] }).execute(ctx)!

    expect(getDocument()!.page.regions.map(r => r.id)).toEqual(['before', id, 'after'])
    const table = region(getDocument, id)
    const model = buildTableModel(table)!
    expect([model.rows, model.columns]).toEqual([2, 2])
    expect(model.cells.map(cell => [cell.regionId, cell.row, cell.col])).toEqual([
      ['a', 0, 0], ['b', 0, 1], ['c', 1, 0], ['d', 1, 1]
    ])
    expect(validateTable(table).filter(issue => issue.severity === 'error')).toEqual([])
  })

  it('restores the regions on undo', () => {
    const originals = cells()
    const { ctx, getDocument } = setup(originals)
    const command = new BuildTableFromRegionsCommand({ regionIds: ['a', 'b', 'c', 'd'] })
    command.execute(ctx)
    command.undo(ctx)
    expect(getDocument()!.page.regions).toEqual(originals)
    expect(getDocument()!.page.regions[0]).toBe(originals[0])
  })

  it('rejects regions with different parents', () => {
    const parent = textRegion('p', rect(0, 50, 50, 30))
    parent.regions = [textRegion('nested', rect(1, 49, 10, 40))]
    const { ctx } = setup([parent, ...cells()])
    expect(() => new BuildTableFromRegionsCommand({ regionIds: ['a', 'nested'] }).execute(ctx)).toThrow()
  })
})

describe('AssignTextLinesToCellsCommand', () => {
  function tableWithLines() {
    const loose = textRegion('loose', rect(0, 20, 20, 0), [line('l1', 1, 18, 9, 12), line('far', 40, 18, 49, 12)])
    const { ctx, getDocument } = setup([loose, textRegion('t', rect(0, 20, 20, 0))])
    const { cellIds } = new CreateTableGridCommand({ regionId: 't', rows: 2, columns: 2 }).execute(ctx)!
    const misplaced = region(getDocument, cellIds[0]!) as TextRegion
    misplaced.textLines = [line('l2', 11, 8, 19, 2), line('l3', 1, 15, 9, 13)]
    return { ctx, getDocument, cellIds }
  }

  it('moves lines inside the table into the cell containing them', () => {
    const { ctx, getDocument, cellIds } = tableWithLines()

    const { moved } = new AssignTextLinesToCellsCommand({ tableId: 't' }).execute(ctx)

    expect(moved).toBe(2)
    expect(lineIds(region(getDocument, cellIds[0]!))).toEqual(['l1', 'l3'])
    expect(lineIds(region(getDocument, cellIds[3]!))).toEqual(['l2'])
    expect(lineIds(region(getDocument, 'loose'))).toEqual(['far'])
  })

  it('restores every touched region on undo', () => {
    const { ctx, getDocument, cellIds } = tableWithLines()
    const command = new AssignTextLinesToCellsCommand({ tableId: 't' })
    command.execute(ctx)
    command.undo(ctx)
    expect(lineIds(region(getDocument, cellIds[0]!))).toEqual(['l2', 'l3'])
    expect(lineIds(region(getDocument, cellIds[3]!))).toEqual([])
    expect(lineIds(region(getDocument, 'loose'))).toEqual(['l1', 'far'])
  })
})

describe('TableStructureCommand', () => {
  function gridTable(rows: number, columns: number) {
    const mock = setup([textRegion('t', rect(0, rows * 10, columns * 10, 0))])
    const { cellIds } = new CreateTableGridCommand({ regionId: 't', rows, columns }).execute(mock.ctx)!
    return { ...mock, cellIds }
  }

  it('splits a row and moves lines below the new line into the new cells', () => {
    const { ctx, getDocument, cellIds } = gridTable(1, 2)
    const cell = region(getDocument, cellIds[0]!) as TextRegion
    cell.textLines = [line('upper', 1, 9, 9, 7), line('lower', 1, 3, 9, 1)]

    const { created } = new TableStructureCommand({ tableId: 't', operation: { kind: 'split-row', row: 0 } }).execute(ctx)!

    const table = region(getDocument, 't')
    expect(buildTableModel(table).rows).toBe(2)
    expect(lineIds(region(getDocument, cellIds[0]!))).toEqual(['upper'])
    expect(lineIds(region(getDocument, created[0]!))).toEqual(['lower'])
    expect(validateTable(table)).toEqual([])
  })

  it('keeps the lines of merged cells and restores everything on undo', () => {
    const { ctx, getDocument, cellIds } = gridTable(1, 2)
    const right = region(getDocument, cellIds[1]!) as TextRegion
    right.textLines = [line('r', 11, 8, 19, 2)]
    const before = region(getDocument, 't')
    const command = new TableStructureCommand({ tableId: 't', operation: { kind: 'merge-cells', cellIds } })

    command.execute(ctx)
    const table = region(getDocument, 't')
    expect(table.regions!.map(child => child.id)).toEqual([cellIds[0]])
    expect(lineIds(table.regions![0]!)).toEqual(['r'])

    command.undo(ctx)
    expect(region(getDocument, 't')).toBe(before)
    expect(lineIds(region(getDocument, cellIds[1]!))).toEqual(['r'])
  })

  it('drops deleted cells from the reading order and restores them on undo', () => {
    const { ctx, getDocument, cellIds } = gridTable(2, 1)
    getDocument()!.page.readingOrder = createTestReadingOrder(['t', ...cellIds])
    const command = new TableStructureCommand({ tableId: 't', operation: { kind: 'delete-row', row: 0 } })

    command.execute(ctx)
    const refs = () => JSON.stringify(getDocument()!.page.readingOrder)
    expect(refs()).not.toContain(cellIds[0]!)
    expect(refs()).toContain(cellIds[1]!)

    command.undo(ctx)
    expect(refs()).toContain(cellIds[0]!)
  })

  it('reuses created cell ids on redo', () => {
    const { ctx } = gridTable(1, 1)
    const command = new TableStructureCommand({ tableId: 't', operation: { kind: 'split-column', col: 0 } })
    const first = command.execute(ctx)!
    command.undo(ctx)
    expect(command.execute(ctx)!.created).toEqual(first.created)
  })
})

describe('TableGridPointCommand', () => {
  function gridTable() {
    const mock = setup([textRegion('t', rect(0, 20, 20, 0))])
    const { cellIds } = new CreateTableGridCommand({ regionId: 't', rows: 2, columns: 2 }).execute(mock.ctx)!
    return { ...mock, cellIds }
  }

  it('moves a shared grid point in every cell and the table outline', () => {
    const { ctx, getDocument, cellIds } = gridTable()
    new TableGridPointCommand({ tableId: 't', edit: { kind: 'move', from: { x: 20, y: 10 }, to: { x: 22, y: 10 } } }).execute(ctx)
    const has = (id: string) => region(getDocument, id).coords.points.some(([x, y]) => x === 22 && y === 10)
    expect([has('t'), has(cellIds[1]!), has(cellIds[3]!), has(cellIds[0]!)]).toEqual([true, true, true, false])
  })

  it('inserts a point on a shared edge and removes it again, but never removes corners', () => {
    const { ctx, getDocument, cellIds } = gridTable()
    new TableGridPointCommand({ tableId: 't', edit: { kind: 'insert', at: { x: 10, y: 15 }, to: { x: 11, y: 15 } } }).execute(ctx)
    const has = (id: string) => region(getDocument, id).coords.points.some(([x, y]) => x === 11 && y === 15)
    expect([has(cellIds[0]!), has(cellIds[1]!), has(cellIds[2]!)]).toEqual([true, true, false])

    new TableGridPointCommand({ tableId: 't', edit: { kind: 'remove', points: [{ x: 11, y: 15 }] } }).execute(ctx)
    expect(has(cellIds[0]!)).toBe(false)
    expect(() => new TableGridPointCommand({ tableId: 't', edit: { kind: 'remove', points: [{ x: 10, y: 10 }] } }).execute(ctx)).toThrow()
  })

  it('moves a whole row line, re-sorts lines into their new cells and undoes it', () => {
    const { ctx, getDocument } = setup([textRegion('t', rect(0, 20, 20, 0), [line('low', 1, 9, 9, 7)])])
    const { cellIds } = new CreateTableGridCommand({ regionId: 't', rows: 2, columns: 2 }).execute(ctx)!
    expect(lineIds(region(getDocument, cellIds[2]!))).toEqual(['low'])

    const command = new TableGridPointCommand({ tableId: 't', edit: { kind: 'move-line', line: { axis: 'row', index: 1 }, delta: -4 } })
    expect(command.execute(ctx)).toBe(true)
    expect(region(getDocument, cellIds[1]!).coords.points.map(([, y]) => y)).toEqual([20, 20, 6, 6])
    expect(lineIds(region(getDocument, cellIds[0]!))).toEqual(['low'])
    expect(lineIds(region(getDocument, cellIds[2]!))).toEqual([])

    command.undo(ctx)
    expect(lineIds(region(getDocument, cellIds[2]!))).toEqual(['low'])
    expect(new TableGridPointCommand({ tableId: 't', edit: { kind: 'move-line', line: { axis: 'row', index: 1 }, delta: 12 } }).execute(ctx)).toBe(false)
  })
})

describe('CreateGridFromCellsCommand', () => {
  it('infers a grid for a table that only has cells and restores it on undo', () => {
    const table = { id: 't', kind: 'TableRegion', coords: new Polygon(rect(0, 20, 20, 0)), regions: [
      textRegion('a', rect(0, 20, 10, 10)), textRegion('b', rect(10, 20, 20, 10)), textRegion('c', rect(0, 10, 20, 0))
    ] } as Region
    const { ctx, getDocument } = setup([table])
    const command = new CreateGridFromCellsCommand({ tableId: 't' })

    command.execute(ctx)
    const model = buildTableModel(region(getDocument, 't'))
    expect(model.grid).toBeDefined()
    expect(model.cells.map(cell => [cell.regionId, cell.row, cell.col, cell.colSpan])).toEqual([['a', 0, 0, 1], ['b', 0, 1, 1], ['c', 1, 0, 2]])

    command.undo(ctx)
    expect(region(getDocument, 't')).toBe(table)
  })
})

describe('MoveElementCommand on tables', () => {
  it('moves cells, their lines and the grid with the table, and undoes all of it', () => {
    const { ctx, getDocument } = setup([textRegion('t', rect(0, 20, 20, 0), [line('l', 1, 18, 9, 12)])])
    const { cellIds } = new CreateTableGridCommand({ regionId: 't', rows: 2, columns: 2 }).execute(ctx)!
    const command = new MoveElementCommand({ elementId: 't', elementType: 'polygon', delta: { x: 5, y: -1 } })

    command.execute(ctx)
    const table = region(getDocument, 't')
    expect(buildTableModel(table).grid!.points[0]![0]).toEqual([5, 19])
    expect(region(getDocument, cellIds[0]!).coords.points[0]).toEqual([5, 19])
    expect((region(getDocument, cellIds[0]!) as TextRegion).textLines![0]!.coords.points[0]).toEqual([6, 17])
    expect(validateTable(table)).toEqual([])

    command.undo(ctx)
    expect(buildTableModel(region(getDocument, 't')).grid!.points[0]![0]).toEqual([0, 20])
    expect(region(getDocument, cellIds[0]!).coords.points[0]).toEqual([0, 20])
  })
})

describe('RepairTableCommand', () => {
  function brokenTable() {
    const { ctx, getDocument } = setup([textRegion('t', rect(0, 20, 20, 0), [line('l', 11, 9, 19, 7)])])
    const { cellIds } = new CreateTableGridCommand({ regionId: 't', rows: 2, columns: 2 }).execute(ctx)!
    const table = region(getDocument, 't')
    const [a, b, c, d] = cellIds.map(id => region(getDocument, id)) as TextRegion[]
    a!.roles = { tableCellRole: { rowIndex: 0, columnIndex: 0, rowSpan: 0 } }
    b!.coords = new Polygon(rect(10, 21, 20, 10))
    c!.roles = undefined
    d!.textLines = []
    ;(a as TextRegion).textLines = [line('l', 11, 9, 19, 7)]
    table.regions = [a!, b!, c!, d!].filter(cell => cell!.id !== cellIds[3])
    table.rows = 5
    return { ctx, getDocument, cellIds, table }
  }

  it('fixes spans, roles, missing cells, geometry, dimensions and misplaced lines, and undoes it', () => {
    const { ctx, getDocument, cellIds, table } = brokenTable()
    expect(validateTable(table).map(issue => issue.code)).toEqual(expect.arrayContaining(['dimension-mismatch', 'cell-missing-role', 'cell-invalid-span', 'uncovered-position', 'line-crosses-cell']))

    const command = new RepairTableCommand({ tableId: 't' })
    expect(command.execute(ctx)!.remaining).toEqual([])
    const repaired = region(getDocument, 't')
    const model = buildTableModel(repaired)
    expect(model.cells.map(cell => [cell.row, cell.col])).toEqual([[0, 0], [0, 1], [1, 0], [1, 1]])
    expect(model.cells.find(cell => cell.row === 1 && cell.col === 0)!.regionId).toBe(cellIds[2])
    const created = model.cells.find(cell => cell.row === 1 && cell.col === 1)!.regionId
    expect(lineIds(region(getDocument, created))).toEqual(['l'])

    command.undo(ctx)
    expect(region(getDocument, 't')).toBe(table)
    command.execute(ctx)
    expect(buildTableModel(region(getDocument, 't')).cells.find(cell => cell.row === 1 && cell.col === 1)!.regionId).toBe(created)
  })
})
