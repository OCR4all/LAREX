import { describe, expect, it } from 'vitest'

import {
  buildApiErrorClipboardPayload,
  extractApiErrorDetails,
  extractApiErrorMessage,
  extractApiMessageFromPayload,
  isStorageQuotaError
} from '../api-error'

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

  it('extracts structured error details from API payloads', () => {
    expect(extractApiErrorDetails({
      statusCode: 409,
      data: {
        timestamp: '2026-03-28T12:30:15.000',
        status: 409,
        error: 'Data Conflict',
        message: 'Workspace already exists',
        path: '/api/workspaces',
        code: 'WORKSPACE_DUPLICATE',
        errorId: 'err-123',
        workspaceId: 'ws-1'
      }
    }, 'fallback')).toEqual({
      timestamp: '2026-03-28T12:30:15.000',
      status: 409,
      error: 'Data Conflict',
      message: 'Workspace already exists',
      path: '/api/workspaces',
      details: undefined,
      code: 'WORKSPACE_DUPLICATE',
      errorId: 'err-123',
      workspaceId: 'ws-1'
    })
  })

  it('builds a support-ready clipboard payload without blank lines', () => {
    expect(buildApiErrorClipboardPayload({
      message: 'Workspace already exists',
      status: 409,
      code: 'WORKSPACE_DUPLICATE',
      errorId: 'err-123',
      path: '/api/workspaces',
      workspaceId: 'ws-1'
    }, 'https://example.test/workspaces')).toBe([
      'Error ID: err-123',
      'HTTP Status: 409',
      'Code: WORKSPACE_DUPLICATE',
      'Message: Workspace already exists',
      'API Path: /api/workspaces',
      'Workspace ID: ws-1',
      'Page URL: https://example.test/workspaces'
    ].join('\n'))
  })
})
