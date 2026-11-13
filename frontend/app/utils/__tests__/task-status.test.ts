import { describe, expect, it } from 'vitest'
import { TASK_STATUSES, getTaskStatusMeta, isTaskArchived } from '../task-status'

describe('task status', () => {
  it('lists the derived statuses before Paused', () => {
    expect(TASK_STATUSES).toEqual(['OPEN', 'IN_PROGRESS', 'COMPLETED', 'PAUSED'])
  })

  it('describes each status', () => {
    expect(getTaskStatusMeta('IN_PROGRESS')).toMatchObject({ label: 'In progress', color: 'info' })
    expect(getTaskStatusMeta('PAUSED')).toMatchObject({ label: 'Paused', color: 'warning' })
  })

  it('falls back for unknown statuses', () => {
    expect(getTaskStatusMeta('CANCELLED')).toMatchObject({ label: 'CANCELLED', color: 'neutral' })
    expect(getTaskStatusMeta(undefined).label).toBe('Unknown')
  })

  it('detects archived assignments', () => {
    expect(isTaskArchived({ archivedAt: '2026-11-13T10:00:00' })).toBe(true)
    expect(isTaskArchived({ archivedAt: null })).toBe(false)
    expect(isTaskArchived(null)).toBe(false)
  })
})
