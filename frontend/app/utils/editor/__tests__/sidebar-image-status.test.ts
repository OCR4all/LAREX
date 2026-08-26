import { describe, expect, it } from 'vitest'
import type { PageData } from '@/stores/editor/types'
import {
  getAnnotationModeStatus,
  getAnnotationStatus,
  getAttentionStatuses,
  getWorkflowStatus,
  partitionStatusRail
} from '../sidebar-image-status'

function createPage(overrides: Partial<PageData> = {}): PageData {
  return {
    id: 'page-1',
    projectId: 'project-1',
    label: 'Page 1',
    imageVariants: [],
    xmlFiles: [],
    ...overrides
  }
}

describe('sidebar image status utilities', () => {
  it('uses Open and no annotations as stable default states', () => {
    expect(getWorkflowStatus(createPage())).toMatchObject({
      key: 'workflow-open',
      label: 'Open',
      display: 'label',
      color: 'neutral'
    })
    expect(getAnnotationStatus(createPage())).toMatchObject({
      key: 'annotations-missing',
      label: 'No annotations',
      display: 'icon',
      color: 'neutral'
    })
  })

  it('detects annotations from both enriched files and summary counts', () => {
    expect(getAnnotationStatus(createPage({ xmlFiles: [{ id: 'xml-1', fileName: 'page.xml', schema: 'PAGE_XML' }] })).key)
      .toBe('annotations-available')
    expect(getAnnotationStatus(createPage({ xmlFileCount: 1 })).key).toBe('annotations-available')
  })

  it('orders preview, indexing, save, lock, and background indexing attention states', () => {
    const statuses = getAttentionStatuses(
      createPage({ indexingStatus: 'UNINDEXED' }),
      {
        previewImageFailed: true,
        hasUnsavedChanges: true,
        pageLockReason: 'LAREX Action running: OCR'
      }
    )

    expect(statuses.map(status => status.key)).toEqual([
      'preview-unavailable',
      'not-indexed',
      'unsaved',
      'locked'
    ])

    expect(getAttentionStatuses(createPage({ indexingStatus: 'INDEXING' })).map(status => status.key))
      .toEqual(['indexing'])
  })

  it('does not duplicate the Done workflow as a lock warning', () => {
    expect(getAttentionStatuses(
      createPage({ workflowState: 'DONE' }),
      { pageLockReason: 'Page workflow state is Done' }
    )).toEqual([])

    expect(getAttentionStatuses(
      createPage({ workflowState: 'DONE' }),
      { pageLockReason: 'LAREX Action running: OCR' }
    ).map(status => status.key)).toEqual(['locked'])
  })

  it('describes dataset link and copy modes consistently', () => {
    expect(getAnnotationModeStatus(createPage({ annotationContext: {
      mode: 'DATASET_LINK',
      basePath: '/api/dataset-link',
      createAllowed: true
    } }))).toMatchObject({ key: 'dataset-link', label: 'Link', color: 'info' })
    expect(getAnnotationModeStatus(createPage({ annotationContext: {
      mode: 'DATASET_COPY',
      basePath: '/api/dataset-copy',
      createAllowed: true
    } }))).toMatchObject({ key: 'dataset-copy', label: 'Copy', color: 'warning' })
  })

  it('keeps the core indicator and exposes the rest through overflow', () => {
    const statuses = [
      getAnnotationStatus(createPage()),
      ...getAttentionStatuses(createPage(), { hasUnsavedChanges: true }),
      getAnnotationModeStatus(createPage({ annotationContext: {
        mode: 'DATASET_LINK',
        basePath: '/api/dataset-link',
        createAllowed: true
      } }))!
    ]

    const narrow = partitionStatusRail(statuses, 120)
    expect(narrow.visible.map(status => status.key)).toEqual(['annotations-missing'])
    expect(narrow.hidden.map(status => status.key)).toEqual(['unsaved', 'dataset-link'])
    expect(narrow.overflowCount).toBe(2)

    const wide = partitionStatusRail(statuses, 500)
    expect(wide.hidden).toEqual([])
    expect(wide.visible).toHaveLength(3)
  })
})
