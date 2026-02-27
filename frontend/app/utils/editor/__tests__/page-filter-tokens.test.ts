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
      altoXml: {
        role: 'TAGREFS',
        tag: 'label'
      },
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
        altoXml: { role: 'TAGREFS', tag: 'paragraph' },
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
        altoXml: { role: 'TAGREFS', tag: 'heading' },
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

  it('creates sorted key-value line custom token', () => {
    const token = createCanonicalTokenFromLabelDefinition(label({
      scope: 'line',
      mapping: {
        altoXml: { role: 'TAGREFS', tag: 'line' },
        pageXml: {
          customKey: 'structure',
          customData: 'zeta:2; alpha:1',
          regionType: null,
          textType: null,
          customSubType: null
        }
      }
    }))

    expect(token).toBe('line|customKey=structure|pairs=alpha=1,zeta=2')
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
          altoXml: { role: 'TAGREFS', tag: 'paragraph' },
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
          altoXml: { role: 'TAGREFS', tag: 'heading' },
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
          altoXml: { role: 'TAGREFS', tag: 'heading-dup' },
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
