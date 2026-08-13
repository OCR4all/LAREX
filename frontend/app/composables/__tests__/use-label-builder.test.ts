import { describe, expect, it } from 'vitest'
import {
  applyPageRegionTypeChange,
  applyPageTextTypeChange,
  createLabelBuilderStateSnapshot,
  createNextFreeLabelMapping,
  moveBuilderLabel,
  moveBuilderLabelByOffset,
  moveBuilderGroup,
  moveBuilderGroupByOffset,
  normalizeEditableLabel,
  useLabelBuilder,
  type BuilderEntry,
  type EditableLabelDefinition
} from '@/composables/use-label-builder'

function label(id: string, group: string | null = null): EditableLabelDefinition {
  return {
    id,
    scope: 'region',
    name: id,
    description: '',
    color: '#123456',
    hasText: true,
    isContainer: false,
    group,
    mapping: {
      pageXml: {
        regionType: 'TextRegion',
        textType: 'paragraph',
        customSubType: '',
        customKey: 'structure',
        customData: ''
      }
    }
  }
}

function labelIds(entries: BuilderEntry[]): string[] {
  return entries.filter(entry => !('isGroup' in entry)).map(entry => entry.id)
}

function groupIds(entries: BuilderEntry[]): string[] {
  const seen = new Set<string>()
  const result: string[] = []
  for (const entry of entries) {
    const group = 'isGroup' in entry ? entry.id : entry.group
    if (group && !seen.has(group)) {
      seen.add(group)
      result.push(group)
    }
  }
  return result
}

describe('moveBuilderLabel', () => {
  it('reorders labels within a group', () => {
    const entries: BuilderEntry[] = [
      label('first', 'Text'),
      label('second', 'Text'),
      label('third', 'Text'),
      { id: 'Text', name: 'Text', isGroup: true }
    ]

    const reordered = moveBuilderLabel(entries, 'third', 'Text', 0)

    expect(labelIds(reordered)).toEqual(['third', 'first', 'second'])
  })

  it('moves a label into another group at the requested position', () => {
    const entries: BuilderEntry[] = [
      label('heading', 'Text'),
      label('image'),
      label('paragraph', 'Text'),
      { id: 'Text', name: 'Text', isGroup: true }
    ]

    const reordered = moveBuilderLabel(entries, 'image', 'Text', 1)

    expect(labelIds(reordered)).toEqual(['heading', 'image', 'paragraph'])
    expect((reordered.find(entry => entry.id === 'image') as EditableLabelDefinition).group).toBe('Text')
  })

  it('moves labels one position with keyboard-style offsets', () => {
    const entries: BuilderEntry[] = [label('first'), label('second'), label('third')]

    const movedDown = moveBuilderLabelByOffset(entries, 'first', 1)
    expect(labelIds(movedDown)).toEqual(['second', 'first', 'third'])

    const movedUp = moveBuilderLabelByOffset(movedDown, 'third', -1)
    expect(labelIds(movedUp)).toEqual(['second', 'third', 'first'])
  })

  it('does not move labels beyond their group boundaries', () => {
    const entries: BuilderEntry[] = [
      label('first', 'Text'),
      label('second', 'Text'),
      label('image'),
      { id: 'Text', name: 'Text', isGroup: true }
    ]

    expect(moveBuilderLabelByOffset(entries, 'first', -1)).toBe(entries)
    expect(moveBuilderLabelByOffset(entries, 'second', 1)).toBe(entries)
  })
})

describe('moveBuilderGroup', () => {
  const entries: BuilderEntry[] = [
    label('heading', 'Text'),
    label('image', 'Media'),
    label('paragraph', 'Text'),
    label('table', 'Data'),
    label('ungrouped'),
    { id: 'Text', name: 'Text', isGroup: true },
    { id: 'Media', name: 'Media', isGroup: true },
    { id: 'Data', name: 'Data', isGroup: true }
  ]

  it('moves grouped label blocks as a unit', () => {
    const reordered = moveBuilderGroup(entries, 'Data', 0)

    expect(groupIds(reordered)).toEqual(['Data', 'Text', 'Media'])
    expect(labelIds(reordered)).toEqual(['table', 'heading', 'paragraph', 'image', 'ungrouped'])
  })

  it('supports one-step group movement and respects boundaries', () => {
    const moved = moveBuilderGroupByOffset(entries, 'Media', -1)
    expect(groupIds(moved)).toEqual(['Media', 'Text', 'Data'])
    expect(moveBuilderGroupByOffset(entries, 'Text', -1)).toBe(entries)
    expect(moveBuilderGroupByOffset(entries, 'Data', 1)).toBe(entries)
  })
})

describe('createNextFreeLabelMapping', () => {
  it('selects the first unused PAGE text type', () => {
    const paragraph = label('paragraph')
    const heading = label('heading')
    heading.mapping.pageXml.textType = 'heading'

    expect(createNextFreeLabelMapping([paragraph, heading], 'New Label').pageXml).toEqual({
      regionType: 'TextRegion',
      textType: 'caption',
      customSubType: '',
      customKey: 'structure',
      customData: ''
    })
  })

  it('does not count non-text region mappings as used text types', () => {
    const image = label('image')
    image.mapping.pageXml.regionType = 'ImageRegion'
    image.mapping.pageXml.textType = undefined

    expect(createNextFreeLabelMapping([image], 'New Label').pageXml.textType).toBe('paragraph')
  })

  it('falls back to a custom subtype matching the generated label name', () => {
    const standardTextTypes = [
      'paragraph', 'heading', 'caption', 'header', 'footer', 'page-number', 'drop-capital',
      'credit', 'floating', 'signature-mark', 'catch-word', 'marginalia', 'footnote',
      'footnote-continued', 'endnote', 'TOC-entry', 'list-label', 'other'
    ] as const
    const entries = standardTextTypes.map((textType) => {
      const entry = label(textType)
      entry.mapping.pageXml.textType = textType
      return entry
    })

    expect(createNextFreeLabelMapping(entries, 'New Label (2)').pageXml).toEqual({
      regionType: 'TextRegion',
      textType: 'custom',
      customSubType: 'New Label (2)',
      customKey: 'structure',
      customData: ''
    })
  })
})

describe('createLabelBuilderStateSnapshot', () => {
  const meta = { name: 'Labels', description: 'Description', tags: ['ocr'] }

  it('tracks persisted label changes and ordering', () => {
    const first = label('first')
    const second = label('second')
    const baseline = createLabelBuilderStateSnapshot(meta, [first, second])

    first.color = '#abcdef'
    expect(createLabelBuilderStateSnapshot(meta, [first, second])).not.toBe(baseline)

    first.color = '#123456'
    expect(createLabelBuilderStateSnapshot(meta, [second, first])).not.toBe(baseline)
  })

  it('ignores builder-only group metadata', () => {
    const entry = label('first', 'Text')
    const baseline = createLabelBuilderStateSnapshot(meta, [entry])

    expect(createLabelBuilderStateSnapshot(meta, [
      entry,
      { id: 'Text', name: 'Text', isGroup: true }
    ])).toBe(baseline)
  })

  it('tracks label-set metadata changes', () => {
    const entries = [label('first')]
    const baseline = createLabelBuilderStateSnapshot(meta, entries)

    expect(createLabelBuilderStateSnapshot({ ...meta, name: 'Renamed' }, entries)).not.toBe(baseline)
  })

  it('tracks the configured default label', () => {
    const entries = [label('first'), label('second')]
    const baseline = createLabelBuilderStateSnapshot(meta, entries)

    expect(createLabelBuilderStateSnapshot({ ...meta, defaultLabelId: 'second' }, entries)).not.toBe(baseline)
  })
})

describe('default label builder state', () => {
  it('sets an existing default and clears it when that label is deleted', () => {
    const builder = useLabelBuilder()
    builder.labels.value = [label('first'), label('second')]
    builder.meta.defaultLabelId = null

    builder.setDefaultLabel('second')
    expect(builder.meta.defaultLabelId).toBe('second')

    builder.deleteLabel('second')
    expect(builder.meta.defaultLabelId).toBeNull()
  })

  it('ignores an unknown default label ID', () => {
    const builder = useLabelBuilder()
    builder.labels.value = [label('first')]
    builder.meta.defaultLabelId = null

    builder.setDefaultLabel('missing')
    expect(builder.meta.defaultLabelId).toBeNull()
  })
})

describe('PAGE mapping field changes', () => {
  it('normalizes loaded labels to the canonical custom block', () => {
    const loaded = label('loaded')
    loaded.mapping.pageXml.customKey = 'layout'
    loaded.mapping.pageXml.customData = 'subclass:lead;'

    expect(normalizeEditableLabel(loaded).mapping.pageXml).toMatchObject({
      customKey: 'structure',
      customData: ''
    })
  })

  it('clears custom text data when changing to a non-text region', () => {
    const pageXml = label('custom').mapping.pageXml
    pageXml.textType = 'custom'
    pageXml.customSubType = 'article'
    pageXml.customData = 'subclass:lead;'

    applyPageRegionTypeChange(pageXml, 'ImageRegion')

    expect(pageXml).toEqual({
      regionType: 'ImageRegion',
      textType: undefined,
      customSubType: '',
      customKey: 'structure',
      customData: ''
    })
  })

  it('starts a newly selected TextRegion with a paragraph subtype', () => {
    const pageXml = label('image').mapping.pageXml
    pageXml.regionType = 'ImageRegion'
    pageXml.textType = undefined
    pageXml.customSubType = 'photo'

    applyPageRegionTypeChange(pageXml, 'TextRegion')

    expect(pageXml.textType).toBe('paragraph')
    expect(pageXml.customSubType).toBe('')
  })

  it('clears custom fields when leaving the custom text subtype', () => {
    const pageXml = label('custom').mapping.pageXml
    pageXml.textType = 'custom'
    pageXml.customSubType = 'article'
    pageXml.customData = 'subclass:lead;'

    applyPageTextTypeChange(pageXml, 'heading')

    expect(pageXml.textType).toBe('heading')
    expect(pageXml.customSubType).toBe('')
    expect(pageXml.customData).toBe('')
  })

  it('canonicalizes the block even when the selected subtype does not change', () => {
    const pageXml = label('custom').mapping.pageXml
    pageXml.textType = 'custom'
    pageXml.customSubType = 'article'
    pageXml.customKey = 'layout'
    pageXml.customData = 'subclass:lead;'

    applyPageTextTypeChange(pageXml, 'custom')

    expect(pageXml.customKey).toBe('structure')
    expect(pageXml.customData).toBe('')
    expect(pageXml.customSubType).toBe('article')
  })
})
