import { describe, expect, it } from 'vitest'
import type { ReviewQueuePage } from '~/types'
import { availablePageTransitions, filterReviewPages, type PageTransitionContext } from '../page-workflow-state'

const editor: Omit<PageTransitionContext, 'workflowState'> = { canReviewPages: false, reviewRequired: false, isSubmitter: false }
const curator = { ...editor, canReviewPages: true }

describe('availablePageTransitions', () => {
  it('lets anyone submit or finish an open page when review is optional', () => {
    expect(availablePageTransitions({ ...editor, workflowState: 'OPEN' })).toEqual(['SUBMIT', 'MARK_DONE'])
  })

  it('treats a missing state as open', () => {
    expect(availablePageTransitions(editor)).toEqual(['SUBMIT', 'MARK_DONE'])
  })

  it('limits editors to submitting when review is required', () => {
    expect(availablePageTransitions({ ...editor, reviewRequired: true, workflowState: 'OPEN' })).toEqual(['SUBMIT'])
    expect(availablePageTransitions({ ...editor, reviewRequired: true, workflowState: 'DONE' })).toEqual([])
  })

  it('keeps curators unrestricted when review is required', () => {
    expect(availablePageTransitions({ ...curator, reviewRequired: true, workflowState: 'OPEN' })).toEqual(['SUBMIT', 'MARK_DONE'])
    expect(availablePageTransitions({ ...curator, reviewRequired: true, workflowState: 'DONE' })).toEqual(['REOPEN'])
  })

  it('offers review decisions to curators only', () => {
    expect(availablePageTransitions({ ...curator, workflowState: 'IN_REVIEW' })).toEqual(['APPROVE', 'REQUEST_CHANGES'])
    expect(availablePageTransitions({ ...editor, workflowState: 'IN_REVIEW' })).toEqual([])
  })

  it('offers withdrawal to the submitter', () => {
    expect(availablePageTransitions({ ...editor, isSubmitter: true, workflowState: 'IN_REVIEW' })).toEqual(['WITHDRAW'])
    expect(availablePageTransitions({ ...curator, isSubmitter: true, workflowState: 'IN_REVIEW' }))
      .toEqual(['APPROVE', 'REQUEST_CHANGES', 'WITHDRAW'])
  })
})

describe('filterReviewPages', () => {
  const page = (pageId: string, overrides: Partial<ReviewQueuePage>): ReviewQueuePage => ({
    pageId,
    pageName: pageId,
    projectId: 'project-1',
    projectName: 'Project',
    blocked: false,
    ...overrides
  })
  const pages = [
    page('a', { submittedByUserId: 'editor-1', assignments: [{ id: 'task-1', title: 'Task' }] }),
    page('b', { projectId: 'project-2', submittedByUserId: 'editor-2' })
  ]

  it('keeps everything without filters', () => {
    expect(filterReviewPages(pages, {})).toEqual(pages)
  })

  it('applies each filter', () => {
    expect(filterReviewPages(pages, { projectId: 'project-2' }).map(item => item.pageId)).toEqual(['b'])
    expect(filterReviewPages(pages, { submittedBy: 'editor-1' }).map(item => item.pageId)).toEqual(['a'])
    expect(filterReviewPages(pages, { taskId: 'task-1' }).map(item => item.pageId)).toEqual(['a'])
  })
})
