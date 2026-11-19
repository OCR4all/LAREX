import type { Command, CommandContext } from './types'
import type { Region } from '@/models/editor'
import { findRegionRecursive } from '@/utils/editor/pcgts-editor-primitives'
import { inferTableFromShapes } from '@/utils/editor/table/table-builder'
import { applyTableModel } from '@/utils/editor/table/table-model'
import { commitDocument, invalidateRegionTrees, replaceRegion, shapePoints } from './table-command-utils'

export interface CreateGridFromCellsCommandData {
  tableId: string
}

export class CreateGridFromCellsCommand implements Command<boolean> {
  private tableId: string
  private original: Region | null = null

  constructor(data: CreateGridFromCellsCommandData) {
    this.tableId = data.tableId
  }

  execute(ctx?: CommandContext): boolean {
    const session = ctx?.session
    const pcGts = session?.document.value
    if (!session || !pcGts) return false

    const table = findRegionRecursive(pcGts.page.regions, this.tableId)?.region
    if (!table || table.kind !== 'TableRegion') return false
    if (table.grid?.rows?.length) throw new Error('This table already has a grid')
    const children = (table.regions ?? []).map(child => ({ ...child }) as Region)
    if (children.length === 0) throw new Error('This table has no cells')

    const { model } = inferTableFromShapes(table.id, children.map(child => ({ id: child.id, points: shapePoints(child) })))
    const next: Region = { ...table, regions: children }
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
    return 'Create table grid from cells'
  }
}
