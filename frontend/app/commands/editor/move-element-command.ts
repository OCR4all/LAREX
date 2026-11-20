import type { Command, CommandContext } from './types'
import { PcGts, isTextRegion } from '@/models/editor'
import { Polygon } from '@/models/editor/geometry'
import type { Point, Region, TextLine, Word } from '@/models/editor'
import { invalidateMultiplePolygonGeometry } from '@/composables/editor/use-geometry-cache-integrations'
import { visibilityService } from '@/services/editor/visibility-service'
import {
  findGlyphRecursive,
  findPolygonCoords,
  findRegionRecursive,
  findTextLineRecursive,
  findWordRecursive,
  type TextLineHit,
  rebuildSpatialIndexFromPcGts,
  baselineIdForTextLineId,
  textLineIdFromBaselineId
} from '@/utils/editor/pcgts-editor-primitives'
import { getPageFrame, pageFrameKindForId, type PageFrameKind } from '@/utils/editor/page-frame'

export interface MoveElementCommandData {
  elementId: string
  elementType: 'polygon' | 'polyline'
  delta: Point
  moveWithChildren?: boolean
}

const GUIDE_LINES = ['ascentLine', 'meanLine', 'descentLine'] as const

interface SavedPoints {
  id: string
  type: 'region' | 'textline' | 'word' | 'glyph' | 'baseline' | typeof GUIDE_LINES[number] | PageFrameKind
  points: [number, number][]
}

export class MoveElementCommand implements Command {
  private elementId: string
  private elementType: 'polygon' | 'polyline'
  private delta: Point
  private moveWithChildren: boolean
  private savedPoints: SavedPoints[] = []
  private savedGrids: { region: Region, grid: NonNullable<Region['grid']> }[] = []

  constructor(data: MoveElementCommandData) {
    this.elementId = data.elementId
    this.elementType = data.elementType
    this.delta = { x: data.delta.x, y: data.delta.y }
    this.moveWithChildren = data.moveWithChildren ?? false
  }

  execute(ctx?: CommandContext): void {
    const session = ctx?.session
    const pcGts = session?.document.value
    if (!session || !pcGts) return

    this.savedPoints = []
    this.savedGrids = []

    if (this.elementType === 'polyline') {
      this.moveBaseline(pcGts)
    } else {
      this.movePolygon(pcGts)
    }

    session.document.value = new PcGts(pcGts.metadata, pcGts.page, pcGts.pcGtsId)
    rebuildSpatialIndexFromPcGts(session)
    this.invalidateMovedGeometry(ctx)
    visibilityService.clearCache()
  }

  private movePolygon(pcGts: PcGts): void {
    const frameKind = pageFrameKindForId(this.elementId)
    if (frameKind) {
      const frame = getPageFrame(pcGts.page, frameKind)
      if (!frame) return
      this.savedPoints.push({ id: this.elementId, type: frameKind, points: [...frame.coords.points] })
      frame.coords.points = this.applyDelta(frame.coords.points)
      return
    }

    const textLineHit = findTextLineRecursive(pcGts.page.regions, this.elementId)
    if (textLineHit) {
      this.saveAndMoveTextLine(textLineHit)
      return
    }

    const wordHit = findWordRecursive(pcGts.page.regions, this.elementId)
    if (wordHit) {
      this.saveAndMoveWord(wordHit.word, this.moveWithChildren)
      return
    }

    const glyphHit = findGlyphRecursive(pcGts.page.regions, this.elementId)
    if (glyphHit) {
      this.saveAndMovePolygon(glyphHit.glyph, 'glyph')
      return
    }

    const regionHit = findRegionRecursive(pcGts.page.regions, this.elementId)
    if (!regionHit) return

    this.saveAndMoveRegion(regionHit.region)

    if (this.moveWithChildren || regionHit.region.kind === 'TableRegion') {
      this.moveRegionChildren(regionHit.region)
    }
  }

  private saveAndMoveRegion(region: Region): void {
    this.savedPoints.push({
      id: region.id,
      type: 'region',
      points: [...region.coords.points]
    })
    region.coords.points = this.applyDelta(region.coords.points)
    if (region.grid) {
      this.savedGrids.push({ region, grid: region.grid })
      const shift = (polygon: Polygon | undefined) => polygon ? new Polygon(this.applyDelta(polygon.points), polygon.conf) : undefined
      region.grid = {
        ...region.grid,
        rows: region.grid.rows?.map(row => ({ ...row, points: shift(row.points)! })),
        addPoints: region.grid.addPoints?.map(entry => ({ ...entry, leftPoints: shift(entry.leftPoints), topPoints: shift(entry.topPoints) }))
      }
    }
  }

  private saveAndMoveTextLine(hit: TextLineHit): void {
    this.savedPoints.push({
      id: hit.textLine.id,
      type: 'textline',
      points: [...hit.textLine.coords.points]
    })
    hit.textLine.coords.points = this.applyDelta(hit.textLine.coords.points)

    if (this.moveWithChildren) {
      this.moveTextLineChildren(hit.textLine)
    }
  }

  private moveTextLineChildren(textLine: TextLine): void {
    if (textLine.baseline) {
      this.savedPoints.push({
        id: baselineIdForTextLineId(textLine.id),
        type: 'baseline',
        points: [...textLine.baseline.points.points]
      })
      textLine.baseline.points.points = this.applyDelta(textLine.baseline.points.points)
    }

    for (const key of GUIDE_LINES) {
      const line = textLine[key]
      if (!line) continue
      this.savedPoints.push({ id: textLine.id, type: key, points: [...line.points.points] })
      line.points.points = this.applyDelta(line.points.points)
    }

    for (const word of textLine.words ?? []) {
      this.saveAndMoveWord(word, true)
    }
  }

  private saveAndMoveWord(word: Word, withGlyphs: boolean): void {
    this.saveAndMovePolygon(word, 'word')
    if (!withGlyphs) return
    for (const glyph of word.glyphs ?? []) {
      this.saveAndMovePolygon(glyph, 'glyph')
    }
  }

  private saveAndMovePolygon(element: { id: string, coords: { points: [number, number][] } }, type: 'word' | 'glyph'): void {
    this.savedPoints.push({
      id: element.id,
      type,
      points: [...element.coords.points]
    })
    element.coords.points = this.applyDelta(element.coords.points)
  }

  private moveRegionChildren(region: Region): void {
    if (region.regions) {
      for (const child of region.regions) {
        this.saveAndMoveRegion(child)
        this.moveRegionChildren(child)
      }
    }

    if (isTextRegion(region) && region.textLines) {
      for (const tl of region.textLines) {
        this.savedPoints.push({
          id: tl.id,
          type: 'textline',
          points: [...tl.coords.points]
        })
        tl.coords.points = this.applyDelta(tl.coords.points)
        this.moveTextLineChildren(tl)
      }
    }
  }

  private moveBaseline(pcGts: PcGts): void {
    const textLineId = textLineIdFromBaselineId(this.elementId)
    if (!textLineId) return

    const hit = findTextLineRecursive(pcGts.page.regions, textLineId)
    if (!hit?.textLine.baseline) return

    this.savedPoints.push({
      id: this.elementId,
      type: 'baseline',
      points: [...hit.textLine.baseline.points.points]
    })
    hit.textLine.baseline.points.points = this.applyDelta(hit.textLine.baseline.points.points)
  }

  undo(ctx?: CommandContext): void {
    if (this.savedPoints.length === 0) return

    const session = ctx?.session
    const pcGts = session?.document.value
    if (!session || !pcGts) return

    for (const saved of this.savedPoints) {
      if (saved.type === 'baseline') {
        const textLineId = textLineIdFromBaselineId(saved.id)
        if (!textLineId) continue

        const hit = findTextLineRecursive(pcGts.page.regions, textLineId)
        if (hit?.textLine.baseline) {
          hit.textLine.baseline.points.points = [...saved.points]
        }
      } else if (saved.type === 'ascentLine' || saved.type === 'meanLine' || saved.type === 'descentLine') {
        const line = findTextLineRecursive(pcGts.page.regions, saved.id)?.textLine[saved.type]
        if (line) {
          line.points.points = [...saved.points]
        }
      } else {
        const coords = findPolygonCoords(pcGts.page, saved.id, saved.type)
        if (coords) {
          coords.points = [...saved.points]
        }
      }
    }

    for (const { region, grid } of this.savedGrids) {
      const current = findRegionRecursive(pcGts.page.regions, region.id)?.region
      if (current) current.grid = grid
    }

    session.document.value = new PcGts(pcGts.metadata, pcGts.page, pcGts.pcGtsId)
    rebuildSpatialIndexFromPcGts(session)
    this.invalidateMovedGeometry(ctx)
    visibilityService.clearCache()
  }

  getDescription(): string {
    return `Move ${this.elementType}${this.moveWithChildren ? ' with children' : ''}`
  }

  private applyDelta(points: [number, number][]): [number, number][] {
    return points.map(([x, y]) => [x + this.delta.x, y + this.delta.y])
  }

  private invalidateMovedGeometry(ctx?: CommandContext): void {
    if (!ctx?.canvasId || this.savedPoints.length === 0) return
    invalidateMultiplePolygonGeometry(ctx.canvasId, this.savedPoints.map(saved => saved.id))
  }
}
