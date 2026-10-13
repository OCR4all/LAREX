import type { ActionRun, ActionRunStatus, ActionRunReportCounts } from '@/types/action'

export function isTerminalActionRun(status: ActionRunStatus) {
  return status === 'COMPLETED' || status === 'COMPLETED_WITH_ISSUES' || status === 'FAILED' || status === 'CANCELLED'
}

export function actionRunStatusLabel(status: ActionRunStatus) {
  return status.toLowerCase().replaceAll('_', ' ').replace(/^./, character => character.toUpperCase())
}

export function actionRunStatusColor(status: ActionRunStatus) {
  if (status === 'COMPLETED') return 'success'
  if (status === 'FAILED' || status === 'CANCELLED') return 'error'
  if (status === 'COMPLETED_WITH_ISSUES' || status === 'QUEUED' || status === 'CANCEL_REQUESTED') return 'warning'
  return 'primary'
}

export function canRetryActionRun(run: ActionRun) {
  return run.kind !== 'INTEGRATION'
    && (run.status === 'FAILED' || run.status === 'CANCELLED' || run.status === 'COMPLETED_WITH_ISSUES')
    && (run.retryablePageCount === undefined || run.retryablePageCount > 0)
}

export function actionRunCountsLabel(counts: ActionRunReportCounts | null | undefined) {
  if (!counts || counts.total === 0) return ''
  const values = [`${counts.succeeded} succeeded`, `${counts.failed} failed`]
  if (counts.skipped) values.push(`${counts.skipped} skipped`)
  if (counts.unfinished) values.push(`${counts.unfinished} unfinished`)
  if (counts.unknown) values.push(`${counts.unknown} unknown`)
  return values.join(', ')
}
