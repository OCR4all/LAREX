export interface RGB {
  r: number
  g: number
  b: number
}

export interface HSL {
  h: number
  s: number
  l: number
}

export type TagVariant = 'solid' | 'subtle' | 'outline'

export interface TagColorStyles {
  backgroundColor: string
  color: string
  borderColor: string
}

export function isValidHex(hex: string): boolean {
  return /^#?([0-9A-Fa-f]{3}|[0-9A-Fa-f]{6})$/.test(hex)
}

function normalizeHex(hex: string): string {
  const value = hex.trim()
  return value.startsWith('#') ? value : `#${value}`
}

function hexToRgb(hex: string): RGB {
  let value = hex.replace(/^#/, '')
  if (value.length === 3) {
    const [r = '0', g = '0', b = '0'] = value
    value = r + r + g + g + b + b
  }
  const n = Number.parseInt(value, 16)
  return { r: (n >> 16) & 255, g: (n >> 8) & 255, b: n & 255 }
}

function rgbToHex({ r, g, b }: RGB): string {
  return `#${[r, g, b].map(c => c.toString(16).padStart(2, '0')).join('')}`
}

function rgbToHsl({ r, g, b }: RGB): HSL {
  const rn = r / 255
  const gn = g / 255
  const bn = b / 255
  const max = Math.max(rn, gn, bn)
  const min = Math.min(rn, gn, bn)
  const l = (max + min) / 2
  let h = 0
  let s = 0

  if (max !== min) {
    const d = max - min
    s = l > 0.5 ? d / (2 - max - min) : d / (max + min)
    switch (max) {
      case rn:
        h = ((gn - bn) / d + (gn < bn ? 6 : 0)) / 6
        break
      case gn:
        h = ((bn - rn) / d + 2) / 6
        break
      case bn:
        h = ((rn - gn) / d + 4) / 6
        break
    }
  }

  return {
    h: Math.round(h * 360),
    s: Math.round(s * 100),
    l: Math.round(l * 100)
  }
}

function hslToRgb({ h, s, l }: HSL): RGB {
  const sn = s / 100
  const ln = l / 100
  const c = (1 - Math.abs(2 * ln - 1)) * sn
  const x = c * (1 - Math.abs(((h / 60) % 2) - 1))
  const m = ln - c / 2

  let rn = 0
  let gn = 0
  let bn = 0
  if (h < 60) {
    rn = c
    gn = x
    bn = 0
  } else if (h < 120) {
    rn = x
    gn = c
    bn = 0
  } else if (h < 180) {
    rn = 0
    gn = c
    bn = x
  } else if (h < 240) {
    rn = 0
    gn = x
    bn = c
  } else if (h < 300) {
    rn = x
    gn = 0
    bn = c
  } else {
    rn = c
    gn = 0
    bn = x
  }

  return {
    r: Math.round((rn + m) * 255),
    g: Math.round((gn + m) * 255),
    b: Math.round((bn + m) * 255)
  }
}

function relativeLuminance({ r, g, b }: RGB): number {
  const channels = [r, g, b].map((c) => {
    const s = c / 255
    return s <= 0.03928 ? s / 12.92 : Math.pow((s + 0.055) / 1.055, 2.4)
  })
  const rs = channels[0] ?? 0
  const gs = channels[1] ?? 0
  const bs = channels[2] ?? 0
  return 0.2126 * rs + 0.7152 * gs + 0.0722 * bs
}

function contrastRatio(a: RGB, b: RGB): number {
  const la = relativeLuminance(a)
  const lb = relativeLuminance(b)
  const lighter = Math.max(la, lb)
  const darker = Math.min(la, lb)
  return (lighter + 0.05) / (darker + 0.05)
}

const WHITE: RGB = { r: 255, g: 255, b: 255 }
const BLACK: RGB = { r: 0, g: 0, b: 0 }

function getContrastingTextColor(bgHex: string, minRatio: number = 4.5): string {
  const bg = hexToRgb(bgHex)
  const ratioWhite = contrastRatio(bg, WHITE)
  const ratioBlack = contrastRatio(bg, BLACK)

  if (ratioWhite >= minRatio) return '#ffffff'
  if (ratioBlack >= minRatio) return '#000000'
  return ratioWhite > ratioBlack ? '#ffffff' : '#000000'
}

function tintedColor(hex: string, targetLightness: number, saturationScale: number = 0.4): string {
  const hsl = rgbToHsl(hexToRgb(hex))
  return rgbToHex(
    hslToRgb({
      h: hsl.h,
      s: Math.round(Math.min(100, hsl.s * saturationScale)),
      l: targetLightness
    })
  )
}

function darkenForContrast(hex: string, bgHex: string, minRatio: number = 4.5): string {
  const hsl = rgbToHsl(hexToRgb(hex))
  const bgRgb = hexToRgb(bgHex)

  for (let l = Math.min(hsl.l, 45); l >= 5; l -= 2) {
    const candidate = hslToRgb({ h: hsl.h, s: Math.min(hsl.s, 90), l })
    if (contrastRatio(candidate, bgRgb) >= minRatio) {
      return rgbToHex(candidate)
    }
  }
  return '#000000'
}

function lightenForContrast(hex: string, bgHex: string, minRatio: number = 4.5): string {
  const hsl = rgbToHsl(hexToRgb(hex))
  const bgRgb = hexToRgb(bgHex)

  for (let l = Math.max(hsl.l, 60); l <= 95; l += 2) {
    const candidate = hslToRgb({ h: hsl.h, s: Math.min(hsl.s, 80), l })
    if (contrastRatio(candidate, bgRgb) >= minRatio) {
      return rgbToHex(candidate)
    }
  }
  return '#ffffff'
}

export function getTagColors(hex: string, variant: TagVariant = 'subtle', isDark: boolean = false): TagColorStyles {
  const safeHex = normalizeHex(hex)

  switch (variant) {
    case 'solid': {
      const textColor = getContrastingTextColor(safeHex)
      return {
        backgroundColor: safeHex,
        color: textColor,
        borderColor: 'transparent'
      }
    }

    case 'subtle': {
      if (isDark) {
        const bg = tintedColor(safeHex, 18, 0.35)
        const text = lightenForContrast(safeHex, bg, 4.5)
        const border = tintedColor(safeHex, 28, 0.45)
        return { backgroundColor: bg, color: text, borderColor: border }
      }
      const bg = tintedColor(safeHex, 94, 0.7)
      const text = darkenForContrast(safeHex, bg, 4.5)
      const border = tintedColor(safeHex, 85, 0.5)
      return { backgroundColor: bg, color: text, borderColor: border }
    }

    case 'outline': {
      const pageBg = isDark ? '#1a1a1a' : '#ffffff'
      const pageBgRgb = hexToRgb(pageBg)
      const baseRgb = hexToRgb(safeHex)

      let textColor: string
      const rawContrast = contrastRatio(baseRgb, pageBgRgb)
      if (rawContrast >= 4.5) {
        textColor = safeHex
      } else if (isDark) {
        textColor = lightenForContrast(safeHex, pageBg, 4.5)
      } else {
        textColor = darkenForContrast(safeHex, pageBg, 4.5)
      }

      const borderHsl = rgbToHsl(hexToRgb(safeHex))
      const borderColor = rgbToHex(
        hslToRgb({
          h: borderHsl.h,
          s: Math.min(borderHsl.s, 70),
          l: isDark ? 45 : 60
        })
      )

      return {
        backgroundColor: 'transparent',
        color: textColor,
        borderColor
      }
    }
  }
}
