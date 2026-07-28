import type { Command, CommandContext } from './types'
import { PcGts } from '@/models/editor'
import { Polygon } from '@/models/editor/geometry'
import { invalidatePolygonGeometry } from '@/composables/editor/use-geometry-cache-integrations'
import { visibilityService } from '@/services/editor/visibility-service'
import { findRegionRecursive, findTextLineRecursive, rebuildSpatialIndexFromPcGts } from '@/utils/editor/pcgts-editor-primitives'

export interface FitToBoundingBoxCommandData {
  elementId: string
  elementType: 'region' | 'textline'
}

export class FitToBoundingBoxCommand implements Command {
  private data: FitToBoundingBoxCommandData
  private originalPoints: [number, number][] | null = null

  constructor(data: FitToBoundingBoxCommandData) {
    this.data = data
  }

  execute(ctx?: CommandContext): void {
    const pcGts = ctx?.session?.document.value
    if (!pcGts) return

    const coords = this.getCoords(pcGts)
    if (!coords) return

    this.originalPoints = [...coords.points]
    const bbox = new Polygon(coords.points).getBoundingBox()
    coords.points = [
      [bbox.minX, bbox.minY],
      [bbox.maxX, bbox.minY],
      [bbox.maxX, bbox.maxY],
      [bbox.minX, bbox.maxY]
    ]

    this.finalize(ctx!, pcGts)
  }

  undo(ctx?: CommandContext): void {
    if (!this.originalPoints) return
    const pcGts = ctx?.session?.document.value
    if (!pcGts) return

    const coords = this.getCoords(pcGts)
    if (coords) coords.points = this.originalPoints

    this.finalize(ctx!, pcGts)
  }

  getDescription(): string {
    return `Fit ${this.data.elementType} to bounding box`
  }

  private getCoords(pcGts: PcGts): Polygon | null {
    if (this.data.elementType === 'textline') {
      return findTextLineRecursive(pcGts.page.regions, this.data.elementId)?.textLine.coords ?? null
    }
    return findRegionRecursive(pcGts.page.regions, this.data.elementId)?.region.coords ?? null
  }

  private finalize(ctx: CommandContext, pcGts: PcGts): void {
    ctx.session.document.value = new PcGts(pcGts.metadata, pcGts.page, pcGts.pcGtsId)
    rebuildSpatialIndexFromPcGts(ctx.session)
    invalidatePolygonGeometry(ctx.canvasId, this.data.elementId)
    visibilityService.clearCache()
  }
}
