export const UNINDEXED_TEXT_INDEX = -1

export function textIndexOf(index: number | null | undefined): number {
  return typeof index === 'number' && Number.isFinite(index) && index >= 0 ? Math.trunc(index) : UNINDEXED_TEXT_INDEX
}

export function matchesTextIndex(index: number | null | undefined, textIndex: number): boolean {
  return textIndexOf(index) === textIndex
}

export function variantIndexFor(textIndex: number): number | undefined {
  return textIndex === UNINDEXED_TEXT_INDEX ? undefined : textIndex
}

export function normalizeGtTextIndex(value: unknown, fallback = 0): number {
  const parsed = Number(value)
  if (!Number.isFinite(parsed)) return fallback
  if (parsed === UNINDEXED_TEXT_INDEX) return UNINDEXED_TEXT_INDEX
  return parsed >= 0 ? Math.trunc(parsed) : fallback
}

export function formatTextIndex(textIndex: number): string {
  return textIndex === UNINDEXED_TEXT_INDEX ? 'Undefined' : String(textIndex)
}
