import { describe, expect, it } from 'vitest'
import { Polygon, TextLine } from '@/models/editor'
import type { Region, TextRegion } from '@/models/editor'
import { buildTableModel } from '@/utils/editor/table/table-model'
import { validateTable } from '@/utils/editor/table/table-validation'
import { AssignTextLinesToCellsCommand } from '../assign-text-lines-to-cells-command'
import { BuildTableFromRegionsCommand } from '../build-table-from-regions-command'
import { CreateTableGridCommand } from '../create-table-grid-command'
import { createMockSession, createTestContext, createTestDocument, findRegionById } from './test-utils'

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
