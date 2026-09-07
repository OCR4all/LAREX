import { afterEach, expect, it, vi } from 'vitest'

afterEach(() => vi.unstubAllGlobals())

it('redirects an expired session but blocks rendering without logout during an outage', async () => {
  const redirect = vi.fn().mockReturnValue('login')
  const session = { value: { authUnavailable: false } }
  vi.stubGlobal('defineNuxtRouteMiddleware', (handler: unknown) => handler)
  vi.stubGlobal('useUserSession', () => ({ loggedIn: { value: false }, session }))
  vi.stubGlobal('navigateToAuth', redirect)
  vi.stubGlobal('createError', (error: unknown) => error)
  const { default: middleware } = await import('../auth.global')
  const route = { path: '/project/123', fullPath: '/project/123?tab=pages' }
  await expect(middleware(route as never, route as never)).resolves.toBe('login')
  expect(redirect).toHaveBeenCalledWith({ redirectTo: route.fullPath, replace: true })
  session.value.authUnavailable = true
  await expect(middleware(route as never, route as never)).rejects.toMatchObject({ statusCode: 503 })
  expect(redirect).toHaveBeenCalledOnce()
})
