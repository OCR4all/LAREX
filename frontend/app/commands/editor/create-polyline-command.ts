import type { Command, CommandContext } from './types'
import { PcGts } from '@/models/editor'
import type { Point } from '@/models/editor'
import { Polyline } from '@/models/editor/geometry'
import type { Baseline } from '@/models/editor/text'
import { toPlainPoints } from './utils'
import { visibilityService } from '@/services/editor/visibility-service'
import {
  baselineIdForTextLineId,
  textLineIdFromBaselineId,
  findTextLineRecursive,
  rebuildSpatialIndexFromPcGts
} from '@/utils/editor/pcgts-editor-primitives'

export interface CreatePolylineCommandData {
  points: Point[]
  parentId: string
}

export class CreatePolylineCommand implements Command {
  private points: Point[]
  private parentId: string

  private previousBaseline: Baseline | undefined

  constructor(data: CreatePolylineCommandData) {
    this.points = toPlainPoints(data.points)
    this.parentId = data.parentId
  }

  execute(ctx?: CommandContext): { id: string, created: boolean } {
    const session = ctx?.session
    const pcGts = session?.document.value
    const fallbackTextLineId = textLineIdFromBaselineId(this.parentId) ?? this.parentId
    if (!session || !pcGts) return { id: baselineIdForTextLineId(fallbackTextLineId), created: false }

    let targetTextLineId = this.parentId
    let hit = findTextLineRecursive(pcGts.page.regions, targetTextLineId)
    if (!hit) {
      const decodedId = textLineIdFromBaselineId(this.parentId)
      if (decodedId) {
        targetTextLineId = decodedId
        hit = findTextLineRecursive(pcGts.page.regions, targetTextLineId)
      }
    }
    if (!hit) return { id: baselineIdForTextLineId(fallbackTextLineId), created: false }

    if (this.previousBaseline === undefined) {
      this.previousBaseline = hit.textLine.baseline
    }

    hit.textLine.baseline = {
      points: new Polyline(this.points.map(p => [p.x, p.y]))
    }

    session.document.value = new PcGts(pcGts.metadata, pcGts.page, pcGts.pcGtsId)
    rebuildSpatialIndexFromPcGts(session)
    visibilityService.clearCache()
    return { id: baselineIdForTextLineId(targetTextLineId), created: true }
  }

  undo(ctx?: CommandContext): void {
    const session = ctx?.session
    const pcGts = session?.document.value
    if (!session || !pcGts) return

    const targetTextLineId = textLineIdFromBaselineId(this.parentId) ?? this.parentId
    const hit = findTextLineRecursive(pcGts.page.regions, targetTextLineId)
    if (!hit) return

    hit.textLine.baseline = this.previousBaseline

    session.document.value = new PcGts(pcGts.metadata, pcGts.page, pcGts.pcGtsId)
    rebuildSpatialIndexFromPcGts(session)
    visibilityService.clearCache()
  }

  getDescription(): string {
    return `Create polyline with ${this.points.length} points`
  }
}
