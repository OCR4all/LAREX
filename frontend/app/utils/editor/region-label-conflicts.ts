import type { LabelSet } from '@/models/editor/labels'
import type { RegionKind } from '@/models/editor/region'
import {
  createCanonicalRegionSignatureFromRuntimeRegion,
  findRegionLabelDefinitionForRegion,
  parsePageCustomBlocks
} from '@/utils/editor/page-label-mapping'

export interface RegionLabelConflictRegion {
  id: string
  kind: RegionKind
  type?: string | null
  custom?: string | null
  regions?: RegionLabelConflictRegion[]
}

export interface RegionLabelConflictGroup {
  key: string
  displayName: string
  mappingDescription: string
  regionKind: RegionKind
  regionSubtype: string | null
  regionIds: string[]
  count: number
}

export interface RegionLabelConflictSummary {
  groups: RegionLabelConflictGroup[]
  regionIds: string[]
  totalRegions: number
}

const EMPTY_SUMMARY: RegionLabelConflictSummary = {
  groups: [],
  regionIds: [],
  totalRegions: 0
}

function normalized(value: string | null | undefined): string | null {
  if (typeof value !== 'string') return null
  const trimmed = value.trim()
  return trimmed.length > 0 ? trimmed : null
}

function describeRegion(region: RegionLabelConflictRegion): { displayName: string, mappingDescription: string } {
  const blocks = parsePageCustomBlocks(region.custom)
  const alias = normalized(blocks.larex?.labelAlias)
  const customType = normalized(blocks.structure?.type)
  const subtype = normalized(region.type)

  const semanticSubtype = customType ?? subtype
  const mappingDescription = semanticSubtype
    ? `${region.kind} · ${semanticSubtype}`
    : region.kind

  return {
    displayName: alias ?? semanticSubtype ?? region.kind,
    mappingDescription
  }
}

function collectRegions(regions: RegionLabelConflictRegion[], output: RegionLabelConflictRegion[]): void {
  for (const region of regions) {
    output.push(region)
    if (region.regions?.length) {
      collectRegions(region.regions, output)
    }
  }
}

export function findRegionLabelConflicts(
  regions: RegionLabelConflictRegion[] | null | undefined,
  labelSet: LabelSet | null | undefined
): RegionLabelConflictSummary {
  if (!labelSet || !regions?.length) return EMPTY_SUMMARY

  const allRegions: RegionLabelConflictRegion[] = []
  collectRegions(regions, allRegions)

  const conflictsByKey = new Map<string, RegionLabelConflictGroup>()
  const regionIds: string[] = []

  for (const region of allRegions) {
    if (findRegionLabelDefinitionForRegion(labelSet.labels, region)) continue

    const key = createCanonicalRegionSignatureFromRuntimeRegion(region)
      ?? `region|kind=${region.kind}|type=${normalized(region.type) ?? ''}`
    const existing = conflictsByKey.get(key)
    regionIds.push(region.id)

    if (existing) {
      existing.regionIds.push(region.id)
      existing.count += 1
      continue
    }

    const description = describeRegion(region)
    conflictsByKey.set(key, {
      key,
      ...description,
      regionKind: region.kind,
      regionSubtype: normalized(region.type),
      regionIds: [region.id],
      count: 1
    })
  }

  const groups = [...conflictsByKey.values()].sort((left, right) =>
    left.displayName.localeCompare(right.displayName) || left.key.localeCompare(right.key)
  )

  return {
    groups,
    regionIds,
    totalRegions: regionIds.length
  }
}
