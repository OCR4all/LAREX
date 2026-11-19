import type { Command, CommandContext } from './types'
import { isTextRegion } from '@/models/editor'
import type { Region, TextLine, TextRegion } from '@/models/editor'
import { clearLarexRegionLabelMetadata } from '@/utils/editor/page-label-mapping'
import { findRegionRecursive } from '@/utils/editor/pcgts-editor-primitives'
import { assignToShapes, createGridTableModel } from '@/utils/editor/table/table-builder'
import { applyTableModel, readTableCells } from '@/utils/editor/table/table-model'
import { commitDocument, createCellRegion, newRegionId, replaceRegion, shapePoints } from './table-command-utils'

export interface CreateTableGridCommandData {
  regionId: string
  rows: number
  columns: number
  headerRows?: number
  moveTextLines?: boolean
}

const TABLE_ATTRIBUTES = [
  'comments', 'continuation', 'alternativeImages', 'userDefined', 'labels', 'roles', 'orientation',
  'mirrored', 'confidence', 'bgColour', 'embText', 'lineColour', 'lineSeparators'
] as const

function asTableRegion(original: Region): Region {
  if (original.kind === 'TableRegion') return { ...original }
  const table: Record<string, unknown> = { id: original.id, kind: 'TableRegion', coords: original.coords }
  for (const key of TABLE_ATTRIBUTES) {
    const value = (original as unknown as Record<string, unknown>)[key]
    if (value !== undefined) table[key] = value
  }
  const custom = clearLarexRegionLabelMetadata(original.custom)
  if (custom) table.custom = custom
  return table as unknown as Region
}

export class CreateTableGridCommand implements Command<{ id: string, cellIds: string[] } | undefined> {
  private data: CreateTableGridCommandData
  private original: Region | null = null
  private cellIds: string[] = []

  constructor(data: CreateTableGridCommandData) {
    if (!Number.isInteger(data.rows) || !Number.isInteger(data.columns) || data.rows < 1 || data.columns < 1) {
      throw new Error('A table needs at least one row and one column')
    }
    this.data = data
  }

  execute(ctx?: CommandContext): { id: string, cellIds: string[] } | undefined {
    const session = ctx?.session
    const pcGts = session?.document.value
    if (!session || !pcGts) return undefined

    const original = findRegionRecursive(pcGts.page.regions, this.data.regionId)?.region
    if (!original) return undefined
    if (original.kind === 'TableRegion' && readTableCells(original).length > 0) {
      throw new Error('This table already has cells')
    }

    const generated = this.cellIds.length > 0 ? [...this.cellIds] : []
    const model = createGridTableModel({
      tableId: original.id,
      outline: shapePoints(original),
      rows: this.data.rows,
      columns: this.data.columns,
      headerRows: this.data.headerRows ?? 0,
      newCellId: (row, col) => generated[row * this.data.columns + col] ?? newRegionId('cell')
    })
    this.cellIds = model.cells.map(cell => cell.regionId)

    const cells = this.cellIds.map(id => createCellRegion(id))
    const table = asTableRegion(original)
    table.regions = [...(original.regions ?? []), ...cells]
    applyTableModel(table, model)

    if (isTextRegion(original) && (this.data.moveTextLines ?? true)) {
      distributeTextLines(original.textLines ?? [], cells)
    }

    this.original = original
    replaceRegion(pcGts, original.id, table)
    commitDocument(session, pcGts)
    return { id: original.id, cellIds: [...this.cellIds] }
  }

  undo(ctx?: CommandContext): void {
    const session = ctx?.session
    const pcGts = session?.document.value
    if (!session || !pcGts || !this.original) return
    replaceRegion(pcGts, this.original.id, this.original)
    commitDocument(session, pcGts)
  }

  getDescription(): string {
    return `Create ${this.data.rows}×${this.data.columns} table grid`
  }
}

export function distributeTextLines(textLines: TextLine[], cells: TextRegion[]): number {
  const assignment = assignToShapes(
    textLines.map(line => ({ id: line.id, points: shapePoints(line) })),
    cells.map(cell => ({ id: cell.id, points: shapePoints(cell) })),
    true
  )
  const byId = new Map(cells.map(cell => [cell.id, cell]))
  let moved = 0
  for (const line of textLines) {
    const cell = byId.get(assignment.get(line.id) ?? '')
    if (!cell) continue
    cell.textLines = [...(cell.textLines ?? []), line]
    moved++
  }
  return moved
}
