import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import * as Vue from 'vue'
import type { NormalizePreview, NormalizeSourcesResponse } from '@/types/normalization-profile'

function preview(pageId: string): NormalizePreview {
  return {
    projectId: 'project', projectName: 'Project', pageId, pageName: pageId, textLineId: `line-${pageId}`,
    originalText: 'a  b', normalizedText: 'a b', matchedRules: []
  }
}

function response(pageId: string, nextPageOffset: number | null): NormalizeSourcesResponse {
  return {
    analyzedProjectCount: 1, analyzedPageCount: 2, analyzedRowCount: 5, changedRowCount: 1, changedPageCount: 1,
    previews: [preview(pageId)], message: '', totalPageCount: 40, nextPageOffset
  }
}

describe('useNormalizationPreview', () => {
  const fetch = vi.fn()
  let scope: Vue.EffectScope
  let run: { useNormalizationPreview: typeof import('../use-normalization-preview').useNormalizationPreview }

  beforeEach(async () => {
    vi.resetModules()
    fetch.mockReset()
    vi.stubGlobal('$fetch', fetch)
    vi.stubGlobal('ref', Vue.ref)
    vi.stubGlobal('shallowRef', Vue.shallowRef)
    vi.stubGlobal('onScopeDispose', Vue.onScopeDispose)
    run = await import('../use-normalization-preview')
    scope = Vue.effectScope()
  })

  afterEach(() => {
    scope.stop()
    vi.unstubAllGlobals()
  })

  it('scans page batches and accumulates rows and counts', async () => {
    fetch.mockResolvedValueOnce(response('p1', 20)).mockResolvedValueOnce(response('p2', null))
    const preview = scope.run(() => run.useNormalizationPreview())!

    await preview.load('/normalize', { sources: [] })

    expect(fetch.mock.calls.map(([, options]) => options.body.pageOffset)).toEqual([0, 20])
    expect(preview.rows.value.map(row => row.pageId)).toEqual(['p1', 'p2'])
    expect(preview.summary.value).toMatchObject({ analyzedRowCount: 10, changedRowCount: 2, scannedPageCount: 40, totalPageCount: 40 })
    expect(preview.isComplete.value).toBe(true)
    expect(preview.isLoading.value).toBe(false)
  })

  it('shows the first batch while later batches are still loading', async () => {
    let finish!: (value: NormalizeSourcesResponse) => void
    fetch.mockResolvedValueOnce(response('p1', 20)).mockReturnValueOnce(new Promise((resolve) => {
      finish = resolve
    }))
    const preview = scope.run(() => run.useNormalizationPreview())!

    const loading = preview.load('/normalize', { sources: [] })
    await vi.waitFor(() => expect(preview.rows.value).toHaveLength(1))

    expect(preview.isLoading.value).toBe(true)
    expect(preview.isComplete.value).toBe(false)
    finish(response('p2', null))
    await loading
  })

  it('ignores an older scan when a new one starts', async () => {
    let finishOld!: (value: NormalizeSourcesResponse) => void
    fetch.mockReturnValueOnce(new Promise((resolve) => {
      finishOld = resolve
    })).mockResolvedValueOnce(response('new', null))
    const preview = scope.run(() => run.useNormalizationPreview())!

    const oldScan = preview.load('/old', { sources: [] })
    await preview.load('/new', { sources: [] })
    finishOld(response('old', null))
    await oldScan

    expect(preview.rows.value.map(row => row.pageId)).toEqual(['new'])
    expect(preview.isComplete.value).toBe(true)
  })

  it('keeps partial results and stays incomplete when a batch fails', async () => {
    fetch.mockResolvedValueOnce(response('p1', 20)).mockRejectedValueOnce(new Error('failed'))
    const preview = scope.run(() => run.useNormalizationPreview())!

    await expect(preview.load('/normalize', { sources: [] })).rejects.toThrow('failed')

    expect(preview.rows.value).toHaveLength(1)
    expect(preview.isComplete.value).toBe(false)
    expect(preview.isLoading.value).toBe(false)
  })

  it('updates the counts when an applied row is removed', async () => {
    fetch.mockResolvedValueOnce(response('p1', null))
    const preview = scope.run(() => run.useNormalizationPreview())!
    await preview.load('/normalize', { sources: [] })

    preview.removeRow(preview.rows.value[0]!)

    expect(preview.rows.value).toEqual([])
    expect(preview.summary.value).toMatchObject({ changedRowCount: 0, changedPageCount: 0 })
  })
})
