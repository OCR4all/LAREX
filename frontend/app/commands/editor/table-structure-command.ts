import type { Command, CommandContext } from './types'
import { isTextRegion } from '@/models/editor'
import type { ReadingOrder, Region, Relation, TextLine, TextRegion } from '@/models/editor'
import { useEditorUiStore } from '@/stores/editor/editor.ui.store'
import { findRegionRecursive } from '@/utils/editor/pcgts-editor-primitives'
import { cloneRelations, removeRelationsReferencingIds } from '@/utils/editor/relations'
import { assignToShapes } from '@/utils/editor/table/table-builder'
import { applyTableModel, buildTableModel } from '@/utils/editor/table/table-model'
import { applyTableOperation, type TableOperation } from '@/utils/editor/table/table-structure'
import { cloneReadingOrder, removeIdsFromReadingOrder } from './reading-order-utils'
import {
  commitDocument,
  compareLinesTopToBottom,
  createCellRegion,
  invalidateRegionTrees,
  newRegionId,
  replaceRegion,
  shapePoints
} from './table-command-utils'

export interface TableStructureCommandData {
  tableId: string
  operation: TableOperation
}

const DESCRIPTIONS: Record<TableOperation['kind'], string> = {
  'split-row': 'Split table row',
  'split-column': 'Split table column',
  'merge-row-with-next': 'Merge table rows',
  'merge-column-with-next': 'Merge table columns',
  'delete-row': 'Delete table row',
  'delete-column': 'Delete table column',
  'merge-cells': 'Merge table cells',
  'split-cell': 'Split table cell',
  'set-header-row': 'Change table header row'
}

function redistributeLines(sources: TextRegion[], cells: TextRegion[]): void {
  const lines = sources.flatMap(source => source.textLines ?? [])
  if (lines.length === 0 || cells.length === 0) return
  const assignment = assignToShapes(
    lines.map(line => ({ id: line.id, points: shapePoints(line) })),
    cells.map(cell => ({ id: cell.id, points: shapePoints(cell) })),
    true
  )
  const received = new Map<string, TextLine[]>()
  for (const line of lines) {
    const target = assignment.get(line.id)!
    received.set(target, [...(received.get(target) ?? []), line])
  }
  const sourceIds = new Set(sources.map(source => source.id))
  for (const cell of cells) {
    const kept = sourceIds.has(cell.id) ? [] : cell.textLines ?? []
    const incoming = received.get(cell.id) ?? []
    if (incoming.length === 0 && !sourceIds.has(cell.id)) continue
    cell.textLines = [...kept, ...incoming].sort(compareLinesTopToBottom)
  }
}

export class TableStructureCommand implements Command<{ created: string[] } | undefined> {
  private data: TableStructureCommandData
  private original: Region | null = null
  private createdIds: string[] = []
  private previousReadingOrder?: ReadingOrder
  private previousRelations?: Relation[]
  private referencesChanged = false

  constructor(data: TableStructureCommandData) {
    this.data = data
  }

  execute(ctx?: CommandContext): { created: string[] } | undefined {
    const session = ctx?.session
    const pcGts = session?.document.value
    if (!session || !pcGts) return undefined

    const table = findRegionRecursive(pcGts.page.regions, this.data.tableId)?.region
    if (!table || table.kind !== 'TableRegion') return undefined
    const model = buildTableModel(table)
    if (!model.grid) throw new Error('Only tables with a grid can be restructured')

    const reusable = [...this.createdIds]
    const result = applyTableOperation(model, this.data.operation, () => reusable.shift() ?? newRegionId('cell'))
    this.createdIds = result.created

    const removed = new Set(result.removed)
    const absorbed = new Set(result.absorbed)
    const children = (table.regions ?? []).map(child => ({ ...child }) as Region)
    const sources = children.filter((child): child is TextRegion =>
      isTextRegion(child) && (absorbed.has(child.id) || result.redistribute.includes(child.id)))

    const next: Region = {
      ...table,
      regions: [
        ...children.filter(child => !removed.has(child.id) && !absorbed.has(child.id)),
        ...result.created.map(id => createCellRegion(id))
      ]
    }
    applyTableModel(next, result.model)
    const cells = (next.regions ?? []).filter((child): child is TextRegion =>
      isTextRegion(child) && child.roles?.tableCellRole !== undefined)
    redistributeLines(sources, cells)

    this.original = table
    this.updateReferences(pcGts.page, table, removed, absorbed)
    replaceRegion(pcGts, table.id, next)
    invalidateRegionTrees(ctx, [table, next])
    commitDocument(session, pcGts)
    return { created: [...result.created] }
  }

  private updateReferences(page: { readingOrder?: ReadingOrder, relations?: Relation[] }, table: Region, removed: Set<string>, absorbed: Set<string>): void {
    const ids = new Set([...removed, ...absorbed])
    for (const child of table.regions ?? []) {
      if (removed.has(child.id) && isTextRegion(child)) child.textLines?.forEach(line => ids.add(line.id))
    }
    this.referencesChanged = false
    if (ids.size === 0) return

    this.previousReadingOrder = cloneReadingOrder(page.readingOrder)
    this.previousRelations = cloneRelations(page.relations)
    const readingOrderChanged = page.readingOrder?.root?.elements
      ? removeIdsFromReadingOrder(page.readingOrder.root.elements, ids)
      : false
    const relations = removeRelationsReferencingIds(page.relations, ids)
    const relationsChanged = (page.relations?.length ?? 0) !== (relations?.length ?? 0)
    if (relationsChanged) page.relations = relations
    this.referencesChanged = readingOrderChanged || relationsChanged
    if (readingOrderChanged) useEditorUiStore().bumpReadingOrderVersion()
  }

  undo(ctx?: CommandContext): void {
    const session = ctx?.session
    const pcGts = session?.document.value
    if (!session || !pcGts || !this.original) return
    const current = findRegionRecursive(pcGts.page.regions, this.original.id)?.region
    replaceRegion(pcGts, this.original.id, this.original)
    if (this.referencesChanged) {
      pcGts.page.readingOrder = cloneReadingOrder(this.previousReadingOrder)
      pcGts.page.relations = cloneRelations(this.previousRelations)
      useEditorUiStore().bumpReadingOrderVersion()
    }
    invalidateRegionTrees(ctx, current ? [current, this.original] : [this.original])
    commitDocument(session, pcGts)
  }

  getDescription(): string {
    return DESCRIPTIONS[this.data.operation.kind]
  }
}
