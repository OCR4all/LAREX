import { afterEach, expect, it, vi } from 'vitest'

vi.mock('#server/utils/session-profile', () => ({ buildSessionUser: vi.fn() }))
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
