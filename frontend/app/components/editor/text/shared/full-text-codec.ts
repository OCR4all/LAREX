export type FullTextCodecSegment = {
  text: string
  start: number
  end: number
  unknown: boolean
}

export function getFullTextCodecSegments(
  text: string,
  codecCharacters: ReadonlySet<string>,
  includeWhitespace: boolean
): FullTextCodecSegment[] {
  if (!text) return []

  const segments: FullTextCodecSegment[] = []
  let cursor = 0

  for (const character of Array.from(text)) {
    const start = cursor
    const end = start + character.length
    const unknown = (includeWhitespace || !/\s/u.test(character))
      && !codecCharacters.has(character)
    const previous = segments.at(-1)

    if (previous?.unknown === unknown) {
      previous.text += character
      previous.end = end
    } else {
      segments.push({
        text: character,
        start,
        end,
        unknown
      })
    }
    cursor = end
  }

  return segments
}

export function getUnknownCodecCharacters(segments: FullTextCodecSegment[]): string[] {
  const unknownCharacters = new Set<string>()
  for (const segment of segments) {
    if (!segment.unknown) continue
    for (const character of Array.from(segment.text)) {
      unknownCharacters.add(character)
    }
  }
  return [...unknownCharacters]
}
