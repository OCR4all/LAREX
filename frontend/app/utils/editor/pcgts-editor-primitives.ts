import type { Glyph, PcGts, Region, TextLine, TextRegion, Word } from '@/models/editor'
import { PolygonType, isTextRegion } from '@/models/editor'
import type { Polygon } from '@/models/editor/geometry'
import type { RenderablePolygon, RenderablePolyline } from '@/types/editor/rendering'
import type { EditorSession } from '@/session/editor/editor-session'

function tuplesToPoints(points: [number, number][]): { x: number, y: number }[] {
  return points.map(([x, y]) => ({ x, y }))
}

export function baselineIdForTextLineId(textLineId: string): string {
  return `baseline:${textLineId}`
}

export function textLineIdFromBaselineId(baselineId: string): string | null {
  if (!baselineId.startsWith('baseline:')) return null
  const id = baselineId.slice('baseline:'.length)
  return id || null
}

function getRegionLabel(region: Region): string {
  if (isTextRegion(region)) {
    return region.type ?? region.kind
  }
  return region.kind
}

function getTextLineLabel(textLine: TextLine): string {
  return textLine.id
}

function collectFromWords(textLine: TextLine, outPolygons: RenderablePolygon[]): void {
  for (const word of textLine.words ?? []) {
    outPolygons.push({
      id: word.id,
      parentId: textLine.id,
      type: PolygonType.WORD,
      label: word.id,
      comments: word.comments,
      points: tuplesToPoints(word.coords.points),
      textContentVariants: word.textContentVariants,
      confidence: word.confidence
    })

    for (const glyph of word.glyphs ?? []) {
      outPolygons.push({
        id: glyph.id,
        parentId: word.id,
        type: PolygonType.GLYPH,
        label: glyph.id,
        comments: glyph.comments,
        points: tuplesToPoints(glyph.coords.points),
        textContentVariants: glyph.textContentVariants,
        confidence: glyph.confidence
      })
    }
  }
}

function collectFromRegions(
  regions: Region[],
  outPolygons: RenderablePolygon[],
  outPolylines: RenderablePolyline[],
  parentId?: string
): void {
  for (const region of regions) {
    const regionSubtype = region.type

    outPolygons.push({
      id: region.id,
      parentId,
      type: PolygonType.REGION,
      label: getRegionLabel(region),
      comments: region.comments,
      points: tuplesToPoints(region.coords.points),
      regionKind: region.kind,
      regionSubtype,
      regionCustom: region.custom,
      confidence: region.confidence,
      textContentVariants: isTextRegion(region) ? region.textContentVariants : undefined
    })

    if (isTextRegion(region) && region.textLines) {
      for (const textLine of region.textLines) {
        outPolygons.push({
          id: textLine.id,
          parentId: region.id,
          type: PolygonType.TEXTLINE,
          label: getTextLineLabel(textLine),
          comments: textLine.comments,
          points: tuplesToPoints(textLine.coords.points),
          textContentVariants: textLine.textContentVariants,
          confidence: textLine.confidence
        } as RenderablePolygon)

        collectFromWords(textLine, outPolygons)

        if (textLine.baseline) {
          outPolylines.push({
            id: baselineIdForTextLineId(textLine.id),
            parentId: textLine.id,
            type: PolygonType.BASELINE,
            label: 'baseline',
            points: tuplesToPoints(textLine.baseline.points.points),
            confidence: textLine.baseline.conf
          })
        }
      }
    }

    if (region.regions && region.regions.length > 0) {
      collectFromRegions(region.regions, outPolygons, outPolylines, region.id)
    }
  }
}

export function collectRenderablePolygonsFromPcGts(pcGts: PcGts): RenderablePolygon[] {
  const polygons: RenderablePolygon[] = []
  const polylines: RenderablePolyline[] = []
  collectFromRegions(pcGts.page.regions, polygons, polylines)
  return polygons
}

export function collectRenderablePolylinesFromPcGts(pcGts: PcGts): RenderablePolyline[] {
  const polygons: RenderablePolygon[] = []
  const polylines: RenderablePolyline[] = []
  collectFromRegions(pcGts.page.regions, polygons, polylines)
  return polylines
}

export function rebuildSpatialIndexFromPcGts(session: EditorSession): void {
  const pcGts = session.document.value
  if (!pcGts) return

  const polygons = collectRenderablePolygonsFromPcGts(pcGts)
  const polylines = collectRenderablePolylinesFromPcGts(pcGts)

  session.spatialIndex.rebuildPolygonIndex(polygons)
  session.spatialIndex.rebuildPolylineIndex(polylines)
}

/**
 * Update the spatial index incrementally for a specific polygon.
 * More efficient than full rebuild for single-element changes.
 */
export function updateSpatialIndexForPolygon(
  session: EditorSession,
  polygonId: string,
  operation: 'add' | 'update' | 'remove'
): void {
  const pcGts = session.document.value
  if (!pcGts) return

  if (operation === 'remove') {
    session.spatialIndex.removePolygon(polygonId)
    return
  }

  const allPolygons = collectRenderablePolygonsFromPcGts(pcGts)
  const polygonIndex = allPolygons.findIndex(p => p.id === polygonId)

  if (polygonIndex >= 0) {
    const polygon = allPolygons[polygonIndex]
    if (!polygon) return

    if (operation === 'add') {
      session.spatialIndex.insertPolygon(polygon, polygonIndex)
    } else {
      session.spatialIndex.updatePolygon(polygon, polygonIndex)
    }
  }
}

/**
 * Update the spatial index incrementally for a specific polyline.
 * More efficient than full rebuild for single-element changes.
 */
export function updateSpatialIndexForPolyline(
  session: EditorSession,
  polylineId: string,
  operation: 'add' | 'update' | 'remove'
): void {
  const pcGts = session.document.value
  if (!pcGts) return

  if (operation === 'remove') {
    session.spatialIndex.removePolyline(polylineId)
    return
  }

  const allPolylines = collectRenderablePolylinesFromPcGts(pcGts)
  const polylineIndex = allPolylines.findIndex(p => p.id === polylineId)

  if (polylineIndex >= 0) {
    const polyline = allPolylines[polylineIndex]
    if (!polyline) return

    if (operation === 'add') {
      session.spatialIndex.insertPolyline(polyline, polylineIndex)
    } else {
      session.spatialIndex.updatePolyline(polyline, polylineIndex)
    }
  }
}

/**
 * Batch update the spatial index for multiple elements.
 * Use when multiple elements change at once (e.g., deleting a region with children).
 */
export function batchUpdateSpatialIndex(
  session: EditorSession,
  polygonIdsToRemove: string[] = [],
  polylineIdsToRemove: string[] = []
): void {
  const pcGts = session.document.value
  if (!pcGts) return

  for (const id of polygonIdsToRemove) {
    session.spatialIndex.removePolygon(id)
  }
  for (const id of polylineIdsToRemove) {
    session.spatialIndex.removePolyline(id)
  }
}

export type RegionHit = {
  region: Region
  parent: Region | null
  index: number
}

export function findRegionRecursive(regions: Region[], id: string, parent: Region | null = null): RegionHit | null {
  for (let index = 0; index < regions.length; index++) {
    const region = regions[index]
    if (!region) continue
    if (region.id === id) return { region, parent, index }

    const children = region.regions ?? []
    if (children.length) {
      const hit = findRegionRecursive(children, id, region)
      if (hit) return hit
    }
  }

  return null
}

export type TextLineHit = {
  textLine: TextLine
  parentTextRegion: TextRegion
  index: number
}

export type WordHit = {
  word: Word
  parentTextLine: TextLine
  parentTextRegion: TextRegion
  index: number
}

export type GlyphHit = {
  glyph: Glyph
  parentWord: Word
  parentTextLine: TextLine
  index: number
}

export function findTextLineRecursive(regions: Region[], textLineId: string): TextLineHit | null {
  for (const region of regions) {
    if (isTextRegion(region) && region.textLines) {
      const idx = region.textLines.findIndex(tl => tl.id === textLineId)
      if (idx >= 0) {
        const textLine = region.textLines[idx]
        if (textLine) return { textLine, parentTextRegion: region, index: idx }
      }
    }

    const children = region.regions ?? []
    if (children.length) {
      const hit = findTextLineRecursive(children, textLineId)
      if (hit) return hit
    }
  }

  return null
}

function* iterateTextLines(regions: Region[]): Generator<{ textLine: TextLine, parentTextRegion: TextRegion }> {
  for (const region of regions) {
    if (isTextRegion(region) && region.textLines) {
      for (const textLine of region.textLines) {
        yield { textLine, parentTextRegion: region }
      }
    }
    if (region.regions?.length) {
      yield* iterateTextLines(region.regions)
    }
  }
}

export function findWordRecursive(regions: Region[], wordId: string): WordHit | null {
  for (const { textLine, parentTextRegion } of iterateTextLines(regions)) {
    const words = textLine.words
    if (!words?.length) continue
    const idx = words.findIndex(word => word.id === wordId)
    const word = idx >= 0 ? words[idx] : undefined
    if (word) return { word, parentTextLine: textLine, parentTextRegion, index: idx }
  }

  return null
}

export function findGlyphRecursive(regions: Region[], glyphId: string): GlyphHit | null {
  for (const { textLine } of iterateTextLines(regions)) {
    for (const word of textLine.words ?? []) {
      const glyphs = word.glyphs
      if (!glyphs?.length) continue
      const idx = glyphs.findIndex(glyph => glyph.id === glyphId)
      const glyph = idx >= 0 ? glyphs[idx] : undefined
      if (glyph) return { glyph, parentWord: word, parentTextLine: textLine, index: idx }
    }
  }

  return null
}

/** Polygon element kinds that carry their own Coords in PAGE XML. */
export type PolygonElementType = 'region' | 'textline' | 'word' | 'glyph'

export function toPolygonElementType(type: string | undefined): PolygonElementType {
  if (type === PolygonType.TEXTLINE || type === PolygonType.WORD || type === PolygonType.GLYPH) return type
  return 'region'
}

/**
 * Resolve the Coords polygon of any region, text line, word or glyph by ID.
 * Pass the element type when known to skip the lookups for other kinds.
 */
export function findPolygonCoordsRecursive(regions: Region[], id: string, type?: PolygonElementType): Polygon | null {
  if (!type || type === 'region') {
    const region = findRegionRecursive(regions, id)?.region
    if (region) return region.coords
    if (type) return null
  }
  if (!type || type === 'textline') {
    const textLine = findTextLineRecursive(regions, id)?.textLine
    if (textLine) return textLine.coords
    if (type) return null
  }
  if (!type || type === 'word') {
    const word = findWordRecursive(regions, id)?.word
    if (word) return word.coords
    if (type) return null
  }
  return findGlyphRecursive(regions, id)?.glyph.coords ?? null
}
