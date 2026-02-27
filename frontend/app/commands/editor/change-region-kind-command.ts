import type { Command, CommandContext } from './types'
import { PcGts, canContainTextLines } from '@/models/editor'
import type { Region, RegionKind, TextRegion, GraphicRegion, ChartRegion, GenericRegion } from '@/models/editor'
import { findRegionRecursive } from '@/utils/editor/pcgts-editor-primitives'

export interface ChangeRegionKindCommandParams {
  regionId: string
  newKind: RegionKind
  newSubtype?: string
  updateCustom?: boolean
  newCustom?: string | null
}

interface SavedRegionState {
  kind: RegionKind
  type?: string
  custom?: string
  textLines?: TextRegion['textLines']
  textContentVariants?: TextRegion['textContentVariants']
}

/**
 * Command to change a region's kind (element type) and optionally its subtype.
 * Handles TextRegion → non-TextRegion conversion by preserving/clearing textLines.
 * Supports undo/redo.
 */
export class ChangeRegionKindCommand implements Command {
  private regionId: string
  private newKind: RegionKind
  private newSubtype?: string
  private updateCustom: boolean
  private newCustom?: string | null
  private savedState: SavedRegionState | null = null
  private removedTextLinesCount: number = 0

  constructor(params: ChangeRegionKindCommandParams) {
    this.regionId = params.regionId
    this.newKind = params.newKind
    this.newSubtype = params.newSubtype
    this.updateCustom = params.updateCustom === true
    this.newCustom = params.newCustom
  }

  getDescription(): string {
    return `Change region to ${this.newKind}${this.newSubtype ? ` (${this.newSubtype})` : ''}`
  }

  /**
   * Check if this command would remove TextLines (for confirmation dialog)
   */
  wouldRemoveTextLines(ctx?: CommandContext): number {
    const session = ctx?.session
    const pcGts = session?.document.value
    if (!session || !pcGts) return 0

    const hit = findRegionRecursive(pcGts.page.regions, this.regionId)
    if (!hit) return 0

    const region = hit.region
    if (region.kind === 'TextRegion' && !canContainTextLines(this.newKind)) {
      const textRegion = region as TextRegion
      return textRegion.textLines?.length ?? 0
    }

    return 0
  }

  execute(ctx?: CommandContext): void {
    const session = ctx?.session
    const pcGts = session?.document.value
    if (!session || !pcGts) return

    const hit = findRegionRecursive(pcGts.page.regions, this.regionId)
    if (!hit) return

    const region = hit.region

    this.savedState = {
      kind: region.kind,
      type: (region as TextRegion | GraphicRegion | ChartRegion | GenericRegion).type,
      custom: region.custom
    }

    if (region.kind === 'TextRegion') {
      const textRegion = region as TextRegion
      this.savedState.textLines = textRegion.textLines ? [...textRegion.textLines] : undefined
      this.savedState.textContentVariants = textRegion.textContentVariants ? [...textRegion.textContentVariants] : undefined
      this.removedTextLinesCount = textRegion.textLines?.length ?? 0
    }

    this.convertRegion(region, this.newKind, this.newSubtype)
    if (this.updateCustom) {
      this.setRegionCustom(region, this.newCustom)
    }

    session.document.value = new PcGts(pcGts.metadata, pcGts.page, pcGts.pcGtsId)
  }

  undo(ctx?: CommandContext): void {
    const session = ctx?.session
    const pcGts = session?.document.value
    if (!session || !pcGts || !this.savedState) return

    const hit = findRegionRecursive(pcGts.page.regions, this.regionId)
    if (!hit) return

    const region = hit.region

    this.convertRegion(region, this.savedState.kind, this.savedState.type)
    if (this.updateCustom) {
      this.setRegionCustom(region, this.savedState.custom)
    }

    if (this.savedState.kind === 'TextRegion') {
      const textRegion = region as TextRegion
      textRegion.textLines = this.savedState.textLines
      textRegion.textContentVariants = this.savedState.textContentVariants
    }

    session.document.value = new PcGts(pcGts.metadata, pcGts.page, pcGts.pcGtsId)
  }

  redo(ctx?: CommandContext): void {
    this.execute(ctx)
  }

  /**
   * Convert a region to a new kind in-place.
   * Preserves common properties and handles kind-specific cleanup.
   */
  private convertRegion(region: Region, newKind: RegionKind, newSubtype?: string): void {
    const oldKind = region.kind;

    (region as { kind: RegionKind }).kind = newKind

    if (newSubtype !== undefined) {
      (region as TextRegion | GraphicRegion | ChartRegion | GenericRegion).type = newSubtype
    } else {
      // Treat missing subtype as "no subtype" and clear stale type information.
      delete (region as TextRegion | GraphicRegion | ChartRegion | GenericRegion).type
    }

    if (newKind === 'TextRegion' && oldKind !== 'TextRegion') {
      (region as unknown as TextRegion).textLines = []
    }

    if (oldKind === 'TextRegion' && !canContainTextLines(newKind)) {
      delete (region as TextRegion).textLines
      delete (region as TextRegion).textContentVariants
    }

    if (oldKind !== newKind) {
      if (oldKind === 'TextRegion' && newKind !== 'TextRegion') {
        const r = region as TextRegion
        delete r.leading
        delete r.readingDirection
        delete r.textLineOrder
        delete r.readingOrientation
        delete r.indented
        delete r.align
        delete r.primaryLanguage
        delete r.secondaryLanguage
        delete r.primaryScript
        delete r.secondaryScript
        delete r.production
      }

      if (oldKind === 'GraphicRegion' && newKind !== 'GraphicRegion') {
        const r = region as GraphicRegion
        delete r.embText
        delete r.numColour
      }

      if (oldKind === 'ChartRegion' && newKind !== 'ChartRegion') {
        const r = region as ChartRegion
        delete r.embText
        delete r.numColour
      }
    }
  }

  private setRegionCustom(region: Region, custom: string | null | undefined): void {
    if (custom === undefined || custom === null || custom.trim() === '') {
      delete region.custom
      return
    }
    region.custom = custom
  }
}
