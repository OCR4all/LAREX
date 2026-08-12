import { describe, expect, it } from 'vitest'
import { normalizeDocxOptions } from '@/utils/project-export'

describe('normalizeDocxOptions', () => {
  it('uses the legacy unclear-word threshold when the option is absent', () => {
    expect(normalizeDocxOptions({ markUnclearWords: true })).toMatchObject({
      markUnclearWords: true,
      unclearConfidenceThreshold: 0.75
    })
  })

  it('preserves valid thresholds and clamps values to confidence bounds', () => {
    expect(normalizeDocxOptions({ unclearConfidenceThreshold: 0.6 }).unclearConfidenceThreshold).toBe(0.6)
    expect(normalizeDocxOptions({ unclearConfidenceThreshold: -0.2 }).unclearConfidenceThreshold).toBe(0)
    expect(normalizeDocxOptions({ unclearConfidenceThreshold: 1.2 }).unclearConfidenceThreshold).toBe(1)
  })
})
