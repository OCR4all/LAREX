import type { ReadingOrder, ReadingOrderGroup, ReadingOrderNode, RegionRef } from '@/models/editor'

interface ReadingOrderInsertion {
  elements: ReadingOrderNode[]
  index: number
  sourceRef: RegionRef
}

export function cloneReadingOrder(readingOrder?: ReadingOrder): ReadingOrder | undefined {
  if (!readingOrder) return undefined
  return JSON.parse(JSON.stringify(readingOrder)) as ReadingOrder
}

export function removeIdsFromReadingOrder(elements: ReadingOrderNode[] | undefined, idsToRemove: Set<string>): boolean {
  if (!elements) return false

  let removed = false
  for (let i = elements.length - 1; i >= 0; i--) {
    const node = elements[i]
    if (!node) continue

    if (isRegionRef(node)) {
      if (idsToRemove.has(node.regionRef)) {
        elements.splice(i, 1)
        removed = true
      }
      continue
    }

    removed = removeIdsFromReadingOrder((node as ReadingOrderGroup).elements, idsToRemove) || removed
  }

  return removed
}

export function replaceIdsInReadingOrder(
  elements: ReadingOrderNode[] | undefined,
  idsToReplace: Set<string>,
  replacementId: string
): boolean {
  if (!elements) return false

  const insertion: { value: ReadingOrderInsertion | null } = { value: null }

  function removeMatches(nodes: ReadingOrderNode[]): void {
    for (let i = 0; i < nodes.length;) {
      const node = nodes[i]
      if (!node) {
        i++
        continue
      }

      if (isRegionRef(node)) {
        if (idsToReplace.has(node.regionRef)) {
          if (!insertion.value) {
            insertion.value = { elements: nodes, index: i, sourceRef: node }
          }
          nodes.splice(i, 1)
          continue
        }
        i++
        continue
      }

      removeMatches((node as ReadingOrderGroup).elements)
      i++
    }
  }

  removeMatches(elements)

  const position = insertion.value
  if (!position) return false

  position.elements.splice(position.index, 0, createReplacementRef(replacementId, position.sourceRef))
  return true
}

export function insertIntoReadingOrderAfter(
  elements: ReadingOrderNode[] | undefined,
  afterId: string,
  newId: string
): boolean {
  if (!elements) return false

  for (let i = 0; i < elements.length; i++) {
    const node = elements[i]
    if (!node) continue

    if (isRegionRef(node)) {
      if (node.regionRef === afterId) {
        elements.splice(i + 1, 0, {
          kind: 'RegionRef',
          id: `rr_${newId}`,
          regionRef: newId
        } as RegionRef)
        return true
      }
      continue
    }

    if (insertIntoReadingOrderAfter((node as ReadingOrderGroup).elements, afterId, newId)) {
      return true
    }
  }

  return false
}

function createReplacementRef(replacementId: string, sourceRef: RegionRef): RegionRef {
  const indexed = sourceRef.kind === 'RegionRefIndexed' || sourceRef.index !== undefined
  return {
    kind: indexed ? 'RegionRefIndexed' : 'RegionRef',
    id: `rr_${replacementId}`,
    regionRef: replacementId,
    index: sourceRef.index
  }
}

function isRegionRef(node: ReadingOrderNode): node is RegionRef {
  return node.kind === 'RegionRef' || node.kind === 'RegionRefIndexed'
}
