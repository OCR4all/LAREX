import type { Command, CommandContext } from './types'
import { PcGts } from '@/models/editor'
import { visibilityService } from '@/services/editor/visibility-service'
import {
  findTextLineRecursive,
  rebuildSpatialIndexFromPcGts,
  textLineIdFromBaselineId
} from '@/utils/editor/pcgts-editor-primitives'

export interface DeletePolylineCommandData {
  polylineId: string // Baseline ID to delete
}

export class DeletePolylineCommand implements Command {
  private polylineId: string
  private previousBaseline: any | undefined = undefined

  constructor(data: DeletePolylineCommandData) {
    this.polylineId = data.polylineId
  }

  execute(ctx?: CommandContext): void {
    const session = ctx?.session
    const pcGts = session?.document.value
    if (!session || !pcGts) return

    const textLineId = textLineIdFromBaselineId(this.polylineId)
    if (!textLineId) return
    const hit = findTextLineRecursive(pcGts.page.regions, textLineId)
    if (!hit) return

    if (this.previousBaseline === undefined) {
      this.previousBaseline = hit.textLine.baseline
    }

    hit.textLine.baseline = undefined
    session.document.value = new PcGts(pcGts.metadata, pcGts.page, pcGts.pcGtsId)
    rebuildSpatialIndexFromPcGts(session)
    visibilityService.clearCache()
  }

  undo(ctx?: CommandContext): void {
    const session = ctx?.session
    const pcGts = session?.document.value
    if (!session || !pcGts) return

    const textLineId = textLineIdFromBaselineId(this.polylineId)
    if (!textLineId) return
    const hit = findTextLineRecursive(pcGts.page.regions, textLineId)
    if (!hit) return

    hit.textLine.baseline = this.previousBaseline
    session.document.value = new PcGts(pcGts.metadata, pcGts.page, pcGts.pcGtsId)
    rebuildSpatialIndexFromPcGts(session)
    visibilityService.clearCache()
  }

  getDescription(): string {
    return `Delete polyline ${this.polylineId}`
  }
}
