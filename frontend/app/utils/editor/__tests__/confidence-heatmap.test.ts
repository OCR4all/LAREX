import { describe, expect, it } from 'vitest'
import {
  averageConfidence,
  computeElementConfidence,
  confidenceToHeatRgba,
  normalizeIndices,
  scaleConfidenceForHeatmap
} from '../confidence-heatmap'

describe('confidence-heatmap utility', () => {
  it('normalizes index selections', () => {
    expect(normalizeIndices([3, 1, 3, -1, 2.9, Number.NaN])).toEqual([1, 2, 3])
  })

  it('computes average confidence from selected indices', () => {
    const value = computeElementConfidence({
      mode: 'indices',
      selectedIndices: [1],
      variants: [
        { index: 0, confidence: 0.2 },
        { index: 1, confidence: 0.6 },
        { index: 1, confidence: 0.8 }
      ]
    })

    expect(value).toBeCloseTo(0.7)
  })

  it('falls back to average-all when indices mode has no selected indices', () => {
    const value = computeElementConfidence({
      mode: 'indices',
      selectedIndices: [],
      variants: [
        { index: 0, confidence: 0.2 },
        { index: 1, confidence: 0.6 },
        { index: 1, confidence: 0.8 }
      ]
    })

    expect(value).toBeCloseTo((0.2 + 0.6 + 0.8) / 3)
  })

  it('supports explicit average mode', () => {
    const value = computeElementConfidence({
      mode: 'average',
      selectedIndices: [0],
      variants: [
        { index: 0, confidence: 0.2 },
        { index: 1, confidence: 0.4 }
      ]
    })

    expect(value).toBeCloseTo(0.3)
  })

  it('falls back to element confidence when no usable variant confidence exists', () => {
    const value = computeElementConfidence({
      mode: 'indices',
      selectedIndices: [9],
      variants: [
        { index: 1, confidence: 0.4 },
        { index: 2, confidence: 0.5 }
      ],
      elementConfidence: 0.33
    })

    expect(value).toBeCloseTo(0.33)
  })

  it('returns undefined when both variant and element confidence are missing', () => {
    const value = computeElementConfidence({
      mode: 'average',
      selectedIndices: [],
      variants: [{ index: 0 }, { index: 1, confidence: Number.NaN }],
      elementConfidence: undefined
    })

    expect(value).toBeUndefined()
  })

  it('maps confidence endpoints to heat colors', () => {
    expect(confidenceToHeatRgba(0, 0.35)).toEqual([1, 0, 0, 0.35])
    expect(confidenceToHeatRgba(0.5, 0.35)).toEqual([1, 1, 0, 0.35])
    expect(confidenceToHeatRgba(1, 0.35)).toEqual([0, 1, 0, 0.35])
  })

  it('applies log scaling to dip faster for lower confidence', () => {
    expect(scaleConfidenceForHeatmap(0.7, false)).toBeCloseTo(0.7)
    expect(scaleConfidenceForHeatmap(0.7, true)).toBeLessThan(0.7)
    expect(scaleConfidenceForHeatmap(0.7, true, 16)).toBeLessThan(scaleConfidenceForHeatmap(0.7, true, 8))
    expect(scaleConfidenceForHeatmap(0, true)).toBe(0)
    expect(scaleConfidenceForHeatmap(1, true)).toBe(1)
  })

  it('computes average confidence for numeric arrays', () => {
    expect(averageConfidence([0.1, 0.5, 0.7])).toBeCloseTo((0.1 + 0.5 + 0.7) / 3)
    expect(averageConfidence([])).toBeUndefined()
  })
})
