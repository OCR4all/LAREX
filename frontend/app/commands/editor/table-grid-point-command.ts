import type { Command, CommandContext } from './types'
import type { Point, Region } from '@/models/editor'
import { findRegionRecursive } from '@/utils/editor/pcgts-editor-primitives'
import type { GridPoint } from '@/utils/editor/table/table-geometry'
import { applyTableModel, buildTableModel, type TableModel } from '@/utils/editor/table/table-model'
import { insertGridAddPoint, moveGridPoint, removeGridAddPoints } from '@/utils/editor/table/table-structure'
import { commitDocument, invalidateRegionTrees, replaceRegion } from './table-command-utils'

export type TableGridPointEdit
  = | { kind: 'move', from: Point, to: Point }
    | { kind: 'insert', at: Point, to: Point }
    | { kind: 'remove', points: Point[] }

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
    }
  }
}
