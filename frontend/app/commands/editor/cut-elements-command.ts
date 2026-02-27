import type { Command, CommandContext } from './types'
import { PcGts, TextLine, PolygonType, isTextRegion } from '@/models/editor'
import type { Point, Region, TextRegion, ReadingOrderNode, ReadingOrderGroup, RegionRef } from '@/models/editor'
import { Polygon as PolygonGeometry, Polyline as PolylineGeometry } from '@/models/editor/geometry'
import { visibilityService } from '@/services/editor/visibility-service'
import {
  collectRenderablePolygonsFromPcGts,
  findRegionRecursive,
  findTextLineRecursive,
  rebuildSpatialIndexFromPcGts
} from '@/utils/editor/pcgts-editor-primitives'
import {
  splitPolygonByLine,
  subtractPolygon,
  doPolygonsIntersect,
  calculatePolygonArea,
  clipPolylineToPolygon,
  isPointInPolygon,
  calculatePolygonOverlapPercentage
} from '@/utils/editor/polygon-clipping'
import { useEditorUiStore } from '@/stores/editor/editor.ui.store'
import { invalidateMultiplePolygonGeometry } from '@/composables/editor/use-geometry-cache-integrations'

/**
 * Cut mode types
 */
export type CutMode = 'line' | 'polygon' | 'rectangle'

/**
 * Data for the cut command
 */
export interface CutElementsCommandData {
  /** The cut mode (line, polygon, or rectangle) */
  mode: CutMode
  /** Points defining the cut shape - line for cut-line, polygon vertices for cut-polygon/rectangle */
  cutPoints: Point[]
  /** Minimum area threshold - polygons below this area are auto-deleted */
  minAreaThreshold?: number
}

/**
 * Snapshot of the original document state for undo
 */
interface UndoSnapshot {
  regions: Region[]
  readingOrderElements?: ReadingOrderNode[]
}

/**
 * Command for cutting regions, textlines, and baselines using line, polygon, or rectangle shapes.
 *
 * Cut Line: Splits intersected elements into multiple pieces
 * Cut Polygon/Rectangle: Subtracts the shape from elements, deleting fully enclosed ones
 *
 * Features:
 * - When cutting regions, also cuts all child textlines and baselines
 * - Child elements are properly assigned to the correct parent pieces
 * - Largest resulting polygon inherits the original ID
 * - Auto-deletes polygons below the area threshold
 * - Proper undo/redo support with full document restoration
 */
export class CutElementsCommand implements Command {
  private mode: CutMode
  private cutPoints: Point[]
  private minAreaThreshold: number

  private undoSnapshot: UndoSnapshot | null = null
  private affectedPolygonIds: string[] = []

  constructor(data: CutElementsCommandData) {
    this.mode = data.mode
    this.cutPoints = data.cutPoints.map(p => ({ x: p.x, y: p.y }))
    this.minAreaThreshold = data.minAreaThreshold ?? 0.0001
  }

  execute(ctx?: CommandContext): { cutCount: number, deletedCount: number, createdCount: number } {
    const session = ctx?.session
    const pcGts = session?.document.value
    if (!session || !pcGts) {
      return { cutCount: 0, deletedCount: 0, createdCount: 0 }
    }

    this.undoSnapshot = {
      regions: this.deepCloneRegions(pcGts.page.regions),
      readingOrderElements: pcGts.page.readingOrder?.root?.elements
        ? this.deepCloneReadingOrder(pcGts.page.readingOrder.root.elements)
        : undefined
    }

    const allPolygons = collectRenderablePolygonsFromPcGts(pcGts)

    this.affectedPolygonIds = []

    let cutCount = 0
    let deletedCount = 0
    let createdCount = 0

    const regionPolygons = allPolygons.filter(p => p.type === PolygonType.REGION)

    for (const regionPoly of regionPolygons) {
      if (!this.shouldCutElement(regionPoly.points)) continue

      this.affectedPolygonIds.push(regionPoly.id)

      const hit = findRegionRecursive(pcGts.page.regions, regionPoly.id)
      if (!hit) continue

      const result = this.cutRegionWithChildren(hit.region, hit.parent, pcGts)
      cutCount += result.cutCount
      deletedCount += result.deletedCount
      createdCount += result.createdCount
    }

    const textlinePolygons = allPolygons.filter(p => p.type === PolygonType.TEXTLINE)

    for (const textlinePoly of textlinePolygons) {
      if (this.affectedPolygonIds.includes(textlinePoly.id)) continue

      if (textlinePoly.parentId && this.affectedPolygonIds.includes(textlinePoly.parentId)) continue

      if (!this.shouldCutElement(textlinePoly.points)) continue

      this.affectedPolygonIds.push(textlinePoly.id)

      const hit = findTextLineRecursive(pcGts.page.regions, textlinePoly.id)
      if (!hit) continue

      const result = this.cutStandaloneTextLine(hit.textLine, hit.parentTextRegion)
      cutCount += result.cutCount
      deletedCount += result.deletedCount
      createdCount += result.createdCount
    }

    if (ctx?.canvasId && this.affectedPolygonIds.length > 0) {
      invalidateMultiplePolygonGeometry(ctx.canvasId, this.affectedPolygonIds)
    }

    session.document.value = new PcGts(pcGts.metadata, pcGts.page, pcGts.pcGtsId)
    rebuildSpatialIndexFromPcGts(session)
    visibilityService.clearCache()

    if (cutCount > 0 || deletedCount > 0) {
      const editorUiStore = useEditorUiStore()
      editorUiStore.bumpReadingOrderVersion()
    }

    return { cutCount, deletedCount, createdCount }
  }

  undo(ctx?: CommandContext): void {
    const session = ctx?.session
    const pcGts = session?.document.value
    if (!session || !pcGts || !this.undoSnapshot) return

    if (ctx?.canvasId && this.affectedPolygonIds.length > 0) {
      invalidateMultiplePolygonGeometry(ctx.canvasId, this.affectedPolygonIds)
    }

    pcGts.page.regions = this.undoSnapshot.regions

    if (this.undoSnapshot.readingOrderElements && pcGts.page.readingOrder?.root) {
      pcGts.page.readingOrder.root.elements = this.undoSnapshot.readingOrderElements
    }

    session.document.value = new PcGts(pcGts.metadata, pcGts.page, pcGts.pcGtsId)
    rebuildSpatialIndexFromPcGts(session)
    visibilityService.clearCache()

    const editorUiStore = useEditorUiStore()
    editorUiStore.bumpReadingOrderVersion()
  }

  getDescription(): string {
    const modeLabel = this.mode === 'line'
      ? 'Cut Line'
      : this.mode === 'polygon' ? 'Cut Polygon' : 'Cut Rectangle'
    return `${modeLabel} operation`
  }

  /**
   * Check if an element should be cut based on intersection with cut shape
   */
  private shouldCutElement(points: Point[]): boolean {
    if (this.mode === 'line') {
      return this.doesLineIntersectForSplit(points)
    } else {
      return doPolygonsIntersect(points, this.cutPoints)
    }
  }

  /**
   * Cut a region and all its children (textlines and their baselines)
   */
  private cutRegionWithChildren(
    region: Region,
    parentRegion: Region | null,
    pcGts: PcGts
  ): { cutCount: number, deletedCount: number, createdCount: number } {
    const regionPoints = region.coords?.points?.map(p => ({ x: p[0], y: p[1] })) ?? []
    const siblings = parentRegion ? (parentRegion.regions ?? []) : pcGts.page.regions

    const regionCutResult = this.cutPolygon(regionPoints)

    if (regionCutResult.deleted) {
      const index = siblings.findIndex(r => r.id === region.id)
      if (index >= 0) siblings.splice(index, 1)
      this.removeFromReadingOrder(pcGts, region.id)
      return { cutCount: 0, deletedCount: 1, createdCount: 0 }
    }

    if (regionCutResult.pieces.length === 0) {
      return { cutCount: 0, deletedCount: 0, createdCount: 0 }
    }

    if (regionCutResult.pieces.length === 1) {
      const piece = regionCutResult.pieces[0]
      if (piece) {
        region.coords = new PolygonGeometry(piece.map(p => [p.x, p.y] as [number, number]))
      }

      if (isTextRegion(region)) {
        this.clipTextLinesToParent(region)
      }

      return { cutCount: 1, deletedCount: 0, createdCount: 0 }
    }

    const textLines: TextLine[] = isTextRegion(region) ? [...(region.textLines ?? [])] : []
    const childRegions: Region[] = [...(region.regions ?? [])]

    if (isTextRegion(region)) {
      region.textLines = []
    }
    region.regions = []

    const sortedPieces = regionCutResult.pieces
      .map((points, index) => ({ points, index, area: calculatePolygonArea(points) }))
      .sort((a, b) => b.area - a.area)

    const regionPieces: Array<{ region: Region, points: Point[], isOriginal: boolean }> = []

    for (let i = 0; i < sortedPieces.length; i++) {
      const piece = sortedPieces[i]
      if (!piece) continue

      const isOriginal = i === 0 // Largest piece inherits original ID

      if (isOriginal) {
        region.coords = new PolygonGeometry(piece.points.map(p => [p.x, p.y] as [number, number]))
        regionPieces.push({ region, points: piece.points, isOriginal: true })
      } else {
        const newRegion = this.cloneRegion(region, this.generateId(), piece.points)
        siblings.push(newRegion)
        this.addToReadingOrderAfter(pcGts, region.id, newRegion.id)
        regionPieces.push({ region: newRegion, points: piece.points, isOriginal: false })
      }
    }

    for (const textLine of textLines) {
      const textLinePoints = textLine.coords?.points?.map(p => ({ x: p[0], y: p[1] })) ?? []

      const textLineCutResult = this.cutPolygon(textLinePoints)

      if (textLineCutResult.deleted) {
        continue
      }

      const originalBaseline = textLine.baseline
      const baselinePoints = originalBaseline?.points?.points?.map(p => ({ x: p[0], y: p[1] })) ?? []

      if (textLineCutResult.pieces.length === 0) {
        const bestRegion = this.findBestMatchingRegion(textLinePoints, regionPieces)
        if (bestRegion && isTextRegion(bestRegion.region)) {
          if (!bestRegion.region.textLines) bestRegion.region.textLines = []

          const clippedTextLine = this.clipTextLineToParent(textLine, bestRegion.points)
          if (clippedTextLine) {
            bestRegion.region.textLines.push(clippedTextLine)
          }
        }
      } else if (textLineCutResult.pieces.length === 1) {
        const piece = textLineCutResult.pieces[0]
        if (piece) {
          const bestRegion = this.findBestMatchingRegion(piece, regionPieces)
          if (bestRegion && isTextRegion(bestRegion.region)) {
            if (!bestRegion.region.textLines) bestRegion.region.textLines = []

            textLine.coords = new PolygonGeometry(piece.map(p => [p.x, p.y] as [number, number]))

            if (baselinePoints.length >= 2) {
              const clippedBaseline = clipPolylineToPolygon(baselinePoints, piece)
              if (clippedBaseline.length >= 2) {
                textLine.baseline = { points: new PolylineGeometry(clippedBaseline.map(p => [p.x, p.y] as [number, number])) }
              } else {
                delete textLine.baseline
              }
            }

            const clippedTextLine = this.clipTextLineToParent(textLine, bestRegion.points)
            if (clippedTextLine) {
              bestRegion.region.textLines.push(clippedTextLine)
            }
          }
        }
      } else {
        for (let i = 0; i < textLineCutResult.pieces.length; i++) {
          const piece = textLineCutResult.pieces[i]
          if (!piece) continue

          const bestRegion = this.findBestMatchingRegion(piece, regionPieces)
          if (!bestRegion || !isTextRegion(bestRegion.region)) continue
          if (!bestRegion.region.textLines) bestRegion.region.textLines = []

          const isFirst = i === 0
          const textLineToUse = isFirst ? textLine : this.cloneTextLine(textLine, this.generateId(), piece)

          if (!isFirst) {
            textLineToUse.coords = new PolygonGeometry(piece.map(p => [p.x, p.y] as [number, number]))
          } else {
            textLine.coords = new PolygonGeometry(piece.map(p => [p.x, p.y] as [number, number]))
          }

          if (baselinePoints.length >= 2) {
            const clippedBaseline = clipPolylineToPolygon(baselinePoints, piece)
            if (clippedBaseline.length >= 2) {
              textLineToUse.baseline = { points: new PolylineGeometry(clippedBaseline.map(p => [p.x, p.y] as [number, number])) }
            } else {
              delete textLineToUse.baseline
            }
          }

          const clippedTextLine = this.clipTextLineToParent(textLineToUse, bestRegion.points)
          if (clippedTextLine) {
            bestRegion.region.textLines.push(clippedTextLine)
          }
        }
      }
    }

    this.distributeChildRegions(childRegions, regionPieces)

    return {
      cutCount: 1,
      deletedCount: 0,
      createdCount: regionPieces.length - 1
    }
  }

  /**
   * Recursively distribute child regions to the correct parent pieces
   */
  private distributeChildRegions(
    childRegions: Region[],
    parentPieces: Array<{ region: Region, points: Point[] }>
  ): void {
    for (const childRegion of childRegions) {
      const childPoints = childRegion.coords?.points?.map(p => ({ x: p[0], y: p[1] })) ?? []
      const grandchildren = [...(childRegion.regions ?? [])]
      childRegion.regions = []

      const childCutResult = this.cutPolygon(childPoints)

      if (childCutResult.deleted) continue

      if (childCutResult.pieces.length <= 1) {
        const points = childCutResult.pieces[0] ?? childPoints
        if (childCutResult.pieces[0]) {
          childRegion.coords = new PolygonGeometry(points.map(p => [p.x, p.y] as [number, number]))
        }
        const bestParent = this.findBestMatchingRegion(points, parentPieces)
        if (bestParent) {
          if (!bestParent.region.regions) bestParent.region.regions = []
          bestParent.region.regions.push(childRegion)
          if (grandchildren.length > 0) {
            this.distributeChildRegions(grandchildren, [{ region: childRegion, points }])
          }
        }
      } else {
        const sortedPieces = childCutResult.pieces
          .map(pts => ({ points: pts, area: calculatePolygonArea(pts) }))
          .sort((a, b) => b.area - a.area)

        const childPieces: Array<{ region: Region, points: Point[] }> = []

        for (let i = 0; i < sortedPieces.length; i++) {
          const piece = sortedPieces[i]
          if (!piece) continue

          const bestParent = this.findBestMatchingRegion(piece.points, parentPieces)
          if (!bestParent) continue
          if (!bestParent.region.regions) bestParent.region.regions = []

          if (i === 0) {
            childRegion.coords = new PolygonGeometry(piece.points.map(p => [p.x, p.y] as [number, number]))
            bestParent.region.regions.push(childRegion)
            childPieces.push({ region: childRegion, points: piece.points })
          } else {
            const newChild = this.cloneRegion(childRegion, this.generateId(), piece.points)
            bestParent.region.regions.push(newChild)
            childPieces.push({ region: newChild, points: piece.points })
          }
        }

        if (grandchildren.length > 0 && childPieces.length > 0) {
          this.distributeChildRegions(grandchildren, childPieces)
        }
      }
    }
  }

  /**
   * Cut a standalone textline (one whose parent region wasn't cut)
   */
  private cutStandaloneTextLine(
    textLine: TextLine,
    parentRegion: TextRegion
  ): { cutCount: number, deletedCount: number, createdCount: number } {
    const textLinePoints = textLine.coords?.points?.map(p => ({ x: p[0], y: p[1] })) ?? []
    const siblings = parentRegion.textLines ?? []

    const cutResult = this.cutPolygon(textLinePoints)

    if (cutResult.deleted) {
      const index = siblings.findIndex(tl => tl.id === textLine.id)
      if (index >= 0) siblings.splice(index, 1)
      return { cutCount: 0, deletedCount: 1, createdCount: 0 }
    }

    if (cutResult.pieces.length === 0) {
      return { cutCount: 0, deletedCount: 0, createdCount: 0 }
    }

    const originalBaseline = textLine.baseline
    const baselinePoints = originalBaseline?.points?.points?.map(p => ({ x: p[0], y: p[1] })) ?? []

    if (cutResult.pieces.length === 1) {
      const piece = cutResult.pieces[0]
      if (piece) {
        textLine.coords = new PolygonGeometry(piece.map(p => [p.x, p.y] as [number, number]))

        if (baselinePoints.length >= 2) {
          const clippedBaseline = clipPolylineToPolygon(baselinePoints, piece)
          if (clippedBaseline.length >= 2) {
            textLine.baseline = { points: new PolylineGeometry(clippedBaseline.map(p => [p.x, p.y] as [number, number])) }
          } else {
            delete textLine.baseline
          }
        }
      }
      return { cutCount: 1, deletedCount: 0, createdCount: 0 }
    }

    const sortedPieces = cutResult.pieces
      .map((points, index) => ({ points, index, area: calculatePolygonArea(points) }))
      .sort((a, b) => b.area - a.area)

    let createdCount = 0

    for (let i = 0; i < sortedPieces.length; i++) {
      const piece = sortedPieces[i]
      if (!piece) continue

      const isOriginal = i === 0

      if (isOriginal) {
        textLine.coords = new PolygonGeometry(piece.points.map(p => [p.x, p.y] as [number, number]))

        if (baselinePoints.length >= 2) {
          const clippedBaseline = clipPolylineToPolygon(baselinePoints, piece.points)
          if (clippedBaseline.length >= 2) {
            textLine.baseline = { points: new PolylineGeometry(clippedBaseline.map(p => [p.x, p.y] as [number, number])) }
          } else {
            delete textLine.baseline
          }
        }
      } else {
        const newTextLine = this.cloneTextLine(textLine, this.generateId(), piece.points)

        if (baselinePoints.length >= 2) {
          const clippedBaseline = clipPolylineToPolygon(baselinePoints, piece.points)
          if (clippedBaseline.length >= 2) {
            newTextLine.baseline = { points: new PolylineGeometry(clippedBaseline.map(p => [p.x, p.y] as [number, number])) }
          }
        }

        siblings.push(newTextLine)
        createdCount++
      }
    }

    return { cutCount: 1, deletedCount: 0, createdCount }
  }

  /**
   * Cut a polygon using the current cut shape
   */
  private cutPolygon(points: Point[]): { deleted: boolean, pieces: Point[][] } {
    if (this.mode === 'line') {
      const result = splitPolygonByLine(points, this.cutPoints, this.minAreaThreshold)
      if (!result.success || result.resultPolygons.length === 0) {
        return { deleted: false, pieces: [] }
      }
      return { deleted: false, pieces: result.resultPolygons }
    } else {
      const result = subtractPolygon(points, this.cutPoints, this.minAreaThreshold)
      if (result.fullyEnclosed) {
        return { deleted: true, pieces: [] }
      }
      if (!result.success || result.resultPolygons.length === 0) {
        return { deleted: false, pieces: [] }
      }
      return { deleted: false, pieces: result.resultPolygons }
    }
  }

  /**
   * Find the region piece that best matches a polygon (based on actual intersection area)
   * Uses multiple strategies to find the best match:
   * 1. Check if centroid is inside a region piece
   * 2. Calculate actual polygon intersection area percentage
   * 3. Find the closest region by centroid distance
   */
  private findBestMatchingRegion(
    points: Point[],
    regionPieces: Array<{ region: Region, points: Point[] }>
  ): { region: Region, points: Point[] } | null {
    if (regionPieces.length === 0) return null
    if (regionPieces.length === 1) return regionPieces[0] ?? null

    const centroid = this.calculateCentroid(points)

    for (const piece of regionPieces) {
      if (isPointInPolygon(centroid, piece.points)) {
        return piece
      }
    }

    let bestMatch: { region: Region, points: Point[] } | null = null
    let maxOverlapPercentage = 0

    for (const piece of regionPieces) {
      const overlapPercentage = calculatePolygonOverlapPercentage(points, piece.points)
      if (overlapPercentage > maxOverlapPercentage) {
        maxOverlapPercentage = overlapPercentage
        bestMatch = piece
      }
    }

    if (bestMatch && maxOverlapPercentage > 0) {
      return bestMatch
    }

    let minDistance = Infinity
    for (const piece of regionPieces) {
      const pieceCentroid = this.calculateCentroid(piece.points)
      const distance = Math.sqrt(
        Math.pow(centroid.x - pieceCentroid.x, 2)
        + Math.pow(centroid.y - pieceCentroid.y, 2)
      )
      if (distance < minDistance) {
        minDistance = distance
        bestMatch = piece
      }
    }

    return bestMatch
  }

  /**
   * Clip a textline to fit within parent bounds
   */
  private clipTextLineToParent(textLine: TextLine, parentPoints: Point[]): TextLine | null {
    const textLinePoints = textLine.coords?.points?.map(p => ({ x: p[0], y: p[1] })) ?? []

    const centroid = this.calculateCentroid(textLinePoints)
    if (!isPointInPolygon(centroid, parentPoints)) {
      if (!doPolygonsIntersect(textLinePoints, parentPoints)) {
        return null
      }
    }

    return textLine
  }

  /**
   * Clip all textlines in a region to fit within the region bounds
   */
  private clipTextLinesToParent(region: TextRegion): void {
    if (!region.textLines) return

    const regionPoints = region.coords?.points?.map(p => ({ x: p[0], y: p[1] })) ?? []
    const validTextLines: TextLine[] = []

    for (const textLine of region.textLines) {
      const textLinePoints = textLine.coords?.points?.map(p => ({ x: p[0], y: p[1] })) ?? []

      if (textLinePoints.length > 0) {
        const centroid = this.calculateCentroid(textLinePoints)
        if (isPointInPolygon(centroid, regionPoints) || doPolygonsIntersect(textLinePoints, regionPoints)) {
          if (textLine.baseline?.points) {
            const baselinePoints = textLine.baseline.points.points.map(p => ({ x: p[0], y: p[1] }))
            const clippedBaseline = clipPolylineToPolygon(baselinePoints, textLinePoints)
            if (clippedBaseline.length >= 2) {
              textLine.baseline = { points: new PolylineGeometry(clippedBaseline.map(p => [p.x, p.y] as [number, number])) }
            } else {
              delete textLine.baseline
            }
          }
          validTextLines.push(textLine)
        }
      }
    }

    region.textLines = validTextLines
  }

  /**
   * Check if the cut line would split the polygon (crosses from one side to the other)
   */
  private doesLineIntersectForSplit(polygon: Point[]): boolean {
    if (this.cutPoints.length < 2 || polygon.length < 3) return false

    let intersectionCount = 0

    for (let i = 0; i < this.cutPoints.length - 1; i++) {
      const lineStart = this.cutPoints[i]
      const lineEnd = this.cutPoints[i + 1]
      if (!lineStart || !lineEnd) continue

      for (let j = 0; j < polygon.length; j++) {
        const polyStart = polygon[j]
        const polyEnd = polygon[(j + 1) % polygon.length]
        if (!polyStart || !polyEnd) continue

        if (this.segmentsIntersect(lineStart, lineEnd, polyStart, polyEnd)) {
          intersectionCount++
        }
      }
    }

    return intersectionCount >= 2
  }

  /**
   * Check if two line segments intersect
   */
  private segmentsIntersect(a1: Point, a2: Point, b1: Point, b2: Point): boolean {
    const d1 = this.crossProduct(b2.x - b1.x, b2.y - b1.y, a1.x - b1.x, a1.y - b1.y)
    const d2 = this.crossProduct(b2.x - b1.x, b2.y - b1.y, a2.x - b1.x, a2.y - b1.y)
    const d3 = this.crossProduct(a2.x - a1.x, a2.y - a1.y, b1.x - a1.x, b1.y - a1.y)
    const d4 = this.crossProduct(a2.x - a1.x, a2.y - a1.y, b2.x - a1.x, b2.y - a1.y)

    if (((d1 > 0 && d2 < 0) || (d1 < 0 && d2 > 0))
      && ((d3 > 0 && d4 < 0) || (d3 < 0 && d4 > 0))) {
      return true
    }

    return false
  }

  private crossProduct(x1: number, y1: number, x2: number, y2: number): number {
    return x1 * y2 - y1 * x2
  }

  /**
   * Calculate the centroid (center point) of a polygon
   */
  private calculateCentroid(points: Point[]): Point {
    if (!points || points.length === 0) {
      return { x: 0, y: 0 }
    }

    let sumX = 0
    let sumY = 0
    for (const p of points) {
      sumX += p.x
      sumY += p.y
    }

    return {
      x: sumX / points.length,
      y: sumY / points.length
    }
  }

  /**
   * Clone a region with new ID and coordinates
   */
  private cloneRegion(original: Region, newId: string, newPoints: Point[]): Region {
    const clone: Region = {
      id: newId,
      kind: original.kind,
      coords: new PolygonGeometry(newPoints.map(p => [p.x, p.y] as [number, number])),
      regions: []
    }

    if (isTextRegion(original)) {
      const textClone = clone as TextRegion
      textClone.type = original.type
      textClone.textContentVariants = original.textContentVariants ? [...original.textContentVariants] : []
      textClone.textLines = []
    }

    return clone
  }

  /**
   * Clone a textline with new ID and coordinates
   */
  private cloneTextLine(original: TextLine, newId: string, newPoints: Point[]): TextLine {
    return new TextLine({
      id: newId,
      coords: new PolygonGeometry(newPoints.map(p => [p.x, p.y] as [number, number])),
      textContentVariants: original.textContentVariants ? [...original.textContentVariants] : undefined
    })
  }

  /**
   * Add to reading order after an existing element
   */
  private addToReadingOrderAfter(pcGts: PcGts, afterId: string, newId: string): void {
    if (!pcGts.page.readingOrder?.root?.elements) return
    this.insertIntoReadingOrderAfter(pcGts.page.readingOrder.root.elements, afterId, newId)
  }

  private insertIntoReadingOrderAfter(elements: ReadingOrderNode[], afterId: string, newId: string): boolean {
    for (let i = 0; i < elements.length; i++) {
      const node = elements[i]
      if (!node) continue

      if (node.kind === 'RegionRef' || node.kind === 'RegionRefIndexed') {
        const ref = node as RegionRef
        if (ref.regionRef === afterId) {
          elements.splice(i + 1, 0, {
            kind: 'RegionRef',
            id: `rr_${newId}`,
            regionRef: newId
          } as RegionRef)
          return true
        }
      } else {
        const group = node as ReadingOrderGroup
        if (group.elements && this.insertIntoReadingOrderAfter(group.elements, afterId, newId)) {
          return true
        }
      }
    }
    return false
  }

  /**
   * Remove from reading order
   */
  private removeFromReadingOrder(pcGts: PcGts, id: string): void {
    if (!pcGts.page.readingOrder?.root?.elements) return
    this.removeFromReadingOrderRecursive(pcGts.page.readingOrder.root.elements, id)
  }

  private removeFromReadingOrderRecursive(elements: ReadingOrderNode[], id: string): void {
    for (let i = elements.length - 1; i >= 0; i--) {
      const node = elements[i]
      if (!node) continue

      if (node.kind === 'RegionRef' || node.kind === 'RegionRefIndexed') {
        const ref = node as RegionRef
        if (ref.regionRef === id) {
          elements.splice(i, 1)
        }
      } else {
        const group = node as ReadingOrderGroup
        if (group.elements) {
          this.removeFromReadingOrderRecursive(group.elements, id)
        }
      }
    }
  }

  /**
   * Generate a unique ID for new elements
   */
  private generateId(): string {
    return `cut_${Date.now()}_${Math.random().toString(36).substr(2, 9)}`
  }

  /**
   * Deep clone regions array for undo
   */
  private deepCloneRegions(regions: Region[]): Region[] {
    return JSON.parse(JSON.stringify(regions, (key, value) => {
      if (value instanceof PolygonGeometry || value instanceof PolylineGeometry) {
        return { __type: value.constructor.name, points: value.points }
      }
      return value
    }), (key, value) => {
      if (value && value.__type === 'Polygon') {
        return new PolygonGeometry(value.points)
      }
      if (value && value.__type === 'Polyline') {
        return new PolylineGeometry(value.points)
      }
      return value
    })
  }

  /**
   * Deep clone reading order for undo
   */
  private deepCloneReadingOrder(elements: ReadingOrderNode[]): ReadingOrderNode[] {
    return JSON.parse(JSON.stringify(elements))
  }
}
