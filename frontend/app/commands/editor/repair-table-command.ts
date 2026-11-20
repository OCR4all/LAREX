import type { Command, CommandContext } from './types'
import { isTextRegion } from '@/models/editor'
import type { Region, TextRegion } from '@/models/editor'
import { findRegionRecursive } from '@/utils/editor/pcgts-editor-primitives'
import { applyTableModel, type TableCell } from '@/utils/editor/table/table-model'
import { planTableRepair } from '@/utils/editor/table/table-repair'
import { validateTable, type TableIssue } from '@/utils/editor/table/table-validation'
import { commitDocument, createCellRegion, invalidateRegionTrees, newRegionId, replaceRegion, resortLinesIntoCells } from './table-command-utils'

export interface RepairTableCommandData {
  tableId: string
}

export class RepairTableCommand implements Command<{ remaining: TableIssue[] } | undefined> {
  private tableId: string
  private original: Region | null = null
  private createdIds: string[] = []

  constructor(data: RepairTableCommandData) {
    this.tableId = data.tableId
  }

  execute(ctx?: CommandContext): { remaining: TableIssue[] } | undefined {
    const session = ctx?.session
    const pcGts = session?.document.value
    if (!session || !pcGts) return undefined

    const table = findRegionRecursive(pcGts.page.regions, this.tableId)?.region
    if (!table || table.kind !== 'TableRegion') return undefined
    const plan = planTableRepair(table)
    if (!plan) throw new Error('This table cannot be repaired automatically')

    const reusable = [...this.createdIds]
    const created: TableCell[] = plan.missing.map(({ row, col }) => ({
      regionId: reusable.shift() ?? newRegionId('cell'),
      row,
      col,
      rowSpan: 1,
      colSpan: 1,
      header: false
    }))
    this.createdIds = created.map(cell => cell.regionId)

    const next: Region = {
      ...table,
      regions: [
        ...(table.regions ?? []).map(child => ({ ...child }) as Region),
        ...created.map(cell => createCellRegion(cell.regionId))
      ]
    }
    applyTableModel(next, { ...plan.model, cells: [...plan.model.cells, ...created] })
    resortLinesIntoCells((next.regions ?? []).filter((child): child is TextRegion =>
      isTextRegion(child) && child.roles?.tableCellRole !== undefined))

    this.original = table
    replaceRegion(pcGts, table.id, next)
    invalidateRegionTrees(ctx, [table, next])
    commitDocument(session, pcGts)
    return { remaining: validateTable(next) }
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
    return 'Repair table'
  }
}
