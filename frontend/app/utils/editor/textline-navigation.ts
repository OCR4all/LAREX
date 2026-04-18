import type { Region } from '@/models/editor/region'
import { isTextRegion } from '@/models/editor/region'

function collectTextlineIdsRecursive(regions: Region[], out: string[]): void {
  for (const region of regions) {
    if (isTextRegion(region) && Array.isArray(region.textLines)) {
      for (const textLine of region.textLines) {
        if (typeof textLine?.id === 'string' && textLine.id.length > 0) {
          out.push(textLine.id)
        }
      }
    }

    if (Array.isArray(region.regions) && region.regions.length > 0) {
      collectTextlineIdsRecursive(region.regions, out)
    }
  }
}

/**
 * Collect textline IDs in PAGE traversal order.
 * Traversal rule: iterate regions in array order, include each TextRegion's textlines
 * in array order, then recurse into child regions.
 */
export function collectTextlineIdsInPageOrder(regions: Region[] | undefined): string[] {
  if (!Array.isArray(regions) || regions.length === 0) return []
  const orderedIds: string[] = []
  collectTextlineIdsRecursive(regions, orderedIds)
  return orderedIds
}

/**
 * Return next/previous textline ID with wrap-around semantics.
 */
export function getAdjacentTextlineId(
  orderedTextlineIds: string[],
  currentId: string | null | undefined,
  direction: 1 | -1
): string | null {
  if (!Array.isArray(orderedTextlineIds) || orderedTextlineIds.length === 0) return null

  if (!currentId) {
    return direction === 1
      ? (orderedTextlineIds[0] ?? null)
      : (orderedTextlineIds[orderedTextlineIds.length - 1] ?? null)
  }

  const currentIndex = orderedTextlineIds.indexOf(currentId)
  if (currentIndex < 0) {
    return direction === 1
      ? (orderedTextlineIds[0] ?? null)
      : (orderedTextlineIds[orderedTextlineIds.length - 1] ?? null)
  }

  const nextIndex = (currentIndex + direction + orderedTextlineIds.length) % orderedTextlineIds.length
  return orderedTextlineIds[nextIndex] ?? null
}

