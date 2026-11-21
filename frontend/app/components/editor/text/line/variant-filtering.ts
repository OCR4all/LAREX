export type TextVariantLike = {
  index?: number
  confidence?: number
}

export type VariantFilterState = {
  selectedIndices: number[]
  filterUnindexed: boolean
  confidenceRange: [number, number]
  hasIndexFilter: boolean
  hasConfidenceFilter: boolean
  hasVariantFilter: boolean
}

const CONFIDENCE_MIN = 0
const CONFIDENCE_MAX = 1
const EPSILON = 1e-9

export function hasConfidenceFilter(confidenceRange: [number, number]): boolean {
  const [min, max] = confidenceRange
  return min > CONFIDENCE_MIN + EPSILON || max < CONFIDENCE_MAX - EPSILON
}

export function createVariantFilterState(input: {
  selectedIndices: number[]
  filterUnindexed: boolean
  confidenceRange: [number, number]
}): VariantFilterState {
  const hasIndexFilter = input.selectedIndices.length > 0 || input.filterUnindexed
  const hasConfidence = hasConfidenceFilter(input.confidenceRange)
  return {
    selectedIndices: input.selectedIndices,
    filterUnindexed: input.filterUnindexed,
    confidenceRange: input.confidenceRange,
    hasIndexFilter,
    hasConfidenceFilter: hasConfidence,
    hasVariantFilter: hasIndexFilter || hasConfidence
  }
}

export function matchesVariantFilter(variant: TextVariantLike, state: VariantFilterState): boolean {
  if (state.hasIndexFilter) {
    const hasValidIndex = typeof variant.index === 'number' && Number.isFinite(variant.index) && variant.index >= 0
    const matchesIndex = hasValidIndex && state.selectedIndices.includes(variant.index as number)
    const matchesUnindexed = !hasValidIndex && state.filterUnindexed
    if (!matchesIndex && !matchesUnindexed) return false
  }

  if (state.hasConfidenceFilter) {
    if (typeof variant.confidence !== 'number' || !Number.isFinite(variant.confidence)) return false
    const [min, max] = state.confidenceRange
    return variant.confidence >= min && variant.confidence <= max
  }

  return true
}

export function filterTextContentVariants<T extends TextVariantLike>(variants: T[], state: VariantFilterState): T[] {
  return variants.filter(variant => matchesVariantFilter(variant, state))
}

export function getMinVariantConfidence<T extends TextVariantLike>(variants: T[]): number | undefined {
  let min: number | undefined
  for (const variant of variants) {
    if (typeof variant.confidence !== 'number' || !Number.isFinite(variant.confidence)) continue
    if (min === undefined || variant.confidence < min) {
      min = variant.confidence
    }
  }
  return min
}

export function compareConfidenceLowFirst(a: number | undefined, b: number | undefined): number {
  return (a ?? Number.POSITIVE_INFINITY) - (b ?? Number.POSITIVE_INFINITY)
}

export function getHiddenTextVariantIndices(
  allVariants: Array<{ pos: number, index?: number, text: string }>,
  visibleVariants: Array<{ pos: number }>
): Array<number | undefined> {
  const visiblePositions = new Set(visibleVariants.map(variant => variant.pos))
  const indices = new Set<number | undefined>()
  for (const variant of allVariants) {
    if (visiblePositions.has(variant.pos) || variant.text.trim().length === 0) continue
    indices.add(variant.index)
  }
  return [...indices].sort((a, b) => (a ?? Number.POSITIVE_INFINITY) - (b ?? Number.POSITIVE_INFINITY))
}

export type VariantVisibilitySettings = {
  selectedIndices: number[]
  filterUnindexed: boolean
  confidenceRange: [number, number]
  showNonAssignedIndices: boolean
}

export function revealTextVariants<S extends VariantVisibilitySettings>(
  settings: S,
  variants: TextVariantLike[],
  isNonAssigned: (index: number | undefined) => boolean
): S {
  const state = createVariantFilterState(settings)
  const selectedIndices = new Set(settings.selectedIndices)
  let filterUnindexed = settings.filterUnindexed
  let confidenceRange = settings.confidenceRange
  let showNonAssignedIndices = settings.showNonAssignedIndices

  for (const variant of variants) {
    if (state.hasIndexFilter && !matchesVariantFilter(variant, { ...state, hasConfidenceFilter: false })) {
      if (typeof variant.index === 'number' && Number.isFinite(variant.index) && variant.index >= 0) {
        selectedIndices.add(variant.index)
      } else {
        filterUnindexed = true
      }
    }
    if (state.hasConfidenceFilter && !matchesVariantFilter(variant, { ...state, hasIndexFilter: false })) {
      confidenceRange = [CONFIDENCE_MIN, CONFIDENCE_MAX]
    }
    if (isNonAssigned(variant.index)) {
      showNonAssignedIndices = true
    }
  }

  return {
    ...settings,
    selectedIndices: [...selectedIndices].sort((a, b) => a - b),
    filterUnindexed,
    confidenceRange,
    showNonAssignedIndices
  }
}
