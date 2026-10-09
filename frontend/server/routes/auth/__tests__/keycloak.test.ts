import { afterEach, beforeEach, expect, it, vi } from 'vitest'

vi.mock('#server/utils/session-profile', () => ({ buildSessionUser: vi.fn() }))
beforeEach(() => vi.resetModules())
afterEach(() => vi.unstubAllGlobals())

it('restarts login for a rejected authorization code but preserves other errors', async () => {
  const rejectedCode = { statusCode: 400, data: { error: 'invalid_grant' } }
  const oauth = vi.fn().mockRejectedValue(rejectedCode)
  const clear = vi.fn().mockResolvedValue(true)
  const redirect = vi.fn().mockReturnValue('login')
  let onError!: (event: unknown, error: unknown) => never
  vi.stubGlobal('defineOAuthKeycloakEventHandler', (options: { onError: typeof onError }) => {
    onError = options.onError
    return oauth
  })
  vi.stubGlobal('defineEventHandler', (handler: unknown) => handler)
  vi.stubGlobal('getQuery', () => ({ code: 'expired-code' }))
  vi.stubGlobal('clearUserSession', clear)
  vi.stubGlobal('sendRedirect', redirect)
  vi.stubGlobal('consumeAuthRedirect', () => '/project/123?tab=pages')
  const { default: handler } = await import('../keycloak.get')
  const event = {} as never
  await expect(handler(event)).resolves.toBe('login')
  expect(clear).toHaveBeenCalledWith(event)
  expect(redirect).toHaveBeenCalledWith(event, '/auth/keycloak?redirectTo=%2Fproject%2F123%3Ftab%3Dpages')

  const unavailable = { statusCode: 503 }
  oauth.mockRejectedValue(unavailable)
  await expect(handler(event)).rejects.toBe(unavailable)
  expect(redirect).toHaveBeenCalledOnce()
  expect(() => onError(event, unavailable)).toThrow()
  expect(redirect).toHaveBeenCalledOnce()
})

it.each([false, true])('records sign-in without blocking login on storage failure (%s)', async (storageFails) => {
  let onSuccess!: (event: unknown, result: { user: object, tokens: { access_token: string, refresh_token: string, expires_in: number } }) => Promise<unknown>
  vi.stubGlobal('defineOAuthKeycloakEventHandler', (options: { onSuccess: typeof onSuccess }) => {
    onSuccess = options.onSuccess
    return vi.fn()
  })
  vi.stubGlobal('defineEventHandler', (handler: unknown) => handler)
  vi.stubGlobal('setUserSession', vi.fn().mockResolvedValue(undefined))
  vi.stubGlobal('useRuntimeConfig', () => ({ apiBaseInternal: 'http://backend' }))
  vi.stubGlobal('consumeAuthRedirect', () => '/')
  const redirect = vi.fn().mockReturnValue('signed-in')
  vi.stubGlobal('sendRedirect', redirect)
  const record = storageFails ? vi.fn().mockRejectedValue(new Error('unavailable')) : vi.fn().mockResolvedValue(undefined)
  vi.stubGlobal('$fetch', record)
  const warn = vi.spyOn(console, 'warn').mockImplementation(() => {})
  try {
    await import('../keycloak.get')
    const event = {}
    await expect(onSuccess(event, {
      user: { sub: 'user-1' },
      tokens: { access_token: 'access-token', refresh_token: 'refresh-token', expires_in: 300 }
    })).resolves.toBe('signed-in')
    expect(record).toHaveBeenCalledExactlyOnceWith('http://backend/profile/login', {
      method: 'POST',
      timeout: 5000,
      headers: { Authorization: 'Bearer access-token' }
    })
    expect(redirect).toHaveBeenCalledWith(event, '/')
    expect(warn).toHaveBeenCalledTimes(storageFails ? 1 : 0)
  } finally {
    warn.mockRestore()
  }
})
