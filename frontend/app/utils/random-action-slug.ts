const ACTION_SLUG_ADJECTIVES = [
  'aligned',
  'annotated',
  'crisp',
  'dense',
  'drifting',
  'faded',
  'gilded',
  'inked',
  'legible',
  'marginal',
  'nested',
  'oblique',
  'parsed',
  'ruled',
  'scanned',
  'serif',
  'skewed',
  'tagged',
  'typed',
  'unified'
]

const ACTION_SLUG_NOUNS = [
  'baseline',
  'bounding',
  'caption',
  'column',
  'corpus',
  'folio',
  'glyph',
  'header',
  'kerning',
  'label',
  'ligature',
  'margin',
  'ocr',
  'recto',
  'region',
  'rune',
  'token',
  'verso',
  'viewport',
  'zone'
]

export function generateRandomActionSlug(existingIds: Iterable<string> = []) {
  const existing = new Set(existingIds)
  for (let attempt = 0; attempt < 10; attempt += 1) {
    const slug = [
      randomWord(ACTION_SLUG_ADJECTIVES),
      randomWord(ACTION_SLUG_NOUNS),
      randomWord(ACTION_SLUG_NOUNS)
    ].join('-')
    if (!existing.has(slug)) {
      return slug
    }
  }
  return `${randomWord(ACTION_SLUG_ADJECTIVES)}-${randomWord(ACTION_SLUG_NOUNS)}-${randomSuffix()}`
}

function randomWord(words: string[]) {
  return words[Math.floor(Math.random() * words.length)] ?? 'action'
}

function randomSuffix() {
  if (import.meta.client && 'crypto' in globalThis && 'randomUUID' in globalThis.crypto) {
    return globalThis.crypto.randomUUID().slice(0, 8)
  }
  return Math.random().toString(36).slice(2, 10)
}
