import type { Command, CommandContext } from './types'
import { PcGts, TextLine } from '@/models/editor'
import type { Region, TextRegion } from '@/models/editor'
import { isTextRegion, canContainTextLines } from '@/models/editor/region'
import { visibilityService } from '@/services/editor/visibility-service'
import { findRegionRecursive, findTextLineRecursive, rebuildSpatialIndexFromPcGts } from '@/utils/editor/pcgts-editor-primitives'

export interface ReparentElementCommandData {
  elementId: string
  elementType: 'region' | 'textline'
  newParentId: string | null // null = root level (regions only)
}

interface UndoState {
  oldParentId: string | null
  oldIndex: number
}

export class ReparentElementCommand implements Command {
  private data: ReparentElementCommandData
  private undoState: UndoState | null = null

  constructor(data: ReparentElementCommandData) {
    this.data = data
  }

  execute(ctx?: CommandContext): boolean {
    const pcGts = ctx?.session?.document.value
    if (!pcGts) return false

    if (this.data.elementType === 'textline') {
      return this.reparentTextLine(pcGts, ctx!)
    }
    return this.reparentRegion(pcGts, ctx!)
  }

  private reparentTextLine(pcGts: PcGts, ctx: CommandContext): boolean {
    const hit = findTextLineRecursive(pcGts.page.regions, this.data.elementId)
    if (!hit) return false

    if (!this.data.newParentId) return false // textlines must have a parent
    const newParentHit = findRegionRecursive(pcGts.page.regions, this.data.newParentId)
    if (!newParentHit || !isTextRegion(newParentHit.region) || !canContainTextLines(newParentHit.region.kind)) return false

    if (hit.parentTextRegion.id === this.data.newParentId) return false

    this.undoState = { oldParentId: hit.parentTextRegion.id, oldIndex: hit.index }

    hit.parentTextRegion.textLines!.splice(hit.index, 1)

    const newParent = newParentHit.region as TextRegion
    newParent.textLines = newParent.textLines ?? []
    newParent.textLines.push(hit.textLine)

    this.finalize(ctx, pcGts)
    return true
  }

  private reparentRegion(pcGts: PcGts, ctx: CommandContext): boolean {
    const hit = findRegionRecursive(pcGts.page.regions, this.data.elementId)
    if (!hit) return false

    const oldParentId = hit.parent?.id ?? null
    if (oldParentId === this.data.newParentId) return false // Same parent

    this.undoState = { oldParentId, oldIndex: hit.index }

    const oldSiblings = hit.parent?.regions ?? pcGts.page.regions
    oldSiblings.splice(hit.index, 1)

    if (this.data.newParentId) {
      const newParentHit = findRegionRecursive(pcGts.page.regions, this.data.newParentId)
      if (!newParentHit) return false
      newParentHit.region.regions = newParentHit.region.regions ?? []
      newParentHit.region.regions.push(hit.region)
    } else {
      pcGts.page.regions.push(hit.region)
    }

    this.finalize(ctx, pcGts)
    return true
  }

  undo(ctx?: CommandContext): void {
    if (!this.undoState) return
    const pcGts = ctx?.session?.document.value
    if (!pcGts) return

    if (this.data.elementType === 'textline') {
      this.undoTextLine(pcGts, ctx!)
    } else {
      this.undoRegion(pcGts, ctx!)
    }
  }

  private undoTextLine(pcGts: PcGts, ctx: CommandContext): void {
    const hit = findTextLineRecursive(pcGts.page.regions, this.data.elementId)
    if (!hit) return

    hit.parentTextRegion.textLines!.splice(hit.index, 1)

    const oldParentHit = findRegionRecursive(pcGts.page.regions, this.undoState!.oldParentId!)
    if (oldParentHit && isTextRegion(oldParentHit.region)) {
      oldParentHit.region.textLines = oldParentHit.region.textLines ?? []
      oldParentHit.region.textLines.splice(this.undoState!.oldIndex, 0, hit.textLine)
    }

    this.finalize(ctx, pcGts)
  }

  private undoRegion(pcGts: PcGts, ctx: CommandContext): void {
    const hit = findRegionRecursive(pcGts.page.regions, this.data.elementId)
    if (!hit) return

    const currentSiblings = hit.parent?.regions ?? pcGts.page.regions
    currentSiblings.splice(hit.index, 1)

    if (this.undoState!.oldParentId) {
      const oldParentHit = findRegionRecursive(pcGts.page.regions, this.undoState!.oldParentId)
      if (oldParentHit) {
        oldParentHit.region.regions = oldParentHit.region.regions ?? []
        oldParentHit.region.regions.splice(this.undoState!.oldIndex, 0, hit.region)
      }
    } else {
      pcGts.page.regions.splice(this.undoState!.oldIndex, 0, hit.region)
    }

    this.finalize(ctx, pcGts)
  }

  getDescription(): string {
    return `Reparent ${this.data.elementType}`
  }

  private finalize(ctx: CommandContext, pcGts: PcGts): void {
    ctx.session.document.value = new PcGts(pcGts.metadata, pcGts.page, pcGts.pcGtsId)
    rebuildSpatialIndexFromPcGts(ctx.session)
    visibilityService.clearCache()
  }
}
