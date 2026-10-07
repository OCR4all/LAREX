import type { Command, CommandContext } from './types'
import { PcGts } from '@/models/editor'
import { findRegionRecursive } from '@/utils/editor/pcgts-editor-primitives'

export class UpdateRegionCommentCommand implements Command {
  private originalComment: string | undefined
  private capturedOriginalComment = false

  constructor(private readonly regionId: string, private readonly comment: string | undefined) {}

  execute(ctx?: CommandContext): void {
    const session = ctx?.session
    const pcGts = session?.document.value
    if (!session || !pcGts) return

    const region = findRegionRecursive(pcGts.page.regions, this.regionId)?.region
    if (!region) return

    if (!this.capturedOriginalComment) {
      this.originalComment = region.comments
      this.capturedOriginalComment = true
    }

    region.comments = this.comment
    pcGts.metadata?.touch?.()
    session.document.value = new PcGts(pcGts.metadata, pcGts.page, pcGts.pcGtsId)
  }

  undo(ctx?: CommandContext): void {
    if (!this.capturedOriginalComment) return

    const session = ctx?.session
    const pcGts = session?.document.value
    if (!session || !pcGts) return

    const region = findRegionRecursive(pcGts.page.regions, this.regionId)?.region
    if (!region) return

    region.comments = this.originalComment
    pcGts.metadata?.touch?.()
    session.document.value = new PcGts(pcGts.metadata, pcGts.page, pcGts.pcGtsId)
  }

  getDescription(): string {
    return `Update region comment (${this.regionId})`
  }
}
