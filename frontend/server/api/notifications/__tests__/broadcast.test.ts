import { createHmac } from 'node:crypto'
import { afterEach, beforeEach, expect, it, vi } from 'vitest'

const broadcast = vi.fn().mockReturnValue(1)
vi.mock('../../../utils/websocket', () => ({ websocketUtils: { broadcast, sendToUser: vi.fn() } }))

const secret = 'bridge-secret'
let headers: Record<string, string | undefined>
let readRawBody: ReturnType<typeof vi.fn>

beforeEach(() => {
  vi.resetModules()
  broadcast.mockClear()
  headers = {}
  readRawBody = vi.fn()
  vi.stubGlobal('defineEventHandler', (handler: unknown) => handler)
  vi.stubGlobal('useRuntimeConfig', () => ({
    notificationBridgeSecret: secret,
    notificationBridgeMaxSkewMs: 60000,
    notificationBridgeRequirePrivateIp: true,
    notificationBridgeAllowedIps: ''
  }))
  vi.stubGlobal('getHeader', (_event: unknown, name: string) => headers[name])
  vi.stubGlobal('getRequestIP', () => '10.0.0.5')
  vi.stubGlobal('readRawBody', readRawBody)
  vi.stubGlobal('createError', (input: { statusCode: number }) => Object.assign(new Error(), input))
  vi.spyOn(console, 'warn').mockImplementation(() => {})
})
afterEach(() => {
  vi.unstubAllGlobals()
  vi.restoreAllMocks()
})

function sign(timestamp: string, body: string) {
  return createHmac('sha256', secret).update(`${timestamp}.${body}`).digest('hex')
}

async function handler() {
  return (await import('../broadcast.post')).default as unknown as (event: unknown) => Promise<unknown>
}

it('rejects unsigned, stale, unsized and oversized requests without reading the body', async () => {
  const handle = await handler()
  const now = String(Date.now())
  const cases: Array<[Record<string, string | undefined>, number]> = [
    [{}, 401],
    [{ 'x-larex-notification-bridge-timestamp': '0', 'x-larex-notification-bridge-signature': 'x', 'content-length': '2' }, 401],
    [{ 'x-larex-notification-bridge-timestamp': now, 'x-larex-notification-bridge-signature': 'x' }, 411],
    [{ 'x-larex-notification-bridge-timestamp': now, 'x-larex-notification-bridge-signature': 'x', 'content-length': '-1' }, 411],
    [{ 'x-larex-notification-bridge-timestamp': now, 'x-larex-notification-bridge-signature': 'x', 'content-length': String(1024 * 1024 + 1) }, 413]
  ]
  for (const [requestHeaders, statusCode] of cases) {
    headers = requestHeaders
    await expect(handle({})).rejects.toMatchObject({ statusCode })
  }
  expect(readRawBody).not.toHaveBeenCalled()
})

it('rejects public sources before reading the body', async () => {
  vi.stubGlobal('getRequestIP', () => '203.0.113.7')
  const handle = await handler()
  await expect(handle({})).rejects.toMatchObject({ statusCode: 403 })
  expect(readRawBody).not.toHaveBeenCalled()
})

it('delivers a correctly signed event', async () => {
  const body = JSON.stringify({ event: { type: 'ACTION_RUN_UPDATED', payload: { runId: 'run' } } })
  const timestamp = String(Date.now())
  headers = {
    'x-larex-notification-bridge-timestamp': timestamp,
    'x-larex-notification-bridge-signature': sign(timestamp, body),
    'content-length': String(Buffer.byteLength(body))
  }
  readRawBody.mockResolvedValue(body)
  const handle = await handler()
  await expect(handle({})).resolves.toMatchObject({ success: true, delivered: 1 })
  expect(broadcast).toHaveBeenCalledWith({ type: 'ACTION_RUN_UPDATED', payload: { runId: 'run' } })
})
