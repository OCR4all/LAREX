import DiffMatchPatch from 'diff-match-patch'
import type { Diff } from 'diff-match-patch'

export type NormalizationDiffSegment = {
  text: string
  changed: boolean
}

const dmp = new DiffMatchPatch()

export function buildNormalizationDiffSegments(
  originalText: string,
  normalizedText: string,
  side: 'original' | 'normalized'
): NormalizationDiffSegment[] {
  const diffs = dmp.diff_main(originalText, normalizedText) as Diff[]
  dmp.diff_cleanupSemantic(diffs)

  const hiddenOperation = side === 'original' ? 1 : -1
  const segments = diffs
    .filter(([operation, text]) => operation !== hiddenOperation && text)
    .map(([operation, text]) => ({ text, changed: operation !== 0 }))

  return segments.length > 0 ? segments : [{ text: side === 'original' ? originalText : normalizedText, changed: false }]
}
