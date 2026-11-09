import { beforeEach, describe, expect, it, vi } from 'vitest'
import { ref } from 'vue'

describe('use-page-filter helpers', () => {
  beforeEach(() => {
    vi.resetModules()
    ;(globalThis as any).useStatusIssues = () => ({
      reportIssue: vi.fn(),
      resolveIssue: vi.fn()
    })
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
      hasComments: false,
      onlyWithOpenSubtasks: false, changesRequested: false
    })

    expect(body).toEqual({
      textContent: 'foo',
      hasTextMismatch: null,
      labelIds: ['region|kind=TextRegion|textType=paragraph'],
      tags: ['tag-1'],
      confidenceMin: null,
      confidenceMax: null,
      confidenceElementTypes: null,
      hasComments: null,
      commentText: null,
      workflowStates: null,
      annotationPresence: null,
      onlyWithOpenSubtasks: null, changesRequested: null,
      xmlAttributeFilters: null,
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
      hasComments: false,
      onlyWithOpenSubtasks: false, changesRequested: false
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
      hasComments: false,
      onlyWithOpenSubtasks: false, changesRequested: false
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

  it('buildPageFilterRequestBody includes hasComments when enabled', async () => {
    const { buildPageFilterRequestBody } = await import('../use-page-filter')

    const body = buildPageFilterRequestBody({
      labelIds: [],
      textContent: '',
      tags: [],
      filterOperator: 'and',
      confidenceRange: [0, 1],
      confidenceElementTypes: [],
      hasComments: true,
      onlyWithOpenSubtasks: false, changesRequested: false
    })

    expect(body).toMatchObject({
      hasComments: true
    })
  })

  it('serializes the changes-requested filter and counts it as active', async () => {
    const { buildPageFilterRequestBody, activePageFilterCount } = await import('../use-page-filter')
    const state = {
      labelIds: [], textContent: '', hasTextMismatch: false, tags: [], filterOperator: 'and' as const, confidenceRange: [0, 1] as [number, number],
      confidenceElementTypes: [], hasComments: false, commentText: '', onlyWithOpenSubtasks: false, changesRequested: true,
      workflowStates: [], annotationPresence: null, xmlAttributeFilters: [], visibleFilters: [], negatedFilters: []
    }

    expect(buildPageFilterRequestBody(state)).toMatchObject({ changesRequested: true })
    expect(activePageFilterCount(state)).toBe(1)
  })

  it('serializes the optional dedicated comment search text', async () => {
    const { buildPageFilterRequestBody } = await import('../use-page-filter')

    const body = buildPageFilterRequestBody({
      labelIds: [], textContent: '', tags: [], filterOperator: 'and', confidenceRange: [0, 1],
      confidenceElementTypes: [], hasComments: true, commentText: '  review me  ', onlyWithOpenSubtasks: false, changesRequested: false
    })

    expect(body).toMatchObject({ hasComments: true, commentText: 'review me' })
  })

  it('serializes complete builder criteria and omits incomplete XML attribute rows', async () => {
    const { buildPageFilterRequestBody } = await import('../use-page-filter')

    const body = buildPageFilterRequestBody({
      labelIds: [],
      textContent: '',
      tags: [],
      filterOperator: 'and',
      confidenceRange: [0, 1],
      confidenceElementTypes: [],
      hasComments: false,
      onlyWithOpenSubtasks: true, changesRequested: false,
      workflowStates: ['IN_REVIEW'],
      annotationPresence: 'with_xml',
      xmlAttributeFilters: [
        { id: 'complete', elementName: 'TextLine', attributeName: 'comments', operator: 'contains', value: 'review' },
        { id: 'missing-name', elementName: '', attributeName: '', operator: 'exists', value: '' },
        { id: 'missing-value', elementName: '', attributeName: 'custom', operator: 'equals', value: '' }
      ]
    })

    expect(body).toMatchObject({
      workflowStates: ['IN_REVIEW'],
      annotationPresence: 'with_xml',
      onlyWithOpenSubtasks: true,
      changesRequested: null,
      xmlAttributeFilters: [{
        elementName: 'TextLine',
        attributeName: 'comments',
        operator: 'contains',
        value: 'review'
      }]
    })
  })

  it('counts only active and complete filter rows', async () => {
    const { activePageFilterCount } = await import('../use-page-filter')

    expect(activePageFilterCount({
      labelIds: [], textContent: '', hasTextMismatch: false, tags: [], filterOperator: 'and', confidenceRange: [0, 1],
      confidenceElementTypes: [], hasComments: false, commentText: '', onlyWithOpenSubtasks: false, changesRequested: false, workflowStates: [],
      annotationPresence: null, visibleFilters: ['labels'], negatedFilters: [],
      xmlAttributeFilters: [
        { id: 'empty', elementName: '', attributeName: '', operator: 'exists', value: '' },
        { id: 'active', elementName: '', attributeName: 'comments', operator: 'not_exists', value: '' }
      ]
    })).toBe(1)
  })

  it('migrates legacy saved values into visible builder rows', async () => {
    const { normalizeStoredPageFilterState } = await import('../use-page-filter')

    const state = normalizeStoredPageFilterState({
      labelIds: ['region|kind=TextRegion'],
      textContent: 'needle',
      hasComments: true,
      workflowStates: ['DONE'],
      confidenceRange: [0, 1]
    })

    expect(state.visibleFilters).toEqual(['workflowStates', 'labels', 'textContent', 'comments'])
  })

  it('keeps singleton rows unique while allowing repeated XML attribute rows', async () => {
    const { addPageFilterRow, removePageFilterRow } = await import('../use-page-filter')
    const initial = {
      labelIds: [], textContent: '', hasTextMismatch: false, tags: [], filterOperator: 'and' as const, confidenceRange: [0, 1] as [number, number],
      confidenceElementTypes: [], hasComments: false, commentText: '', onlyWithOpenSubtasks: false, changesRequested: false, workflowStates: [],
      annotationPresence: null, xmlAttributeFilters: [], visibleFilters: [], negatedFilters: []
    }

    let state = addPageFilterRow(initial, 'labels')
    state = addPageFilterRow(state, 'labels')
    state = addPageFilterRow(state, 'xmlAttribute')
    state = addPageFilterRow(state, 'xmlAttribute')

    expect(state.visibleFilters).toEqual(['labels'])
    expect(state.xmlAttributeFilters).toHaveLength(2)

    state = removePageFilterRow(state, 'xmlAttribute', state.xmlAttributeFilters[0]?.id)
    expect(state.xmlAttributeFilters).toHaveLength(1)
  })

  it('adds, restores, and removes the text mismatch criterion', async () => {
    const {
      activePageFilterCount, addPageFilterRow, buildPageFilterRequestBody, normalizeStoredPageFilterState, removePageFilterRow
    } = await import('../use-page-filter')

    const enabled = addPageFilterRow(normalizeStoredPageFilterState({}), 'textMismatch')
    const restored = normalizeStoredPageFilterState({ ...enabled })
    expect(restored.visibleFilters).toEqual(['textMismatch'])
    expect(activePageFilterCount(restored)).toBe(1)
    expect(buildPageFilterRequestBody(restored)).toMatchObject({ hasTextMismatch: true })

    const removed = removePageFilterRow(restored, 'textMismatch')
    expect(activePageFilterCount(removed)).toBe(0)
    expect(buildPageFilterRequestBody(removed)).toMatchObject({ hasTextMismatch: null })
  })

  it('restores and serializes negation for individual rows', async () => {
    const { addPageFilterRow, buildPageFilterRequestBody, normalizeStoredPageFilterState, removePageFilterRow } = await import('../use-page-filter')

    const state = normalizeStoredPageFilterState({
      labelIds: ['region|kind=ImageRegion'],
      negatedFilters: ['labels', 'labels', 'comments', 'unknown'],
      xmlAttributeFilters: [
        { id: 'negated', attributeName: 'comments', negated: true },
        { id: 'positive', attributeName: 'custom' },
        { id: 'incomplete', negated: true }
      ]
    })

    const body = buildPageFilterRequestBody(state)
    expect(body).toMatchObject({
      labelIds: ['region|kind=ImageRegion'],
      negatedFilters: ['labels']
    })
    expect(body.xmlAttributeFilters).toEqual([
      { elementName: null, attributeName: 'comments', operator: 'exists', value: null, negated: true },
      { elementName: null, attributeName: 'custom', operator: 'exists', value: null }
    ])

    const readded = addPageFilterRow(removePageFilterRow(state, 'labels'), 'labels')
    expect(buildPageFilterRequestBody(readded)).not.toHaveProperty('negatedFilters')
  })

  it('ignores stale responses and preserves the newest successful result', async () => {
    const first = Promise.withResolvers<{ pageIds: string[], count: number }>()
    const second = Promise.withResolvers<{ pageIds: string[], count: number }>()
    ;(globalThis as any).$fetch = vi.fn()
      .mockReturnValueOnce(first.promise)
      .mockReturnValueOnce(second.promise)
    const { usePageFilter } = await import('../use-page-filter')
    const filter = usePageFilter(ref('project-1'))
    filter.textContent.value = 'first'
    const firstRequest = filter.applyFiltersForProjects(['project-1'])
    filter.textContent.value = 'second'
    const secondRequest = filter.applyFiltersForProjects(['project-1'])

    second.resolve({ pageIds: ['newest'], count: 1 })
    await secondRequest
    first.resolve({ pageIds: ['stale'], count: 1 })
    await firstRequest

    expect(filter.filteredPageIdsByProjectId.value['project-1']).toEqual(['newest'])
  })

  it('keeps the last successful result when a refresh fails', async () => {
    ;(globalThis as any).$fetch = vi.fn()
      .mockResolvedValueOnce({ pageIds: ['kept'], count: 1 })
      .mockRejectedValueOnce(new Error('offline'))
    const { usePageFilter } = await import('../use-page-filter')
    const filter = usePageFilter(ref('project-1'))
    filter.textContent.value = 'needle'
    await filter.applyFiltersForProjects(['project-1'])
    filter.textContent.value = 'changed'
    await filter.applyFiltersForProjects(['project-1'])

    expect(filter.filteredPageIdsByProjectId.value['project-1']).toEqual(['kept'])
    expect(filter.filterError.value).toContain('Last successful results')
  })
})
