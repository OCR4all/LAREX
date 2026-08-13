import { describe, expect, it } from 'vitest'
import { LabelDefinition, LabelSet } from '@/models/editor/labels'

function label(id: string): LabelDefinition {
  return new LabelDefinition(
    id,
    id,
    'region',
    '#123456',
    '',
    true,
    false,
    null,
    {
      pageXml: {
        regionType: 'TextRegion',
        textType: 'paragraph',
        customKey: 'structure',
        customData: ''
      }
    }
  )
}

describe('LabelSet default label', () => {
  it('uses the configured default label when it exists', () => {
    const first = label('first')
    const configured = label('configured')
    const labelSet = new LabelSet('set', 'Labels', [first, configured], '', configured.id)

    expect(labelSet.getDefaultLabel()).toBe(configured)
  })

  it('falls back to the first ordered label', () => {
    const first = label('first')
    const second = label('second')

    expect(new LabelSet('set', 'Labels', [first, second]).getDefaultLabel()).toBe(first)
    expect(new LabelSet('set', 'Labels', [first, second], '', 'missing').getDefaultLabel()).toBe(first)
  })
})
