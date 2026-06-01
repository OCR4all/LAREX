/* eslint-disable @typescript-eslint/no-explicit-any */
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { computed, readonly, ref } from 'vue'

;(globalThis as any).computed = computed
;(globalThis as any).readonly = readonly

describe('use-status-issues', () => {
  beforeEach(() => {
    vi.resetModules()

    const state = new Map<string, ReturnType<typeof ref>>()
    ;(globalThis as any).useState = <T>(key: string, init: () => T) => {
      if (!state.has(key)) {
        state.set(key, ref(init()))
      }
      return state.get(key)
    }
  })

  it('stores higher-severity issues first and updates existing issues in place', async () => {
    const { useStatusIssues } = await import('../use-status-issues')
    const issues = useStatusIssues()

    issues.reportIssue({
      id: 'warning-1',
      source: 'notifications',
      title: 'Notifications stale',
      message: 'Could not refresh notifications.'
    })

    issues.reportIssue({
      id: 'error-1',
      source: 'startup',
      title: 'Startup incomplete',
      message: 'Could not load startup data.',
      severity: 'error'
    })

    issues.reportIssue({
      id: 'warning-1',
      source: 'notifications',
      title: 'Notifications stale',
      message: 'Notifications are still stale.',
      severity: 'warning'
    })

    expect(issues.issues.value).toHaveLength(2)
    expect(issues.issues.value[0]?.id).toBe('error-1')
    expect(issues.issues.value[1]?.message).toBe('Notifications are still stale.')
  })

  it('runs retry handlers and clears resolved issues', async () => {
    const { useStatusIssues } = await import('../use-status-issues')
    const issues = useStatusIssues()
    const retry = vi.fn().mockResolvedValue(undefined)

    issues.reportIssue({
      id: 'startup',
      source: 'startup',
      title: 'Startup incomplete',
      message: 'Could not load startup data.',
      severity: 'error',
      retryLabel: 'Retry startup',
      retry
    })

    expect(issues.isRetrying('startup')).toBe(false)
    await issues.retryIssue('startup')
    expect(retry).toHaveBeenCalledTimes(1)
    expect(issues.isRetrying('startup')).toBe(false)

    issues.resolveIssue('startup')
    expect(issues.issues.value).toEqual([])
  })
})
