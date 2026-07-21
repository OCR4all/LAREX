import { tokenizeForDictionary } from './text-highlighting'

export type FullTextDictionarySegment = {
  text: string
  start: number
  end: number
  token: boolean
  unknown: boolean
  pending: boolean
}

type DictionaryLookupResult = {
  known: boolean
} | null

export function getMissingFullTextDictionaryTokens(
  tokens: string[],
  getResult: (token: string) => DictionaryLookupResult
): string[] {
  return tokens.filter(token => getResult(token) === null)
}

export function getFullTextDictionarySegments(
  text: string,
  getResult: (token: string) => DictionaryLookupResult
): FullTextDictionarySegment[] {
  if (!text) return []

  const segments: FullTextDictionarySegment[] = []
  let cursor = 0

  for (const token of tokenizeForDictionary(text)) {
    const index = text.indexOf(token, cursor)
    if (index < 0) continue

    if (index > cursor) {
      segments.push({
        text: text.slice(cursor, index),
        start: cursor,
        end: index,
        token: false,
        unknown: false,
        pending: false
      })
    }

    const result = getResult(token)
    segments.push({
      text: token,
      start: index,
      end: index + token.length,
      token: true,
      unknown: result?.known === false,
      pending: result === null
    })
    cursor = index + token.length
  }

  if (cursor < text.length) {
    segments.push({
      text: text.slice(cursor),
      start: cursor,
      end: text.length,
      token: false,
      unknown: false,
      pending: false
    })
  }

  return segments.length > 0
    ? segments
    : [{
        text,
        start: 0,
        end: text.length,
        token: false,
        unknown: false,
        pending: false
      }]
}

export function getUnknownFullTextDictionarySegmentAtOffset(
  segments: FullTextDictionarySegment[],
  offset: number
): FullTextDictionarySegment | null {
  if (!Number.isFinite(offset) || offset < 0) return null
  return segments.find(segment =>
    segment.unknown
    && offset >= segment.start
    && offset <= segment.end
  ) ?? null
}
