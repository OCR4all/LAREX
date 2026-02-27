import type { Command, CommandContext } from './types'
import { PcGts } from '@/models/editor'
import type { Point } from '@/models/editor'
import { toPlainPoints } from './utils'
import { visibilityService } from '@/services/editor/visibility-service'
import {
  findTextLineRecursive,
  rebuildSpatialIndexFromPcGts,
  textLineIdFromBaselineId
} from '@/utils/editor/pcgts-editor-primitives'

export interface UpdatePolylineCommandData {
  polylineId: string // Baseline ID to update
  newPoints: Point[]
}

export class UpdatePolylineCommand implements Command {
  private polylineId: string
  private newPoints: Point[]
  private oldPoints: [number, number][] | null = null

  constructor(data: UpdatePolylineCommandData) {
    this.polylineId = data.polylineId
    this.newPoints = toPlainPoints(data.newPoints)
  }

  execute(ctx?: CommandContext): void {
    const session = ctx?.session
    const pcGts = session?.document.value
    if (!session || !pcGts) return

    const textLineId = textLineIdFromBaselineId(this.polylineId)
    if (!textLineId) return

    const hit = findTextLineRecursive(pcGts.page.regions, textLineId)
    if (!hit || !hit.textLine.baseline) return

    if (this.oldPoints === null) {
      this.oldPoints = [...hit.textLine.baseline.points.points]
    }

    hit.textLine.baseline.points.points = this.newPoints.map(p => [p.x, p.y])
    session.document.value = new PcGts(pcGts.metadata, pcGts.page, pcGts.pcGtsId)
    rebuildSpatialIndexFromPcGts(session)
    visibilityService.clearCache()
  }

  undo(ctx?: CommandContext): void {
    if (this.oldPoints === null) return
    const session = ctx?.session
    const pcGts = session?.document.value
    if (!session || !pcGts) return

    const textLineId = textLineIdFromBaselineId(this.polylineId)
    if (!textLineId) return
    const hit = findTextLineRecursive(pcGts.page.regions, textLineId)
    if (!hit || !hit.textLine.baseline) return

    hit.textLine.baseline.points.points = [...this.oldPoints]
    session.document.value = new PcGts(pcGts.metadata, pcGts.page, pcGts.pcGtsId)
    rebuildSpatialIndexFromPcGts(session)
    visibilityService.clearCache()
  }

  getDescription(): string {
    return `Update polyline ${this.polylineId} with ${this.newPoints.length} points`
  }
}
