import { describe, expect, it } from 'vitest'
import { EXPORT_WARNINGS_HEADER, normalizeDocxOptions, readExportWarnings } from '@/utils/project-export'

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

describe('readExportWarnings', () => {
  it('decodes the lines the backend encodes', () => {
    const headers = new Headers({
      [EXPORT_WARNINGS_HEADER]: 'FormRegion%20%27f1%27%3A%20changed%0AGr%C3%B6%C3%9Fe%3A%20%C3%A4%2B%C3%B6%20100%25'
    })

    expect(readExportWarnings(headers)).toEqual(["FormRegion 'f1': changed", 'Größe: ä+ö 100%'])
  })

  it('returns nothing without the header or for a malformed value', () => {
    expect(readExportWarnings(new Headers())).toEqual([])
    expect(readExportWarnings(new Headers({ [EXPORT_WARNINGS_HEADER]: '%E0%A4%A' }))).toEqual([])
  })
})
