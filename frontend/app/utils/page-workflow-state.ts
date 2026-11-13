import type { ReviewQueuePage } from '~/types'
import type { PageWorkflowAction, PageWorkflowState } from '@/types/project-page'
import type { ReviewQueueFilters } from '@/stores/editor/editor.session.store'

export const PAGE_WORKFLOW_STATES: PageWorkflowState[] = ['OPEN', 'IN_REVIEW', 'DONE']

export const PAGE_WORKFLOW_STATE_META: Record<PageWorkflowState, {
  label: string
  color: 'neutral' | 'warning' | 'success'
  icon: string
}> = {
  OPEN: { label: 'Open', color: 'neutral', icon: 'i-lucide-circle' },
  IN_REVIEW: { label: 'In review', color: 'warning', icon: 'i-lucide-eye' },
  DONE: { label: 'Done', color: 'success', icon: 'i-lucide-circle-check' }
}

export const PAGE_WORKFLOW_ACTION_META: Record<PageWorkflowAction, { label: string, icon: string }> = {
  SUBMIT: { label: 'Submit for review', icon: 'i-lucide-send' },
  APPROVE: { label: 'Approve', icon: 'i-lucide-circle-check' },
  REQUEST_CHANGES: { label: 'Request changes', icon: 'i-lucide-message-square-warning' },
  WITHDRAW: { label: 'Withdraw from review', icon: 'i-lucide-undo-2' },
  MARK_DONE: { label: 'Mark as done', icon: 'i-lucide-check' },
  REOPEN: { label: 'Reopen', icon: 'i-lucide-rotate-ccw' }
}

export interface PageTransitionContext {
  workflowState?: PageWorkflowState
  canReviewPages: boolean
  reviewRequired: boolean
  isSubmitter: boolean
}

/**
 * The only source of allowed actions in the UI. Assignments can also require review, which the
 * client doesn't know per page, so the backend remains the final check for MARK_DONE and REOPEN.
 */
export function availablePageTransitions(context: PageTransitionContext): PageWorkflowAction[] {
  const unrestricted = context.canReviewPages || !context.reviewRequired
  switch (context.workflowState ?? 'OPEN') {
    case 'OPEN':
      return unrestricted ? ['SUBMIT', 'MARK_DONE'] : ['SUBMIT']
    case 'IN_REVIEW':
      return [
        ...(context.canReviewPages ? ['APPROVE', 'REQUEST_CHANGES'] as const : []),
        ...(context.isSubmitter ? ['WITHDRAW'] as const : [])
      ]
    case 'DONE':
      return unrestricted ? ['REOPEN'] : []
  }
}

export function filterReviewPages(pages: ReviewQueuePage[], filters: ReviewQueueFilters): ReviewQueuePage[] {
  return pages.filter(page =>
    (!filters.projectId || page.projectId === filters.projectId)
    && (!filters.submittedBy || page.submittedByUserId === filters.submittedBy)
    && (!filters.taskId || page.assignments?.some(assignment => assignment.id === filters.taskId))
  )
}

export function reviewSubmitterName(page: ReviewQueuePage) {
  const submitter = page.submittedBy
  if (submitter?.firstName && submitter.lastName) return `${submitter.firstName} ${submitter.lastName}`
  return submitter?.username ?? 'Unknown'
}
