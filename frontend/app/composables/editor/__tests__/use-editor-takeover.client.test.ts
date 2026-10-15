import { afterEach, expect, it, vi } from 'vitest'
import { effectScope, nextTick, reactive, ref, type Ref } from 'vue'
import type { Peer } from 'crossws'
import type { CollaborationLeaseState, CollaborationRoomBootstrap, CollaborationUserIdentity } from '@/types/collaboration'
import type { CollaborationRoomTokenPayload } from '#server/utils/collaboration-token'
import { collaborationState } from '#server/utils/collaboration-state'
import { clearAllEditorSessions, ensureEditorSession } from '@/session/editor/editor-session'
import { convertPageDtoToPcGts } from '@/services/editor/page-conversion.service'
import { useEditorCanvasCollaboration } from '../use-editor-canvas-collaboration'
import { useEditorCollaboration } from '../use-editor-collaboration'

const store = vi.hoisted(() => ({
  canvases: {} as Record<string, { projectId: string, pageId: string, xmlFileId: string | null, isLoadingAnnotations: boolean }>,
  getCanvas: vi.fn(), getPage: vi.fn(), loadPageIntoCanvas: vi.fn(), resetCanvasHistoryBaseline: vi.fn()
}))
vi.mock('@/stores/editor/editor.store', () => ({ useEditorStore: () => store }))

afterEach(() => {
  clearAllEditorSessions()
  vi.clearAllTimers()
  vi.useRealTimers()
  vi.unstubAllGlobals()
  vi.clearAllMocks()
})

it.each([true, false])('keeps takeover ownership through a slow reload and renewal (forced=%s)', async (force) => {
  vi.useFakeTimers()
  vi.setSystemTime(new Date('2026-10-14T12:00:36Z'))
  vi.stubGlobal('window', { sessionStorage: { getItem: () => 'curator-instance' }, addEventListener: vi.fn() })
  vi.stubGlobal('BroadcastChannel', undefined)
  vi.stubGlobal('useToast', () => ({ add: vi.fn() }))
  vi.stubGlobal('useThrottleFn', (callback: unknown) => callback)
  vi.stubGlobal('useDebounceFn', (callback: unknown) => callback)
  const roomKey = 'takeover-project:page:xml'
  const canvasId = 'takeover-canvas'
  const curator: CollaborationUserIdentity = { id: 'curator', username: 'curator', displayName: 'Curator' }
  const previous: CollaborationUserIdentity = { id: 'previous', username: 'previous', displayName: 'Previous editor' }
  const viewer: CollaborationUserIdentity = { id: 'viewer', username: 'viewer', displayName: 'Viewer' }
  const states = new Map<string, Ref>([['editor-collaboration.instance-id', ref('curator-instance')]])
  vi.stubGlobal('useState', (key: string, init: () => unknown) => {
    if (!states.has(key)) states.set(key, ref(init()))
    return states.get(key)
  })
  vi.stubGlobal('useUserSession', () => ({ user: ref(curator), loggedIn: ref(true) }))
  store.canvases = reactive({ [canvasId]: {
    projectId: 'takeover-project', pageId: 'page', xmlFileId: 'xml', isLoadingAnnotations: false
  } })
  store.getCanvas.mockImplementation((id: string) => store.canvases[id])
  store.getPage.mockReturnValue({ locked: false, workflowState: 'IN_PROGRESS' })
  store.loadPageIntoCanvas.mockImplementation(async () => {
    store.canvases[canvasId]!.isLoadingAnnotations = true
    await new Promise(resolve => setTimeout(resolve, 9000))
    store.canvases[canvasId]!.isLoadingAnnotations = false
  })
  let epoch = 2n
  let lease: CollaborationLeaseState = {
    editor: { user: previous, acquiredAt: '2026-10-14T12:00:00Z' },
    pendingTakeover: null, leaseEpoch: epoch.toString(), expiresAt: '2026-10-14T12:00:45Z'
  }
  const copyLease = () => structuredClone(lease)
  const publish = () => collaborationState.syncLeaseState(roomKey, copyLease())
  const renew = () => {
    if (lease.editor?.user.id === curator.id) lease.expiresAt = new Date(Date.now() + 45_000).toISOString()
    publish()
  }
  const grant = () => {
    lease = { editor: { user: curator, acquiredAt: new Date().toISOString() }, pendingTakeover: null,
      leaseEpoch: (++epoch).toString(), expiresAt: new Date(Date.now() + 45_000).toISOString() }
    publish()
  }
  function token(user: CollaborationUserIdentity): CollaborationRoomTokenPayload {
    return { sub: user.id, username: user.username, displayName: user.displayName,
      workspaceId: 'workspace', projectId: 'takeover-project', pageId: 'page', xmlId: 'xml', roomKey,
      canEdit: true, canForceTakeover: user.id === curator.id, persistedRevision: 'revision',
      annotationRoute: { scope: 'PROJECT', projectId: 'takeover-project', pageId: 'page' }, exp: Date.now() + 3600_000 }
  }
  let handleMessage: (message: { type: string, payload: unknown }) => void = () => {}
  const peers = [previous, viewer, curator].map(user => ({ id: user.id, send: (raw: string) => {
    if (user.id === curator.id) handleMessage(JSON.parse(raw))
  } }) as unknown as Peer)
  const socketStatus = ref('idle')
  vi.stubGlobal('useRealtimeSocket', () => ({ connectionStatus: socketStatus, connect: vi.fn(),
    subscribe: (callback: typeof handleMessage) => { handleMessage = callback },
    send: (message: { type: string }) => {
      const peer = peers[2]!
      if (message.type === 'JOIN_ROOM') collaborationState.joinRoom(peer, token(curator))
      if (message.type === 'LEAVE_ROOM') collaborationState.leaveRoom(peer.id, roomKey)
      return true
    }
  }))
  const fetch = vi.fn(async (path: string, options?: { body?: { force?: boolean } }) => {
    if (path.endsWith('/token')) {
      return { roomKey, workspaceId: 'workspace', projectId: 'takeover-project', pageId: 'page', xmlId: 'xml',
        annotationRoute: { scope: 'PROJECT', projectId: 'takeover-project', pageId: 'page' },
        token: 'token', user: curator, canEdit: true, canForceTakeover: true, persistedRevision: 'revision', lease: copyLease()
      } satisfies CollaborationRoomBootstrap
    }
    if (path === '/api/collaboration/leases/renew') {
      renew()
      return { renewals: [{ roomKey, lease: copyLease() }] }
    }
    if (path.endsWith('/lease/join')) {
      renew()
      return { roomKey, lease: copyLease() }
    }
    if (path.endsWith('/lease/request')) {
      if (options?.body?.force) grant()
      else {
        lease.pendingTakeover = { requester: curator, requestedAt: new Date().toISOString(), force: false }
        lease.leaseEpoch = (++epoch).toString()
        publish()
      }
      return { roomKey, lease: copyLease(), outcome: options?.body?.force ? 'GRANTED' : 'PENDING', message: '' }
    }
    if (path.endsWith('/lease/release')) {
      if (lease.editor?.user.id === curator.id) {
        lease = { ...lease, editor: { user: previous, acquiredAt: new Date().toISOString() }, leaseEpoch: (++epoch).toString() }
      }
      return { roomKey, lease: copyLease() }
    }
    return { persistedRevision: 'revision' }
  })
  vi.stubGlobal('$fetch', fetch)
  const scope = effectScope()
  const collaboration = useEditorCollaboration()
  try {
    collaboration.connect()
    for (let index = 0; index < peers.length; index++) {
      collaborationState.registerPeer(peers[index]!)
      if (index < 2) collaborationState.joinRoom(peers[index]!, token(index === 0 ? previous : viewer))
    }
    publish()
    scope.run(() => useEditorCanvasCollaboration(canvasId))
    await vi.advanceTimersByTimeAsync(0)
    socketStatus.value = 'connected'
    await nextTick()
    expect(collaboration.getCanvasCollaborators(canvasId)).toHaveLength(2)
    expect(collaboration.canEditCanvas(canvasId)).toBe(false)

    const outcome = await collaboration.requestTakeover(canvasId, force)
    if (!force) {
      expect(outcome).toBe('PENDING')
      grant()
    } else {
      expect(outcome).toBe('GRANTED')
    }
    await nextTick()
    expect(store.canvases[canvasId]!.isLoadingAnnotations).toBe(true)
    expect(collaboration.canEditCanvas(canvasId)).toBe(true)
    expect(collaboration.getCanvasSecondsUntilExpiry(canvasId)).toBe(45)
    expect(fetch.mock.calls.some(([path]) => path.endsWith('/lease/release'))).toBe(false)

    // An old same-epoch response must not replace 45 seconds with the previous nine.
    handleMessage({ type: 'COLLAB_ROOM_STATE', payload: { roomKey, members: [],
      lease: { ...copyLease(), expiresAt: '2026-10-14T12:00:45Z' } } })
    expect(collaboration.getCanvasSecondsUntilExpiry(canvasId)).toBe(45)
    // A late HTTP acknowledgement must follow the same ordering rule.
    fetch.mockResolvedValueOnce({ roomKey, lease: { ...copyLease(), expiresAt: '2026-10-14T12:00:45Z' },
      outcome: 'GRANTED', message: '' })
    await collaboration.requestTakeover(canvasId, false)
    expect(collaboration.getCanvasSecondsUntilExpiry(canvasId)).toBe(45)
    await vi.advanceTimersByTimeAsync(60_000)
    expect(collaboration.canEditCanvas(canvasId)).toBe(true)
    expect(collaboration.getCanvasEditor(canvasId)?.user.id).toBe(curator.id)
    expect(fetch.mock.calls.some(([path]) => path.endsWith('/lease/release'))).toBe(false)
    const secondCanvasId = `${canvasId}-second-view`
    store.canvases[secondCanvasId] = { ...store.canvases[canvasId]! }
    const secondView = effectScope()
    try {
      secondView.run(() => useEditorCanvasCollaboration(secondCanvasId))
      await vi.advanceTimersByTimeAsync(0)
      secondView.stop()
      expect(collaborationState.getProjectPageSummaries('takeover-project')[0]?.isLive).toBe(true)
      expect(fetch.mock.calls.some(([path]) => path.endsWith('/lease/release'))).toBe(false)
    } finally {
      secondView.stop()
    }
    scope.stop()
    await vi.advanceTimersByTimeAsync(0)
    expect(fetch.mock.calls.filter(([path]) => path.endsWith('/lease/release'))).toHaveLength(1)
  } finally {
    scope.stop()
    for (const peer of peers) collaborationState.unregisterPeer(peer.id)
    collaboration.leaveCanvasRoom(canvasId)
    collaboration.disconnect()
  }
})

it.each([
  { reopen: false, pendingJoin: true, host: true },
  { reopen: true, pendingJoin: true, host: true },
  { reopen: false, pendingJoin: false, host: true },
  { reopen: true, pendingJoin: false, host: true },
  { reopen: true, pendingJoin: true, host: false },
  { reopen: false, pendingJoin: true, host: false, initialCreation: true }
])('orders join and leave responses (reopened=$reopen, pendingJoin=$pendingJoin, host=$host, initialCreation=$initialCreation)', async ({ reopen, pendingJoin, host, initialCreation = false }) => {
  vi.useFakeTimers()
  vi.stubGlobal('window', { sessionStorage: { getItem: () => 'closing-instance' }, addEventListener: vi.fn() })
  vi.stubGlobal('BroadcastChannel', undefined)
  vi.stubGlobal('useToast', () => ({ add: vi.fn() }))
  vi.stubGlobal('useThrottleFn', (callback: unknown) => callback)
  vi.stubGlobal('useDebounceFn', (callback: unknown) => callback)
  const user: CollaborationUserIdentity = { id: 'closing-user', username: 'closing-user', displayName: 'Closing user' }
  const states = new Map<string, Ref>([['editor-collaboration.instance-id', ref('closing-instance')]])
  vi.stubGlobal('useState', (key: string, init: () => unknown) => {
    if (!states.has(key)) states.set(key, ref(init()))
    return states.get(key)
  })
  vi.stubGlobal('useUserSession', () => ({ user: ref(user), loggedIn: ref(true) }))
  vi.stubGlobal('useRealtimeSocket', () => ({ connectionStatus: ref('idle'), connect: vi.fn(),
    subscribe: vi.fn(), send: vi.fn(() => true) }))
  store.canvases = reactive({ closing: {
    projectId: 'closing-project', pageId: 'page', xmlFileId: initialCreation ? null : 'xml', isLoadingAnnotations: false
  } })
  store.getCanvas.mockImplementation((id: string) => store.canvases[id])
  store.getPage.mockReturnValue({ locked: false, workflowState: 'IN_PROGRESS', label: 'page', xmlFiles: [] })
  if (initialCreation) ensureEditorSession('closing', { document: convertPageDtoToPcGts({ imageWidth: 100, imageHeight: 100 }) })
  const events: string[] = []
  let owned = false
  let finishFirstJoin: () => void = () => {}
  const firstJoinResponse = new Promise<void>((resolve) => {
    finishFirstJoin = resolve
  })
  let finishFirstRelease: () => void = () => {}
  const firstReleaseResponse = new Promise<void>((resolve) => {
    finishFirstRelease = resolve
  })
  const lease = (): CollaborationLeaseState => ({
    editor: owned ? { user, acquiredAt: new Date().toISOString() } : null,
    pendingTakeover: null, leaseEpoch: events.length.toString(), expiresAt: new Date(Date.now() + 45_000).toISOString()
  })
  vi.stubGlobal('$fetch', vi.fn(async (path: string) => {
    const roomKey = 'closing-project:page:xml'
    if (path.endsWith('/annotations')) {
      events.push('create')
      await firstJoinResponse
      return { xmlId: 'xml' }
    }
    if (path.endsWith('/token')) {
      return { roomKey, workspaceId: 'workspace', projectId: 'closing-project', pageId: 'page', xmlId: 'xml',
        annotationRoute: { scope: 'PROJECT', projectId: 'closing-project', pageId: 'page' },
        token: 'token', user, canEdit: true, canForceTakeover: true, persistedRevision: 'revision', lease: lease()
      } satisfies CollaborationRoomBootstrap
    }
    if (path.endsWith('/lease/join')) {
      events.push('join')
      owned = true
      if (events.length === 1) await firstJoinResponse
    }
    if (path.endsWith('/lease/release')) {
      events.push('release')
      await firstReleaseResponse
      owned = false
    }
    if (path === '/api/collaboration/leases/renew') return { renewals: [{ roomKey, lease: lease() }] }
    return { roomKey, lease: lease(), persistedRevision: 'revision' }
  }))
  const scope = effectScope()
  const reopenedScope = effectScope()
  const collaboration = useEditorCollaboration()
  try {
    if (!pendingJoin) finishFirstJoin()
    if (host) scope.run(() => useEditorCanvasCollaboration('closing'))
    else void collaboration.ensureCanvasRoom('closing')
    await vi.advanceTimersByTimeAsync(0)
    expect(events).toEqual(initialCreation ? ['create'] : ['join'])
    expect(owned).toBe(!initialCreation)
    scope.stop()
    if (!host) collaboration.leaveCanvasRoom('closing')
    if (reopen) reopenedScope.run(() => useEditorCanvasCollaboration('closing'))
    finishFirstJoin()
    await vi.advanceTimersByTimeAsync(0)
    if (initialCreation) {
      expect(events).toEqual(['create'])
      expect(owned).toBe(false)
      expect(collaboration.getCanvasEditor('closing')).toBe(null)
      return
    }
    // The next join must wait until the previous membership has been released.
    expect(events).toEqual(['join', 'release'])
    finishFirstRelease()
    await vi.advanceTimersByTimeAsync(0)
    expect(events).toEqual(reopen ? ['join', 'release', 'join'] : ['join', 'release'])
    expect(owned).toBe(reopen)
    expect(collaboration.getCanvasEditor('closing')?.user.id ?? null).toBe(reopen ? user.id : null)
  } finally {
    finishFirstJoin()
    finishFirstRelease()
    scope.stop()
    reopenedScope.stop()
    collaboration.leaveCanvasRoom('closing')
    await vi.advanceTimersByTimeAsync(0)
    collaboration.disconnect()
  }
})
