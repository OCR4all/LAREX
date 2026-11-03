import type { Command, CommandContext } from './types'
import { Glyph, PcGts, TextLine, PolygonType, Word, isTextRegion } from '@/models/editor'
import type { Point, Region, TextRegion, ReadingOrderNode, Relation } from '@/models/editor'
import { Polygon as PolygonGeometry, Polyline as PolylineGeometry } from '@/models/editor/geometry'
import { visibilityService } from '@/services/editor/visibility-service'
import {
  collectRenderablePolygonsFromPcGts,
  findGlyphRecursive,
  findRegionRecursive,
  findTextLineRecursive,
  findWordRecursive,
  rebuildSpatialIndexFromPcGts
} from '@/utils/editor/pcgts-editor-primitives'
import {
  splitPolygonByLine,
  subtractPolygon,
  intersectPolygons,
  doPolygonsIntersect,
  calculatePolygonArea,
  clipPolylineToPolygon,
  isPointInPolygon,
  calculatePolygonOverlapPercentage
} from '@/utils/editor/polygon-clipping'
import { useEditorUiStore } from '@/stores/editor/editor.ui.store'
import { invalidateMultiplePolygonGeometry } from '@/composables/editor/use-geometry-cache-integrations'
import {
  cloneRelations,
  collectRegionIds,
  filterRelationsByExistingRegionIds
} from '@/utils/editor/relations'
import { insertIntoReadingOrderAfter, removeIdsFromReadingOrder } from './reading-order-utils'

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
  /** Optional element IDs that define the current cut level. Descendants of cut regions are still affected. */
  targetElementIds?: string[]
  /** Minimum area threshold - polygons below this area are auto-deleted */
  minAreaThreshold?: number
  /** Keep the overlap with the cut shape instead of subtracting it. */
  intersect?: boolean
}

/**
 * Snapshot of the original document state for undo
 */
interface UndoSnapshot {
  regions: Region[]
  readingOrderElements?: ReadingOrderNode[]
  relations?: Relation[]
}

/** A polygon element nested inside a text line (word) or word (glyph). */
type TextShape = Word | Glyph

interface PlacedPiece<T> {
  element: T
  points: Point[]
}

/**
 * Command for cutting regions, textlines, words, glyphs and baselines using line, polygon, or rectangle shapes.
 *
 * Cut Line: Splits intersected elements into multiple pieces
 * Cut Polygon/Rectangle: Subtracts the shape from elements, deleting fully enclosed ones
 *
 * Features:
 * - When cutting regions, also cuts all child textlines, words, glyphs and baselines
 * - Child elements are properly assigned to the correct parent pieces
 * - Largest resulting polygon inherits the original ID
 * - Auto-deletes polygons below the area threshold
 * - Proper undo/redo support with full document restoration
 */
export class CutElementsCommand implements Command {
  private mode: CutMode
  private cutPoints: Point[]
  private targetElementIds?: string[]
  private intersect: boolean
  private minAreaThreshold: number

  private undoSnapshot: UndoSnapshot | null = null
  private affectedPolygonIds: string[] = []

  constructor(data: CutElementsCommandData) {
    this.intersect = data.intersect ?? false
    this.mode = data.mode
    this.cutPoints = data.cutPoints.map(p => ({ x: p.x, y: p.y }))
    this.targetElementIds = data.targetElementIds
      ? Array.from(new Set(data.targetElementIds))
      : undefined
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
        : undefined,
      relations: cloneRelations(pcGts.page.relations)
    }

    const allPolygons = collectRenderablePolygonsFromPcGts(pcGts)

    this.affectedPolygonIds = []

    let cutCount = 0
    let deletedCount = 0
    let createdCount = 0

    const targetElementIdSet = this.targetElementIds ? new Set(this.targetElementIds) : null

    const regionPolygons = allPolygons.filter(p =>
      p.type === PolygonType.REGION && (!targetElementIdSet || targetElementIdSet.has(p.id))
    )

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

    const textlinePolygons = allPolygons.filter(p =>
      p.type === PolygonType.TEXTLINE && (!targetElementIdSet || targetElementIdSet.has(p.id))
    )

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

    const parentById = new Map(allPolygons.map(p => [p.id, p.parentId]))

    const wordPolygons = allPolygons.filter(p =>
      p.type === PolygonType.WORD && (!targetElementIdSet || targetElementIdSet.has(p.id))
    )

    for (const wordPoly of wordPolygons) {
      if (this.isAffectedOrHasAffectedAncestor(wordPoly.id, parentById)) continue
      if (!this.shouldCutElement(wordPoly.points)) continue

      this.affectedPolygonIds.push(wordPoly.id)

      const hit = findWordRecursive(pcGts.page.regions, wordPoly.id)
      if (!hit) continue

      const glyphs = this.detachGlyphs(hit.word)
      const result = this.cutTextShapeInPlace(hit.word, hit.parentTextLine.words ?? [], (word, id, points) => this.cloneWord(word, id, points))
      this.distributeGlyphs(glyphs, result.pieces)
      cutCount += result.cutCount
      deletedCount += result.deletedCount
      createdCount += result.createdCount
    }

    const glyphPolygons = allPolygons.filter(p =>
      p.type === PolygonType.GLYPH && (!targetElementIdSet || targetElementIdSet.has(p.id))
    )

    for (const glyphPoly of glyphPolygons) {
      if (this.isAffectedOrHasAffectedAncestor(glyphPoly.id, parentById)) continue
      if (!this.shouldCutElement(glyphPoly.points)) continue

      this.affectedPolygonIds.push(glyphPoly.id)

      const hit = findGlyphRecursive(pcGts.page.regions, glyphPoly.id)
      if (!hit) continue

      const result = this.cutTextShapeInPlace(hit.glyph, hit.parentWord.glyphs ?? [], (glyph, id, points) => this.cloneGlyph(glyph, id, points))
      cutCount += result.cutCount
      deletedCount += result.deletedCount
      createdCount += result.createdCount
    }

    if (ctx?.canvasId && this.affectedPolygonIds.length > 0) {
      invalidateMultiplePolygonGeometry(ctx.canvasId, this.affectedPolygonIds)
    }

    if (this.intersect) {
      const existingIds = collectRegionIds(pcGts.page.regions)
      const removedIds = new Set([...collectRegionIds(this.undoSnapshot.regions)].filter(id => !existingIds.has(id)))
      removeIdsFromReadingOrder(pcGts.page.readingOrder?.root?.elements, removedIds)
    }

    pcGts.page.relations = filterRelationsByExistingRegionIds(
      pcGts.page.relations,
      collectRegionIds(pcGts.page.regions)
    )

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

    pcGts.page.relations = cloneRelations(this.undoSnapshot.relations)

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
    if (this.intersect) return true
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

      if (this.intersect) {
        this.constrainChildrenToRegion(region, pcGts)
      } else if (isTextRegion(region)) {
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

      const words = this.detachWords(textLine)
      const linePieces: Array<PlacedPiece<TextLine>> = []

      const originalBaseline = textLine.baseline
      const baselinePoints = originalBaseline?.points?.points?.map(p => ({ x: p[0], y: p[1] })) ?? []

      if (textLineCutResult.pieces.length === 0) {
        const bestRegion = this.findBestMatchingRegion(textLinePoints, regionPieces)
        if (bestRegion && isTextRegion(bestRegion.region)) {
          if (!bestRegion.region.textLines) bestRegion.region.textLines = []

          const clippedTextLine = this.clipTextLineToParent(textLine, bestRegion.points)
          if (clippedTextLine) {
            bestRegion.region.textLines.push(clippedTextLine)
            linePieces.push({ element: clippedTextLine, points: textLinePoints })
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
              linePieces.push({ element: clippedTextLine, points: piece })
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
            linePieces.push({ element: clippedTextLine, points: piece })
          }
        }
      }

      this.distributeWords(words, linePieces)
    }

    this.distributeChildRegions(childRegions, regionPieces)
    if (this.intersect) {
      for (const piece of regionPieces) this.constrainChildrenToRegion(piece.region, pcGts)
    }

    return {
      cutCount: 1,
      deletedCount: 0,
      createdCount: regionPieces.length - 1
    }
  }

  /** Keep descendants legal when their selected container is clipped or split. */
  private constrainChildrenToRegion(region: Region, pcGts: PcGts): void {
    const previousCutPoints = this.cutPoints
    this.cutPoints = region.coords.points.map(([x, y]) => ({ x, y }))
    try {
      if (isTextRegion(region)) {
        for (const line of [...(region.textLines ?? [])]) {
          this.affectedPolygonIds.push(line.id)
          this.cutStandaloneTextLine(line, region)
        }
      }
      for (const child of [...(region.regions ?? [])]) {
        this.affectedPolygonIds.push(child.id)
        this.cutRegionWithChildren(child, region, pcGts)
      }
    } finally {
      this.cutPoints = previousCutPoints
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
        const textLines = this.intersect && isTextRegion(childRegion) ? [...(childRegion.textLines ?? [])] : []
        if (this.intersect && isTextRegion(childRegion)) childRegion.textLines = []
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

        if (this.intersect) this.distributeIntersectionTextLines(textLines, childPieces)
        if (grandchildren.length > 0 && childPieces.length > 0) {
          this.distributeChildRegions(grandchildren, childPieces)
        }
      }
    }
  }

  /** Preserve nested line geometry in every surviving region piece. */
  private distributeIntersectionTextLines(lines: TextLine[], parents: Array<{ region: Region, points: Point[] }>): void {
    for (const line of lines) {
      const points = line.coords.points.map(([x, y]) => ({ x, y }))
      const baseline = line.baseline?.points.points.map(([x, y]) => ({ x, y }))
      const words = this.detachWords(line)
      const linePieces: Array<PlacedPiece<TextLine>> = []
      let usedOriginal = false
      this.affectedPolygonIds.push(line.id)
      for (const parent of parents) {
        if (!isTextRegion(parent.region)) continue
        for (const piece of intersectPolygons(points, parent.points)) {
          const result = usedOriginal ? this.cloneTextLine(line, this.generateId(), piece) : line
          usedOriginal = true
          result.coords = new PolygonGeometry(piece.map(p => [p.x, p.y]))
          if (baseline) {
            const clipped = clipPolylineToPolygon(baseline, piece)
            if (clipped.length >= 2) {
              result.baseline = { points: new PolylineGeometry(clipped.map(p => [p.x, p.y])) }
            } else {
              delete result.baseline
            }
          }
          parent.region.textLines ??= []
          parent.region.textLines.push(result)
          linePieces.push({ element: result, points: piece })
        }
      }
      this.distributeWords(words, linePieces)
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
    const words = this.detachWords(textLine)

    if (cutResult.pieces.length === 1) {
      const piece = cutResult.pieces[0]
      if (piece) {
        textLine.coords = new PolygonGeometry(piece.map(p => [p.x, p.y] as [number, number]))
        this.distributeWords(words, [{ element: textLine, points: piece }])

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
    const linePieces: Array<PlacedPiece<TextLine>> = []

    for (let i = 0; i < sortedPieces.length; i++) {
      const piece = sortedPieces[i]
      if (!piece) continue

      const isOriginal = i === 0

      if (isOriginal) {
        textLine.coords = new PolygonGeometry(piece.points.map(p => [p.x, p.y] as [number, number]))
        linePieces.push({ element: textLine, points: piece.points })

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
        if (this.intersect) delete newTextLine.baseline

        if (baselinePoints.length >= 2) {
          const clippedBaseline = clipPolylineToPolygon(baselinePoints, piece.points)
          if (clippedBaseline.length >= 2) {
            newTextLine.baseline = { points: new PolylineGeometry(clippedBaseline.map(p => [p.x, p.y] as [number, number])) }
          }
        }

        siblings.push(newTextLine)
        linePieces.push({ element: newTextLine, points: piece.points })
        createdCount++
      }
    }

    this.distributeWords(words, linePieces)

    return { cutCount: 1, deletedCount: 0, createdCount }
  }

  /**
   * Cut a word or glyph within its sibling list. The largest piece keeps the
   * original ID; additional pieces are inserted right after it.
   */
  private cutTextShapeInPlace<T extends TextShape>(
    element: T,
    siblings: T[],
    clone: (original: T, newId: string, points: Point[]) => T
  ): { pieces: Array<PlacedPiece<T>>, cutCount: number, deletedCount: number, createdCount: number } {
    const points = element.coords.points.map(([x, y]) => ({ x, y }))
    const cutResult = this.cutPolygon(points)

    if (cutResult.deleted) {
      const index = siblings.findIndex(sibling => sibling.id === element.id)
      if (index >= 0) siblings.splice(index, 1)
      return { pieces: [], cutCount: 0, deletedCount: 1, createdCount: 0 }
    }

    if (cutResult.pieces.length === 0) {
      return { pieces: [{ element, points }], cutCount: 0, deletedCount: 0, createdCount: 0 }
    }

    const sortedPieces = [...cutResult.pieces].sort((a, b) => calculatePolygonArea(b) - calculatePolygonArea(a))
    const pieces: Array<PlacedPiece<T>> = []
    let insertAt = siblings.findIndex(sibling => sibling.id === element.id)

    for (const [i, piece] of sortedPieces.entries()) {
      const target = i === 0 ? element : clone(element, this.generateId(), piece)
      target.coords = new PolygonGeometry(piece.map(p => [p.x, p.y] as [number, number]))
      if (i > 0) {
        insertAt = insertAt >= 0 ? insertAt + 1 : siblings.length
        siblings.splice(insertAt, 0, target)
      }
      pieces.push({ element: target, points: piece })
    }

    return { pieces, cutCount: 1, deletedCount: 0, createdCount: pieces.length - 1 }
  }

  /**
   * Cut a nested word or glyph against the cut shape and assign each piece to
   * the best matching parent piece. When intersecting, the child is clipped to
   * every parent piece it overlaps instead.
   */
  private placeTextShape<T extends TextShape, P extends { points: Point[] }>(
    child: T,
    parents: P[],
    clone: (original: T, newId: string, points: Point[]) => T
  ): Array<PlacedPiece<T> & { parent: P }> {
    const childPoints = child.coords.points.map(([x, y]) => ({ x, y }))
    const placed: Array<PlacedPiece<T> & { parent: P }> = []
    const place = (piece: Point[], parent: P, reshape: boolean) => {
      const element = placed.length === 0 ? child : clone(child, this.generateId(), piece)
      if (reshape) element.coords = new PolygonGeometry(piece.map(p => [p.x, p.y] as [number, number]))
      placed.push({ element, points: piece, parent })
    }

    if (this.intersect) {
      for (const parent of parents) {
        for (const piece of intersectPolygons(childPoints, parent.points)) {
          place(piece, parent, true)
        }
      }
      return placed
    }

    const cutResult = this.cutPolygon(childPoints)
    if (cutResult.deleted) return placed

    const pieces = cutResult.pieces.length > 0
      ? [...cutResult.pieces].sort((a, b) => calculatePolygonArea(b) - calculatePolygonArea(a))
      : [childPoints]

    for (const piece of pieces) {
      const parent = this.findBestMatchingRegion(piece, parents)
      if (parent) place(piece, parent, cutResult.pieces.length > 0)
    }

    return placed
  }

  private distributeWords(words: Word[], linePieces: Array<PlacedPiece<TextLine>>): void {
    if (words.length === 0) return

    for (const word of words) {
      const glyphs = this.detachGlyphs(word)
      const placed = this.placeTextShape(word, linePieces, (original, id, points) => this.cloneWord(original, id, points))
      for (const { element, parent } of placed) {
        parent.element.words ??= []
        parent.element.words.push(element)
      }
      this.distributeGlyphs(glyphs, placed)
    }
  }

  private distributeGlyphs(glyphs: Glyph[], wordPieces: Array<PlacedPiece<Word>>): void {
    if (glyphs.length === 0) return

    for (const glyph of glyphs) {
      const placed = this.placeTextShape(glyph, wordPieces, (original, id, points) => this.cloneGlyph(original, id, points))
      for (const { element, parent } of placed) {
        parent.element.glyphs ??= []
        parent.element.glyphs.push(element)
      }
    }
  }

  private detachWords(textLine: TextLine): Word[] {
    const words = textLine.words ?? []
    if (textLine.words) textLine.words = []
    return words
  }

  private detachGlyphs(word: Word): Glyph[] {
    const glyphs = word.glyphs ?? []
    if (word.glyphs) word.glyphs = []
    return glyphs
  }

  private isAffectedOrHasAffectedAncestor(id: string, parentById: Map<string, string | undefined>): boolean {
    let current: string | undefined = id
    while (current) {
      if (this.affectedPolygonIds.includes(current)) return true
      current = parentById.get(current)
    }
    return false
  }

  /**
   * Cut a polygon using the current cut shape
   */
  private cutPolygon(points: Point[]): { deleted: boolean, pieces: Point[][] } {
    if (this.intersect) {
      const pieces = intersectPolygons(points, this.cutPoints)
      return { deleted: pieces.length === 0, pieces }
    }
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
  private findBestMatchingRegion<P extends { points: Point[] }>(
    points: Point[],
    regionPieces: P[]
  ): P | null {
    if (regionPieces.length === 0) return null
    if (regionPieces.length === 1) return regionPieces[0] ?? null

    const centroid = this.calculateCentroid(points)

    for (const piece of regionPieces) {
      if (isPointInPolygon(centroid, piece.points)) {
        return piece
      }
    }

    let bestMatch: P | null = null
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
      ...(this.intersect ? original : {}),
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
      ...(this.intersect ? original : {}),
      // Words are redistributed across the line pieces separately.
      words: undefined,
      id: newId,
      coords: new PolygonGeometry(newPoints.map(p => [p.x, p.y] as [number, number])),
      textContentVariants: original.textContentVariants ? [...original.textContentVariants] : undefined
    })
  }

  /**
   * Clone a word piece. Glyphs are redistributed separately.
   */
  private cloneWord(original: Word, newId: string, newPoints: Point[]): Word {
    return Word.fromFields({
      ...(this.intersect ? original : {}),
      glyphs: undefined,
      id: newId,
      coords: new PolygonGeometry(newPoints.map(p => [p.x, p.y] as [number, number])),
      textContentVariants: original.textContentVariants ? [...original.textContentVariants] : undefined
    })
  }

  /**
   * Clone a glyph piece
   */
  private cloneGlyph(original: Glyph, newId: string, newPoints: Point[]): Glyph {
    return Glyph.fromFields({
      ...(this.intersect ? original : {}),
      id: newId,
      coords: new PolygonGeometry(newPoints.map(p => [p.x, p.y] as [number, number])),
      unicode: original.unicode,
      textContentVariants: original.textContentVariants ? [...original.textContentVariants] : undefined
    })
  }

  /**
   * Add to reading order after an existing element
   */
  private addToReadingOrderAfter(pcGts: PcGts, afterId: string, newId: string): void {
    insertIntoReadingOrderAfter(pcGts.page.readingOrder?.root?.elements, afterId, newId)
  }

  /**
   * Remove from reading order
   */
  private removeFromReadingOrder(pcGts: PcGts, id: string): void {
    removeIdsFromReadingOrder(pcGts.page.readingOrder?.root?.elements, new Set([id]))
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
