/**
 * Returns a readable foreground RGBA color based on a background hex color.
 * Intended for subtle UI elements such as dividers, grid lines, or borders.
 */
export const getReadableOverlayColor = (
  backgroundColor?: string
): string | undefined => {
  if (!backgroundColor || !backgroundColor.startsWith('#')) return undefined

  const hex = backgroundColor.slice(1)

  const normalized
    = hex.length === 3
      ? hex.split('').map(c => c + c).join('')
      : hex

  if (normalized.length !== 6) return undefined

  const r = parseInt(normalized.slice(0, 2), 16) / 255
  const g = parseInt(normalized.slice(2, 4), 16) / 255
  const b = parseInt(normalized.slice(4, 6), 16) / 255

  const toLinear = (c: number) =>
    c <= 0.03928 ? c / 12.92 : Math.pow((c + 0.055) / 1.055, 2.4)

  const luminance
    = 0.2126 * toLinear(r)
      + 0.7152 * toLinear(g)
      + 0.0722 * toLinear(b)

  if (luminance > 0.6) {
    const alpha = 0.12 + (luminance - 0.6) * 0.15
    return `rgba(0, 0, 0, ${Math.min(alpha, 0.3)})`
  } else {
    const alpha = 0.08 + (0.6 - luminance) * 0.2
    return `rgba(255, 255, 255, ${Math.min(alpha, 0.3)})`
  }
}
