import { afterEach, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia, storeToRefs } from 'pinia'
import { nextTick, ref, type Ref } from 'vue'
import { useEditorStore } from '@/stores/editor/editor.store'
import { useEditorDocumentStore } from '@/stores/editor/editor.document.store'
import { clearAllEditorSessions, getEditorSession } from '@/session/editor/editor-session'
import { convertPcGtsToPageDto } from '@/services/editor/page-conversion.service'
import { snapshotFromPageDto } from '@/utils/editor/collaboration-page-doc'
import type { PageDto } from '@/types/page-dto'
import type { CollaborationRoomSession } from '@/types/collaboration'
import { useEditorCollaboration } from '../use-editor-collaboration'

afterEach(() => {
  clearAllEditorSessions()
  vi.clearAllTimers()
  vi.useRealTimers()
  vi.unstubAllGlobals()
})

it.each(['PROJECT', 'DATASET'] as const)('keeps force-saved live annotations through a cached, delayed $0 reload', async (scope) => {
  vi.useFakeTimers()
  setActivePinia(createPinia())
  const canvasId = 'cached-takeover-canvas'
  const projectId = 'cached-takeover-project'
  const pageId = 'page'
  const roomKey = `${projectId}:${pageId}:xml`
  const user = { id: 'curator', username: 'curator', displayName: 'Curator' }
  const previous = { id: 'previous', username: 'previous', displayName: 'Previous' }
  const annotationRoute = scope === 'PROJECT'
    ? { scope, projectId, pageId }
    : { scope, workspaceId: 'workspace', datasetId: 'dataset', itemId: 'item' }
  const room: CollaborationRoomSession = {
    identity: { roomKey, workspaceId: 'workspace', projectId, pageId, xmlId: 'xml',
      annotationRoute, token: 'token', canEdit: true, canForceTakeover: true, user },
    lease: { editor: { user: previous, acquiredAt: '2026-10-16T10:00:00Z' }, pendingTakeover: null,
      leaseEpoch: '2', expiresAt: '2099-01-01T00:00:00Z' },
    presence: { members: [] },
    viewerSync: { persistedRevision: 'old-revision', resyncRequired: false, snapshotReady: true }
  }
  const states = new Map<string, Ref>([
    ['editor-collaboration.rooms', ref({ [roomKey]: room })],
    ['editor-collaboration.canvas-rooms', ref({ [canvasId]: roomKey })],
    ['editor-collaboration.instance-id', ref('curator-instance')]
  ])
  vi.stubGlobal('storeToRefs', storeToRefs)
  vi.stubGlobal('useState', (key: string, init: () => unknown) => {
    if (!states.has(key)) states.set(key, ref(init()))
    return states.get(key)
  })
  vi.stubGlobal('useUserSession', () => ({ user: ref(user), loggedIn: ref(true) }))
  vi.stubGlobal('window', { addEventListener: vi.fn() })
  vi.stubGlobal('BroadcastChannel', undefined)
  vi.stubGlobal('useToast', () => ({ add: vi.fn() }))
  vi.stubGlobal('useThrottleFn', (callback: unknown) => callback)
  vi.stubGlobal('useDebounceFn', (callback: unknown) => callback)
  let handleMessage: (message: { type: string, payload: unknown }) => void = () => {}
  vi.stubGlobal('useRealtimeSocket', () => ({ connectionStatus: ref('idle'), connect: vi.fn(), send: vi.fn(() => true),
    subscribe: (callback: typeof handleMessage) => { handleMessage = callback } }))

  const annotationBasePath = scope === 'PROJECT'
    ? `/api/projects/${projectId}/pages/${pageId}/annotations`
    : '/api/workspaces/workspace/datasets/dataset/items/item/annotations'
  const annotationPath = `${annotationBasePath}/xml`
  let delayReload = false
  let persisted: PageDto = { imageWidth: 100, imageHeight: 100, custom: 'last saved XML' }
  const live: PageDto = { imageWidth: 100, imageHeight: 100, custom: 'unsaved live changes',
    regions: [{ id: 'new-region', kind: 'TextRegion', coords: { points: [[0, 0], [50, 0], [50, 50], [0, 50]] } }] }
  const fetch = vi.fn(async (path: string, options?: { body?: { annotations?: PageDto } }) => {
    if (path === annotationPath) {
      if (delayReload) await new Promise(resolve => setTimeout(resolve, 2000))
      return structuredClone(persisted)
    }
    if (path === '/api/collaboration/leases/renew') return { renewals: [] }
    if (path.endsWith('/lease/request')) {
      persisted = structuredClone(options!.body!.annotations!)
      const lease = { ...room.lease, editor: { user, acquiredAt: '2026-10-16T10:01:00Z' }, leaseEpoch: '3' }
      handleMessage({ type: 'COLLAB_ROOM_STATE', payload: { roomKey, members: [], lease } })
      handleMessage({ type: 'COLLAB_RELOAD_REQUIRED', payload: { roomKey } })
      return { roomKey, lease, outcome: 'GRANTED', message: '' }
    }
    return { persistedRevision: 'saved-revision' }
  })
  vi.stubGlobal('$fetch', fetch)
  const store = useEditorStore()
  const documentStore = useEditorDocumentStore()
  documentStore.setProjectPages(projectId, [{ id: pageId, projectId, label: 'Page',
    imageVariants: [{ id: 'image', label: 'original', url: '/image.png' }],
    xmlFiles: [{ id: 'xml', fileName: 'page.xml', schema: 'PAGE_XML' }],
    annotationContext: { mode: scope === 'PROJECT' ? 'PROJECT' : 'DATASET_COPY', basePath: annotationBasePath, createAllowed: false },
    tags: [], resolvedTags: null, locked: false, lockedReason: null }], { markLoaded: true })
  const collaboration = useEditorCollaboration()
  try {
    await store.loadPageIntoCanvas(canvasId, projectId, pageId, undefined, { awaitAnnotations: true })
    expect(fetch.mock.calls.filter(([path]) => path === annotationPath)).toHaveLength(1)
    const session = getEditorSession(canvasId)!
    collaboration.attachCanvasSession(canvasId, session)
    collaboration.connect()
    handleMessage({ type: 'COLLAB_SNAPSHOT_UPDATE', payload: { roomKey, snapshot: snapshotFromPageDto(live) } })
    expect(convertPcGtsToPageDto(session.document.value!).custom).toBe(live.custom)

    delayReload = true
    expect(await collaboration.requestTakeover(canvasId, true, 'save')).toBe('GRANTED')
    await nextTick()
    await vi.advanceTimersByTimeAsync(0)

    expect(persisted.custom).toBe(live.custom)
    expect(collaboration.getRoomForCanvas(canvasId)?.viewerSync.persistedRevision).toBe('old-revision')
    expect(store.getCanvas(canvasId)?.isLoadingAnnotations).toBe(true)
    await vi.advanceTimersByTimeAsync(2000)
    expect(collaboration.getRoomForCanvas(canvasId)?.viewerSync.persistedRevision).toBe('saved-revision')
    expect(fetch.mock.calls.filter(([path]) => path === annotationPath)).toHaveLength(2)
    expect(convertPcGtsToPageDto(session.document.value!).custom).toBe(live.custom)
    expect(convertPcGtsToPageDto(session.document.value!).regions?.map(region => region.id)).toEqual(['new-region'])
  } finally {
    collaboration.leaveCanvasRoom(canvasId)
    collaboration.disconnect()
    store.resetEditorState()
  }
})
