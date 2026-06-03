/**
 * Helpers for working with labels and label sets
 */

import type { LabelSet, LabelDefinition } from '@/models/editor/labels'
import type { PcGts } from '@/models/editor/document'
import type { RegionKind } from '@/models/editor/region'
import type { RGBA } from '@/utils/editor/editor-constants'
import { getRegionColor, hexToRgba } from '@/utils/editor/region-colors'
import { findRegionLabelDefinitionForRegion } from '@/utils/editor/page-label-mapping'

/**
 * Get the color for a region based on its label (which encodes kind and subtype)
 * Label format: "RegionKind" or "RegionKind:subtype"
 * @param label The region's label/name
 * @param document Optional document containing label sets
 * @returns RGB color array [r, g, b, a] normalized to 0-1 range
 */
function findLabelByMapping(
  labelSet: LabelSet,
  regionKind?: RegionKind,
  regionSubtype?: string,
  regionCustom?: string
): LabelDefinition | undefined {
  if (!regionKind) return undefined
  return findRegionLabelDefinitionForRegion(labelSet.labels, {
    regionKind,
    regionSubtype,
    regionCustom
  })
}

export function getColorForLabel(
  label: string | undefined,
  document?: PcGts | null,
  labelSet?: LabelSet | null,
  regionKind?: RegionKind,
  regionSubtype?: string,
  regionCustom?: string
): RGBA {
  if (!label) {
    return [0.5, 0.5, 0.5, 0.3]
  }

  const documentLabelSet = (document as { labelSets?: LabelSet[] } | null | undefined)?.labelSets?.[0]
  const effectiveLabelSet = labelSet || documentLabelSet
  if (effectiveLabelSet) {
    const mappedLabel = findLabelByMapping(effectiveLabelSet, regionKind, regionSubtype, regionCustom)
    if (mappedLabel?.color) {
      return hexToRgba(mappedLabel.color, 0.3)
    }
    const labelDef = effectiveLabelSet.labels.find((l: LabelDefinition) => l.name === label)
    if (labelDef?.color) {
      return hexToRgba(labelDef.color, 0.3)
    }
  }

  if (regionKind) {
    return hexToRgba(getRegionColor(regionKind, regionSubtype), 0.3)
  }

  const [kind, subtype] = label.includes(':') ? label.split(':') : [label, undefined]

  const color = getRegionColor(kind as RegionKind, subtype)
  return hexToRgba(color, 0.3)
}

/**
 * Get the stroke color for a region (slightly darker/more opaque than fill)
 * @param label The region's label/name
 * @param document Optional document containing label sets
 * @returns RGB color array [r, g, b, a] normalized to 0-1 range
 */
export function getStrokeColorForLabel(
  label: string | undefined,
  document?: PcGts | null,
  labelSet?: LabelSet | null,
  regionKind?: RegionKind,
  regionSubtype?: string,
  regionCustom?: string
): RGBA {
  const fillColor = getColorForLabel(label, document, labelSet, regionKind, regionSubtype, regionCustom)
  return [fillColor[0], fillColor[1], fillColor[2], 1.0]
}

/**
 * Get a label definition by name from a label set
 * @param labelName The label name to find
 * @param labelSet The label set to search in
 * @returns The label definition if found, undefined otherwise
 */
export function getLabelDefinition(
  labelName: string,
  labelSet?: LabelSet
): LabelDefinition | undefined {
  if (!labelSet) return undefined
  return labelSet.labels.find(l => l.name === labelName)
}
