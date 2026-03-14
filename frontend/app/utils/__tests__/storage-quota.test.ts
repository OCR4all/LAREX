import { describe, expect, it } from 'vitest'

import { getStorageQuotaAlertState, getStorageQuotaProgressValue } from '../storage-quota'

describe('storage-quota utils', () => {
  it('caps progress at 100 percent for rendering', () => {
    expect(getStorageQuotaProgressValue(133)).toBe(100)
    expect(getStorageQuotaProgressValue(42)).toBe(42)
  })

  it('treats over-quota workspaces as exceeded', () => {
    expect(getStorageQuotaAlertState({ usagePercentage: 133, isQuotaExceeded: true })).toBe('exceeded')
    expect(getStorageQuotaAlertState({ usagePercentage: 85, isQuotaExceeded: false })).toBe('warning')
    expect(getStorageQuotaAlertState({ usagePercentage: 40, isQuotaExceeded: false })).toBe('ok')
  })
})
