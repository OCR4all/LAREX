import { describe, expect, it } from 'vitest'

import { extractApiErrorMessage, extractApiMessageFromPayload, isStorageQuotaError } from '../api-error'

describe('api-error utils', () => {
  it('prefers message fields from API payloads', () => {
    expect(extractApiMessageFromPayload({ message: 'Quota exceeded' }, 'fallback')).toBe('Quota exceeded')
    expect(extractApiErrorMessage({ data: { message: 'Quota exceeded' } }, 'fallback')).toBe('Quota exceeded')
  })

  it('strips raw transport prefixes from fetch error messages', () => {
    expect(extractApiErrorMessage(new Error('[POST] "/api/upload": 507 Insufficient Storage'), 'fallback')).toBe('Uploads are blocked because this workspace has exceeded its storage quota. Remove data or ask an admin to increase the quota.')
  })

  it('falls back to a friendly quota message when only the transport status is available', () => {
    expect(extractApiErrorMessage({ statusCode: 507 }, 'fallback')).toBe('Uploads are blocked because this workspace has exceeded its storage quota. Remove data or ask an admin to increase the quota.')
  })

  it('detects storage quota failures', () => {
    expect(isStorageQuotaError({ statusCode: 507 })).toBe(true)
    expect(isStorageQuotaError({ data: { code: 'STORAGE_QUOTA_EXCEEDED' } })).toBe(true)
    expect(isStorageQuotaError({ data: { code: 'OTHER' } })).toBe(false)
  })
})
