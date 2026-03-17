export type HighlightSegment = {
  text: string
  matched: boolean
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
