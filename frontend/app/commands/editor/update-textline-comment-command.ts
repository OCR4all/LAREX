import type { Command, CommandContext } from './types'
import { PcGts } from '@/models/editor'
import { findTextLineRecursive } from '@/utils/editor/pcgts-editor-primitives'

export interface UpdateTextlineCommentCommandData {
  textlineId: string
  comment?: string
}

export class UpdateTextlineCommentCommand implements Command {
  private readonly textlineId: string
  private readonly comment: string | undefined
  private originalComment: string | undefined
  private capturedOriginalComment = false

  constructor(data: UpdateTextlineCommentCommandData) {
    this.textlineId = data.textlineId
    this.comment = data.comment
  }

  execute(ctx?: CommandContext): void {
    const session = ctx?.session
    const pcGts = session?.document.value
    if (!session || !pcGts) return

    const hit = findTextLineRecursive(pcGts.page.regions, this.textlineId)
    if (!hit) return

    if (!this.capturedOriginalComment) {
      this.originalComment = hit.textLine.comments
      this.capturedOriginalComment = true
    }

    hit.textLine.comments = this.comment
    pcGts.metadata?.touch?.()
    session.document.value = new PcGts(pcGts.metadata, pcGts.page, pcGts.pcGtsId)
  }

  undo(ctx?: CommandContext): void {
    if (!this.capturedOriginalComment) return

    const session = ctx?.session
    const pcGts = session?.document.value
    if (!session || !pcGts) return

    const hit = findTextLineRecursive(pcGts.page.regions, this.textlineId)
    if (!hit) return

    hit.textLine.comments = this.originalComment
    pcGts.metadata?.touch?.()
    session.document.value = new PcGts(pcGts.metadata, pcGts.page, pcGts.pcGtsId)
  }

  getDescription(): string {
    return `Update text-line comment (${this.textlineId})`
  }
}
