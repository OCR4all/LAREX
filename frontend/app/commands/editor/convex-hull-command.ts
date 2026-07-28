import type { Command, CommandContext } from './types'
import { PcGts } from '@/models/editor'
import type { Polygon } from '@/models/editor/geometry'
import { invalidatePolygonGeometry } from '@/composables/editor/use-geometry-cache-integrations'
import { visibilityService } from '@/services/editor/visibility-service'
import { findRegionRecursive, findTextLineRecursive, rebuildSpatialIndexFromPcGts } from '@/utils/editor/pcgts-editor-primitives'

export interface ConvexHullCommandData {
  elementId: string
  elementType: 'region' | 'textline'
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
    coords.points = this.computeConvexHull(coords.points)

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
    if (this.data.elementType === 'textline') {
      return findTextLineRecursive(pcGts.page.regions, this.data.elementId)?.textLine.coords ?? null
    }
    return findRegionRecursive(pcGts.page.regions, this.data.elementId)?.region.coords ?? null
  }

  private computeConvexHull(points: [number, number][]): [number, number][] {
    if (points.length < 3) return points

    const sorted = [...points].sort((a, b) => a[0] === b[0] ? a[1] - b[1] : a[0] - b[0])
    const cross = (o: [number, number], a: [number, number], b: [number, number]) =>
      (a[0] - o[0]) * (b[1] - o[1]) - (a[1] - o[1]) * (b[0] - o[0])

    const lower: [number, number][] = []
    for (const p of sorted) {
      while (lower.length >= 2 && cross(lower[lower.length - 2]!, lower[lower.length - 1]!, p) <= 0) lower.pop()
      lower.push(p)
    }

    const upper: [number, number][] = []
    for (let i = sorted.length - 1; i >= 0; i--) {
      const p = sorted[i]!
      while (upper.length >= 2 && cross(upper[upper.length - 2]!, upper[upper.length - 1]!, p) <= 0) upper.pop()
      upper.push(p)
    }

    lower.pop()
    upper.pop()
    return [...lower, ...upper]
  }

  private finalize(ctx: CommandContext, pcGts: PcGts): void {
    ctx.session.document.value = new PcGts(pcGts.metadata, pcGts.page, pcGts.pcGtsId)
    rebuildSpatialIndexFromPcGts(ctx.session)
    invalidatePolygonGeometry(ctx.canvasId, this.data.elementId)
    visibilityService.clearCache()
  }
}
