import type { ResourceCapabilities } from './capabilities'

export interface KeyboardItem {
  id: number
  x: number
  y: number
  w: number
  char: string
  shiftChar: string
  colorClass?: string | null
  textClass?: string | null
  description?: string
  shiftDescription?: string
}

export interface KeyboardLayout {
  id: string
  name: string
  description: string
  tags?: string[]
  cols: number
  rows: number
  themeId?: string
  items: KeyboardItem[]
  capabilities?: ResourceCapabilities
}

export interface BoardTheme {
  id?: string
  name: string
  bgClass: string
  borderClass: string
  gridLineClass: string
  keyBgClass: string
  keyTextClass: string
  previewClass: string
  bgStyle?: string
  keyBgStyle?: string
  keyTextStyle?: string
}

export interface Glyph {
  codepoint: string
  utf8: string
  description: string
  ent?: string
  source: 'mufi' | 'unicode'
}
