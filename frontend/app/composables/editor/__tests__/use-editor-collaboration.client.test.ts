import { afterEach, expect, it, vi } from 'vitest'
import { nextTick, ref, type Ref } from 'vue'
import type { Peer } from 'crossws'
import type { CollaborationLeaseState, CollaborationRoomSession } from '@/types/collaboration'
import { collaborationState } from '#server/utils/collaboration-state'
import type { CollaborationRoomTokenPayload } from '#server/utils/collaboration-token'

const editorStore = vi.hoisted(() => ({ getCanvas: vi.fn(), getPage: vi.fn() }))
const backendFetch = vi.hoisted(() => vi.fn())
vi.mock('@/stores/editor/editor.store', () => ({ useEditorStore: () => editorStore }))
vi.mock('#server/utils/backendFetch', () => ({ backendFetch }))

afterEach(() => {
  vi.clearAllTimers()
  vi.useRealTimers()
  vi.unstubAllGlobals()
})

it.each([
  { initialEpoch: '2', releasedEpoch: '3', renewedEpoch: '10' },
  { initialEpoch: '9007199254740991', releasedEpoch: '9007199254740992', renewedEpoch: '9007199254740993' }
])('recovers editing after reconnect and rejects delayed decimal epoch $releasedEpoch', async ({ initialEpoch, releasedEpoch, renewedEpoch }) => {
  expect(import.meta.client).toBe(true)
  vi.useFakeTimers()
  vi.stubGlobal('window', { sessionStorage: { getItem: () => 'instance-1' }, addEventListener: vi.fn() })
  vi.stubGlobal('BroadcastChannel', undefined)
  vi.stubGlobal('useToast', () => ({ add: vi.fn() }))
  const roomKey = 'project-reconnect:page-1:xml-1'
  const user = { id: 'user-1', username: 'user-1', displayName: 'User 1' }
  const ownedLease: CollaborationLeaseState = {
    editor: { user, acquiredAt: '2026-01-01T00:00:00Z' },
    pendingTakeover: null,
    leaseEpoch: initialEpoch,
    expiresAt: '2099-01-01T00:00:00Z'
  }
  const releasedLease: CollaborationLeaseState = {
    editor: null, pendingTakeover: null, leaseEpoch: releasedEpoch, expiresAt: null
  }
  let backendLease = ownedLease
  backendFetch.mockImplementation(async () => new Response(JSON.stringify({ renewals: [{ roomKey, lease: backendLease }] })))
  vi.stubGlobal('defineEventHandler', (handler: unknown) => handler)
  vi.stubGlobal('readBody', async (event: { body: unknown }) => event.body)
  const { default: renewHandler } = await import('#server/api/collaboration/leases/renew.post')
  const renew = renewHandler as unknown as (event: unknown) => Promise<unknown>
  const renewals: Promise<unknown>[] = []
  const room: CollaborationRoomSession = {
    identity: {
      roomKey, workspaceId: 'workspace-1', projectId: 'project-reconnect',
      pageId: 'page-1', xmlId: 'xml-1', token: 'test-token',
      canEdit: true, canForceTakeover: false, user
    },
    lease: ownedLease,
    presence: { members: [] },
    viewerSync: { persistedRevision: 'revision-1', resyncRequired: false, snapshotReady: true }
  }
  const states = new Map<string, Ref>([
    ['editor-collaboration.rooms', ref({ [roomKey]: room })],
    ['editor-collaboration.canvas-rooms', ref({ 'canvas-1': roomKey })],
    ['editor-collaboration.instance-id', ref('instance-1')]
  ])
  let handleMessage: (message: { type: string, payload: unknown }) => void = () => {}
  const socketStatus = ref('idle')
  const token: CollaborationRoomTokenPayload = {
    sub: user.id, username: user.username, displayName: user.displayName,
    roomKey, workspaceId: 'workspace-1', projectId: 'project-reconnect', pageId: 'page-1', xmlId: 'xml-1',
    canEdit: true, canForceTakeover: false, persistedRevision: 'revision-1',
    annotationRoute: { scope: 'PROJECT', projectId: 'project-reconnect', pageId: 'page-1' },
    exp: Date.now() + 60_000
  }
  const peer = {
    id: 'peer-editor-reconnect',
    send: (raw: string) => {
      const message = JSON.parse(raw)
      if (message.type === 'COLLAB_ROOM_STATE') handleMessage(message)
    }
  } as unknown as Peer
  const send = vi.fn((message: { type: string, payload?: { roomKeys?: string[] } }) => {
    if (message.type === 'JOIN_ROOM') collaborationState.joinRoom(peer, token)
    if (message.type === 'LEASE_RENEW') {
      renewals.push(renew({ body: {
        instanceId: 'instance-1',
        targets: collaborationState.getLeaseRenewalTargets(peer.id, message.payload?.roomKeys ?? [])
      } }))
    }
    return true
  })
  vi.stubGlobal('useState', (key: string, init: () => unknown) => {
    if (!states.has(key)) states.set(key, ref(init()))
    return states.get(key)
  })
  vi.stubGlobal('useUserSession', () => ({ user: ref(user), loggedIn: ref(true) }))
  vi.stubGlobal('useRealtimeSocket', () => ({
    connectionStatus: socketStatus, send, connect: vi.fn(),
    subscribe: (callback: typeof handleMessage) => { handleMessage = callback }
  }))
  vi.stubGlobal('useThrottleFn', (callback: unknown) => callback)
  vi.stubGlobal('$fetch', vi.fn().mockResolvedValue({ roomKey, lease: releasedLease, persistedRevision: 'revision-1' }))
  editorStore.getCanvas.mockReturnValue({ projectId: 'project-reconnect', pageId: 'page-1', xmlFileId: 'xml-1' })
  editorStore.getPage.mockReturnValue({ locked: false, workflowState: 'IN_PROGRESS' })

  const { useEditorCollaboration } = await import('../use-editor-collaboration')
  const collaboration = useEditorCollaboration()
  try {
    collaboration.connect()
    collaborationState.registerPeer(peer)
    collaborationState.joinRoom(peer, token)
    collaborationState.syncLeaseState(roomKey, ownedLease)
    expect(collaboration.canEditCanvas('canvas-1')).toBe(true)
    socketStatus.value = 'connected'
    await nextTick()
    await Promise.all(renewals.splice(0))

    backendLease = releasedLease
    collaborationState.syncLeaseState(roomKey, releasedLease)
    expect(collaboration.canEditCanvas('canvas-1')).toBe(false)
    expect(collaboration.getCanvasEditor('canvas-1')).toBeNull()
    expect(collaboration.hasCanvasLeaseExpiredLocally('canvas-1')).toBe(true)
    expect(collaboration.canReclaimCanvasEdit('canvas-1')).toBe(true)
    expect(collaborationState.getProjectPageSummaries('project-reconnect')[0]?.editor).toBeNull()

    // Reconnect to a recreated websocket room while the backend lease is ownerless.
    collaborationState.unregisterPeer(peer.id)
    socketStatus.value = 'disconnected'
    await nextTick()
    socketStatus.value = 'connected'
    await nextTick()
    await Promise.all(renewals.splice(0))
    expect(send).toHaveBeenCalledWith({ type: 'JOIN_ROOM', payload: { token: 'test-token' } })
    collaborationState.syncLeaseState(roomKey, releasedLease)
    expect(collaboration.canEditCanvas('canvas-1')).toBe(false)

    // Exercise the renewal API, websocket cache, and browser with a post-restart version.
    const renewedLease = { ...ownedLease, leaseEpoch: renewedEpoch }
    backendLease = renewedLease
    await vi.advanceTimersByTimeAsync(10_000)
    await Promise.all(renewals.splice(0))
    expect(backendFetch).toHaveBeenCalledWith(expect.anything(), '/collaboration/leases/renew', expect.anything())
    expect(collaboration.canEditCanvas('canvas-1')).toBe(true)
    expect(collaboration.getCanvasEditor('canvas-1')?.user.id).toBe(user.id)
    expect(collaboration.hasCanvasLeaseExpiredLocally('canvas-1')).toBe(false)

    collaborationState.syncLeaseState(roomKey, releasedLease)
    // Also check the browser guard against a state already in transit before renewal.
    handleMessage({ type: 'COLLAB_ROOM_STATE', payload: { roomKey, members: [], lease: releasedLease } })
    expect(collaboration.canEditCanvas('canvas-1')).toBe(true)
    expect(collaboration.getRoomForCanvas('canvas-1')?.lease.leaseEpoch).toBe(renewedLease.leaseEpoch)
    expect(collaboration.hasCanvasLeaseExpiredLocally('canvas-1')).toBe(false)
  } finally {
    collaborationState.unregisterPeer(peer.id)
    collaboration.leaveCanvasRoom('canvas-1')
    collaboration.disconnect()
  }
})
