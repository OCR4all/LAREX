import { describe, expect, it } from 'vitest'
import { getTextSidebarItems } from '../text-sidebar-items'

describe('getTextSidebarItems', () => {
  it.each(['visual', 'expert'] as const)('keeps the complete Text sidebar in %s mode', (mode) => {
    expect(getTextSidebarItems(mode).map(item => item.slot)).toEqual([
      'metadata',
      'tasks',
      'settings',
      'virtualKeyboard',
      'codec',
      'dictionary',
      'diff',
      'filter'
    ])
  })

  it('keeps only controls that apply to Full text mode', () => {
    const items = getTextSidebarItems('full')

    expect(items.map(item => item.slot)).toEqual([
      'metadata',
      'tasks',
      'settings',
      'virtualKeyboard',
      'diff'
    ])
    expect(items.find(item => item.slot === 'diff')).toMatchObject({
      label: 'Text Variants',
      icon: 'i-lucide-list-ordered'
    })
  })
})
