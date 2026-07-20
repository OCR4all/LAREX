import type { TextContentVariantData } from '@/models/editor'
import type { Region } from '@/models/editor/region'
import type { Command } from '@/commands/editor/types'
import { CompoundCommand } from '@/commands/editor/compound-command'
import { UpdateTextContentVariantsCommand } from '@/commands/editor/update-text-content-variants-command'
import { findTextLineRecursive } from '@/utils/editor/pcgts-editor-primitives'
import { buildRegionGtSyncedVariants, composeRegionGtFromTextLines } from './region-gt-sync'
import { normalizeTextContentVariants } from './text-view-runtime'

type CreateTextlineVariantsUpdateCommandOptions = {
  pageRegions: Region[] | undefined
  textlineId: string
  nextTextContentVariants: TextContentVariantData[] | undefined
  gtIndex: number
}

/**
 * Build the canonical textline update command used by every text-editing
 * surface. When the parent TextRegion GT would change, the returned compound
 * command keeps both mutations in one undo step.
 */
export function createTextlineVariantsUpdateCommand(
  options: CreateTextlineVariantsUpdateCommandOptions
): Command {
  const textlineCommand = new UpdateTextContentVariantsCommand({
    elementId: options.textlineId,
    nextTextContentVariants: options.nextTextContentVariants
  })

  const textlineHit = findTextLineRecursive(options.pageRegions ?? [], options.textlineId)
  if (!textlineHit) return textlineCommand

  const parentTextRegion = textlineHit.parentTextRegion
  if (!parentTextRegion.id) return textlineCommand

  const syncedTextLines = (parentTextRegion.textLines ?? []).map((textline) => {
    if (textline.id !== options.textlineId) return textline
    return {
      ...textline,
      textContentVariants: options.nextTextContentVariants
    }
  })
  const nextGtText = composeRegionGtFromTextLines(syncedTextLines, options.gtIndex)
  const nextRegionVariants = buildRegionGtSyncedVariants(
    parentTextRegion.textContentVariants as TextContentVariantData[] | undefined,
    nextGtText,
    options.gtIndex
  )
  const currentNormalized = normalizeTextContentVariants(
    parentTextRegion.textContentVariants as TextContentVariantData[] | undefined
  )
  const nextNormalized = normalizeTextContentVariants(nextRegionVariants)
  if (JSON.stringify(currentNormalized) === JSON.stringify(nextNormalized)) {
    return textlineCommand
  }

  return new CompoundCommand(
    [
      textlineCommand,
      new UpdateTextContentVariantsCommand({
        elementId: parentTextRegion.id,
        nextTextContentVariants: nextRegionVariants
      })
    ],
    'Update textline GT and sync parent region GT'
  )
}
