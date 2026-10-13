import { describe, expect, it } from 'vitest'
import { actionRunCountsLabel, actionRunStatusColor, actionRunStatusLabel, canRetryActionRun, isTerminalActionRun } from '../action-run-status'
import type { ActionRun } from '@/types/action'

describe('Action run outcomes', () => {
  it('treats completed with issues as terminal and amber', () => {
    expect(isTerminalActionRun('COMPLETED_WITH_ISSUES')).toBe(true)
    expect(actionRunStatusColor('COMPLETED_WITH_ISSUES')).toBe('warning')
    expect(actionRunStatusLabel('COMPLETED_WITH_ISSUES')).toBe('Completed with issues')
    expect(actionRunCountsLabel({ total: 1000, succeeded: 998, failed: 2, skipped: 0, unfinished: 0, unknown: 0 }))
      .toBe('998 succeeded, 2 failed')
  })

  it('only offers retry when failed or unfinished pages remain', () => {
    const run = { kind: 'PROCESSING', status: 'COMPLETED_WITH_ISSUES', retryablePageCount: 2 } as ActionRun
    expect(canRetryActionRun(run)).toBe(true)
    expect(canRetryActionRun({ ...run, retryablePageCount: 0 })).toBe(false)
    expect(canRetryActionRun({ ...run, kind: 'INTEGRATION' })).toBe(false)
    expect(canRetryActionRun({ ...run, status: 'COMPLETED' })).toBe(false)
  })
})
