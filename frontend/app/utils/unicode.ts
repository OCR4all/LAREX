export const isPUA = (hex: string) => {
  if (!hex) return false
  const code = parseInt(hex, 16)
  if (isNaN(code)) return false
  return (code >= 0xE000 && code <= 0xF8FF) || (code >= 0xF0000 && code <= 0xFFFFD) || (code >= 0x100000 && code <= 0x10FFFD)
}

export const toUnicodeCodepoint = (value: string) => {
  const codepoint = value.codePointAt(0)
  if (!codepoint) return null
  const minWidth = codepoint > 0xFFFF ? 6 : 4
  return `U+${codepoint.toString(16).toUpperCase().padStart(minWidth, '0')}`
}
