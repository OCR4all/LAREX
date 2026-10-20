import type { TextModeSubmode } from '@/stores/editor/types'

export type TextSidebarSlot
  = | 'metadata'
    | 'settings'
    | 'virtualKeyboard'
    | 'codec'
    | 'dictionary'
    | 'diff'

export type TextSidebarItem = {
  label: string
  icon: string
  slot: TextSidebarSlot
}

const TEXT_SIDEBAR_ITEMS: readonly TextSidebarItem[] = [
  { label: 'Metadata', icon: 'i-lucide-badge-info', slot: 'metadata' },
  { label: 'Settings', icon: 'i-lucide-settings', slot: 'settings' },
  { label: 'Virtual Keyboard', icon: 'i-lucide-keyboard', slot: 'virtualKeyboard' },
  { label: 'Codec', icon: 'i-lucide-badge-check', slot: 'codec' },
  { label: 'Dictionary', icon: 'i-lucide-book-copy', slot: 'dictionary' },
  { label: 'Diff', icon: 'i-lucide-git-compare', slot: 'diff' }
]

export function getTextSidebarItems(mode: TextModeSubmode): TextSidebarItem[] {
  if (mode !== 'full') return [...TEXT_SIDEBAR_ITEMS]

  return TEXT_SIDEBAR_ITEMS.map(item => item.slot === 'diff'
    ? { ...item, label: 'Text Variants', icon: 'i-lucide-list-ordered' }
    : { ...item })
}
