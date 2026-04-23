export interface VirtualKeyboardPalette {
  boardClass: string
  boardBorderClass: string
  gridLineClass: string
  keyBgClass: string
  keyTextClass: string
  boardStyle: string
  keyBgStyle: string
  keyTextStyle: string
}

const DARK_PALETTE: VirtualKeyboardPalette = {
  boardClass: 'bg-neutral-900',
  boardBorderClass: 'border-neutral-700',
  gridLineClass: 'border-neutral-700',
  keyBgClass: 'bg-neutral-800',
  keyTextClass: 'text-neutral-100',
  boardStyle: '#171717',
  keyBgStyle: '#262626',
  keyTextStyle: '#fafafa'
}

const LIGHT_PALETTE: VirtualKeyboardPalette = {
  boardClass: 'bg-neutral-100',
  boardBorderClass: 'border-neutral-300',
  gridLineClass: 'border-neutral-300',
  keyBgClass: 'bg-white',
  keyTextClass: 'text-neutral-800',
  boardStyle: '#f5f5f5',
  keyBgStyle: '#ffffff',
  keyTextStyle: '#1f2937'
}

export function useVirtualKeyboardPalette() {
  const colorMode = useColorMode()
  return computed<VirtualKeyboardPalette>(() =>
    colorMode.value === 'dark' ? DARK_PALETTE : LIGHT_PALETTE
  )
}

export function getVirtualKeyboardGridLineColor(boardStyle: string): string {
  if (!boardStyle.startsWith('#')) {
    return 'rgba(0,0,0,0.1)'
  }

  const hex = boardStyle.replace('#', '')
  const normalizedHex = hex.length === 3
    ? `${hex[0]}${hex[0]}${hex[1]}${hex[1]}${hex[2]}${hex[2]}`
    : hex
  const r = parseInt(normalizedHex.slice(0, 2), 16)
  const g = parseInt(normalizedHex.slice(2, 4), 16)
  const b = parseInt(normalizedHex.slice(4, 6), 16)
  const yiq = ((r * 299) + (g * 587) + (b * 114)) / 1000
  return yiq >= 128 ? 'rgba(23,23,23,0.1)' : 'rgba(245,245,245,0.18)'
}
