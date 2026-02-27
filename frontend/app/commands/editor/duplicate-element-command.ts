import type { Command, CommandContext } from './types'
import { PcGts, TextLine } from '@/models/editor'
import type { Point, TextRegion } from '@/models/editor'
import { Polygon, Polyline } from '@/models/editor/geometry'
import { toPlainPoints } from './utils'
import { visibilityService } from '@/services/editor/visibility-service'
import {
  findRegionRecursive,
  findTextLineRecursive,
  rebuildSpatialIndexFromPcGts,
  baselineIdForTextLineId
} from '@/utils/editor/pcgts-editor-primitives'

const DUPLICATE_OFFSET = 20

export interface DuplicateElementCommandData {
  elementId: string
  elementType: 'polygon' | 'polyline'
  parentId?: string
}

export class DuplicateElementCommand implements Command {
  private elementId: string
  private elementType: 'polygon' | 'polyline'
  private parentId?: string
  private createdId: string | null = null

  constructor(data: DuplicateElementCommandData) {
    this.elementId = data.elementId
    this.elementType = data.elementType
    this.parentId = data.parentId
  }

  execute(ctx?: CommandContext): { id: string } | undefined {
    const session = ctx?.session
    const pcGts = session?.document.value
    if (!session || !pcGts) return undefined

    if (this.elementType === 'polyline') {
      return this.duplicateBaseline(pcGts, session)
    }
    return this.duplicatePolygon(pcGts, session)
  }

  private duplicatePolygon(pcGts: PcGts, session: any): { id: string } | undefined {
    const textLineHit = findTextLineRecursive(pcGts.page.regions, this.elementId)
    if (textLineHit) {
      return this.duplicateTextLine(pcGts, session, textLineHit)
    }

    const regionHit = findRegionRecursive(pcGts.page.regions, this.elementId)
    if (!regionHit) return undefined

    const newId = this.generateId()
    this.createdId = newId

    const offsetPoints = this.offsetPoints(
      regionHit.region.coords.points.map(([x, y]) => ({ x, y }))
    )

    const newRegion: TextRegion = {
      id: newId,
      kind: regionHit.region.kind,
      type: regionHit.region.type,
      coords: new Polygon(offsetPoints.map(p => [p.x, p.y])),
      regions: [],
      textLines: [],
      textContentVariants: []
    }

    if (regionHit.parent) {
      regionHit.parent.regions = regionHit.parent.regions ?? []
      regionHit.parent.regions.push(newRegion)
    } else {
      pcGts.page.regions.push(newRegion)
    }

    session.document.value = new PcGts(pcGts.metadata, pcGts.page, pcGts.pcGtsId)
    rebuildSpatialIndexFromPcGts(session)
    visibilityService.clearCache()
    return { id: newId }
  }

  private duplicateTextLine(pcGts: PcGts, session: any, hit: any): { id: string } | undefined {
    const newId = this.generateId()
    this.createdId = newId

    const offsetPoints = this.offsetPoints(
      hit.textLine.coords.points.map(([x, y]: [number, number]) => ({ x, y }))
    )

    const newTextLine = new TextLine({
      id: newId,
      coords: new Polygon(offsetPoints.map(p => [p.x, p.y]))
    })

    hit.parentTextRegion.textLines = hit.parentTextRegion.textLines ?? []
    hit.parentTextRegion.textLines.push(newTextLine)

    session.document.value = new PcGts(pcGts.metadata, pcGts.page, pcGts.pcGtsId)
    rebuildSpatialIndexFromPcGts(session)
    visibilityService.clearCache()
    return { id: newId }
  }

  private duplicateBaseline(pcGts: PcGts, session: any): { id: string } | undefined {
    const textLineId = this.parentId
    if (!textLineId) return undefined

    const hit = findTextLineRecursive(pcGts.page.regions, textLineId)
    if (!hit || !hit.textLine.baseline) return undefined

    const newTextLineId = this.generateId()
    const newBaselineId = baselineIdForTextLineId(newTextLineId)
    this.createdId = newTextLineId

    const textLinePoints = hit.textLine.coords.points.map(([x, y]: [number, number]) => ({ x, y }))
    const baselinePoints = hit.textLine.baseline.points.points.map(([x, y]: [number, number]) => ({ x, y }))

    const newTextLine = new TextLine({
      id: newTextLineId,
      coords: new Polygon(this.offsetPoints(textLinePoints).map(p => [p.x, p.y])),
      baseline: {
        points: new Polyline(this.offsetPoints(baselinePoints).map(p => [p.x, p.y]))
      }
    })

    hit.parentTextRegion.textLines = hit.parentTextRegion.textLines ?? []
    hit.parentTextRegion.textLines.push(newTextLine)

    session.document.value = new PcGts(pcGts.metadata, pcGts.page, pcGts.pcGtsId)
    rebuildSpatialIndexFromPcGts(session)
    visibilityService.clearCache()
    return { id: newBaselineId }
  }

  undo(ctx?: CommandContext): void {
    if (!this.createdId) return

    const session = ctx?.session
    const pcGts = session?.document.value
    if (!session || !pcGts) return

    if (this.elementType === 'polyline') {
      const hit = findTextLineRecursive(pcGts.page.regions, this.createdId)
      if (hit) {
        hit.parentTextRegion.textLines?.splice(hit.index, 1)
      }
    } else {
      const textLineHit = findTextLineRecursive(pcGts.page.regions, this.createdId)
      if (textLineHit) {
        textLineHit.parentTextRegion.textLines?.splice(textLineHit.index, 1)
      } else {
        const regionHit = findRegionRecursive(pcGts.page.regions, this.createdId)
        if (regionHit) {
          const siblings = regionHit.parent?.regions ?? pcGts.page.regions
          siblings.splice(regionHit.index, 1)
        }
      }
    }

    session.document.value = new PcGts(pcGts.metadata, pcGts.page, pcGts.pcGtsId)
    rebuildSpatialIndexFromPcGts(session)
    visibilityService.clearCache()
  }

  getDescription(): string {
    return `Duplicate ${this.elementType}`
  }

  private offsetPoints(points: Point[]): Point[] {
    return points.map(p => ({ x: p.x + DUPLICATE_OFFSET, y: p.y + DUPLICATE_OFFSET }))
  }

  private generateId(): string {
    return `dup_${Date.now()}_${Math.random().toString(36).substr(2, 9)}`
  }
}
