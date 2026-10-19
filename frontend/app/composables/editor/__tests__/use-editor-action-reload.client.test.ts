import { afterEach, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia, storeToRefs } from 'pinia'
import { effectScope, nextTick, ref, type Ref } from 'vue'
import type { Peer } from 'crossws'
import { useEditorStore } from '@/stores/editor/editor.store'
import { useEditorDocumentStore } from '@/stores/editor/editor.document.store'
import { clearAllEditorSessions, getEditorSession } from '@/session/editor/editor-session'
import { convertPcGtsToPageDto } from '@/services/editor/page-conversion.service'
import type { PageDto } from '@/types/page-dto'
import { collaborationState } from '#server/utils/collaboration-state'
import type { CollaborationRoomTokenPayload } from '#server/utils/collaboration-token'
import { useEditorCollaboration } from '../use-editor-collaboration'
import { useEditorCanvasCollaboration } from '../use-editor-canvas-collaboration'

afterEach(() => {
  clearAllEditorSessions()
  vi.clearAllTimers()
  vi.useRealTimers()
  vi.unstubAllGlobals()
})

it('keeps action output after annotation reloads while retaining the edit lease', async () => {
  vi.useFakeTimers()
  setActivePinia(createPinia())
  const canvasId = 'action-canvas'
  const projectId = 'action-project'
  const pageId = 'page'
  const roomKey = `${projectId}:${pageId}:xml`
  const user = { id: 'editor', username: 'editor', displayName: 'Editor' }
  const lease = { editor: { user, acquiredAt: '2026-10-19T10:00:00Z' }, pendingTakeover: null,
    leaseEpoch: '2', expiresAt: '2099-01-01T00:00:00Z' }
  const annotationPath = `/api/projects/${projectId}/pages/${pageId}/annotations/xml`
  const token: CollaborationRoomTokenPayload = {
    sub: user.id, username: user.username, displayName: user.displayName,
    roomKey, workspaceId: 'workspace', projectId, pageId, xmlId: 'xml',
    canEdit: true, canForceTakeover: false, persistedRevision: 'before-action',
    annotationRoute: { scope: 'PROJECT', projectId, pageId }, exp: Date.now() + 60_000
  }
  const states = new Map<string, Ref>()
  vi.stubGlobal('storeToRefs', storeToRefs)
  vi.stubGlobal('useState', (key: string, init: () => unknown) => {
    if (!states.has(key)) states.set(key, ref(init()))
    return states.get(key)
  })
  vi.stubGlobal('useUserSession', () => ({ user: ref(user), loggedIn: ref(true) }))
  vi.stubGlobal('window', { sessionStorage: { getItem: () => 'instance' }, addEventListener: vi.fn() })
  vi.stubGlobal('BroadcastChannel', undefined)
  vi.stubGlobal('useToast', () => ({ add: vi.fn() }))
  vi.stubGlobal('useThrottleFn', (callback: unknown) => callback)
  vi.stubGlobal('useDebounceFn', (callback: () => void, delay: number) => {
    let timer: ReturnType<typeof setTimeout>
    return () => {
      clearTimeout(timer)
      timer = setTimeout(callback, delay)
    }
  })
  let handleMessage: (message: { type: string, payload: unknown }) => void = () => {}
  const peer = { id: 'action-peer', send: (raw: string) => handleMessage(JSON.parse(raw)) } as unknown as Peer
  const send = vi.fn((message: { type: string, payload: { snapshot?: unknown } }) => {
    if (message.type === 'JOIN_ROOM') collaborationState.joinRoom(peer, token)
    if (message.type === 'SNAPSHOT_UPDATE') collaborationState.applySnapshotUpdate(peer.id, roomKey, message.payload.snapshot)
    return true
  })
  vi.stubGlobal('useRealtimeSocket', () => ({ connectionStatus: ref('idle'), connect: vi.fn(), send,
    subscribe: (callback: typeof handleMessage) => { handleMessage = callback } }))
  let persisted: PageDto = { imageWidth: 100, imageHeight: 100, custom: 'saved before action',
    regions: [{ id: 'saved-region', kind: 'TextRegion', coords: { points: [[0, 0], [20, 0], [20, 20], [0, 20]] } }] }
  const fetch = vi.fn(async (path: string) => {
    if (path === annotationPath) {
      await new Promise(resolve => setTimeout(resolve, 20))
      return structuredClone(persisted)
    }
    if (path.endsWith('/token')) return { ...token, user, token: 'token', lease, persistedRevision: persisted.custom }
    if (path.endsWith('/lease/join') || path.endsWith('/lease/release')) return { roomKey, lease }
    if (path === '/api/collaboration/leases/renew') return { renewals: [] }
    return { persistedRevision: persisted.custom }
  })
  vi.stubGlobal('$fetch', fetch)
  const store = useEditorStore()
  useEditorDocumentStore().setProjectPages(projectId, [{ id: pageId, projectId, label: 'Page',
    imageVariants: [{ id: 'image', label: 'original', url: '/image.png' }],
    xmlFiles: [{ id: 'xml', fileName: 'page.xml', schema: 'PAGE_XML' }],
    tags: [], resolvedTags: null, locked: false, lockedReason: null }], { markLoaded: true })
  const scope = effectScope()
  const collaboration = useEditorCollaboration()
  const settle = async () => {
    await nextTick()
    await vi.advanceTimersByTimeAsync(0)
    await nextTick()
  }
  try {
    collaborationState.syncLeaseState(roomKey, lease)
    const loading = store.loadPageIntoCanvas(canvasId, projectId, pageId, undefined, { awaitAnnotations: true })
    await vi.advanceTimersByTimeAsync(20)
    await loading
    scope.run(() => useEditorCanvasCollaboration(canvasId))
    await settle()
    const session = getEditorSession(canvasId)!
    expect(collaboration.canEditCanvas(canvasId)).toBe(true)
    expect(send.mock.calls.some(([message]) => message.type === 'SNAPSHOT_UPDATE')).toBe(true)
    expect(convertPcGtsToPageDto(session.document.value!).custom).toBe('saved before action')

    // Action page-result handling reloads the XML replaced by the processor.
    persisted = { imageWidth: 100, imageHeight: 100, custom: 'new action output',
      regions: [{ id: 'action-region', kind: 'TextRegion', coords: { points: [[0, 0], [50, 0], [50, 50], [0, 50]] } }] }
    store.invalidateAnnotationCache(pageId, projectId)
    const reloading = store.loadPageIntoCanvas(canvasId, projectId, pageId, undefined, { awaitAnnotations: true })
    await vi.advanceTimersByTimeAsync(20)
    await reloading
    await settle()

    expect(fetch.mock.calls.filter(([path]) => path === annotationPath)).toHaveLength(2)
    expect(convertPcGtsToPageDto(session.document.value!).custom).toBe('new action output')
    expect(convertPcGtsToPageDto(session.document.value!).regions?.map(region => region.id)).toEqual(['action-region'])
    expect(collaboration.canEditCanvas(canvasId)).toBe(true)
    expect(send.mock.calls.filter(([message]) => message.type === 'LEAVE_ROOM')).toHaveLength(0)
  } finally {
    scope.stop()
    collaborationState.unregisterPeer(peer.id)
    collaboration.disconnect()
    store.resetEditorState()
    await settle()
  }
})
