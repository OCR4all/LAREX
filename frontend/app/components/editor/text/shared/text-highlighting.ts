export type HighlightSegment = {
  text: string
  matched: boolean
}

export type DictionaryFormLike = {
  display: string
  normalized: string
}

export type DictionaryTokenSegment = {
  text: string
  unknown: boolean
}

export function hasTextHighlight(text: string, query: string): boolean {
  const normalizedQuery = query.trim()
  if (!normalizedQuery) return false
  return text.toLocaleLowerCase().includes(normalizedQuery.toLocaleLowerCase())
}

export function getTextHighlightShellClass(index: number | undefined, text: string, query: string, variantRole: (index: number | undefined) => 'gt' | 'recognition' | 'nonAssigned'): string {
  if (!hasTextHighlight(text, query)) return ''

  switch (variantRole(index)) {
    case 'gt':
      return 'rounded-md bg-emerald-100/95 dark:bg-emerald-900/90'
    case 'recognition':
      return 'rounded-md bg-slate-400/12'
    case 'nonAssigned':
      return 'rounded-md bg-rose-50/90 dark:bg-rose-950/50'
  }
}

export function getHighlightedSegments(text: string, query: string): HighlightSegment[] {
  const normalizedQuery = query.trim()
  if (!normalizedQuery) return [{ text, matched: false }]

  const lowerText = text.toLocaleLowerCase()
  const lowerQuery = normalizedQuery.toLocaleLowerCase()
  if (!lowerQuery || !lowerText.includes(lowerQuery)) {
    return [{ text, matched: false }]
  }

  const result: HighlightSegment[] = []
  let cursor = 0

  while (cursor < text.length) {
    const nextIndex = lowerText.indexOf(lowerQuery, cursor)
    if (nextIndex < 0) {
      result.push({ text: text.slice(cursor), matched: false })
      break
    }

    if (nextIndex > cursor) {
      result.push({ text: text.slice(cursor, nextIndex), matched: false })
    }

    const matchEnd = nextIndex + normalizedQuery.length
    result.push({ text: text.slice(nextIndex, matchEnd), matched: true })
    cursor = matchEnd
  }

  return result
}

export function tokenizeForDictionary(text: string): string[] {
  const tokens: string[] = []
  if (!text) return tokens

  let current = ''
  const chars = Array.from(text)

  const isWord = (char: string | undefined) => !!char && /[\p{L}\p{N}]/u.test(char)
  const isJoiner = (char: string | undefined) => !!char && /['’\-‐‑‒–—]/u.test(char)

  for (let index = 0; index < chars.length; index += 1) {
    const char = chars[index]
    const next = chars[index + 1]

    if (isWord(char)) {
      current += char
      continue
    }

    if (isJoiner(char) && current.length > 0 && isWord(next)) {
      current += char
      continue
    }

    if (current && /[\p{L}\p{N}]/u.test(current)) {
      tokens.push(current)
    }
    current = ''
  }

  if (current && /[\p{L}\p{N}]/u.test(current)) {
    tokens.push(current)
  }

  return tokens
}

export function normalizeDictionaryToken(token: string, options?: {
  caseSensitive?: boolean
  unicodeNormalization?: string
}): string {
  const normalization = options?.unicodeNormalization || 'NFC'
  const normalized = token.normalize(normalization as 'NFC' | 'NFD' | 'NFKC' | 'NFKD')
  return options?.caseSensitive ? normalized : normalized.toLocaleLowerCase()
}

export function getUnknownDictionaryTokenSegments(
  text: string,
  forms: DictionaryFormLike[],
  options?: {
    enabled?: boolean
    caseSensitive?: boolean
    unicodeNormalization?: string
  }
): DictionaryTokenSegment[] {
  if (!options?.enabled) return [{ text, unknown: false }]

  const normalizedForms = new Set(forms.map(form => form.normalized))
  if (normalizedForms.size === 0) return [{ text, unknown: false }]

  const segments: DictionaryTokenSegment[] = []
  let cursor = 0

  for (const token of tokenizeForDictionary(text)) {
    const index = text.indexOf(token, cursor)
    if (index < 0) continue

    if (index > cursor) {
      segments.push({ text: text.slice(cursor, index), unknown: false })
    }

    const normalized = normalizeDictionaryToken(token, options)
    segments.push({
      text: token,
      unknown: !normalizedForms.has(normalized)
    })
    cursor = index + token.length
  }

  if (cursor < text.length) {
    segments.push({ text: text.slice(cursor), unknown: false })
  }

  return segments.length > 0 ? segments : [{ text, unknown: false }]
}
