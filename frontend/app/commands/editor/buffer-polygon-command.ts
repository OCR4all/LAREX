import type { Command, CommandContext } from './types'
import { PcGts } from '@/models/editor'
import { Polygon } from '@/models/editor/geometry'
import { visibilityService } from '@/services/editor/visibility-service'
import { findRegionRecursive, findTextLineRecursive, rebuildSpatialIndexFromPcGts } from '@/utils/editor/pcgts-editor-primitives'

export interface BufferPolygonCommandData {
  elementId: string
  elementType: 'region' | 'textline'
  distance: number // positive = expand, negative = shrink
}

export class BufferPolygonCommand implements Command {
  private data: BufferPolygonCommandData
  private originalPoints: [number, number][] | null = null

  constructor(data: BufferPolygonCommandData) {
    this.data = data
  }

  execute(ctx?: CommandContext): void {
    const pcGts = ctx?.session?.document.value
    if (!pcGts) return

    const coords = this.getCoords(pcGts)
    if (!coords) return

    this.originalPoints = [...coords.points]
    const buffered = new Polygon(coords.points).buffer(this.data.distance)
    coords.points = buffered.points

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
    return this.data.distance > 0 ? `Expand ${this.data.elementType}` : `Shrink ${this.data.elementType}`
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
