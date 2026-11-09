import type { Command, CommandContext } from './types'
import { PcGts } from '@/models/editor'
import type { Polygon } from '@/models/editor/geometry'
import { invalidatePolygonGeometry } from '@/composables/editor/use-geometry-cache-integrations'
import { visibilityService } from '@/services/editor/visibility-service'
import { computeConvexHull } from '@/utils/editor/convex-hull'
import { findPolygonCoordsRecursive, rebuildSpatialIndexFromPcGts, type PolygonElementType } from '@/utils/editor/pcgts-editor-primitives'

export interface ConvexHullCommandData {
  elementId: string
  elementType: PolygonElementType
}

export class ConvexHullCommand implements Command {
  private data: ConvexHullCommandData
  private originalPoints: [number, number][] | null = null

  constructor(data: ConvexHullCommandData) {
    this.data = data
  }

  execute(ctx?: CommandContext): void {
    const pcGts = ctx?.session?.document.value
    if (!pcGts) return

    const coords = this.getCoords(pcGts)
    if (!coords) return

    this.originalPoints = [...coords.points]
    coords.points = computeConvexHull(coords.points)

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
    return `Convex hull ${this.data.elementType}`
  }

  private getCoords(pcGts: PcGts): Polygon | null {
    return findPolygonCoordsRecursive(pcGts.page.regions, this.data.elementId, this.data.elementType)
  }

  private finalize(ctx: CommandContext, pcGts: PcGts): void {
    ctx.session.document.value = new PcGts(pcGts.metadata, pcGts.page, pcGts.pcGtsId)
    rebuildSpatialIndexFromPcGts(ctx.session)
    invalidatePolygonGeometry(ctx.canvasId, this.data.elementId)
    visibilityService.clearCache()
  }
}
