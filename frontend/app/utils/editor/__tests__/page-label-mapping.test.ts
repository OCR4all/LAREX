import { describe, expect, it } from 'vitest'
import type { LabelDefinition } from '@/types/label-set'
import {
  buildMergedCustomForAppliedRegionLabel,
  buildMergedCustomForRegionLabel,
  clearLarexRegionLabelMetadata,
  createCanonicalRegionMappingSignatureFromLabel,
  findRegionLabelDefinitionForRegion,
  parsePageCustomBlocks,
  resolveRegionLabelDisplayName,
  resolvePageXmlRegionLabel,
  serializePageXmlRegionStartTag
} from '../page-label-mapping'

function createRegionLabel(overrides: Partial<LabelDefinition> = {}): LabelDefinition {
  return {
    id: overrides.id ?? 'l1',
    scope: 'region',
    name: overrides.name ?? 'Label',
    description: null,
    color: '#123ABC',
    hasText: true,
    isContainer: false,
    group: null,
    mapping: overrides.mapping ?? {
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

describe('page-label-mapping', () => {
  it('parses PAGE custom blocks and merges repeated blocks', () => {
    const parsed = parsePageCustomBlocks('structure { type:custom; x:1; } reading { dir:ltr; } structure { y:2; }')
    expect(parsed).toEqual({
      structure: { type: 'custom', x: '1', y: '2' },
      reading: { dir: 'ltr' }
    })
  })

  it('ignores malformed PAGE custom fragments', () => {
    const parsed = parsePageCustomBlocks('garbage structure { ok:1; broken; } trailing')
    expect(parsed).toEqual({
      structure: { ok: '1' }
    })
  })

  it('merges label custom payload into structure block while preserving unrelated blocks', () => {
    const label = createRegionLabel({
      mapping: {
        pageXml: {
          regionType: 'TextRegion',
          textType: 'custom',
          customSubType: 'article',
          customKey: 'structure',
          customData: 'subclass:lead'
        }
      }
    })

    const merged = buildMergedCustomForRegionLabel('reading { dir:ltr; } structure { foo:bar; }', label.mapping.pageXml)
    expect(merged).toBe('reading { dir:ltr; } structure { foo:bar; subclass:lead; type:article; }')
  })

  it('stores label alias metadata in larex custom block when applying label', () => {
    const label = createRegionLabel({
      id: 'label-42',
      name: 'Main Heading'
    })

    const merged = buildMergedCustomForAppliedRegionLabel('reading { dir:ltr; }', label)
    expect(merged).toBe('larex { labelAlias:Main Heading; labelId:label-42; } reading { dir:ltr; }')
  })

  it('uses the configured label name instead of the raw PAGE subtype for display', () => {
    const label = createRegionLabel({
      id: 'custom-c',
      name: 'c'
    })

    expect(resolveRegionLabelDisplayName([label], {
      kind: 'TextRegion',
      type: 'paragraph',
      custom: 'larex { labelId:custom-c; labelAlias:c; }'
    }, 'paragraph')).toBe('c')

    expect(resolveRegionLabelDisplayName([], {
      kind: 'TextRegion',
      type: 'paragraph'
    }, 'paragraph')).toBe('paragraph')
  })

  it('resolves the same custom TextRegion attributes used by the editor and XML preview', () => {
    const label = createRegionLabel({
      mapping: {
        pageXml: {
          regionType: 'TextRegion',
          textType: 'custom',
          customSubType: 'article',
          customKey: 'structure',
          customData: 'subclass:lead'
        }
      }
    })

    expect(resolvePageXmlRegionLabel(label)).toEqual({
      regionType: 'TextRegion',
      type: 'other',
      custom: 'larex { labelAlias:Label; labelId:l1; } structure { subclass:lead; type:article; }'
    })
    expect(serializePageXmlRegionStartTag(label.mapping.pageXml)).toBe(
      '<TextRegion type="other" custom="structure { subclass:lead; type:article; }">'
    )
  })

  it('ignores stale text types when serializing non-text regions', () => {
    expect(serializePageXmlRegionStartTag({
      regionType: 'ImageRegion',
      textType: 'header',
      customSubType: ''
    })).toBe('<ImageRegion>')
  })

  it('serializes a regular TextRegion subtype', () => {
    expect(serializePageXmlRegionStartTag({
      regionType: 'TextRegion',
      textType: 'heading'
    })).toBe('<TextRegion type="heading">')
  })

  it('uses customSubType as PAGE type for non-text regions', () => {
    expect(serializePageXmlRegionStartTag({
      regionType: 'GraphicRegion',
      textType: 'heading',
      customSubType: 'logo'
    })).toBe('<GraphicRegion type="logo">')
  })

  it('escapes generated PAGE XML attribute values', () => {
    expect(serializePageXmlRegionStartTag({
      regionType: 'TextRegion',
      textType: 'custom',
      customSubType: 'article & "feature"',
      customKey: 'structure',
      customData: ''
    })).toBe('<TextRegion type="other" custom="structure { type:article &amp; &quot;feature&quot;; }">')
  })

  it('clears only larex label metadata on manual type changes', () => {
    const cleared = clearLarexRegionLabelMetadata('larex { labelAlias:Main Heading; labelId:label-42; } reading { dir:ltr; }')
    expect(cleared).toBe('reading { dir:ltr; }')
  })

  it('builds distinct canonical signatures for custom text labels with different payload', () => {
    const a = createRegionLabel({
      mapping: {
        pageXml: {
          regionType: 'TextRegion',
          textType: 'custom',
          customSubType: 'article',
          customKey: 'structure',
          customData: 'subclass:lead'
        }
      }
    })
    const b = createRegionLabel({
      id: 'l2',
      mapping: {
        pageXml: {
          regionType: 'TextRegion',
          textType: 'custom',
          customSubType: 'article',
          customKey: 'structure',
          customData: 'subclass:body'
        }
      }
    })

    expect(createCanonicalRegionMappingSignatureFromLabel(a)).not.toBe(createCanonicalRegionMappingSignatureFromLabel(b))
  })

  it('prefers exact custom payload match over subset match', () => {
    const generic = createRegionLabel({
      id: 'generic',
      name: 'Generic Article',
      mapping: {
        pageXml: {
          regionType: 'TextRegion',
          textType: 'custom',
          customSubType: 'article',
          customKey: 'structure',
          customData: ''
        }
      }
    })
    const specific = createRegionLabel({
      id: 'specific',
      name: 'Lead Article',
      mapping: {
        pageXml: {
          regionType: 'TextRegion',
          textType: 'custom',
          customSubType: 'article',
          customKey: 'structure',
          customData: 'subclass:lead'
        }
      }
    })

    const match = findRegionLabelDefinitionForRegion([generic, specific], {
      kind: 'TextRegion',
      type: 'other',
      custom: 'structure { type:article; subclass:lead; ext:1; }'
    })

    expect(match?.id).toBe('specific')
  })

  it('treats plain TextRegion type=other as distinct from custom-text structure subtype', () => {
    const plainOther = createRegionLabel({
      id: 'plain-other',
      name: 'Other',
      mapping: {
        pageXml: {
          regionType: 'TextRegion',
          textType: 'other',
          customSubType: '',
          customKey: 'structure',
          customData: ''
        }
      }
    })
    const customFoo = createRegionLabel({
      id: 'custom-foo',
      name: 'Foo',
      mapping: {
        pageXml: {
          regionType: 'TextRegion',
          textType: 'custom',
          customSubType: 'foo',
          customKey: 'structure',
          customData: ''
        }
      }
    })

    const plainMatch = findRegionLabelDefinitionForRegion([plainOther, customFoo], {
      kind: 'TextRegion',
      type: 'other',
      custom: 'reading { dir:ltr; }'
    })
    expect(plainMatch?.id).toBe('plain-other')

    const structuredMatch = findRegionLabelDefinitionForRegion([plainOther, customFoo], {
      kind: 'TextRegion',
      type: 'other',
      custom: 'reading { dir:ltr; } structure { type:foo; }'
    })
    expect(structuredMatch?.id).toBe('custom-foo')
  })

  it('matches legacy malformed custom TextRegion type on reload fallback', () => {
    const custom = createRegionLabel({
      id: 'custom',
      name: 'Foo',
      mapping: {
        pageXml: {
          regionType: 'TextRegion',
          textType: 'custom',
          customSubType: 'foo',
          customKey: 'structure',
          customData: ''
        }
      }
    })

    const match = findRegionLabelDefinitionForRegion([custom], {
      kind: 'TextRegion',
      type: 'custom',
      custom: 'larex { labelAlias:Foo; } structure { type:foo; }'
    })

    expect(match?.id).toBe('custom')
  })

  it('matches custom label on reload fallback when TextRegion type is missing but structure.type exists', () => {
    const custom = createRegionLabel({
      id: 'custom',
      name: 'Foo',
      mapping: {
        pageXml: {
          regionType: 'TextRegion',
          textType: 'custom',
          customSubType: 'foo',
          customKey: 'structure',
          customData: ''
        }
      }
    })

    const match = findRegionLabelDefinitionForRegion([custom], {
      kind: 'TextRegion',
      type: '',
      custom: 'larex { labelAlias:Foo; } structure { type:foo; }'
    })

    expect(match?.id).toBe('custom')
  })
})
