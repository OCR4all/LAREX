import type { Command, CommandContext } from './types'
import { PcGts } from '@/models/editor'
import { findGlyphRecursive, findTextLineRecursive, findWordRecursive } from '@/utils/editor/pcgts-editor-primitives'

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

    const target = findCommentOwner(pcGts, this.textlineId)
    if (!target) return

    if (!this.capturedOriginalComment) {
      this.originalComment = target.comments
      this.capturedOriginalComment = true
    }

    target.comments = this.comment
    pcGts.metadata?.touch?.()
    session.document.value = new PcGts(pcGts.metadata, pcGts.page, pcGts.pcGtsId)
  }

  undo(ctx?: CommandContext): void {
    if (!this.capturedOriginalComment) return

    const session = ctx?.session
    const pcGts = session?.document.value
    if (!session || !pcGts) return

    const target = findCommentOwner(pcGts, this.textlineId)
    if (!target) return

    target.comments = this.originalComment
    pcGts.metadata?.touch?.()
    session.document.value = new PcGts(pcGts.metadata, pcGts.page, pcGts.pcGtsId)
  }

  getDescription(): string {
    return `Update text-line comment (${this.textlineId})`
  }
}

/** Text lines, words and glyphs all carry a PAGE @comments attribute. */
function findCommentOwner(pcGts: PcGts, id: string): { comments?: string } | null {
  return findTextLineRecursive(pcGts.page.regions, id)?.textLine
    ?? findWordRecursive(pcGts.page.regions, id)?.word
    ?? findGlyphRecursive(pcGts.page.regions, id)?.glyph
    ?? null
}
