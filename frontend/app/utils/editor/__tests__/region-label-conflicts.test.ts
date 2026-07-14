import { describe, expect, it } from 'vitest'
import { LabelDefinition, LabelSet } from '@/models/editor/labels'
import type { RegionLabelConflictRegion } from '@/utils/editor/region-label-conflicts'
import { findRegionLabelConflicts } from '@/utils/editor/region-label-conflicts'

function label(overrides: Partial<LabelDefinition> = {}): LabelDefinition {
  return new LabelDefinition(
    overrides.id ?? 'paragraph',
    overrides.name ?? 'Paragraph',
    overrides.scope ?? 'region',
    overrides.color ?? '#123456',
    overrides.description ?? '',
    overrides.hasText ?? true,
    overrides.isContainer ?? false,
    overrides.group ?? null,
    overrides.mapping ?? {
      pageXml: {
        regionType: 'TextRegion',
        textType: 'paragraph',
        customSubType: '',
        customKey: 'structure',
        customData: ''
      }
    }
  )
}

function region(overrides: Partial<RegionLabelConflictRegion> = {}): RegionLabelConflictRegion {
  return {
    id: overrides.id ?? 'r1',
    kind: overrides.kind ?? 'TextRegion',
    type: overrides.type ?? 'paragraph',
    custom: overrides.custom,
    regions: overrides.regions
  }
}

describe('findRegionLabelConflicts', () => {
  it('returns no conflicts when no label set is assigned', () => {
    expect(findRegionLabelConflicts([region()], null)).toEqual({
      groups: [],
      regionIds: [],
      totalRegions: 0
    })
  })

  it('matches equivalent PAGE mappings regardless of stale label metadata', () => {
    const labelSet = new LabelSet('set-1', 'Labels', [label({ id: 'new-id', name: 'New name' })])
    const summary = findRegionLabelConflicts([
      region({ custom: 'larex { labelId:old-id; labelAlias:Old name; }' })
    ], labelSet)

    expect(summary.totalRegions).toBe(0)
  })

  it('finds nested regions and groups equal mappings', () => {
    const labelSet = new LabelSet('set-1', 'Labels', [label()])
    const summary = findRegionLabelConflicts([
      region({
        id: 'parent',
        regions: [
          region({ id: 'image-1', kind: 'ImageRegion', type: 'photo' }),
          region({ id: 'image-2', kind: 'ImageRegion', type: 'photo' })
        ]
      })
    ], labelSet)

    expect(summary.totalRegions).toBe(2)
    expect(summary.regionIds).toEqual(['image-1', 'image-2'])
    expect(summary.groups).toHaveLength(1)
    expect(summary.groups[0]).toMatchObject({
      displayName: 'photo',
      mappingDescription: 'ImageRegion · photo',
      count: 2,
      regionIds: ['image-1', 'image-2']
    })
  })

  it('uses the old label alias for the conflict display name', () => {
    const labelSet = new LabelSet('set-1', 'Labels', [])
    const summary = findRegionLabelConflicts([
      region({ custom: 'larex { labelId:old-id; labelAlias:Body Copy; }' })
    ], labelSet)

    expect(summary.groups[0]?.displayName).toBe('Body Copy')
    expect(summary.groups[0]?.mappingDescription).toBe('TextRegion · paragraph')
  })

  it('keeps custom text mappings with different payloads in separate groups', () => {
    const labelSet = new LabelSet('set-1', 'Labels', [])
    const summary = findRegionLabelConflicts([
      region({ id: 'lead', type: 'other', custom: 'structure { type:article; subclass:lead; }' }),
      region({ id: 'body', type: 'other', custom: 'structure { type:article; subclass:body; }' })
    ], labelSet)

    expect(summary.totalRegions).toBe(2)
    expect(summary.groups).toHaveLength(2)
  })

  it('ignores line-scoped labels when matching regions', () => {
    const lineLabel = label({ scope: 'line' })
    const labelSet = new LabelSet('set-1', 'Labels', [lineLabel])

    expect(findRegionLabelConflicts([region()], labelSet).totalRegions).toBe(1)
  })
})
