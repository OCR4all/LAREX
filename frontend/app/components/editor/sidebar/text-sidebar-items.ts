import type { TextModeSubmode } from '@/stores/editor/types'

export type TextSidebarSlot
  = | 'metadata'
    | 'tasks'
    | 'settings'
    | 'virtualKeyboard'
    | 'codec'
    | 'dictionary'
    | 'diff'
    | 'filter'

export type TextSidebarItem = {
  label: string
  icon: string
  slot: TextSidebarSlot
}

const TEXT_SIDEBAR_ITEMS: readonly TextSidebarItem[] = [
  { label: 'Metadata', icon: 'i-lucide-badge-info', slot: 'metadata' },
  { label: 'Tasks', icon: 'i-lucide-check-square', slot: 'tasks' },
  { label: 'Settings', icon: 'i-lucide-settings', slot: 'settings' },
  { label: 'Virtual Keyboard', icon: 'i-lucide-keyboard', slot: 'virtualKeyboard' },
  { label: 'Codec', icon: 'i-lucide-badge-check', slot: 'codec' },
  { label: 'Dictionary', icon: 'i-lucide-book-copy', slot: 'dictionary' },
  { label: 'Diff', icon: 'i-lucide-git-compare', slot: 'diff' },
  { label: 'Filter', icon: 'i-lucide-filter', slot: 'filter' }
]

const FULL_TEXT_SIDEBAR_SLOTS = new Set<TextSidebarSlot>([
  'metadata',
  'tasks',
  'settings',
  'virtualKeyboard',
  'diff'
])

export function getTextSidebarItems(mode: TextModeSubmode): TextSidebarItem[] {
  if (mode !== 'full') return [...TEXT_SIDEBAR_ITEMS]

  return TEXT_SIDEBAR_ITEMS
    .filter(item => FULL_TEXT_SIDEBAR_SLOTS.has(item.slot))
    .map(item => item.slot === 'diff'
      ? { ...item, label: 'Text Variants', icon: 'i-lucide-list-ordered' }
      : { ...item })
}
