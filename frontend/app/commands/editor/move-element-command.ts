import type { Command, CommandContext } from './types'
import { PcGts, isTextRegion } from '@/models/editor'
import type { Point, Region } from '@/models/editor'
import { visibilityService } from '@/services/editor/visibility-service'
import {
  findRegionRecursive,
  findTextLineRecursive,
  type TextLineHit,
  rebuildSpatialIndexFromPcGts,
  baselineIdForTextLineId,
  textLineIdFromBaselineId
} from '@/utils/editor/pcgts-editor-primitives'

export interface MoveElementCommandData {
  elementId: string
  elementType: 'polygon' | 'polyline'
  delta: Point
  moveWithChildren?: boolean
}

interface SavedPoints {
  id: string
  type: 'region' | 'textline' | 'baseline'
  points: [number, number][]
}

export class MoveElementCommand implements Command {
  private elementId: string
  private elementType: 'polygon' | 'polyline'
  private delta: Point
  private moveWithChildren: boolean
  private savedPoints: SavedPoints[] = []

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

    if (this.elementType === 'polyline') {
      this.moveBaseline(pcGts)
    } else {
      this.movePolygon(pcGts)
    }

    session.document.value = new PcGts(pcGts.metadata, pcGts.page, pcGts.pcGtsId)
    rebuildSpatialIndexFromPcGts(session)
    visibilityService.clearCache()
  }

  private movePolygon(pcGts: PcGts): void {
    const textLineHit = findTextLineRecursive(pcGts.page.regions, this.elementId)
    if (textLineHit) {
      this.saveAndMoveTextLine(textLineHit)
      return
    }

    const regionHit = findRegionRecursive(pcGts.page.regions, this.elementId)
    if (!regionHit) return

    this.saveAndMoveRegion(regionHit.region)

    if (this.moveWithChildren) {
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
  }

  private saveAndMoveTextLine(hit: TextLineHit): void {
    this.savedPoints.push({
      id: hit.textLine.id,
      type: 'textline',
      points: [...hit.textLine.coords.points]
    })
    hit.textLine.coords.points = this.applyDelta(hit.textLine.coords.points)

    if (this.moveWithChildren && hit.textLine.baseline) {
      this.savedPoints.push({
        id: baselineIdForTextLineId(hit.textLine.id),
        type: 'baseline',
        points: [...hit.textLine.baseline.points.points]
      })
      hit.textLine.baseline.points.points = this.applyDelta(hit.textLine.baseline.points.points)
    }
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

        if (tl.baseline) {
          this.savedPoints.push({
            id: baselineIdForTextLineId(tl.id),
            type: 'baseline',
            points: [...tl.baseline.points.points]
          })
          tl.baseline.points.points = this.applyDelta(tl.baseline.points.points)
        }
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
      } else if (saved.type === 'textline') {
        const hit = findTextLineRecursive(pcGts.page.regions, saved.id)
        if (hit) {
          hit.textLine.coords.points = [...saved.points]
        }
      } else {
        const hit = findRegionRecursive(pcGts.page.regions, saved.id)
        if (hit) {
          hit.region.coords.points = [...saved.points]
        }
      }
    }

    session.document.value = new PcGts(pcGts.metadata, pcGts.page, pcGts.pcGtsId)
    rebuildSpatialIndexFromPcGts(session)
    visibilityService.clearCache()
  }

  getDescription(): string {
    return `Move ${this.elementType}${this.moveWithChildren ? ' with children' : ''}`
  }

  private applyDelta(points: [number, number][]): [number, number][] {
    return points.map(([x, y]) => [x + this.delta.x, y + this.delta.y])
  }
}
