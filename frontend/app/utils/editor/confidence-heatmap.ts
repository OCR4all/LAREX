import type { ConfidenceHeatmapMode } from '@/stores/editor/types'

export interface ConfidenceVariantLike {
  index?: number
  confidence?: number
}

export interface ComputeElementConfidenceInput {
  variants?: ConfidenceVariantLike[]
  elementConfidence?: number
  mode: ConfidenceHeatmapMode
  selectedIndices?: number[]
}

function clamp01(value: number): number {
  return Math.max(0, Math.min(1, value))
}

const DEFAULT_LOG_SCALE_FACTOR = 8

function normalizeLogScaleFactor(value: number): number {
  if (!Number.isFinite(value)) return DEFAULT_LOG_SCALE_FACTOR
  return Math.max(1, Math.min(32, Math.trunc(value)))
}

export function isFiniteConfidence(value: unknown): value is number {
  return typeof value === 'number' && Number.isFinite(value)
}

export function normalizeIndices(indices: number[] = []): number[] {
  return [...new Set(
    indices
      .map(v => Number(v))
      .filter((v): v is number => Number.isFinite(v) && v >= 0)
      .map(v => Math.trunc(v))
  )].sort((a, b) => a - b)
}

export function averageConfidence(values: number[]): number | undefined {
  if (!values.length) return undefined
  const sum = values.reduce((acc, value) => acc + value, 0)
  return sum / values.length
}

export function computeElementConfidence(input: ComputeElementConfidenceInput): number | undefined {
  const variants = Array.isArray(input.variants) ? input.variants : []
  const normalizedIndices = normalizeIndices(input.selectedIndices ?? [])

  const variantConfidences = variants
    .map(variant => variant.confidence)
    .filter(isFiniteConfidence)
    .map(clamp01)

  const fallbackConfidence = isFiniteConfidence(input.elementConfidence)
    ? clamp01(input.elementConfidence)
    : undefined

  if (input.mode === 'average' || normalizedIndices.length === 0) {
    return averageConfidence(variantConfidences) ?? fallbackConfidence
  }

  const selectedVariantConfidences = variants
    .filter((variant) => {
      if (!isFiniteConfidence(variant.confidence)) return false
      if (!isFiniteConfidence(variant.index)) return false
      return normalizedIndices.includes(Math.trunc(variant.index))
    })
    .map(variant => clamp01(variant.confidence as number))

  return averageConfidence(selectedVariantConfidences) ?? fallbackConfidence
}

export function scaleConfidenceForHeatmap(confidence: number, useLogScale: boolean, logScaleStrength: number = DEFAULT_LOG_SCALE_FACTOR): number {
  const c = clamp01(confidence)
  if (!useLogScale) return c

  const factor = normalizeLogScaleFactor(logScaleStrength)
  const transformed = 1 - (Math.log1p((1 - c) * factor) / Math.log1p(factor))
  return clamp01(transformed)
}

export function confidenceToHeatRgba(confidence: number, alpha: number): [number, number, number, number] {
  const c = clamp01(confidence)
  const a = clamp01(alpha)

  if (c <= 0.5) {
    const t = c / 0.5
    return [1, t, 0, a]
  }

  const t = (c - 0.5) / 0.5
  return [1 - t, 1, 0, a]
}
