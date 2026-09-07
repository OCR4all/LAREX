import { afterEach, beforeEach, expect, it, vi } from 'vitest'
import type { H3Event } from 'h3'
import type { UserSession } from '#auth-utils'
import { refreshAccessToken, refreshTokenIfExpired } from '../../utils/auth'

const jwt = (exp: number) => `header.${Buffer.from(JSON.stringify({ exp })).toString('base64url')}.signature`
const session = (): UserSession => ({
  user: { id: 'user-1' },
  secure: { accessToken: jwt(1), refreshToken: crypto.randomUUID(), accessTokenExpires: 1000 }
} as UserSession)
const eventFor = (data = session()) => ({ path: '/', context: { data } }) as unknown as H3Event
const fetchToken = vi.fn()
const clear = vi.fn(async (event: H3Event) => {
  event.context.data = {}
})
const replace = vi.fn(async (event: H3Event, data: UserSession) => {
  event.context.data = data
})
let renderHook: (ctx: { event: H3Event }) => Promise<void>
let fetchHook: (session: UserSession, event: H3Event) => Promise<void>

beforeEach(async () => {
  vi.clearAllMocks()
  fetchToken.mockReset()
  vi.stubGlobal('defineNitroPlugin', (setup: unknown) => setup)
  vi.stubGlobal('sessionHooks', { hook: (_: string, hook: typeof fetchHook) => {
    fetchHook = hook
  } })
  vi.stubGlobal('getUserSession', async (event: H3Event) => ({ ...event.context.data }))
  vi.stubGlobal('setHeader', vi.fn())
  vi.stubGlobal('clearUserSession', clear)
  vi.stubGlobal('replaceUserSession', replace)
  vi.stubGlobal('useRuntimeConfig', () => ({ oauth: { keycloak: { serverUrl: 'http://keycloak.localhost', realm: 'larex-dev' } } }))
  vi.stubGlobal('$fetch', fetchToken)
  const { default: setup } = await import('../auth-session')
  setup({ hooks: { hook: (_: string, hook: typeof renderHook) => {
    renderHook = hook
  } } } as never)
})
afterEach(() => vi.unstubAllGlobals())

it('refreshes on the original SSR response and reuses its session for internal fetches', async () => {
  const event = eventFor()
  const accessToken = jwt(Math.floor(Date.now() / 1000) + 300)
  fetchToken.mockResolvedValue({ access_token: accessToken, refresh_token: 'rotated', expires_in: 300 })
  await renderHook({ event })
  expect(replace).toHaveBeenCalledWith(event, expect.objectContaining({ secure: expect.objectContaining({ accessToken, refreshToken: 'rotated' }) }))
  await fetchHook({ ...event.context.data }, { context: event.context } as H3Event)
  expect(fetchToken).toHaveBeenCalledOnce()
})

it('clears an expired session on the original SSR response before rendering', async () => {
  const event = eventFor()
  fetchToken.mockRejectedValue({ statusCode: 400, data: { error: 'invalid_grant' } })
  await renderHook({ event })
  expect(clear).toHaveBeenCalledWith(event)
  expect(event.context.data.user).toBeUndefined()
  await fetchHook({ ...event.context.data }, event)
  expect(fetchToken).toHaveBeenCalledOnce()
})

it.each([
  { statusCode: 503 },
  { statusCode: 429 },
  { statusCode: 401, data: { error: 'invalid_client' } },
  new Error('timeout')
])('blocks SSR and preserves the cookie on an unavailable auth service: %j', async (error) => {
  const event = eventFor()
  fetchToken.mockRejectedValue(error)
  await expect(renderHook({ event })).rejects.toMatchObject({ statusCode: 503 })
  expect(clear).not.toHaveBeenCalled()
  expect(replace).not.toHaveBeenCalled()
  expect(event.context.data.user).toBeDefined()
})

it('returns an unavailable snapshot on CSR without clearing the cookie, then recovers', async () => {
  const event = eventFor()
  const snapshot = { ...event.context.data }
  fetchToken.mockRejectedValue(new Error('network unavailable'))
  await fetchHook(snapshot, event)
  expect(snapshot).toMatchObject({ authUnavailable: true })
  expect(snapshot.user).toBeUndefined()
  expect(clear).not.toHaveBeenCalled()
  fetchToken.mockResolvedValue({ access_token: jwt(Math.floor(Date.now() / 1000) + 300), expires_in: 300 })
  const retrySnapshot = { ...event.context.data }
  await fetchHook(retrySnapshot, event)
  expect(retrySnapshot.user).toBeDefined()
  expect(retrySnapshot.authUnavailable).toBeUndefined()
})

it('deduplicates forced, automatic, and briefly delayed refreshes and writes every response', async () => {
  const data = session()
  const events = [eventFor(data), eventFor(data), eventFor(data)]
  let resolve!: (value: unknown) => void
  fetchToken.mockReturnValue(new Promise((r) => {
    resolve = r
  }))
  const first = refreshAccessToken(events[0]!, data)
  const second = refreshTokenIfExpired(events[1]!, data)
  resolve({ access_token: jwt(Math.floor(Date.now() / 1000) + 300), refresh_token: 'rotated', expires_in: 300 })
  await Promise.all([first, second])
  await refreshAccessToken(events[2]!, data)
  expect(fetchToken).toHaveBeenCalledOnce()
  expect(fetchToken.mock.calls[0]![1]).toMatchObject({ timeout: 10000, retry: false })
  expect(replace).toHaveBeenCalledTimes(3)
  for (const event of events) expect(event.context.data.secure.refreshToken).toBe('rotated')
})

it('does not refresh a fresh token or trust a malformed token response', async () => {
  const data = session()
  data.secure!.accessToken = jwt(Math.floor(Date.now() / 1000) + 300)
  const event = eventFor(data)
  await refreshTokenIfExpired(event, data)
  expect(fetchToken).not.toHaveBeenCalled()
  fetchToken.mockResolvedValue({ access_token: 'invalid', expires_in: 300 })
  await expect(refreshAccessToken(event, data)).rejects.toMatchObject({ statusCode: 503 })
  expect(replace).not.toHaveBeenCalled()
  expect(clear).not.toHaveBeenCalled()
})

it('clears a malformed local session and skips public/error rendering', async () => {
  const data = session()
  data.secure!.accessToken = 'invalid'
  const event = eventFor(data)
  await renderHook({ event })
  expect(clear).toHaveBeenCalledOnce()
  for (const path of ['/share/123', '/auth/keycloak', '/__nuxt_error?statusCode=503']) {
    const publicEvent = eventFor()
    Object.defineProperty(publicEvent, 'path', { value: path })
    await renderHook({ event: publicEvent })
  }
  expect(fetchToken).not.toHaveBeenCalled()
})
