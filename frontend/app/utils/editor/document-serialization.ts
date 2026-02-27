/**
 * Serialization utilities for the PAGE XML 2019 runtime model (PcGts).
 *
 * Option A: Frontend persists JSON; XML import/export is handled by backend.
 *
 * Notes on coordinates:
 * - Most editor state uses world coordinates (-1..1).
 * - Some dev/test fixtures are authored in pixel coordinates; if `imageSize` is
 *   provided, this module converts pixels -> world at creation time.
 */

import { PcGts, Metadata } from '@/models/editor/document'
import { Page } from '@/models/editor/page'
import type { Region, TextRegion } from '@/models/editor/region'
import { Polygon, Polyline } from '@/models/editor/geometry'
import { TextLine } from '@/models/editor/text'
import type { ImageSize, Point } from '@/models/editor'
import { PolygonType } from '@/models/editor'
import { imageToWorld } from './coordinates'

export type FlatPolygon = {
  id: string
  label?: string
  type: string
  points: Point[]
  parentId?: string
}

export type FlatPolyline = {
  id: string
  label?: string
  type?: string
  points: Point[]
  parentId?: string
}

function maybeToWorld(points: Point[], imageSize?: ImageSize): Point[] {
  return imageSize ? points.map(p => imageToWorld(p, imageSize)) : points
}

function toTuplePoints(points: Point[]): [number, number][] {
  return points.map(p => [p.x, p.y])
}

/**
 * Create a PcGts from flat editor primitives (regions/textlines + baselines).
 * This exists mostly for dev toolbar fixture generation.
 */
export function createDocumentFromFlatData(
  documentId: string,
  regions: FlatPolygon[],
  baselines: FlatPolyline[],
  imageSize?: ImageSize
): PcGts {
  const convertedRegions = regions.map(r => ({ ...r, points: maybeToWorld(r.points, imageSize) }))
  const convertedBaselines = baselines.map(b => ({ ...b, points: maybeToWorld(b.points, imageSize) }))

  const rootRegions: FlatPolygon[] = []
  const childRegionsByParent = new Map<string, FlatPolygon[]>()
  const textLinesByParent = new Map<string, FlatPolygon[]>()
  const baselinesByParent = new Map<string, FlatPolyline[]>()

  for (const region of convertedRegions) {
    if (region.type === PolygonType.TEXTLINE) {
      if (region.parentId) {
        const items = textLinesByParent.get(region.parentId) ?? []
        items.push(region)
        textLinesByParent.set(region.parentId, items)
      }
      continue
    }

    if (!region.parentId) {
      rootRegions.push(region)
    } else {
      const items = childRegionsByParent.get(region.parentId) ?? []
      items.push(region)
      childRegionsByParent.set(region.parentId, items)
    }
  }

  for (const baseline of convertedBaselines) {
    if (!baseline.parentId) continue
    const items = baselinesByParent.get(baseline.parentId) ?? []
    items.push(baseline)
    baselinesByParent.set(baseline.parentId, items)
  }

  function buildRegion(regionData: FlatPolygon): Region {
    const coords = new Polygon(toTuplePoints(regionData.points))
    const childRegions = (childRegionsByParent.get(regionData.id) ?? []).map(buildRegion)
    const textLinePolys = textLinesByParent.get(regionData.id) ?? []

    const textLines = textLinePolys.map((tl) => {
      const baselineCandidates = baselinesByParent.get(tl.id) ?? []
      const baselineData = baselineCandidates[0]
      const baseline = baselineData
        ? { points: new Polyline(toTuplePoints(baselineData.points)) }
        : undefined

      return new TextLine({
        id: tl.id,
        coords: new Polygon(toTuplePoints(tl.points)),
        baseline
      })
    })

    const region: TextRegion = {
      id: regionData.id,
      kind: 'TextRegion',
      coords,
      type: regionData.label,
      regions: childRegions.length > 0 ? childRegions : undefined,
      textLines: textLines.length > 0 ? textLines : undefined
    }

    return region
  }

  const now = new Date().toISOString()
  const metadata = new Metadata({ creator: 'Umbra', created: now, lastChange: now })
  const page = new Page({
    imageFilename: `${documentId}`,
    imageWidth: imageSize?.width ?? 1000,
    imageHeight: imageSize?.height ?? 1000,
    regions: rootRegions.map(buildRegion)
  })

  return new PcGts(metadata, page, documentId)
}

/**
 * Serialize PcGts to JSON.
 * Keep it intentionally simple; backend can handle PAGE XML conversion later.
 */
export function serializeDocument(document: PcGts, _imageSize?: ImageSize): string {
  return JSON.stringify(document, null, 2)
}

type UnknownRecord = Record<string, unknown>

function asRecord(value: unknown): UnknownRecord {
  return value && typeof value === 'object' ? (value as UnknownRecord) : {}
}

function asArray(value: unknown): unknown[] {
  return Array.isArray(value) ? value : []
}

function toTuplePointsUnknown(points: unknown): [number, number][] {
  return asArray(points)
    .filter(p => Array.isArray(p) && p.length >= 2)
    .map((p) => {
      const arr = p as unknown[]
      return [Number(arr[0]), Number(arr[1])] as [number, number]
    })
}

function rehydrateRegion(raw: unknown): Region {
  const regionRec = asRecord(raw)
  const coordsRec = asRecord(regionRec.coords)
  const coords = new Polygon(toTuplePointsUnknown(coordsRec.points))

  const regions = asArray(regionRec.regions).map(rehydrateRegion)
  const regionsOrUndef = regions.length > 0 ? regions : undefined

  if (regionRec.kind === 'TextRegion') {
    const textLines = asArray(regionRec.textLines)
      .map((tlRaw) => {
        const tlRec = asRecord(tlRaw)
        const tlCoordsRec = asRecord(tlRec.coords)
        const tlCoords = new Polygon(toTuplePointsUnknown(tlCoordsRec.points))

        const baselineRec = asRecord(tlRec.baseline)
        const baselinePointsRec = asRecord(baselineRec.points)
        const baselineTuples = toTuplePointsUnknown(baselinePointsRec.points)
        const baseline = baselineTuples.length > 0 ? { points: new Polyline(baselineTuples) } : undefined

        const id = typeof tlRec.id === 'string' ? tlRec.id : ''

        return new TextLine({
          id,
          coords: tlCoords,
          baseline,
          words: tlRec.words as unknown as TextLine['words'],
          textContentVariants: tlRec.textContentVariants as unknown as TextLine['textContentVariants'],
          styleRefs: tlRec.styleRefs as unknown as TextLine['styleRefs'],
          processingRefs: tlRec.processingRefs as unknown as TextLine['processingRefs'],
          confidence: tlRec.confidence as unknown as TextLine['confidence'],
          primaryLanguage: tlRec.primaryLanguage as unknown as TextLine['primaryLanguage'],
          primaryScript: tlRec.primaryScript as unknown as TextLine['primaryScript'],
          readingDirection: tlRec.readingDirection as unknown as TextLine['readingDirection']
        })
      })
      .filter(tl => Boolean(tl.id))

    return {
      ...(regionRec as object),
      coords,
      regions: regionsOrUndef,
      textLines: textLines.length > 0 ? textLines : undefined
    } as Region
  }

  return {
    ...(regionRec as object),
    coords,
    regions: regionsOrUndef
  } as Region
}

/**
 * Best-effort PcGts JSON rehydration into class instances.
 */
export function deserializeDocument(json: string, _imageSize?: ImageSize): PcGts {
  const raw = JSON.parse(json)

  const metadata = new Metadata({
    creator: raw?.metadata?.creator ?? 'Umbra',
    created: raw?.metadata?.created ?? new Date().toISOString(),
    lastChange: raw?.metadata?.lastChange ?? raw?.metadata?.created ?? new Date().toISOString(),
    comments: raw?.metadata?.comments,
    externalRef: raw?.metadata?.externalRef,
    userDefined: raw?.metadata?.userDefined,
    items: raw?.metadata?.items
  })

  const page = new Page({
    imageFilename: raw?.page?.imageFilename ?? 'unknown',
    imageWidth: raw?.page?.imageWidth ?? 1000,
    imageHeight: raw?.page?.imageHeight ?? 1000,
    imageXResolution: raw?.page?.imageXResolution,
    imageYResolution: raw?.page?.imageYResolution,
    imageResolutionUnit: raw?.page?.imageResolutionUnit,
    border: raw?.page?.border
      ? { coords: new Polygon(raw.page.border.coords?.points ?? []) }
      : undefined,
    printSpace: raw?.page?.printSpace
      ? { coords: new Polygon(raw.page.printSpace.coords?.points ?? []) }
      : undefined,
    readingOrder: raw?.page?.readingOrder,
    regions: Array.isArray(raw?.page?.regions) ? raw.page.regions.map(rehydrateRegion) : []
  })

  return new PcGts(metadata, page, raw?.pcGtsId)
}
