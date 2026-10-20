import { describe, expect, it } from 'vitest'
import { getTextSidebarItems } from '../text-sidebar-items'

describe('getTextSidebarItems', () => {
  it.each(['visual', 'expert'] as const)('keeps settings in the %s sidebar while filters live in the list toolbar', (mode) => {
    expect(getTextSidebarItems(mode).map(item => item.slot)).toEqual([
      'metadata',
      'settings',
      'virtualKeyboard',
      'codec',
      'dictionary',
      'diff'
    ])
  })

  it('keeps only controls that apply to Full text mode', () => {
    const items = getTextSidebarItems('full')

    expect(items.map(item => item.slot)).toEqual([
      'metadata',
      'settings',
      'virtualKeyboard',
      'codec',
      'dictionary',
      'diff'
    ])
    expect(items.find(item => item.slot === 'diff')).toMatchObject({
      label: 'Text Variants',
      icon: 'i-lucide-list-ordered'
    })
  })
})
