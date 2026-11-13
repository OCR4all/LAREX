import { describe, expect, it } from 'vitest'
import { TASK_STATUSES, getTaskStatusMeta, isTaskArchived, isTaskDueSoon, isTaskOverdue, matchesTaskDueFilter } from '../task-status'

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

  describe('due dates', () => {
    const now = new Date('2026-11-13T12:00:00')

    it('flags open work past its due date as overdue', () => {
      expect(isTaskOverdue({ status: 'IN_PROGRESS', dueDate: '2026-11-12T12:00:00' }, now)).toBe(true)
      expect(isTaskOverdue({ status: 'OPEN', dueDate: '2026-11-14T12:00:00' }, now)).toBe(false)
      expect(isTaskOverdue({ status: 'OPEN', dueDate: null }, now)).toBe(false)
    })

    it('never flags stopped assignments', () => {
      expect(isTaskOverdue({ status: 'COMPLETED', dueDate: '2026-11-01T12:00:00' }, now)).toBe(false)
      expect(isTaskOverdue({ status: 'PAUSED', dueDate: '2026-11-01T12:00:00' }, now)).toBe(false)
      expect(isTaskOverdue({ status: 'OPEN', dueDate: '2026-11-01T12:00:00', archivedAt: '2026-11-02T12:00:00' }, now)).toBe(false)
    })

    it('treats the next seven days as due soon', () => {
      expect(isTaskDueSoon({ status: 'OPEN', dueDate: '2026-11-19T12:00:00' }, now)).toBe(true)
      expect(isTaskDueSoon({ status: 'OPEN', dueDate: '2026-11-21T12:00:00' }, now)).toBe(false)
      expect(isTaskDueSoon({ status: 'OPEN', dueDate: '2026-11-12T12:00:00' }, now)).toBe(false)
    })

    it('filters by due date', () => {
      const overdue = { status: 'OPEN' as const, dueDate: '2026-11-10T12:00:00' }
      expect(matchesTaskDueFilter(overdue, 'ALL', now)).toBe(true)
      expect(matchesTaskDueFilter(overdue, 'OVERDUE', now)).toBe(true)
      expect(matchesTaskDueFilter(overdue, 'DUE_SOON', now)).toBe(false)
    })
  })
})
