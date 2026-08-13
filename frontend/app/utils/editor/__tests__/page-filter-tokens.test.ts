import { describe, expect, it } from 'vitest'
import type { LabelDefinition } from '@/types/label-set'
import {
  createCanonicalLabelFilterOptions,
  createCanonicalTokenFromLabelDefinition,
  createRegionBaseToken,
  normalizeLegacyLabelFilterValues
} from '../page-filter-tokens'

function label(overrides: Partial<LabelDefinition>): LabelDefinition {
  return {
    id: overrides.id ?? 'label-1',
    scope: overrides.scope ?? 'region',
    name: overrides.name ?? 'Label',
    color: overrides.color ?? '#000000',
    hasText: overrides.hasText ?? true,
    isContainer: overrides.isContainer ?? false,
    mapping: overrides.mapping ?? {
      pageXml: {
        customKey: 'structure',
        regionType: 'TextRegion',
        textType: 'paragraph',
        customSubType: null,
        customData: null
      }
    }
  }
}

describe('page-filter-tokens', () => {
  it('creates distinct canonical tokens for text region subtypes', () => {
    const paragraph = createCanonicalTokenFromLabelDefinition(label({
      id: 'l-paragraph',
      mapping: {
        pageXml: {
          customKey: 'structure',
          regionType: 'TextRegion',
          textType: 'paragraph',
          customSubType: null,
          customData: null
        }
      }
    }))

    const heading = createCanonicalTokenFromLabelDefinition(label({
      id: 'l-heading',
      mapping: {
        pageXml: {
          customKey: 'structure',
          regionType: 'TextRegion',
          textType: 'heading',
          customSubType: null,
          customData: null
        }
      }
    }))

    expect(paragraph).toBe('region|kind=TextRegion|textType=paragraph')
    expect(heading).toBe('region|kind=TextRegion|textType=heading')
    expect(paragraph).not.toBe(heading)
  })

  it('normalizes legacy filter values and keeps canonical tokens', () => {
    const normalized = normalizeLegacyLabelFilterValues([
      'TextRegion',
      'region|kind=TextRegion|textType=heading',
      'unknown-value',
      '  ',
      createRegionBaseToken('ImageRegion')
    ])

    expect(normalized).toEqual([
      'region|kind=TextRegion',
      'region|kind=TextRegion|textType=heading',
      'region|kind=ImageRegion'
    ])
  })

  it('builds unique canonical option values for filter menus', () => {
    const options = createCanonicalLabelFilterOptions([
      label({
        id: 'text-p',
        name: 'Paragraph',
        mapping: {
          pageXml: {
            customKey: 'structure',
            regionType: 'TextRegion',
            textType: 'paragraph',
            customSubType: null,
            customData: null
          }
        }
      }),
      label({
        id: 'text-h',
        name: 'Heading',
        mapping: {
          pageXml: {
            customKey: 'structure',
            regionType: 'TextRegion',
            textType: 'heading',
            customSubType: null,
            customData: null
          }
        }
      }),
      label({
        id: 'text-h-duplicate',
        name: 'Heading duplicate',
        mapping: {
          pageXml: {
            customKey: 'structure',
            regionType: 'TextRegion',
            textType: 'heading',
            customSubType: null,
            customData: null
          }
        }
      })
    ])

    expect(options.map(o => o.value)).toEqual([
      'region|kind=TextRegion|textType=paragraph',
      'region|kind=TextRegion|textType=heading'
    ])
  })
})
