import type { ReadingOrder, ReadingOrderGroup, ReadingOrderNode, RegionRef } from '@/models/editor'
import type { TableModel } from './table-model'

export type TableReadingDirection = 'rows' | 'columns'

function isGroup(node: ReadingOrderNode): node is ReadingOrderGroup {
  return 'elements' in node
}

function cloneNode(node: ReadingOrderNode): ReadingOrderNode {
  return isGroup(node) ? { ...node, elements: node.elements.map(cloneNode) } : { ...node }
}

function withoutRefs(elements: ReadingOrderNode[], ids: Set<string>): ReadingOrderNode[] {
  return elements.flatMap((node): ReadingOrderNode[] => {
    if (!isGroup(node)) return ids.has(node.regionRef) ? [] : [node]
    if (node.regionRef && ids.has(node.regionRef)) return []
    return [{ ...node, elements: withoutRefs(node.elements, ids) }]
  })
}

function replaceTableNode(elements: ReadingOrderNode[], tableId: string, build: (existing: ReadingOrderNode) => ReadingOrderGroup): boolean {
  for (let index = 0; index < elements.length; index++) {
    const node = elements[index]!
    const refersToTable = isGroup(node) ? node.regionRef === tableId : node.regionRef === tableId
    if (refersToTable) {
      elements[index] = build(node)
      return true
    }
    if (isGroup(node) && replaceTableNode(node.elements, tableId, build)) return true
  }
  return false
}

export function orderedTableCellIds(model: TableModel, direction: TableReadingDirection): string[] {
  const cells = [...model.cells]
  cells.sort(direction === 'rows'
    ? (a, b) => a.row - b.row || a.col - b.col
    : (a, b) => a.col - b.col || a.row - b.row)
  return cells.map(cell => cell.regionId)
}

export function withTableReadingOrder(readingOrder: ReadingOrder | undefined, model: TableModel, direction: TableReadingDirection): ReadingOrder {
  const tableId = model.tableId
  const cellIds = orderedTableCellIds(model, direction)
  const root: ReadingOrderGroup = readingOrder
    ? { ...readingOrder.root, elements: readingOrder.root.elements.map(cloneNode) }
    : { kind: 'OrderedGroup', id: 'ro_root', elements: [] }
  root.elements = withoutRefs(root.elements, new Set(cellIds))

  const build = (existing?: ReadingOrderNode): ReadingOrderGroup => {
    const indexed = existing !== undefined && (existing.kind.endsWith('Indexed') || existing.index !== undefined)
    const elements: RegionRef[] = cellIds.map((cellId, index) => ({
      kind: 'RegionRefIndexed',
      id: `ro_${cellId}`,
      regionRef: cellId,
      index
    }))
    const base = existing && isGroup(existing) ? existing : undefined
    return {
      ...base,
      kind: indexed ? 'OrderedGroupIndexed' : 'OrderedGroup',
      id: base?.id ?? `ro_${tableId}`,
      regionRef: tableId,
      index: existing?.index,
      elements
    }
  }

  if (!replaceTableNode(root.elements, tableId, build)) root.elements.push(build())
  return { ...readingOrder, root }
}
