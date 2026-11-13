import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import * as Vue from 'vue'

const PROJECT_NAMES: Record<string, string> = { 'project-a': 'A', 'project-b': 'B' }

function page(id: string, description = '') {
  return { id, name: id, description, tags: [], resolvedTags: null, xmlFileCount: 0, imageCount: 1, workflowState: 'OPEN' }
}

async function settle() {
  await Vue.nextTick()
  await new Promise(resolve => setTimeout(resolve, 0))
  await Vue.nextTick()
}

describe('useMultiProjectPages', () => {
  const fetch = vi.fn()
  let scope: Vue.EffectScope
  let useMultiProjectPages: typeof import('../use-multi-project-pages').useMultiProjectPages

  beforeEach(async () => {
    vi.resetModules()
    fetch.mockReset()
    fetch.mockImplementation(async (url: string) => {
      if (url === '/api/projects/project-a/pages') return [page('page-1'), page('page-2', 'Auto-created from bulk upload')]
      if (url === '/api/projects/project-b/pages') return [page('page-3')]
      throw new Error(`Unexpected request: ${url}`)
    })
    vi.stubGlobal('$fetch', fetch)
    vi.stubGlobal('getPageDisplayDescription', (await import('../use-page-table-filters')).getPageDisplayDescription)
    useMultiProjectPages = (await import('../use-multi-project-pages')).useMultiProjectPages
    scope = Vue.effectScope()
  })

  afterEach(() => {
    scope.stop()
    vi.unstubAllGlobals()
  })

  it('combines pages from every chosen project with their project name', async () => {
    const projectIds = Vue.ref(['project-a', 'project-b'])
    const state = scope.run(() => useMultiProjectPages(projectIds, id => PROJECT_NAMES[id]!))!
    expect(state.isLoading.value).toBe(true)
    expect(state.pages.value).toBeNull()
    await settle()

    expect(state.isLoading.value).toBe(false)
    expect(state.rows.value.map(row => [row.id, row.projectId, row.projectName])).toEqual([
      ['page-1', 'project-a', 'A'],
      ['page-2', 'project-a', 'A'],
      ['page-3', 'project-b', 'B']
    ])
    expect(state.rows.value[1]!.description).toBe('')
  })

  it('drops rows for removed projects and reuses loaded pages when one is added back', async () => {
    const projectIds = Vue.ref(['project-a', 'project-b'])
    const state = scope.run(() => useMultiProjectPages(projectIds, id => PROJECT_NAMES[id]!))!
    await settle()

    projectIds.value = ['project-b']
    await settle()
    expect(state.rows.value.map(row => row.id)).toEqual(['page-3'])

    projectIds.value = ['project-b', 'project-a']
    await settle()
    expect(state.rows.value.map(row => row.id)).toEqual(['page-3', 'page-1', 'page-2'])
    expect(fetch).toHaveBeenCalledTimes(2)
  })

  it('marks failed projects and loads them again on retry', async () => {
    fetch.mockRejectedValueOnce(new Error('offline'))
    const projectIds = Vue.ref(['project-a'])
    const state = scope.run(() => useMultiProjectPages(projectIds, id => PROJECT_NAMES[id]!))!
    await settle()

    expect(state.failedProjectIds.value.has('project-a')).toBe(true)
    expect(state.rows.value).toEqual([])

    await state.retry('project-a')
    expect(state.failedProjectIds.value.has('project-a')).toBe(false)
    expect(state.rows.value.map(row => row.id)).toEqual(['page-1', 'page-2'])
  })
})
