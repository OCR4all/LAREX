import { afterEach, describe, expect, it, vi } from 'vitest'
import type { PageData } from '../../../stores/editor/types'
import { createSkeletonPageData, loadSinglePageData } from '../project-loader'

describe('project-loader skeleton variants', () => {
  it('maps page-list image variants into sidebar-ready image variants', () => {
    const pages = createSkeletonPageData([{
      id: 'page-1',
      name: 'Page 1',
      thumbnailUrl: '/thumb',
      sortOrder: 2000,
      textConfidence: { min: 0.1, max: 0.9, mean: 0.5, median: 0.55, count: 4 },
      imageVariants: [
        { id: 'img-1', fileName: '0001.raw.jpg', variant: 'raw.jpg' },
        { id: 'img-2', fileName: '0001.nrm.jpg', variant: null }
      ]
    }], { projectId: 'proj-1', projectName: 'Project' })

    expect(pages[0]?.imageVariants).toEqual([
      {
        id: 'img-1',
        url: '/api/projects/proj-1/pages/images/img-1/thumbnail',
        fileName: '0001.raw.jpg',
        type: 'raw.jpg',
        label: 'raw.jpg'
      },
      {
        id: 'img-2',
        url: '/api/projects/proj-1/pages/images/img-2/thumbnail',
        fileName: '0001.nrm.jpg',
        type: undefined,
        label: '0001.nrm.jpg'
      }
    ])
    expect(pages[0]?.sortOrder).toBe(2000)
    expect(pages[0]?.textConfidence).toEqual({ min: 0.1, max: 0.9, mean: 0.5, median: 0.55, count: 4 })
  })

  it('maps workflow state and defaults legacy responses to Open', () => {
    const pages = createSkeletonPageData([
      { id: 'page-open', name: 'Open page' },
      { id: 'page-done', name: 'Done page', workflowState: 'DONE', locked: true }
    ], { projectId: 'proj-1' })

    expect(pages[0]?.workflowState).toBe('OPEN')
    expect(pages[1]?.workflowState).toBe('DONE')
    expect(pages[1]?.locked).toBe(true)
  })

  it('preserves workflow state when enriching an editor page', async () => {
    vi.stubGlobal('$fetch', vi.fn().mockResolvedValue([]))
    const page: PageData = {
      id: 'page-done',
      projectId: 'proj-1',
      label: 'Done page',
      imageVariants: [],
      xmlFiles: [],
      tags: [],
      resolvedTags: null,
      locked: true,
      lockedReason: 'Page workflow state is Done',
      workflowState: 'DONE'
    }

    const enrichedPage = await loadSinglePageData('proj-1', page)

    expect(enrichedPage.label).toBe('Done page')
    expect(enrichedPage.workflowState).toBe('DONE')
    expect(enrichedPage.locked).toBe(true)
  })

  afterEach(() => {
    vi.unstubAllGlobals()
  })
})
