/* eslint-disable import/newline-after-import, @typescript-eslint/no-explicit-any */
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { computed, readonly, ref, watch } from 'vue'

;(globalThis as any).ref = ref
;(globalThis as any).computed = computed
;(globalThis as any).watch = watch
;(globalThis as any).readonly = readonly

describe('use-page-filter helpers', () => {
  beforeEach(() => {
    vi.resetModules()
  })

  it('buildPageFilterRequestBody omits confidence fields when confidence filter is inactive', async () => {
    const { buildPageFilterRequestBody } = await import('../use-page-filter')

    const body = buildPageFilterRequestBody({
      labelIds: ['region|kind=TextRegion|textType=paragraph'],
      textContent: '  foo  ',
      tags: ['tag-1'],
      filterOperator: 'and',
      confidenceRange: [0, 1],
      confidenceElementTypes: [],
      onlyWithOpenSubtasks: false
    })

    expect(body).toEqual({
      textContent: 'foo',
      labelIds: ['region|kind=TextRegion|textType=paragraph'],
      tags: ['tag-1'],
      confidenceMin: null,
      confidenceMax: null,
      confidenceElementTypes: null,
      filterOperator: 'and'
    })
  })

  it('buildPageFilterRequestBody includes confidence fields when range or types are active', async () => {
    const { buildPageFilterRequestBody } = await import('../use-page-filter')

    const bodyWithRange = buildPageFilterRequestBody({
      labelIds: [],
      textContent: '',
      tags: [],
      filterOperator: 'or',
      confidenceRange: [0.2, 0.8],
      confidenceElementTypes: [],
      onlyWithOpenSubtasks: false
    })

    expect(bodyWithRange).toMatchObject({
      confidenceMin: 0.2,
      confidenceMax: 0.8,
      confidenceElementTypes: null
    })

    const bodyWithTypes = buildPageFilterRequestBody({
      labelIds: [],
      textContent: '',
      tags: [],
      filterOperator: 'or',
      confidenceRange: [0, 1],
      confidenceElementTypes: ['TEXTEQUIV', 'COORDS'],
      onlyWithOpenSubtasks: false
    })

    expect(bodyWithTypes).toMatchObject({
      confidenceMin: 0,
      confidenceMax: 1,
      confidenceElementTypes: ['TEXTEQUIV', 'COORDS']
    })
  })

  it('isConfidenceFilterActive reflects range and selected element types', async () => {
    const { isConfidenceFilterActive } = await import('../use-page-filter')

    expect(isConfidenceFilterActive({
      confidenceRange: [0, 1],
      confidenceElementTypes: []
    })).toBe(false)

    expect(isConfidenceFilterActive({
      confidenceRange: [0.1, 1],
      confidenceElementTypes: []
    })).toBe(true)

    expect(isConfidenceFilterActive({
      confidenceRange: [0, 1],
      confidenceElementTypes: ['PAGE']
    })).toBe(true)
  })
})
