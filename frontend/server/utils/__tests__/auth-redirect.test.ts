import { describe, expect, it } from 'vitest'
import { resolveAuthRedirect } from '../auth-redirect'

describe('auth redirect utils', () => {
  it('preserves a same-origin project route including query and hash', () => {
    expect(resolveAuthRedirect('/project/123?tab=pages#page-7')).toBe('/project/123?tab=pages#page-7')
  })

  it('falls back for absolute and protocol-relative URLs', () => {
    expect(resolveAuthRedirect('https://example.com/project/123')).toBe('/')
    expect(resolveAuthRedirect('//example.com/project/123')).toBe('/')
    expect(resolveAuthRedirect('/\\example.com/project/123')).toBe('/')
  })

  it('falls back for the auth callback to prevent redirect loops', () => {
    expect(resolveAuthRedirect('/auth/keycloak?redirectTo=/project/123')).toBe('/')
  })

  it('uses the provided fallback for missing or malformed values', () => {
    expect(resolveAuthRedirect(undefined, '')).toBe('')
    expect(resolveAuthRedirect(['/project/123'], '')).toBe('')
  })
})
