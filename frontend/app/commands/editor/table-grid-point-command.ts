import type { Command, CommandContext } from './types'
import { isTextRegion } from '@/models/editor'
import type { Point, Region, TextRegion } from '@/models/editor'
import { findRegionRecursive } from '@/utils/editor/pcgts-editor-primitives'
import type { GridPoint } from '@/utils/editor/table/table-geometry'
import { applyTableModel, buildTableModel, type TableModel } from '@/utils/editor/table/table-model'
import { insertGridAddPoint, moveGridLine, moveGridPoint, removeGridAddPoints, type GridLineRef } from '@/utils/editor/table/table-structure'
import { commitDocument, invalidateRegionTrees, replaceRegion, resortLinesIntoCells } from './table-command-utils'

export type TableGridPointEdit
  = | { kind: 'move', from: Point, to: Point }
    | { kind: 'insert', at: Point, to: Point }
    | { kind: 'remove', points: Point[] }
    | { kind: 'move-line', line: GridLineRef, delta: number }

export interface TableGridPointCommandData {
  tableId: string
  edit: TableGridPointEdit
}

const tuple = (point: Point): GridPoint => [point.x, point.y]

function editModel(model: TableModel, edit: TableGridPointEdit): TableModel | undefined {
  switch (edit.kind) {
    case 'move': return moveGridPoint(model, tuple(edit.from), tuple(edit.to))
    case 'insert': return insertGridAddPoint(model, tuple(edit.at), tuple(edit.to))
    case 'remove': return removeGridAddPoints(model, edit.points.map(tuple))
    case 'move-line': return moveGridLine(model, edit.line, edit.delta)
  }
}

export class TableGridPointCommand implements Command<boolean> {
  private data: TableGridPointCommandData
  private original: Region | null = null

  constructor(data: TableGridPointCommandData) {
    this.data = data
  }

  execute(ctx?: CommandContext): boolean {
    const session = ctx?.session
    const pcGts = session?.document.value
    if (!session || !pcGts) return false

    const table = findRegionRecursive(pcGts.page.regions, this.data.tableId)?.region
    if (!table || table.kind !== 'TableRegion') return false
    const model = editModel(buildTableModel(table), this.data.edit)
    if (!model) return false

    const next: Region = { ...table, regions: (table.regions ?? []).map(child => ({ ...child }) as Region) }
    applyTableModel(next, model)
    resortLinesIntoCells((next.regions ?? []).filter((child): child is TextRegion =>
      isTextRegion(child) && child.roles?.tableCellRole !== undefined))

    this.original = table
    replaceRegion(pcGts, table.id, next)
    invalidateRegionTrees(ctx, [table, next])
    commitDocument(session, pcGts)
    return true
  }

  undo(ctx?: CommandContext): void {
    const session = ctx?.session
    const pcGts = session?.document.value
    if (!session || !pcGts || !this.original) return
    const current = findRegionRecursive(pcGts.page.regions, this.original.id)?.region
    replaceRegion(pcGts, this.original.id, this.original)
    invalidateRegionTrees(ctx, current ? [current, this.original] : [this.original])
    commitDocument(session, pcGts)
  }

  getDescription(): string {
    switch (this.data.edit.kind) {
      case 'move': return 'Move table grid point'
      case 'insert': return 'Add table grid point'
      case 'remove': return 'Remove table grid points'
      case 'move-line': return this.data.edit.line.axis === 'row' ? 'Move table row line' : 'Move table column line'
    }
  }
}
