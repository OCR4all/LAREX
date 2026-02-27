import type { Command, CommandContext } from './types'
import { PcGts } from '@/models/editor'
import type { TextRegion } from '@/models/editor'
import { findRegionRecursive } from '@/utils/editor/pcgts-editor-primitives'

export interface ChangeRegionLabelCommandParams {
  regionId: string
  newLabel: string
}

/**
 * Command to change a region's label
 * Supports undo/redo
 */
export class ChangeRegionLabelCommand implements Command {
  private regionId: string
  private newLabel: string
  private oldLabel: string | undefined = undefined

  constructor(params: ChangeRegionLabelCommandParams) {
    this.regionId = params.regionId
    this.newLabel = params.newLabel
  }

  getDescription(): string {
    return `Change region label to "${this.newLabel}"`
  }

  execute(ctx?: CommandContext): void {
    const session = ctx?.session
    const pcGts = session?.document.value
    if (!session || !pcGts) return

    const hit = findRegionRecursive(pcGts.page.regions, this.regionId)
    if (!hit || hit.region.kind !== 'TextRegion') return

    const region = hit.region as TextRegion
    this.oldLabel = region.type
    region.type = this.newLabel
    session.document.value = new PcGts(pcGts.metadata, pcGts.page, pcGts.pcGtsId)
  }

  undo(ctx?: CommandContext): void {
    const session = ctx?.session
    const pcGts = session?.document.value
    if (!session || !pcGts) return

    const hit = findRegionRecursive(pcGts.page.regions, this.regionId)
    if (!hit || hit.region.kind !== 'TextRegion') return

    const region = hit.region as TextRegion
    region.type = this.oldLabel
    session.document.value = new PcGts(pcGts.metadata, pcGts.page, pcGts.pcGtsId)
  }

  redo(): void {
    this.execute()
  }
}
