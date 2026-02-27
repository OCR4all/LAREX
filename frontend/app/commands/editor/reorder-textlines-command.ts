import type { Command, CommandContext } from './types'
import { PcGts } from '@/models/editor'
import type { TextLine, TextRegion } from '@/models/editor'
import { isTextRegion } from '@/models/editor/region'
import { visibilityService } from '@/services/editor/visibility-service'
import { findRegionRecursive, rebuildSpatialIndexFromPcGts } from '@/utils/editor/pcgts-editor-primitives'

export interface ReorderTextLinesCommandData {
  parentTextRegionId: string
  orderedTextLineIds: string[]
}

export class ReorderTextLinesCommand implements Command {
  private data: ReorderTextLinesCommandData
  private previousOrder: string[] | null = null

  constructor(data: ReorderTextLinesCommandData) {
    this.data = data
  }

  execute(ctx?: CommandContext): boolean {
    const pcGts = ctx?.session?.document.value
    if (!ctx || !pcGts) return false

    const hit = findRegionRecursive(pcGts.page.regions, this.data.parentTextRegionId)
    if (!hit || !isTextRegion(hit.region)) return false

    const region = hit.region as TextRegion
    region.textLines = region.textLines ?? []

    const current = region.textLines
    const currentIds = current.map(tl => tl.id)
    this.previousOrder = currentIds

    const byId = new Map<string, TextLine>(current.map(tl => [tl.id, tl]))
    const next: TextLine[] = []

    for (const id of this.data.orderedTextLineIds) {
      const tl = byId.get(id)
      if (!tl) continue
      next.push(tl)
      byId.delete(id)
    }

    for (const tl of current) {
      if (!byId.has(tl.id)) continue
      next.push(tl)
      byId.delete(tl.id)
    }

    const nextIds = next.map(tl => tl.id)
    if (currentIds.length === nextIds.length && currentIds.every((id, i) => id === nextIds[i])) {
      return false
    }

    region.textLines = next
    this.finalize(ctx, pcGts)
    return true
  }

  undo(ctx?: CommandContext): void {
    const pcGts = ctx?.session?.document.value
    if (!ctx || !pcGts) return
    if (!this.previousOrder) return

    const hit = findRegionRecursive(pcGts.page.regions, this.data.parentTextRegionId)
    if (!hit || !isTextRegion(hit.region)) return

    const region = hit.region as TextRegion
    region.textLines = region.textLines ?? []

    const current = region.textLines
    const byId = new Map<string, TextLine>(current.map(tl => [tl.id, tl]))
    const next: TextLine[] = []

    for (const id of this.previousOrder) {
      const tl = byId.get(id)
      if (!tl) continue
      next.push(tl)
      byId.delete(id)
    }

    for (const tl of current) {
      if (!byId.has(tl.id)) continue
      next.push(tl)
      byId.delete(tl.id)
    }

    region.textLines = next
    this.finalize(ctx, pcGts)
  }

  getDescription(): string {
    return 'Reorder text lines'
  }

  private finalize(ctx: CommandContext, pcGts: PcGts): void {
    ctx.session.document.value = new PcGts(pcGts.metadata, pcGts.page, pcGts.pcGtsId)
    rebuildSpatialIndexFromPcGts(ctx.session)
    visibilityService.clearCache()
  }
}
