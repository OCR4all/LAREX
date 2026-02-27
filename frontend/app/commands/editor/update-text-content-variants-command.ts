import type { Command, CommandContext } from './types'
import { PcGts, TextContentVariant } from '@/models/editor'
import type { TextRegion } from '@/models/editor'
import { deepClone } from './utils'
import { findRegionRecursive, findTextLineRecursive } from '@/utils/editor/pcgts-editor-primitives'

export type UpdateTextContentVariantsCommandData = {
  elementId: string
  nextTextContentVariants: Array<{
    unicode: string
    plainText?: string
    confidence?: number
    index?: number
    dataType?: string
    dataTypeDetails?: string
    comments?: string
  }> | undefined
}

export class UpdateTextContentVariantsCommand implements Command {
  private elementId: string
  private nextTextContentVariants: UpdateTextContentVariantsCommandData['nextTextContentVariants']
  private originalTextContentVariants: TextContentVariant[] | undefined

  constructor(data: UpdateTextContentVariantsCommandData) {
    this.elementId = data.elementId
    this.nextTextContentVariants = data.nextTextContentVariants ? (deepClone(data.nextTextContentVariants) as any) : undefined
  }

  execute(ctx?: CommandContext): void {
    const session = ctx?.session
    const pcGts = session?.document.value
    if (!session || !pcGts) return

    const target = findTextContentVariantOwner(pcGts, this.elementId)
    if (!target) return

    if (!this.originalTextContentVariants) {
      this.originalTextContentVariants = target.textContentVariants ? [...target.textContentVariants] : undefined
    }

    if (this.nextTextContentVariants && this.nextTextContentVariants.length > 0) {
      target.textContentVariants = this.nextTextContentVariants.map((te: any) => new TextContentVariant(
        te.unicode ?? '',
        te.plainText,
        te.confidence,
        te.index,
        te.dataType,
        te.dataTypeDetails,
        te.comments
      ))
    } else {
      target.textContentVariants = undefined
    }

    session.document.value = new PcGts(pcGts.metadata, pcGts.page, pcGts.pcGtsId)
  }

  undo(ctx?: CommandContext): void {
    const session = ctx?.session
    const pcGts = session?.document.value
    if (!session || !pcGts) return

    const target = findTextContentVariantOwner(pcGts, this.elementId)
    if (!target) return

    if (this.originalTextContentVariants && this.originalTextContentVariants.length > 0) {
      target.textContentVariants = [...this.originalTextContentVariants]
    } else {
      target.textContentVariants = undefined
    }

    session.document.value = new PcGts(pcGts.metadata, pcGts.page, pcGts.pcGtsId)
  }

  getDescription(): string {
    return `Update textContentVariants (${this.elementId})`
  }
}

type TextContentVariantOwner = { textContentVariants?: TextContentVariant[] }

function findTextContentVariantOwner(pcGts: PcGts, id: string): TextContentVariantOwner | null {
  const tlHit = findTextLineRecursive(pcGts.page.regions, id)
  if (tlHit) return tlHit.textLine

  const regionHit = findRegionRecursive(pcGts.page.regions, id)
  if (regionHit && regionHit.region.kind === 'TextRegion') {
    return regionHit.region as TextRegion
  }

  return null
}
