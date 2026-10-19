import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia, storeToRefs } from 'pinia'
import { effectScope, nextTick, ref, type Ref } from 'vue'
import type { Peer } from 'crossws'
import { useEditorStore } from '@/stores/editor/editor.store'
import { useEditorDocumentStore } from '@/stores/editor/editor.document.store'
import { clearAllEditorSessions, getEditorSession } from '@/session/editor/editor-session'
import { convertPcGtsToPageDto } from '@/services/editor/page-conversion.service'
import { snapshotFromPageDto } from '@/utils/editor/collaboration-page-doc'
import type { PageDto } from '@/types/page-dto'
import { collaborationState } from '#server/utils/collaboration-state'
import type { CollaborationRoomTokenPayload } from '#server/utils/collaboration-token'
import { useEditorCollaboration } from '../use-editor-collaboration'
import { useEditorCanvasCollaboration } from '../use-editor-canvas-collaboration'

const canvasId = 'action-canvas'
const projectId = 'action-project'
const pageId = 'page'
const roomKey = `${projectId}:${pageId}:xml`
const annotationPath = `/api/projects/${projectId}/pages/${pageId}/annotations/xml`
const imagesPath = `/api/projects/${projectId}/pages/${pageId}/images`
const user = { id: 'editor', username: 'editor', displayName: 'Editor' }

function document(custom: string): PageDto {
  return { imageWidth: 100, imageHeight: 100, custom,
    regions: [{ id: custom, kind: 'TextRegion', coords: { points: [[0, 0], [20, 0], [20, 20], [0, 20]] } }] }
}

function deferred<T>() {
  let resolve!: (value: T) => void
  const promise = new Promise<T>((done) => {
    resolve = done
  })
  return { promise, resolve }
}

async function createEditor() {
  vi.useFakeTimers()
  setActivePinia(createPinia())
  let lease = { editor: { user, acquiredAt: '2026-10-19T10:00:00Z' }, pendingTakeover: null,
    leaseEpoch: '2', expiresAt: '2099-01-01T00:00:00Z' }
  let persisted = document('saved before action')
  let revision = 'revision-1'
  let token: CollaborationRoomTokenPayload = {
    sub: user.id, username: user.username, displayName: user.displayName,
    roomKey, workspaceId: 'workspace', projectId, pageId, xmlId: 'xml',
    canEdit: true, canForceTakeover: false, persistedRevision: revision,
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
  const toast = vi.fn()
  vi.stubGlobal('useToast', () => ({ add: toast }))
  vi.stubGlobal('useThrottleFn', (callback: unknown) => callback)
  vi.stubGlobal('useDebounceFn', (callback: () => void, delay: number) => {
    let timer: ReturnType<typeof setTimeout>
    return () => {
      clearTimeout(timer)
      timer = setTimeout(callback, delay)
    }
  })
  let receive: (message: { type: string, payload: unknown }) => void = () => {}
  const peer = { id: 'action-peer', send: (raw: string) => receive(JSON.parse(raw)) } as unknown as Peer
  const send = vi.fn((message: { type: string, payload: { roomKey: string, snapshot?: unknown, persistedRevision: string, leaseEpoch: string } }) => {
    if (message.type === 'JOIN_ROOM') collaborationState.joinRoom(peer, token)
    if (message.type === 'SNAPSHOT_UPDATE') collaborationState.applySnapshotUpdate(peer.id, message.payload.roomKey, message.payload.snapshot, message.payload.persistedRevision, message.payload.leaseEpoch)
    return true
  })
  const socketStatus = ref('idle')
  vi.stubGlobal('useRealtimeSocket', () => ({ connectionStatus: socketStatus, connect: vi.fn(), send,
    subscribe: (callback: typeof receive) => { receive = callback } }))
  const images = [{ id: 'image', fileName: 'image.png', filePath: '', variant: 'original' }]
  const xmlFiles = [{ id: 'xml', fileName: 'page.xml', schema: 'PAGE_XML' }]
  const fetch = vi.fn(async (path: string): Promise<unknown> => {
    if (path.startsWith(`/api/projects/${projectId}/pages/${pageId}/annotations/`) && !path.includes('/collaboration/')) {
      const response = structuredClone(persisted)
      await new Promise(resolve => setTimeout(resolve, 20))
      return response
    }
    if (path === imagesPath) return images
    if (path.endsWith('/xml')) return xmlFiles
    if (path.endsWith('/token')) {
      token = { ...token, xmlId: xmlFiles[0]!.id, roomKey: `${projectId}:${pageId}:${xmlFiles[0]!.id}`, persistedRevision: revision }
      return { ...token, user, token: 'token', lease }
    }
    if (path.endsWith('/lease/join')) {
      collaborationState.syncLeaseState(token.roomKey, lease)
      return { roomKey: token.roomKey, lease }
    }
    if (path.endsWith('/lease/release')) return { roomKey, lease }
    if (path === '/api/collaboration/leases/renew') return { renewals: [] }
    return { persistedRevision: revision }
  })
  vi.stubGlobal('$fetch', fetch)
  const store = useEditorStore()
  useEditorDocumentStore().setProjectPages(projectId, [{ id: pageId, projectId, label: 'Page',
    imageVariants: [{ id: 'image', label: 'original', type: 'original', url: '/image.png' }],
    xmlFiles: [{ id: 'xml', fileName: 'page.xml', schema: 'PAGE_XML' }],
    tags: [], resolvedTags: null, locked: false, lockedReason: null }], { markLoaded: true })
  const scope = effectScope()
  const collaboration = useEditorCollaboration()
  const settle = async (delay = 0) => {
    await nextTick()
    await vi.advanceTimersByTimeAsync(delay)
    await nextTick()
  }
  collaborationState.syncLeaseState(roomKey, lease)
  const loading = store.loadPageIntoCanvas(canvasId, projectId, pageId, undefined, { awaitAnnotations: true })
  await settle(20)
  await loading
  scope.run(() => useEditorCanvasCollaboration(canvasId))
  await settle()
  return {
    store, collaboration, fetch, images, xmlFiles, toast, receive, send, socketStatus, settle,
    persist: (custom: string, nextRevision: string) => {
      persisted = document(custom)
      revision = nextRevision
    },
    content: () => convertPcGtsToPageDto(getEditorSession(canvasId)!.document.value!),
    takeover: () => {
      lease = { ...lease, editor: { user: { ...user, id: 'curator' }, acquiredAt: '2026-10-19T10:01:00Z' }, leaseEpoch: '3' }
      collaborationState.syncLeaseState(roomKey, lease)
    },
    cachedSnapshot: () => {
      const viewer = { id: 'new-viewer', send: vi.fn() } as unknown as Peer
      collaborationState.joinRoom(viewer, { ...token, persistedRevision: revision })
      const messages = vi.mocked(viewer.send).mock.calls.map(([raw]) => JSON.parse(String(raw)))
      collaborationState.unregisterPeer(viewer.id)
      return messages.find(message => message.type === 'COLLAB_SNAPSHOT_SYNC')?.payload
    },
    cleanup: async () => {
      scope.stop()
      collaborationState.unregisterPeer(peer.id)
      collaboration.disconnect()
      store.resetEditorState()
      await settle()
    }
  }
}

describe('persisted editor document reloads', () => {
  let editor: Awaited<ReturnType<typeof createEditor>>
  beforeEach(async () => {
    editor = await createEditor()
  })
  afterEach(async () => {
    await editor.cleanup()
    clearAllEditorSessions()
    vi.clearAllTimers()
    vi.useRealTimers()
    vi.unstubAllGlobals()
  })

  it('keeps action output through reconnect and publishes it even without other viewers', async () => {
    editor.persist('action output', 'revision-2')
    const reloading = editor.collaboration.reloadCanvasDocument(canvasId)
    await editor.settle(20)
    expect(await reloading).toBe(true)
    expect(editor.content().custom).toBe('action output')
    expect(editor.content().regions?.map(region => region.id)).toEqual(['action output'])
    expect(editor.collaboration.canEditCanvas(canvasId)).toBe(true)
    expect(editor.send.mock.calls.filter(([message]) => message.type === 'JOIN_ROOM')).toHaveLength(1)
    expect(editor.send.mock.calls.filter(([message]) => message.type === 'LEAVE_ROOM')).toHaveLength(0)
    expect(editor.cachedSnapshot()).toMatchObject({ persistedRevision: 'revision-2', snapshot: { page: { custom: 'action output' } } })

    editor.socketStatus.value = 'connected'
    await editor.settle()
    editor.socketStatus.value = 'disconnected'
    await editor.settle()
    editor.socketStatus.value = 'connected'
    await editor.settle()
    editor.receive({ type: 'COLLAB_SNAPSHOT_SYNC', payload: {
      roomKey, persistedRevision: 'revision-1', leaseEpoch: '2', snapshot: snapshotFromPageDto(document('old snapshot'))
    } })
    expect(editor.content().custom).toBe('action output')
    expect(editor.collaboration.getRoomForCanvas(canvasId)?.viewerSync.resyncRequired).toBe(false)
    editor.receive({ type: 'COLLAB_REVISION_CHANGED', payload: {
      roomKey, persistedRevision: 'revision-1', sourceUserId: user.id
    } })
    expect(editor.collaboration.getRoomForCanvas(canvasId)?.viewerSync.persistedRevision).toBe('revision-2')
    editor.receive({ type: 'COLLAB_SNAPSHOT_UPDATE', payload: {
      roomKey, persistedRevision: 'revision-2', leaseEpoch: '2', snapshot: snapshotFromPageDto(document('current live edit'))
    } })
    expect(editor.content().custom).toBe('current live edit')
  })

  it.each(['navigation', 'reload'] as const)('rejects a delayed %s response and keeps it out of the annotation cache', async (kind) => {
    const stale = deferred<PageDto>()
    const implementation = editor.fetch.getMockImplementation()!
    let deferNextRead = true
    editor.fetch.mockImplementation((path) => {
      if (path === annotationPath && deferNextRead) {
        deferNextRead = false
        return stale.promise
      }
      return implementation(path)
    })
    editor.store.invalidateAnnotationCache(pageId, projectId)
    const older = kind === 'reload'
      ? editor.collaboration.reloadCanvasDocument(canvasId)
      : editor.store.loadPageIntoCanvas(canvasId, projectId, pageId, undefined, { awaitAnnotations: true })
    await editor.settle()
    editor.persist('latest output', 'revision-2')
    const newer = editor.collaboration.reloadCanvasDocument(canvasId)
    await editor.settle(20)
    expect(await newer).toBe(true)
    stale.resolve(document('stale output'))
    await older
    expect(editor.content().custom).toBe('latest output')
    const reads = editor.fetch.mock.calls.filter(([path]) => path === annotationPath).length
    await editor.store.loadPageIntoCanvas(canvasId, projectId, pageId, undefined, { awaitAnnotations: true })
    expect(editor.content().custom).toBe('latest output')
    expect(editor.fetch.mock.calls.filter(([path]) => path === annotationPath)).toHaveLength(reads)
  })

  it('retries if the persisted revision changes during an annotation read', async () => {
    const reloading = editor.collaboration.reloadCanvasDocument(canvasId)
    await editor.settle(5)
    editor.persist('concurrent save', 'revision-2')
    await editor.settle(35)
    expect(await reloading).toBe(true)
    expect(editor.content().custom).toBe('concurrent save')
    expect(editor.collaboration.getRoomForCanvas(canvasId)?.viewerSync.persistedRevision).toBe('revision-2')
    expect(editor.fetch.mock.calls.filter(([path]) => path === annotationPath)).toHaveLength(3)
  })

  it('does not let a response from a closed canvas replace a reopened canvas with the same id', async () => {
    const stale = deferred<PageDto>()
    const implementation = editor.fetch.getMockImplementation()!
    let deferNextRead = true
    editor.fetch.mockImplementation((path) => {
      if (path === annotationPath && deferNextRead) {
        deferNextRead = false
        return stale.promise
      }
      return implementation(path)
    })
    editor.store.invalidateAnnotationCache(pageId, projectId)
    const older = editor.store.loadPageIntoCanvas(canvasId, projectId, pageId, undefined, { awaitAnnotations: true })
    await editor.settle()
    editor.store.unregisterCanvas(canvasId)
    editor.persist('reopened content', 'revision-2')
    const reopened = editor.store.loadPageIntoCanvas(canvasId, projectId, pageId, undefined, { awaitAnnotations: true })
    await editor.settle(20)
    await reopened
    stale.resolve(document('closed canvas content'))
    expect(await older).toBeNull()
    expect(editor.content().custom).toBe('reopened content')
    expect(editor.store.getCanvas(canvasId)?.isLoadingAnnotations).toBe(false)
  })

  it('keeps the document and accepted revision when reloading fails', async () => {
    const implementation = editor.fetch.getMockImplementation()!
    editor.fetch.mockImplementation(path => path === annotationPath ? Promise.reject(new Error('unavailable')) : implementation(path))
    vi.spyOn(console, 'error').mockImplementation(() => {})
    expect(await editor.collaboration.reloadCanvasDocument(canvasId)).toBe(false)
    expect(editor.content().custom).toBe('saved before action')
    expect(editor.collaboration.getRoomForCanvas(canvasId)?.viewerSync.persistedRevision).toBe('revision-1')
    expect(editor.store.getCanvas(canvasId)?.isLoadingAnnotations).toBe(false)
    expect(editor.toast).toHaveBeenCalledWith(expect.objectContaining({ title: 'Could not reload page' }))
    vi.restoreAllMocks()
  })

  it('keeps the latest metadata and logical image variant when metadata requests overlap', async () => {
    const staleImages = deferred<typeof editor.images>()
    const implementation = editor.fetch.getMockImplementation()!
    let deferNextImages = true
    editor.fetch.mockImplementation((path) => {
      if (path === imagesPath && deferNextImages) {
        deferNextImages = false
        return staleImages.promise
      }
      return implementation(path)
    })
    const older = editor.collaboration.reloadCanvasDocument(canvasId, { refreshPageData: true })
    await editor.settle()
    editor.images[0]!.id = 'replacement-image'
    editor.persist('latest action', 'revision-2')
    const newer = editor.collaboration.reloadCanvasDocument(canvasId, { refreshPageData: true })
    await editor.settle(20)
    expect(await newer).toBe(true)
    staleImages.resolve([{ ...editor.images[0]!, id: 'stale-image' }])
    expect(await older).toBe(false)
    expect(editor.store.getCanvas(canvasId)?.imageVariantId).toBe('replacement-image')
    expect(editor.store.getPage(pageId, projectId)?.imageVariants[0]?.id).toBe('replacement-image')
    expect(editor.content().custom).toBe('latest action')
  })

  it('joins the replacement XML resource after an action replaces its file id', async () => {
    editor.xmlFiles[0]!.id = 'replacement-xml'
    editor.persist('replacement XML output', 'revision-2')
    const reloading = editor.collaboration.reloadCanvasDocument(canvasId, { refreshPageData: true })
    await editor.settle(20)
    expect(await reloading).toBe(true)
    await editor.settle()
    expect(editor.content().custom).toBe('replacement XML output')
    expect(editor.store.getCanvas(canvasId)?.xmlFileId).toBe('replacement-xml')
    expect(editor.collaboration.getRoomForCanvas(canvasId)?.identity.xmlId).toBe('replacement-xml')
    expect(editor.collaboration.getRoomForCanvas(canvasId)?.viewerSync.persistedRevision).toBe('revision-2')
  })

  it('ignores a snapshot delivered while reloading and an old lease snapshot after takeover', async () => {
    editor.persist('action output', 'revision-2')
    const older = editor.collaboration.reloadCanvasDocument(canvasId)
    await editor.settle(5)
    editor.receive({ type: 'COLLAB_SNAPSHOT_UPDATE', payload: {
      roomKey, persistedRevision: 'revision-1', leaseEpoch: '2', snapshot: snapshotFromPageDto(document('stale snapshot'))
    } })
    expect(editor.content().custom).toBe('saved before action')
    editor.persist('takeover save', 'revision-3')
    editor.takeover()
    await editor.settle(20)
    expect(await older).toBe(false)
    expect(editor.content().custom).toBe('takeover save')
    expect(editor.collaboration.getRoomForCanvas(canvasId)?.viewerSync.persistedRevision).toBe('revision-3')
    expect(editor.collaboration.canEditCanvas(canvasId)).toBe(false)
    editor.receive({ type: 'COLLAB_SNAPSHOT_UPDATE', payload: {
      roomKey, persistedRevision: 'revision-3', leaseEpoch: '2', snapshot: snapshotFromPageDto(document('old owner snapshot'))
    } })
    expect(editor.content().custom).toBe('takeover save')
  })
})
