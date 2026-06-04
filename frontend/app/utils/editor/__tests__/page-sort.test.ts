import { describe, expect, it } from 'vitest'
import type { PageData } from '@/stores/editor/types'
import { createPageSortOrderRequest, sortPagesForEditor } from '../page-sort'

function page(id: string, label: string, sortOrder: number | null, confidence?: Partial<NonNullable<PageData['textConfidence']>>): PageData {
  return {
    id,
    projectId: 'project-1',
    label,
    sortOrder,
    textConfidence: confidence
      ? {
          min: confidence.min ?? 0,
          max: confidence.max ?? 0,
          mean: confidence.mean ?? 0,
          median: confidence.median ?? 0,
          count: confidence.count ?? 1
        }
      : null,
    imageVariants: [],
    xmlFiles: []
  }
}

describe('sortPagesForEditor', () => {
  it('sorts by preserved project order in both directions', () => {
    const pages = [
      page('b', 'page 10', null),
      page('a', 'page 2', null),
      page('c', 'page 1', 1000)
    ]

    expect(sortPagesForEditor(pages, 'projectOrder:asc').map(p => p.id)).toEqual(['b', 'a', 'c'])
    expect(sortPagesForEditor(pages, 'projectOrder:desc').map(p => p.id)).toEqual(['c', 'a', 'b'])
  })

  it('sorts alphabetically in both directions', () => {
    const pages = [
      page('b', 'page 10', 1000),
      page('a', 'page 2', 2000)
    ]

    expect(sortPagesForEditor(pages, 'name:asc').map(p => p.id)).toEqual(['a', 'b'])
    expect(sortPagesForEditor(pages, 'name:desc').map(p => p.id)).toEqual(['b', 'a'])
  })

  it('sorts confidence metrics with missing confidence last for both directions', () => {
    const pages = [
      page('missing', 'missing', 1000),
      page('low', 'low', 2000, { min: 0.2, max: 0.7, mean: 0.4, median: 0.3 }),
      page('high', 'high', 3000, { min: 0.5, max: 0.9, mean: 0.8, median: 0.85 })
    ]

    expect(sortPagesForEditor(pages, 'confidenceMean:asc').map(p => p.id)).toEqual(['low', 'high', 'missing'])
    expect(sortPagesForEditor(pages, 'confidenceMean:desc').map(p => p.id)).toEqual(['high', 'low', 'missing'])
  })

  it('builds a complete ordered reorder payload', () => {
    const pages = [
      page('first', 'first', null),
      page('second', 'second', null),
      page('third', 'third', null)
    ]

    expect(createPageSortOrderRequest(pages)).toEqual({
      pageIds: ['first', 'second', 'third']
    })
  })
})
