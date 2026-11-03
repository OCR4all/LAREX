/**
 * Auto-parent detection utilities for Textline/Word/Glyph/Baseline creation in view modes.
 * Provides real-time preview of which parent will be used or what helper shapes will be created.
 */

import type { Point } from '@/models/editor'
import { PolygonType } from '@/models/editor'
import type { RenderablePolygon, RenderablePolyline, AutoParentPreview } from '@/types/editor/rendering'
import {
  findBestContainingPolygon,
  createBoundingRectangleFromPoints
} from '@/utils/editor/polygon-clipping'

/**
 * Default padding for auto-generated parent shapes (in coordinate units).
 */
const AUTO_PARENT_PADDING = 0.02

/**
 * Minimum overlap percentage required to assign an element to an existing parent.
 */
const MIN_OVERLAP_PERCENTAGE = 50
export const AUTO_PARENT_MIN_OVERLAP_PERCENTAGE = MIN_OVERLAP_PERCENTAGE

/**
 * Padding for helper words/textlines generated around new words and glyphs.
 * Smaller than the region padding because these shapes are much smaller.
 */
export const NESTED_AUTO_PARENT_PADDING = 0.005

/**
 * Compute the auto-parent preview for Textline creation in Textline view mode.
 *
 * @param currentPoints - Points of the textline being drawn
 * @param allPolygons - All existing polygons in the document
 * @returns AutoParentPreview with detected parent or helper shape preview
 */
export function computeTextlineAutoParentPreview(
  currentPoints: Point[],
  allPolygons: RenderablePolygon[]
): AutoParentPreview | undefined {
  if (!currentPoints || currentPoints.length < 2) {
    return undefined
  }

  const textRegionCandidates = allPolygons
    .filter((p): p is RenderablePolygon & { regionKind: 'TextRegion' } =>
      p.type === PolygonType.REGION && p.regionKind === 'TextRegion'
    )
    .map(p => ({ polygon: p.points, id: p.id }))

  const bestParent = findBestContainingPolygon(
    currentPoints,
    textRegionCandidates,
    MIN_OVERLAP_PERCENTAGE
  )

  if (bestParent) {
    const parentPolygon = allPolygons.find(p => p.id === bestParent.id)
    return {
      parentPolygon: parentPolygon ?? null,
      isExisting: true,
      creationLevel: 'none'
    }
  } else {
    const helperShapePoints = createBoundingRectangleFromPoints(currentPoints, AUTO_PARENT_PADDING)
    return {
      parentPolygon: null,
      isExisting: false,
      helperShapePoints,
      creationLevel: 'region'
    }
  }
}

/**
 * Compute the auto-parent preview for Baseline creation in Baseline view mode.
 *
 * @param currentPoints - Points of the baseline being drawn
 * @param allPolygons - All existing polygons in the document
 * @param allPolylines - All existing polylines (baselines) in the document
 * @returns AutoParentPreview with detected parent or helper shape preview
 */
export function computeBaselineAutoParentPreview(
  currentPoints: Point[],
  allPolygons: RenderablePolygon[],
  allPolylines: RenderablePolyline[]
): AutoParentPreview | undefined {
  if (!currentPoints || currentPoints.length < 2) {
    return undefined
  }

  const textlinesWithBaselines = new Set<string>()
  for (const polyline of allPolylines) {
    if (polyline.parentId) {
      textlinesWithBaselines.add(polyline.parentId)
    }
  }

  const availableTextlines = allPolygons
    .filter((p): p is RenderablePolygon =>
      p.type === PolygonType.TEXTLINE && !textlinesWithBaselines.has(p.id)
    )
    .map(p => ({ polygon: p.points, id: p.id, parentId: p.parentId }))

  const bestTextline = findBestContainingPolygon(
    currentPoints,
    availableTextlines,
    MIN_OVERLAP_PERCENTAGE
  )

  if (bestTextline) {
    const parentPolygon = allPolygons.find(p => p.id === bestTextline.id)
    return {
      parentPolygon: parentPolygon ?? null,
      isExisting: true,
      creationLevel: 'none'
    }
  }

  const textRegionCandidates = allPolygons
    .filter((p): p is RenderablePolygon & { regionKind: 'TextRegion' } =>
      p.type === PolygonType.REGION && p.regionKind === 'TextRegion'
    )
    .map(p => ({ polygon: p.points, id: p.id }))

  const bestRegion = findBestContainingPolygon(
    currentPoints,
    textRegionCandidates,
    MIN_OVERLAP_PERCENTAGE
  )

  const helperTextlinePoints = createBoundingRectangleFromPoints(currentPoints, AUTO_PARENT_PADDING)

  if (bestRegion) {
    const parentPolygon = allPolygons.find(p => p.id === bestRegion.id)
    return {
      parentPolygon: parentPolygon ?? null,
      isExisting: true,
      helperTextlinePoints,
      creationLevel: 'textline'
    }
  } else {
    const helperRegionPoints = createBoundingRectangleFromPoints(currentPoints, AUTO_PARENT_PADDING * 2)
    return {
      parentPolygon: null,
      isExisting: false,
      helperShapePoints: helperRegionPoints,
      helperTextlinePoints,
      creationLevel: 'both'
    }
  }
}

/**
 * Compute the auto-parent preview for Word or Glyph creation in Word/Glyph view mode.
 * Mirrors CreateTextElementAutoParentCommand: missing parents are previewed as
 * bounding-box helpers, walking up glyph -> word -> textline -> region.
 */
export function computeTextElementAutoParentPreview(
  currentPoints: Point[],
  allPolygons: RenderablePolygon[],
  type: typeof PolygonType.WORD | typeof PolygonType.GLYPH
): AutoParentPreview | undefined {
  if (!currentPoints || currentPoints.length < 2) {
    return undefined
  }

  const findParent = (points: Point[], parentType: string): RenderablePolygon | null => {
    const candidates = allPolygons
      .filter(p => p.type === parentType)
      .map(p => ({ polygon: p.points, id: p.id }))
    const best = findBestContainingPolygon(points, candidates, MIN_OVERLAP_PERCENTAGE)
    return best ? allPolygons.find(p => p.id === best.id) ?? null : null
  }

  let points = currentPoints
  let helperWordPoints: Point[] | undefined
  if (type === PolygonType.GLYPH) {
    const word = findParent(points, PolygonType.WORD)
    if (word) {
      return { parentPolygon: word, isExisting: true, creationLevel: 'none' }
    }
    helperWordPoints = createBoundingRectangleFromPoints(points, NESTED_AUTO_PARENT_PADDING)
    points = helperWordPoints
  }

  const textline = findParent(points, PolygonType.TEXTLINE)
  if (textline) {
    return { parentPolygon: textline, isExisting: true, helperWordPoints, creationLevel: helperWordPoints ? 'word' : 'none' }
  }

  const helperTextlinePoints = createBoundingRectangleFromPoints(points, NESTED_AUTO_PARENT_PADDING)
  const regionPreview = computeTextlineAutoParentPreview(helperTextlinePoints, allPolygons)
  return {
    parentPolygon: regionPreview?.parentPolygon ?? null,
    isExisting: regionPreview?.isExisting ?? false,
    helperShapePoints: regionPreview?.helperShapePoints,
    helperTextlinePoints,
    helperWordPoints,
    creationLevel: regionPreview?.isExisting ? 'textline' : 'both'
  }
}

/**
 * Compute auto-parent preview based on current view mode and drawing state.
 *
 * @param viewMode - Current view mode ('default', 'textline', 'word', 'glyph', 'baseline')
 * @param isDrawingPolygon - Whether polygon drawing is active
 * @param isDrawingPolyline - Whether polyline drawing is active
 * @param currentPolygonPoints - Current polygon points being drawn
 * @param currentPolylinePoints - Current polyline points being drawn
 * @param allPolygons - All existing polygons
 * @param allPolylines - All existing polylines
 * @param regionType - Current region type being drawn (for polygon mode)
 * @returns AutoParentPreview or undefined if not applicable
 */
export function computeAutoParentPreview(
  viewMode: string | undefined,
  isDrawingPolygon: boolean,
  isDrawingPolyline: boolean,
  currentPolygonPoints: Point[],
  currentPolylinePoints: Point[],
  allPolygons: RenderablePolygon[],
  allPolylines: RenderablePolyline[],
  regionType?: string
): AutoParentPreview | undefined {
  if (!viewMode || viewMode === 'default') {
    return undefined
  }

  if (viewMode === 'textline' && isDrawingPolygon && regionType === PolygonType.TEXTLINE) {
    return computeTextlineAutoParentPreview(currentPolygonPoints, allPolygons)
  }

  if (viewMode === 'word' && isDrawingPolygon && regionType === PolygonType.WORD) {
    return computeTextElementAutoParentPreview(currentPolygonPoints, allPolygons, PolygonType.WORD)
  }

  if (viewMode === 'glyph' && isDrawingPolygon && regionType === PolygonType.GLYPH) {
    return computeTextElementAutoParentPreview(currentPolygonPoints, allPolygons, PolygonType.GLYPH)
  }

  if (viewMode === 'baseline' && isDrawingPolyline) {
    return computeBaselineAutoParentPreview(currentPolylinePoints, allPolygons, allPolylines)
  }

  return undefined
}
