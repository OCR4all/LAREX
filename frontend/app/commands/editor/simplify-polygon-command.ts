import type { Command, CommandContext } from './types'
import { PcGts } from '@/models/editor'
import { Polygon } from '@/models/editor/geometry'
import { visibilityService } from '@/services/editor/visibility-service'
import { findRegionRecursive, findTextLineRecursive, rebuildSpatialIndexFromPcGts } from '@/utils/editor/pcgts-editor-primitives'

export interface SimplifyPolygonCommandData {
  elementId: string
  elementType: 'region' | 'textline'
  tolerance?: number
}

export class SimplifyPolygonCommand implements Command {
  private data: SimplifyPolygonCommandData
  private originalPoints: [number, number][] | null = null

  constructor(data: SimplifyPolygonCommandData) {
    this.data = { tolerance: 0.001, ...data }
  }

  execute(ctx?: CommandContext): void {
    const pcGts = ctx?.session?.document.value
    if (!pcGts) return

    const coords = this.getCoords(pcGts)
    if (!coords) return

    this.originalPoints = [...coords.points]
    const simplified = new Polygon(coords.points).simplify(this.data.tolerance!)
    coords.points = simplified.points

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
    return `Simplify ${this.data.elementType}`
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
    visibilityService.clearCache()
  }
}
