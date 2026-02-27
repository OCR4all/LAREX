import type { Command, CommandContext } from './types'
import { PcGts } from '@/models/editor'
import type { Point } from '@/models/editor'
import { invalidatePolygonGeometry } from '@/composables/editor/use-geometry-cache-integrations'
import { toPlainPoints } from './utils'
import { visibilityService } from '@/services/editor/visibility-service'
import {
  findRegionRecursive,
  findTextLineRecursive,
  rebuildSpatialIndexFromPcGts
} from '@/utils/editor/pcgts-editor-primitives'

export interface UpdatePolygonCommandData {
  polygonId: string // Region ID to update
  newPoints: Point[]
}

export class UpdatePolygonCommand implements Command {
  private polygonId: string
  private newPoints: Point[]
  private originalPoints: [number, number][] | null = null

  constructor(data: UpdatePolygonCommandData) {
    this.polygonId = data.polygonId
    this.newPoints = data.newPoints
  }

  execute(ctx?: CommandContext): void {
    const session = ctx?.session
    const pcGts = session?.document.value
    if (!session || !pcGts) return

    const hitRegion = findRegionRecursive(pcGts.page.regions, this.polygonId)
    if (hitRegion) {
      if (this.originalPoints === null) {
        this.originalPoints = [...hitRegion.region.coords.points]
      }
      hitRegion.region.coords.points = toPlainPoints(this.newPoints).map(p => [p.x, p.y])
    } else {
      const hitTextLine = findTextLineRecursive(pcGts.page.regions, this.polygonId)
      if (!hitTextLine) return

      if (this.originalPoints === null) {
        this.originalPoints = [...hitTextLine.textLine.coords.points]
      }
      hitTextLine.textLine.coords.points = toPlainPoints(this.newPoints).map(p => [p.x, p.y])
    }

    session.document.value = new PcGts(pcGts.metadata, pcGts.page, pcGts.pcGtsId)
    rebuildSpatialIndexFromPcGts(session)

    if (ctx?.canvasId) {
      invalidatePolygonGeometry(ctx.canvasId, this.polygonId)
    }
    visibilityService.clearCache()
  }

  undo(ctx?: CommandContext): void {
    if (this.originalPoints === null) {
      return
    }

    const session = ctx?.session
    const pcGts = session?.document.value
    if (!session || !pcGts) return

    const hitRegion = findRegionRecursive(pcGts.page.regions, this.polygonId)
    if (hitRegion) {
      hitRegion.region.coords.points = [...this.originalPoints]
    } else {
      const hitTextLine = findTextLineRecursive(pcGts.page.regions, this.polygonId)
      if (!hitTextLine) return
      hitTextLine.textLine.coords.points = [...this.originalPoints]
    }

    session.document.value = new PcGts(pcGts.metadata, pcGts.page, pcGts.pcGtsId)
    rebuildSpatialIndexFromPcGts(session)

    if (ctx?.canvasId) {
      invalidatePolygonGeometry(ctx.canvasId, this.polygonId)
    }
    visibilityService.clearCache()
  }

  getDescription(): string {
    return `Update polygon ${this.polygonId} (${this.newPoints.length} points)`
  }
}
