import { afterEach, expect, it, vi } from 'vitest'
import { nextTick, ref, type Ref } from 'vue'
import type { H3Event } from 'h3'
import type { UserSession } from '#auth-utils'
import type { CollaborationAnnotationRoute, CollaborationLeaseState, CollaborationRoomSession } from '@/types/collaboration'
import { useEditorCollaboration } from '../use-editor-collaboration'
import { collaborationState } from '#server/utils/collaboration-state'

const store = vi.hoisted(() => ({ getCanvas: vi.fn(), getPage: vi.fn() }))
vi.mock('@/stores/editor/editor.store', () => ({ useEditorStore: () => store }))
const jwt = (exp: number) => `header.${Buffer.from(JSON.stringify({ exp })).toString('base64url')}.signature`

let closeFixture: (() => Promise<void>) | undefined

afterEach(async () => {
  await closeFixture?.()
  closeFixture = undefined
  vi.restoreAllMocks()
  vi.clearAllTimers()
  vi.useRealTimers()
  vi.unstubAllGlobals()
})

async function setupLeaseRenewal(annotationRoute: CollaborationAnnotationRoute = { scope: 'PROJECT', projectId: 'auth-project', pageId: 'page' }) {
  vi.useFakeTimers()
  vi.stubGlobal('window', { sessionStorage: { getItem: () => 'auth-instance' }, addEventListener: vi.fn() })
  vi.stubGlobal('BroadcastChannel', undefined)
  vi.stubGlobal('useToast', () => ({ add: vi.fn() }))
  vi.stubGlobal('useThrottleFn', (callback: unknown) => callback)
  const user = { id: 'auth-editor', username: 'editor', displayName: 'Editor',
    login: 'editor', name: 'Editor', email: 'editor@example.test', roles: [] }
  const roomKey = 'auth-project:page:xml'
  let browserSession: UserSession = { id: 'test-session', user, secure: {
    accessToken: jwt(1), refreshToken: crypto.randomUUID(), accessTokenExpires: 1000
  } } as UserSession
  let refreshToken = browserSession.secure!.refreshToken
  let expectedAccessToken = browserSession.secure!.accessToken
  let lease: CollaborationLeaseState = {
    editor: { user, acquiredAt: new Date().toISOString() }, pendingTakeover: null,
    leaseEpoch: '2', expiresAt: new Date(Date.now() + 45_000).toISOString()
  }
  const room: CollaborationRoomSession = {
    identity: { roomKey, workspaceId: 'workspace', projectId: 'auth-project', pageId: 'page', xmlId: 'xml',
      annotationRoute,
      token: 'room-token', canEdit: true, canForceTakeover: true, user },
    lease, presence: { members: [] },
    viewerSync: { persistedRevision: 'revision', resyncRequired: false, snapshotReady: true }
  }
  const states = new Map<string, Ref>([
    ['editor-collaboration.rooms', ref({ [roomKey]: room })],
    ['editor-collaboration.canvas-rooms', ref({ canvas: roomKey })],
    ['editor-collaboration.instance-id', ref('auth-instance')]
  ])
  vi.stubGlobal('useState', (key: string, init: () => unknown) => {
    if (!states.has(key)) states.set(key, ref(init()))
    return states.get(key)
  })
  vi.stubGlobal('useUserSession', () => ({ user: ref(user), loggedIn: ref(true) }))
  const socketStatus = ref('idle')
  const send = vi.fn((_message: { type: string }) => true)
  vi.stubGlobal('useRealtimeSocket', () => ({ connectionStatus: socketStatus, send, connect: vi.fn(), subscribe: vi.fn() }))
  vi.stubGlobal('defineEventHandler', (handler: unknown) => handler)
  vi.stubGlobal('readBody', async (event: H3Event) => event.context.body)
  vi.stubGlobal('getUserSession', async (event: H3Event) => event.context.session)
  vi.stubGlobal('replaceUserSession', async (event: H3Event, session: UserSession) => {
    event.context.session = session
    event.context.responseSession = session
  })
  vi.stubGlobal('clearUserSession', async (event: H3Event) => {
    event.context.session = {}
  })
  vi.stubGlobal('useRuntimeConfig', () => ({ apiBaseInternal: 'http://backend.localhost',
    oauth: { keycloak: { serverUrl: 'http://keycloak.localhost', realm: 'test', clientId: 'test' } } }))
  const backend = vi.fn(async (_url: string, options: RequestInit) => {
    expect(new Headers(options.headers).get('Authorization')).toBe(`Bearer ${expectedAccessToken}`)
    expect(JSON.parse(options.body as string)).toEqual({ instanceId: 'auth-instance', targets: [{ ...annotationRoute, xmlId: 'xml' }] })
    if (lease.editor) lease = { ...lease, expiresAt: new Date(Date.now() + 45_000).toISOString() }
    return new Response(JSON.stringify({ renewals: [{ roomKey, lease }] }))
  })
  vi.stubGlobal('fetch', backend)
  const { default: route } = await import('#server/api/collaboration/leases/renew.post')
  const renew = route as unknown as (event: H3Event) => Promise<unknown>
  let rotations = 0
  const fetch = vi.fn(async (path: string, options?: { body?: unknown }) => {
    if (path.includes('/openid-connect/token')) {
      const body = options!.body as URLSearchParams
      if (body.get('refresh_token') !== refreshToken) throw { statusCode: 400, data: { error: 'invalid_grant' } }
      refreshToken = crypto.randomUUID()
      rotations++
      expectedAccessToken = jwt(Math.floor(Date.now() / 1000) + 80)
      return { access_token: expectedAccessToken, refresh_token: refreshToken, expires_in: 80 }
    }
    if (path === '/api/collaboration/leases/renew') {
      const event = { context: { session: structuredClone(browserSession), body: options!.body } } as unknown as H3Event
      const data = await renew(event)
      if (event.context.responseSession) browserSession = event.context.responseSession
      return data
    }
    if (path.endsWith('/collaboration/token')) return { ...room.identity, persistedRevision: 'revision', lease }
    if (path.endsWith('/lease/join')) {
      lease = { editor: { user, acquiredAt: new Date().toISOString() }, pendingTakeover: null,
        leaseEpoch: (BigInt(lease.leaseEpoch) + 1n).toString(), expiresAt: new Date(Date.now() + 45_000).toISOString() }
    }
    return { roomKey, lease }
  })
  vi.stubGlobal('$fetch', fetch)
  store.getCanvas.mockReturnValue({ projectId: 'auth-project', pageId: 'page', xmlFileId: 'xml' })
  store.getPage.mockReturnValue({ locked: false, workflowState: 'IN_PROGRESS' })
  const collaboration = useEditorCollaboration()
  collaboration.connect()
  socketStatus.value = 'connected'
  await nextTick()
  await vi.advanceTimersByTimeAsync(0)
  const fixture = {
    collaboration, socketStatus, send, backend, fetch, room, states,
    rotations: () => rotations,
    expireBackend() {
      lease = { editor: null, pendingTakeover: null,
        leaseEpoch: (BigInt(lease.leaseEpoch) + 1n).toString(), expiresAt: null }
    },
    async close() {
      collaboration.leaveCanvasRoom('canvas')
      collaboration.disconnect()
      await vi.advanceTimersByTimeAsync(0)
      collaborationState.syncLeaseState(roomKey, { editor: null, pendingTakeover: null, leaseEpoch: '999', expiresAt: null })
    }
  }
  closeFixture = fixture.close
  return fixture
}

it.each([
  { scope: 'PROJECT', projectId: 'auth-project', pageId: 'page' },
  { scope: 'DATASET', workspaceId: 'workspace', datasetId: 'dataset', itemId: 'item' }
] satisfies CollaborationAnnotationRoute[])('renews $scope leases beyond token rotations without reconnecting', async (annotationRoute) => {
  const fixture = await setupLeaseRenewal(annotationRoute)
  expect(fixture.backend).toHaveBeenCalledOnce()
  await vi.advanceTimersByTimeAsync(120_000)
  expect(fixture.rotations()).toBeGreaterThanOrEqual(4)
  expect(fixture.backend).toHaveBeenCalledTimes(13)
  expect(fixture.collaboration.getCanvasSecondsUntilExpiry('canvas')).toBe(45)
  expect(fixture.collaboration.getCanvasEditor('canvas')?.user.id).toBe('auth-editor')
  expect(fixture.socketStatus.value).toBe('connected')
  expect(fixture.send.mock.calls.some(([message]) => message.type === 'LEASE_RENEW')).toBe(false)
})

it('continues renewing through a websocket interruption using the HTTP response', async () => {
  const fixture = await setupLeaseRenewal()
  fixture.socketStatus.value = 'disconnected'
  await vi.advanceTimersByTimeAsync(60_000)
  expect(fixture.backend).toHaveBeenCalledTimes(7)
  expect(fixture.collaboration.getCanvasSecondsUntilExpiry('canvas')).toBe(45)
})

it('explicitly rejoins a still-open page after backend membership expires', async () => {
  const fixture = await setupLeaseRenewal()
  fixture.expireBackend()
  await vi.advanceTimersByTimeAsync(10_000)
  expect(fixture.fetch.mock.calls.filter(([path]) => path.endsWith('/lease/join'))).toHaveLength(1)
  expect(fixture.collaboration.getCanvasEditor('canvas')?.user.id).toBe('auth-editor')
  await vi.advanceTimersByTimeAsync(60_000)
  expect(fixture.collaboration.getCanvasSecondsUntilExpiry('canvas')).toBe(45)
})

it('deduplicates ownerless recovery and cancels its pending join when the page closes', async () => {
  const fixture = await setupLeaseRenewal()
  let finishToken: (value: unknown) => void = () => {}
  try {
    fixture.expireBackend()
    const original = fixture.fetch.getMockImplementation()!
    fixture.fetch.mockImplementation((path, options) => path.endsWith('/collaboration/token')
      ? new Promise((resolve) => { finishToken = resolve })
      : original(path, options))
    await vi.advanceTimersByTimeAsync(30_000)
    expect(fixture.fetch.mock.calls.filter(([path]) => path.endsWith('/collaboration/token'))).toHaveLength(1)
    fixture.collaboration.leaveCanvasRoom('canvas')
    finishToken({ ...fixture.room.identity, persistedRevision: 'revision', lease: fixture.room.lease })
    await vi.advanceTimersByTimeAsync(0)
    expect(fixture.fetch.mock.calls.some(([path]) => path.endsWith('/lease/join'))).toBe(false)
    expect(fixture.collaboration.getRoomForCanvas('canvas')).toBeNull()
  } finally {
    finishToken({})
  }
})

it.each([401, 403, 503])('reports renewal status %s and retries on the next heartbeat', async (status) => {
  const fixture = await setupLeaseRenewal()
  const warning = vi.spyOn(console, 'warn').mockImplementation(() => {})
  fixture.fetch.mockRejectedValueOnce({ statusCode: status })
  await vi.advanceTimersByTimeAsync(10_000)
  expect(warning).toHaveBeenCalledWith('[editor-collaboration] Lease renewal failed', {
    status, roomCount: 1, connectionStatus: 'connected'
  })
  await vi.advanceTimersByTimeAsync(10_000)
  expect(fixture.collaboration.getCanvasSecondsUntilExpiry('canvas')).toBe(45)
  expect(fixture.backend).toHaveBeenCalledTimes(2)
})

it('does not overlap renewals or delay leaving, and ignores the late response', async () => {
  const fixture = await setupLeaseRenewal()
  let finishRenewal: (value: unknown) => void = () => {}
  try {
    fixture.fetch.mockImplementationOnce(() => new Promise((resolve) => {
      finishRenewal = resolve
    }))
    await vi.advanceTimersByTimeAsync(10_000)
    const count = fixture.fetch.mock.calls.filter(([path]) => path === '/api/collaboration/leases/renew').length
    await vi.advanceTimersByTimeAsync(10_000)
    expect(fixture.fetch.mock.calls.filter(([path]) => path === '/api/collaboration/leases/renew')).toHaveLength(count)
    fixture.collaboration.leaveCanvasRoom('canvas')
    await vi.advanceTimersByTimeAsync(0)
    expect(fixture.fetch.mock.calls.filter(([path]) => path.endsWith('/lease/release'))).toHaveLength(1)
    expect(fixture.collaboration.getRoomForCanvas('canvas')).toBeNull()
    finishRenewal({ renewals: [{ roomKey: fixture.room.identity.roomKey, lease: fixture.room.lease }] })
    await vi.advanceTimersByTimeAsync(0)
    expect(fixture.fetch.mock.calls.filter(([path]) => path.endsWith('/lease/release'))).toHaveLength(1)
    expect(fixture.collaboration.getRoomForCanvas('canvas')).toBeNull()
  } finally {
    finishRenewal({ renewals: [] })
  }
})

it('renews every room in batches that respect the backend limit', async () => {
  const fixture = await setupLeaseRenewal()
  try {
    fixture.states.get('editor-collaboration.rooms')!.value = Object.fromEntries(
      Array.from({ length: 101 }, (_, index) => {
        const roomKey = index === 0 ? fixture.room.identity.roomKey : `auth-project:page:xml-${index}`
        return [roomKey, { ...fixture.room, identity: { ...fixture.room.identity, roomKey, xmlId: `xml-${index}` } }]
      })
    )
    fixture.fetch.mockResolvedValue({ renewals: [] })
    const previousCalls = fixture.fetch.mock.calls.length
    await vi.advanceTimersByTimeAsync(10_000)
    const batches = fixture.fetch.mock.calls.slice(previousCalls).map(([, options]) =>
      (options!.body as { targets: unknown[] }).targets
    )
    expect(batches.map(batch => batch.length)).toEqual([100, 1])
    expect(new Set(batches.flat().map(target => (target as { xmlId: string }).xmlId)).size).toBe(101)
  } finally {
    // Restore the original fixture so teardown closes its only bound room.
    fixture.states.get('editor-collaboration.rooms')!.value = { [fixture.room.identity.roomKey]: fixture.room }
    fixture.fetch.mockReset().mockResolvedValue({ roomKey: fixture.room.identity.roomKey, lease: fixture.room.lease })
  }
})
