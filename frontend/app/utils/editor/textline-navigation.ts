import type { Region } from '@/models/editor/region'
import { isTextRegion } from '@/models/editor/region'
import type { ReadingOrder, ReadingOrderNode } from '@/models/editor/reading-order'
import type { TextLine } from '@/models/editor/text'
import { PolygonType } from '@/models/editor'

/**
 * Fallback order for textlines inside a region that carry no explicit
 * textline reading order (TextLine/@index or Transkribus-style
 * `custom="readingOrder {index:N;}"`).
 */
export type TextlineSortOrder = 'top-to-bottom' | 'alphabetical'

export const DEFAULT_TEXTLINE_SORT_ORDER: TextlineSortOrder = 'top-to-bottom'

export function normalizeTextlineSortOrder(value: unknown): TextlineSortOrder {
  return value === 'alphabetical' ? 'alphabetical' : DEFAULT_TEXTLINE_SORT_ORDER
}

export interface TextlineSortEntry {
  id: string
  /** Explicit textline reading order within its region, if any. */
  explicitOrder?: number
  /** Vertical anchor (bounding-box center); smaller is higher on the page. */
  y?: number
  /** Horizontal anchor (bounding-box left edge) in image coordinates. */
  x?: number
}

const CUSTOM_READING_ORDER_INDEX_PATTERN = /readingOrder\s*\{[^}]*?\bindex\s*:\s*(-?\d+)/

/**
 * Resolve an explicit textline reading order. TextLine/@index wins over the
 * Transkribus-style `readingOrder {index:N;}` entry in @custom.
 */
export function getTextlineExplicitOrder(textLine: { index?: number, custom?: string } | null | undefined): number | undefined {
  if (!textLine) return undefined
  if (typeof textLine.index === 'number' && Number.isFinite(textLine.index)) return textLine.index
  if (typeof textLine.custom !== 'string') return undefined
  const match = CUSTOM_READING_ORDER_INDEX_PATTERN.exec(textLine.custom)
  if (!match?.[1]) return undefined
  const parsed = Number.parseInt(match[1], 10)
  return Number.isFinite(parsed) ? parsed : undefined
}

/**
 * Compute the sort anchor for a polygon given as `{x, y}` points or `[x, y]` tuples.
 */
export function getTextlineSortAnchor(points: ReadonlyArray<{ x: number, y: number } | readonly [number, number]> | null | undefined): { x?: number, y?: number } {
  if (!Array.isArray(points) || points.length === 0) return {}

  let minX = Infinity
  let minY = Infinity
  let maxY = -Infinity
  for (const point of points) {
    const x = Array.isArray(point) ? point[0] : (point as { x: number }).x
    const y = Array.isArray(point) ? point[1] : (point as { y: number }).y
    if (!Number.isFinite(x) || !Number.isFinite(y)) continue
    if (x < minX) minX = x
    if (y < minY) minY = y
    if (y > maxY) maxY = y
  }

  if (!Number.isFinite(minX) || !Number.isFinite(minY)) return {}

  // Editor documents usually hold WebGL world coordinates (-1..1, y pointing up);
  // flip y so that smaller values are always higher on the page.
  const looksLikeWorldCoords = points.every((point) => {
    const x = Array.isArray(point) ? point[0] : (point as { x: number }).x
    const y = Array.isArray(point) ? point[1] : (point as { y: number }).y
    return Math.abs(x) <= 2 && Math.abs(y) <= 2
  })
  const centerY = (minY + maxY) / 2
  return { x: minX, y: looksLikeWorldCoords ? -centerY : centerY }
}

const idCollator = new Intl.Collator(undefined, { numeric: true, sensitivity: 'base' })

/**
 * Compare two textlines of the same region. Textlines with an explicit reading
 * order come first (ordered by it); the rest follow in the given fallback order.
 */
export function compareTextlineSortEntries(a: TextlineSortEntry, b: TextlineSortEntry, sortOrder: TextlineSortOrder): number {
  const aExplicit = a.explicitOrder
  const bExplicit = b.explicitOrder
  if (aExplicit !== undefined && bExplicit !== undefined) {
    if (aExplicit !== bExplicit) return aExplicit - bExplicit
  } else if (aExplicit !== undefined) {
    return -1
  } else if (bExplicit !== undefined) {
    return 1
  }

  if (sortOrder === 'top-to-bottom') {
    if (a.y !== undefined && b.y !== undefined && a.y !== b.y) return a.y - b.y
    if (a.x !== undefined && b.x !== undefined && a.x !== b.x) return a.x - b.x
    return 0
  }

  return idCollator.compare(a.id, b.id)
}

export function toTextlineSortEntry(textLine: TextLine): TextlineSortEntry {
  return {
    id: textLine.id,
    explicitOrder: getTextlineExplicitOrder(textLine),
    ...getTextlineSortAnchor(textLine.coords?.points)
  }
}

/**
 * Return a region's textlines in effective order (stable for ties).
 */
export function sortRegionTextLines(textLines: TextLine[], sortOrder: TextlineSortOrder = DEFAULT_TEXTLINE_SORT_ORDER): TextLine[] {
  return textLines
    .map(textLine => ({ textLine, entry: toTextlineSortEntry(textLine) }))
    .sort((a, b) => compareTextlineSortEntries(a.entry, b.entry, sortOrder))
    .map(item => item.textLine)
}

/**
 * Map every textline ID on the page to its explicit textline reading order, if any.
 */
export function collectTextlineExplicitOrders(regions: Region[] | undefined): Map<string, number> {
  const byId = new Map<string, number>()
  const visit = (items: Region[] | undefined) => {
    if (!Array.isArray(items)) return
    for (const region of items) {
      if (isTextRegion(region) && Array.isArray(region.textLines)) {
        for (const textLine of region.textLines) {
          const order = getTextlineExplicitOrder(textLine)
          if (textLine?.id && order !== undefined) byId.set(textLine.id, order)
        }
      }
      visit(region.regions)
    }
  }
  visit(regions)
  return byId
}

function collectTextlineIdsRecursive(regions: Region[], out: string[], sortOrder: TextlineSortOrder): void {
  for (const region of regions) {
    if (isTextRegion(region) && Array.isArray(region.textLines)) {
      for (const textLine of sortRegionTextLines(region.textLines.filter(Boolean), sortOrder)) {
        if (typeof textLine?.id === 'string' && textLine.id.length > 0) {
          out.push(textLine.id)
        }
      }
    }

    if (Array.isArray(region.regions) && region.regions.length > 0) {
      collectTextlineIdsRecursive(region.regions, out, sortOrder)
    }
  }
}

/**
 * Collect textline IDs in PAGE traversal order.
 * Traversal rule: iterate regions in array order, include each TextRegion's textlines
 * in effective textline order (see sortRegionTextLines), then recurse into child regions.
 */
export function collectTextlineIdsInPageOrder(
  regions: Region[] | undefined,
  sortOrder: TextlineSortOrder = DEFAULT_TEXTLINE_SORT_ORDER
): string[] {
  if (!Array.isArray(regions) || regions.length === 0) return []
  const orderedIds: string[] = []
  collectTextlineIdsRecursive(regions, orderedIds, sortOrder)
  return orderedIds
}

function collectRegionsRecursive(regions: Region[], out: Region[]): void {
  for (const region of regions) {
    out.push(region)
    if (Array.isArray(region.regions) && region.regions.length > 0) {
      collectRegionsRecursive(region.regions, out)
    }
  }
}

function isReadingOrderGroup(node: ReadingOrderNode): node is Extract<ReadingOrderNode, { elements: ReadingOrderNode[] }> {
  return 'elements' in node && Array.isArray(node.elements)
}

/**
 * Collect region IDs in PAGE reading-order traversal order.
 */
export function collectRegionIdsInReadingOrder(readingOrder: ReadingOrder | undefined): string[] {
  if (!readingOrder?.root?.elements?.length) return []

  const orderedIds: string[] = []
  const seen = new Set<string>()

  function add(regionId: string | undefined): void {
    if (!regionId || seen.has(regionId)) return
    seen.add(regionId)
    orderedIds.push(regionId)
  }

  function traverse(node: ReadingOrderNode): void {
    if (isReadingOrderGroup(node)) {
      add(node.regionRef)
      for (const child of node.elements) {
        traverse(child)
      }
      return
    }

    add(node.regionRef)
  }

  for (const element of readingOrder.root.elements) {
    traverse(element)
  }

  return orderedIds
}

/**
 * Collect every textline in effective PAGE reading order.
 *
 * Reading-order references determine the order of referenced text regions.
 * Textlines within each region follow their explicit textline reading order
 * when present, otherwise `sortOrder`. Text regions omitted from reading order
 * are appended in recursive PAGE traversal order so the caller never silently
 * loses transcription content.
 */
export function collectTextlineIdsInReadingOrder(
  regions: Region[] | undefined,
  readingOrder: ReadingOrder | undefined,
  sortOrder: TextlineSortOrder = DEFAULT_TEXTLINE_SORT_ORDER
): string[] {
  if (!Array.isArray(regions) || regions.length === 0) return []

  const pageRegions: Region[] = []
  collectRegionsRecursive(regions, pageRegions)

  const regionById = new Map(pageRegions.map(region => [region.id, region]))
  const orderedTextlineIds: string[] = []
  const seenTextlineIds = new Set<string>()
  const visitedRegionIds = new Set<string>()

  function appendTextRegion(region: Region | undefined): void {
    if (!region || visitedRegionIds.has(region.id)) return
    visitedRegionIds.add(region.id)
    if (!isTextRegion(region) || !Array.isArray(region.textLines)) return

    for (const textLine of sortRegionTextLines(region.textLines.filter(Boolean), sortOrder)) {
      const id = textLine?.id
      if (typeof id !== 'string' || id.length === 0 || seenTextlineIds.has(id)) continue
      seenTextlineIds.add(id)
      orderedTextlineIds.push(id)
    }
  }

  for (const regionId of collectRegionIdsInReadingOrder(readingOrder)) {
    appendTextRegion(regionById.get(regionId))
  }

  for (const region of pageRegions) {
    appendTextRegion(region)
  }

  return orderedTextlineIds
}

export function isTextlinePolygonType(type: unknown): boolean {
  return typeof type === 'string' && type.toLowerCase() === PolygonType.TEXTLINE
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
