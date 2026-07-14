import { ChangeRegionKindCommand } from '@/commands/editor/change-region-kind-command'
import { CompoundCommand } from '@/commands/editor/compound-command'
import { canContainTextLines, type Region, type RegionKind } from '@/models/editor'
import type { LabelDefinition } from '@/models/editor/labels'
import { buildMergedCustomForAppliedRegionLabel } from '@/utils/editor/page-label-mapping'
import { findRegionRecursive } from '@/utils/editor/pcgts-editor-primitives'
import type { RegionLabelConflictGroup } from '@/utils/editor/region-label-conflicts'

export type RegionLabelConflictReplacements = Readonly<Record<string, LabelDefinition | undefined>>

export interface RegionLabelConflictResolutionPlan {
  command: CompoundCommand
  affectedRegionCount: number
  textLinesToRemove: number
}

function replacementKind(label: LabelDefinition): RegionKind | null {
  const regionType = label.mapping?.pageXml?.regionType
  return regionType ? regionType as RegionKind : null
}

export function countTextLinesRemovedForLabelConflictReplacements(
  regions: Region[],
  groups: RegionLabelConflictGroup[],
  replacements: RegionLabelConflictReplacements
): number {
  let count = 0

  for (const group of groups) {
    const label = replacements[group.key]
    const newKind = label ? replacementKind(label) : null
    if (!newKind || canContainTextLines(newKind)) continue

    for (const regionId of group.regionIds) {
      const hit = findRegionRecursive(regions, regionId)
      if (hit?.region.kind === 'TextRegion') {
        count += hit.region.textLines?.length ?? 0
      }
    }
  }

  return count
}

export function createRegionLabelConflictResolutionPlan(
  regions: Region[],
  groups: RegionLabelConflictGroup[],
  replacements: RegionLabelConflictReplacements
): RegionLabelConflictResolutionPlan {
  const commands: ChangeRegionKindCommand[] = []

  for (const group of groups) {
    const label = replacements[group.key]
    const mapping = label?.mapping?.pageXml
    const newKind = label ? replacementKind(label) : null
    if (!label || !mapping || !newKind) {
      throw new Error(`No valid replacement is selected for "${group.displayName}".`)
    }

    const newSubtype = newKind === 'TextRegion'
      ? (mapping.textType === 'custom' ? 'other' : (mapping.textType || undefined))
      : (mapping.customSubType || undefined)

    for (const regionId of group.regionIds) {
      const hit = findRegionRecursive(regions, regionId)
      if (!hit) throw new Error(`Region "${regionId}" is no longer available.`)

      commands.push(new ChangeRegionKindCommand({
        regionId,
        newKind,
        newSubtype,
        updateCustom: true,
        newCustom: buildMergedCustomForAppliedRegionLabel(hit.region.custom, label)
      }))
    }
  }

  if (commands.length === 0) throw new Error('There are no label conflicts to resolve.')

  return {
    command: new CompoundCommand(
      commands,
      `Resolve ${commands.length} region label conflict${commands.length === 1 ? '' : 's'}`
    ),
    affectedRegionCount: commands.length,
    textLinesToRemove: countTextLinesRemovedForLabelConflictReplacements(regions, groups, replacements)
  }
}
