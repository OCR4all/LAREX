import { nextTick, ref } from 'vue'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { useProjectRunSource } from '../use-project-run-source'

const summary = { id: 'managed', name: 'Alpha' }

describe('useProjectRunSource', () => {
  const fetch = vi.fn()

  beforeEach(() => {
    fetch.mockReset()
    vi.stubGlobal('$fetch', fetch)
  })
  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('syncs the chosen projects into a dataset', async () => {
    fetch.mockResolvedValue({ dataset: summary, pagesWithoutXml: 3 })
    const source = useProjectRunSource({ workspaceId: ref('workspace'), initialProjectIds: ['alpha'] })

    await source.sync()

    expect(fetch).toHaveBeenCalledWith('/api/workspaces/workspace/project-datasets', { method: 'POST', body: { projectIds: ['alpha'] } })
    expect(source.dataset.value).toEqual(summary)
    expect(source.pagesWithoutXml.value).toBe(3)
    expect(source.syncing.value).toBe(false)
  })

  it('does not call the server without projects', async () => {
    const source = useProjectRunSource({ workspaceId: ref('workspace') })

    await source.sync()

    expect(fetch).not.toHaveBeenCalled()
    expect(source.dataset.value).toBeNull()
  })

  it('syncs again when the projects change and ignores the older answer', async () => {
    const resolvers: Array<(value: unknown) => void> = []
    fetch.mockImplementation(() => new Promise(resolve => resolvers.push(resolve)))
    const source = useProjectRunSource({ workspaceId: ref('workspace') })

    source.projectIds.value = ['alpha']
    await nextTick()
    source.projectIds.value = ['alpha', 'beta']
    await nextTick()
    resolvers[1]!({ dataset: { id: 'both', name: 'Alpha + Beta' }, pagesWithoutXml: 0 })
    resolvers[0]!({ dataset: summary, pagesWithoutXml: 9 })
    await new Promise(resolve => setTimeout(resolve))

    expect(source.dataset.value?.id).toBe('both')
    expect(source.pagesWithoutXml.value).toBe(0)
  })

  it('clears the dataset and reports failures', async () => {
    fetch.mockRejectedValue(new Error('offline'))
    const source = useProjectRunSource({ workspaceId: ref('workspace'), initialProjectIds: ['alpha'] })

    await source.sync()

    expect(source.dataset.value).toBeNull()
    expect(source.error.value).toBeTruthy()
    expect(source.syncing.value).toBe(false)
  })
})
