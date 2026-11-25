import type { Command, CommandContext } from './types'
import { CompoundCommand } from './compound-command'
import { regionSiblings } from './table-command-utils'
import { PcGts } from '@/models/editor'
import type { TextRegion } from '@/models/editor'
import { isTextRegion, canContainTextLines } from '@/models/editor/region'
import { visibilityService } from '@/services/editor/visibility-service'
import { findGlyphRecursive, findRegionRecursive, findTextLineRecursive, findWordRecursive, rebuildSpatialIndexFromPcGts, toPolygonElementType } from '@/utils/editor/pcgts-editor-primitives'

export interface ReparentElementCommandData {
  elementId: string
  elementType: 'region' | 'textline' | 'word' | 'glyph'
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
    this.undoState = null
    const pcGts = ctx?.session?.document.value
    if (!pcGts) return false

    if (this.data.elementType === 'textline') {
      return this.reparentTextLine(pcGts, ctx!)
    }
    if (this.data.elementType === 'word') {
      return this.reparentWord(pcGts, ctx!)
    }
    if (this.data.elementType === 'glyph') {
      return this.reparentGlyph(pcGts, ctx!)
    }
    return this.reparentRegion(pcGts, ctx!)
  }

  private reparentWord(pcGts: PcGts, ctx: CommandContext): boolean {
    const hit = findWordRecursive(pcGts.page.regions, this.data.elementId)
    if (!hit || !this.data.newParentId) return false

    const newParent = findTextLineRecursive(pcGts.page.regions, this.data.newParentId)?.textLine
    if (!newParent || hit.parentTextLine.id === newParent.id) return false

    this.undoState = { oldParentId: hit.parentTextLine.id, oldIndex: hit.index }
    hit.parentTextLine.words!.splice(hit.index, 1)
    newParent.words = newParent.words ?? []
    newParent.words.push(hit.word)

    this.finalize(ctx, pcGts)
    return true
  }

  private reparentGlyph(pcGts: PcGts, ctx: CommandContext): boolean {
    const hit = findGlyphRecursive(pcGts.page.regions, this.data.elementId)
    if (!hit || !this.data.newParentId) return false

    const newParent = findWordRecursive(pcGts.page.regions, this.data.newParentId)?.word
    if (!newParent || hit.parentWord.id === newParent.id) return false

    this.undoState = { oldParentId: hit.parentWord.id, oldIndex: hit.index }
    hit.parentWord.glyphs!.splice(hit.index, 1)
    newParent.glyphs = newParent.glyphs ?? []
    newParent.glyphs.push(hit.glyph)

    this.finalize(ctx, pcGts)
    return true
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

    const { newParentId } = this.data
    const oldParentId = hit.parent?.id ?? null
    if (oldParentId === newParentId) return false
    if (newParentId && findRegionRecursive([hit.region], newParentId)) return false

    const newSiblings = regionSiblings(pcGts, newParentId)
    if (!newSiblings) return false

    this.undoState = { oldParentId, oldIndex: hit.index }
    regionSiblings(pcGts, oldParentId)!.splice(hit.index, 1)
    newSiblings.push(hit.region)

    this.finalize(ctx, pcGts)
    return true
  }

  undo(ctx?: CommandContext): void {
    if (!this.undoState) return
    const pcGts = ctx?.session?.document.value
    if (!pcGts) return

    if (this.data.elementType === 'textline') {
      this.undoTextLine(pcGts, ctx!)
    } else if (this.data.elementType === 'word') {
      this.undoWord(pcGts, ctx!)
    } else if (this.data.elementType === 'glyph') {
      this.undoGlyph(pcGts, ctx!)
    } else {
      this.undoRegion(pcGts, ctx!)
    }
  }

  private undoWord(pcGts: PcGts, ctx: CommandContext): void {
    const hit = findWordRecursive(pcGts.page.regions, this.data.elementId)
    if (!hit) return

    hit.parentTextLine.words!.splice(hit.index, 1)

    const oldParent = findTextLineRecursive(pcGts.page.regions, this.undoState!.oldParentId!)?.textLine
    if (oldParent) {
      oldParent.words = oldParent.words ?? []
      oldParent.words.splice(this.undoState!.oldIndex, 0, hit.word)
    }

    this.finalize(ctx, pcGts)
  }

  private undoGlyph(pcGts: PcGts, ctx: CommandContext): void {
    const hit = findGlyphRecursive(pcGts.page.regions, this.data.elementId)
    if (!hit) return

    hit.parentWord.glyphs!.splice(hit.index, 1)

    const oldParent = findWordRecursive(pcGts.page.regions, this.undoState!.oldParentId!)?.word
    if (oldParent) {
      oldParent.glyphs = oldParent.glyphs ?? []
      oldParent.glyphs.splice(this.undoState!.oldIndex, 0, hit.glyph)
    }

    this.finalize(ctx, pcGts)
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
    const oldSiblings = regionSiblings(pcGts, this.undoState!.oldParentId)
    if (!hit || !oldSiblings) return

    regionSiblings(pcGts, hit.parent?.id ?? null)!.splice(hit.index, 1)
    oldSiblings.splice(this.undoState!.oldIndex, 0, hit.region)

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

export interface ReparentMove {
  id: string
  type?: string
  newParentId: string | null
}

export function createReparentCommand(moves: ReparentMove[], description: string): Command {
  const commands = moves.map(({ id, type, newParentId }) => new ReparentElementCommand({
    elementId: id,
    elementType: toPolygonElementType(type),
    newParentId
  }))
  return commands.length === 1 ? commands[0]! : new CompoundCommand(commands, description)
}
