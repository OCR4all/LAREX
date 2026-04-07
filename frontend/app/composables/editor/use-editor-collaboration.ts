import { getEditorSession, type EditorSession } from '@/session/editor/editor-session'
import { collectPolygonsFromPcGts, collectPolylinesFromPcGts } from '@/stores/editor/editor.collectors.store'
import { useEditorStore } from '@/stores/editor/editor.store'
import { visibilityService } from '@/services/editor/visibility-service'
import { convertPageDtoToPcGts, convertPcGtsToPageDto } from '@/services/editor/page-conversion.service'
import {
  createEmptySnapshot,
  snapshotFromPageDto,
  snapshotToPageDto,
  type CollaborationPageSnapshot
} from '@/utils/editor/collaboration-page-doc'
import type {
  CollaborationLeaseResponse,
  CollaborationLeaseOwner,
  CollaborationPresence,
  CollaborationPresenceMessage,
  CollaborationRevisionResponse,
  CollaborationRoomBootstrap,
  CollaborationRoomMember,
  CollaborationRoomState,
  CollaborationRoomStateMessage,
  CollaborationTakeoverRequest
} from '@/types/collaboration'

type ConnectionStatus = 'idle' | 'connecting' | 'connected' | 'disconnected' | 'error'

const revisionPollers = new Map<string, ReturnType<typeof setInterval>>()
const roomReady = new Set<string>()
const roomSnapshots = new Map<string, CollaborationPageSnapshot>()
const roomSnapshotVersions = new Map<string, number>()
const roomCanvasIds = new Map<string, Set<string>>()
const canvasSessions = new Map<string, EditorSession>()
const canvasWatchStops = new Map<string, () => void>()
const canvasSyncRunners = new Map<string, () => void>()
const canvasRemoteApply = new Set<string>()
const canvasSnapshotVersions = new Map<string, number>()
const canvasSyncSuspended = new Set<string>()
const canvasPendingSync = new Set<string>()
const lastPresencePayloads = new Map<string, string>()
const roomHeartbeatTimers = new Map<string, ReturnType<typeof setInterval>>()
const roomBroadcastChannels = new Map<string, BroadcastChannel>()
const roomBroadcastIntervals = new Map<string, ReturnType<typeof setInterval>>()
const roomKnownInstances = new Map<string, Map<string, number>>()
const roomLeaderInstances = new Map<string, string | null>()
const pendingInitialXmlCreations = new Map<string, Promise<string | null>>()

const INSTANCE_ALIVE_INTERVAL_MS = 5000
const INSTANCE_STALE_AFTER_MS = 15000
const LEASE_HEARTBEAT_INTERVAL_MS = 10000

function getBrowserInstanceId(): string {
  if (import.meta.server) return ''

  const storageKey = 'editor-collaboration.instance-id'
  const existing = window.sessionStorage.getItem(storageKey)
  if (existing) return existing

  const next = typeof crypto !== 'undefined' && typeof crypto.randomUUID === 'function'
    ? crypto.randomUUID()
    : `instance-${Date.now()}-${Math.random().toString(36).slice(2, 10)}`

  window.sessionStorage.setItem(storageKey, next)
  return next
}

function cloneRooms(value: Record<string, CollaborationRoomState>): Record<string, CollaborationRoomState> {
  return Object.fromEntries(Object.entries(value).map(([key, room]) => [key, {
    ...room,
    members: [...room.members],
    editor: room.editor
      ? {
          ...room.editor,
          user: { ...room.editor.user }
        }
      : null,
    pendingTakeover: room.pendingTakeover
      ? {
          ...room.pendingTakeover,
          requester: { ...room.pendingTakeover.requester }
        }
      : null
  }]))
}

function dedupeMembers(members: CollaborationRoomMember[], currentUserId?: string | null): CollaborationRoomMember[] {
  const byUserId = new Map<string, CollaborationRoomMember>()

  for (const member of members) {
    if (!member.user?.id) continue
    if (currentUserId && member.user.id === currentUserId) continue

    const existing = byUserId.get(member.user.id)
    const existingUpdatedAt = existing?.presence?.updatedAt ?? existing?.lastSeenAt ?? ''
    const currentUpdatedAt = member.presence?.updatedAt ?? member.lastSeenAt ?? ''

    if (!existing || currentUpdatedAt >= existingUpdatedAt) {
      byUserId.set(member.user.id, member)
    }
  }

  return Array.from(byUserId.values())
}

function updateRoomState(
  currentRooms: Record<string, CollaborationRoomState>,
  roomKey: string,
  updater: (room: CollaborationRoomState) => CollaborationRoomState
): Record<string, CollaborationRoomState> {
  const existing = currentRooms[roomKey]
  if (!existing) return currentRooms

  return {
    ...currentRooms,
    [roomKey]: updater(existing)
  }
}

function closeActiveConnection(ws: WebSocket | null, code = 1000, reason = 'client disconnect') {
  if (!ws) return

  try {
    if (ws.readyState === WebSocket.OPEN || ws.readyState === WebSocket.CONNECTING) {
      ws.close(code, reason)
    }
  } catch {
    // Ignore browser teardown edge cases.
  }
}

function rebuildSessionDocument(session: EditorSession, source: ReturnType<typeof convertPageDtoToPcGts>) {
  session.document.value = source
  session.spatialIndex.rebuildPolygonIndex(collectPolygonsFromPcGts(source))
  session.spatialIndex.rebuildPolylineIndex(collectPolylinesFromPcGts(source))
  visibilityService.clearCache()
}

function hasSnapshotAnnotationPayload(snapshot: CollaborationPageSnapshot | null | undefined): boolean {
  if (!snapshot) return false

  return snapshot.rootRegionIds.length > 0
    || Object.keys(snapshot.regions).length > 0
    || Object.keys(snapshot.textLines).length > 0
    || Object.keys(snapshot.relations).length > 0
    || Boolean(snapshot.readingOrder)
}

export function useEditorCollaboration() {
  const { user, loggedIn } = useUserSession()
  const editorStore = useEditorStore()
  const toast = import.meta.client ? useToast() : null

  const wsConnection = useState<WebSocket | null>('editor-collaboration.ws', () => null)
  const connectionStatus = useState<ConnectionStatus>('editor-collaboration.status', () => 'idle')
  const reconnectTimeout = useState<ReturnType<typeof setTimeout> | null>('editor-collaboration.reconnectTimeout', () => null)
  const shouldReconnect = useState<boolean>('editor-collaboration.shouldReconnect', () => true)
  const rooms = useState<Record<string, CollaborationRoomState>>('editor-collaboration.rooms', () => ({}))
  const canvasRooms = useState<Record<string, string>>('editor-collaboration.canvas-rooms', () => ({}))
  const currentInstanceId = useState<string>('editor-collaboration.instance-id', () => getBrowserInstanceId())
  const unloadHandlerRegistered = useState<boolean>('editor-collaboration.unload-handler-registered', () => false)

  const ensureInstanceId = (): string => {
    if (currentInstanceId.value) {
      return currentInstanceId.value
    }

    const nextInstanceId = getBrowserInstanceId()
    currentInstanceId.value = nextInstanceId
    return nextInstanceId
  }

  if (import.meta.client && !currentInstanceId.value) {
    currentInstanceId.value = getBrowserInstanceId()
  }

  const currentUserId = computed(() => {
    const value = user.value as { id?: string, sub?: string } | null | undefined
    return value?.id ?? value?.sub ?? null
  })

  const pageLabel = (room: CollaborationRoomState) => {
    const page = editorStore.getPage(room.pageId, room.projectId)
    const resolvedProjectLabel = page?.projectName?.trim() || room.projectId
    const resolvedPageLabel = page?.label?.trim() || room.pageId
    return `${resolvedProjectLabel} / ${resolvedPageLabel}`
  }

  const sameEditor = (left: CollaborationLeaseOwner | null, right: CollaborationLeaseOwner | null) => {
    return (left?.user.id ?? null) === (right?.user.id ?? null)
  }

  const sameTakeover = (left: CollaborationTakeoverRequest | null, right: CollaborationTakeoverRequest | null) => {
    return (left?.requester.id ?? null) === (right?.requester.id ?? null)
      && (left?.requestedAt ?? null) === (right?.requestedAt ?? null)
      && (left?.force ?? false) === (right?.force ?? false)
  }

  const notifyLeaseTransition = (
    room: CollaborationRoomState,
    previousEditor: CollaborationLeaseOwner | null,
    nextEditor: CollaborationLeaseOwner | null,
    previousTakeover: CollaborationTakeoverRequest | null,
    nextTakeover: CollaborationTakeoverRequest | null
  ) => {
    if (!toast) return

    const currentId = currentUserId.value
    const roomPageLabel = pageLabel(room)

    if (!sameTakeover(previousTakeover, nextTakeover) && nextTakeover && previousEditor?.user.id === currentId) {
      toast.add({
        title: 'Edit request received',
        description: `${nextTakeover.requester.displayName} requested edit access for ${roomPageLabel}.`,
        color: 'info'
      })
      return
    }

    if (previousTakeover && !nextTakeover && previousTakeover.requester.id === currentId) {
      if (nextEditor?.user.id === currentId) {
        toast.add({
          title: 'Edit access granted',
          description: `You can now edit ${roomPageLabel}.`,
          color: 'success'
        })
      } else {
        toast.add({
          title: 'Edit request declined',
          description: `Your edit request for ${roomPageLabel} was declined.`,
          color: 'warning'
        })
      }
      return
    }

    if (!sameEditor(previousEditor, nextEditor)) {
      if (nextEditor?.user.id === currentId) {
        toast.add({
          title: 'Edit lock acquired',
          description: `You now hold the edit lock for ${roomPageLabel}.`,
          color: 'success'
        })
        return
      }

      if (previousEditor?.user.id === currentId && nextEditor?.user.id && nextEditor.user.id !== currentId) {
        toast.add({
          title: 'Edit lock transferred',
          description: `${nextEditor.user.displayName} is now editing ${roomPageLabel}.`,
          color: 'info'
        })
        return
      }

      if (previousEditor?.user.id === currentId && !nextEditor) {
        toast.add({
          title: 'Edit lock expired',
          description: `Your edit lock for ${roomPageLabel} expired after the lease heartbeat stopped.`,
          color: 'warning'
        })
      }
    }
  }

  const clearReconnectTimeout = () => {
    if (reconnectTimeout.value) {
      clearTimeout(reconnectTimeout.value)
      reconnectTimeout.value = null
    }
  }

  const roomChannelName = (roomKey: string): string => {
    return `editor-collaboration:${currentUserId.value ?? 'anonymous'}:${roomKey}`
  }

  const pruneKnownInstances = (roomKey: string) => {
    const known = roomKnownInstances.get(roomKey)
    if (!known) return

    const cutoff = Date.now() - INSTANCE_STALE_AFTER_MS
    for (const [instanceId, lastSeenAt] of known.entries()) {
      if (lastSeenAt < cutoff) {
        known.delete(instanceId)
      }
    }
  }

  const electLeaderInstance = (roomKey: string): string | null => {
    pruneKnownInstances(roomKey)
    const known = roomKnownInstances.get(roomKey)
    if (!known || known.size === 0) return ensureInstanceId()

    return Array.from(known.keys()).sort()[0] ?? ensureInstanceId()
  }

  const stopLeaseHeartbeat = (roomKey: string) => {
    const timer = roomHeartbeatTimers.get(roomKey)
    if (timer) {
      clearInterval(timer)
      roomHeartbeatTimers.delete(roomKey)
    }
  }

  const applyLeaseState = (
    roomKey: string,
    lease: CollaborationRoomBootstrap['lease'] | CollaborationLeaseResponse['lease']
  ) => {
    const previousRoom = rooms.value[roomKey] ?? null
    const previousEditor = previousRoom?.editor ?? null
    const previousTakeover = previousRoom?.pendingTakeover ?? null

    rooms.value = updateRoomState(rooms.value, roomKey, room => ({
      ...room,
      editor: lease.editor,
      pendingTakeover: lease.pendingTakeover,
      leaseEpoch: lease.leaseEpoch
    }))

    const nextRoom = rooms.value[roomKey] ?? null
    if (previousRoom && nextRoom) {
      notifyLeaseTransition(
        nextRoom,
        previousEditor,
        lease.editor,
        previousTakeover,
        lease.pendingTakeover
      )
    }
    reconcileRoomHeartbeat(roomKey)
  }

  const heartbeatLease = async (roomKey: string) => {
    const room = rooms.value[roomKey]
    if (!room || !room.canEdit || room.editor?.user.id !== currentUserId.value) {
      stopLeaseHeartbeat(roomKey)
      return
    }

    try {
      const lease = await $fetch<CollaborationLeaseResponse>(
        `/api/projects/${room.projectId}/pages/${room.pageId}/annotations/${room.xmlId}/collaboration/lease/heartbeat`,
        {
          method: 'POST',
          body: { instanceId: ensureInstanceId() }
        }
      )
      applyLeaseState(roomKey, lease.lease)
    } catch (error) {
      console.warn('[editor-collaboration] Lease heartbeat failed:', error)
    }
  }

  const startLeaseHeartbeat = (roomKey: string) => {
    if (roomHeartbeatTimers.has(roomKey)) return

    roomHeartbeatTimers.set(roomKey, setInterval(() => {
      void heartbeatLease(roomKey)
    }, LEASE_HEARTBEAT_INTERVAL_MS))

    void heartbeatLease(roomKey)
  }

  const setupRoomBroadcastChannel = (roomKey: string) => {
    if (import.meta.server || typeof BroadcastChannel === 'undefined' || !currentUserId.value) return
    if (roomBroadcastChannels.has(roomKey)) return

    const channel = new BroadcastChannel(roomChannelName(roomKey))
    const instanceId = ensureInstanceId()
    const known = new Map<string, number>([[instanceId, Date.now()]])
    roomKnownInstances.set(roomKey, known)

    channel.onmessage = (event: MessageEvent<{ type?: string, instanceId?: string }>) => {
      const message = event.data
      if (!message?.instanceId) return

      if (message.type === 'leave') {
        known.delete(message.instanceId)
      } else {
        known.set(message.instanceId, Date.now())
      }
      reconcileRoomHeartbeat(roomKey)
    }

    roomBroadcastChannels.set(roomKey, channel)
    channel.postMessage({ type: 'announce', instanceId })

    roomBroadcastIntervals.set(roomKey, setInterval(() => {
      const aliveInstanceId = ensureInstanceId()
      known.set(aliveInstanceId, Date.now())
      channel.postMessage({ type: 'alive', instanceId: aliveInstanceId })
      reconcileRoomHeartbeat(roomKey)
    }, INSTANCE_ALIVE_INTERVAL_MS))
  }

  const teardownRoomBroadcastChannel = (roomKey: string, announceLeave = true) => {
    const channel = roomBroadcastChannels.get(roomKey)
    if (channel && announceLeave) {
      try {
        channel.postMessage({ type: 'leave', instanceId: ensureInstanceId() })
      } catch {
        // Ignore teardown edge cases.
      }
    }

    const interval = roomBroadcastIntervals.get(roomKey)
    if (interval) {
      clearInterval(interval)
      roomBroadcastIntervals.delete(roomKey)
    }

    if (channel) {
      channel.close()
      roomBroadcastChannels.delete(roomKey)
    }

    roomKnownInstances.delete(roomKey)
    roomLeaderInstances.delete(roomKey)
  }

  async function releaseLeaseForRoom(roomKey: string, keepalive = false) {
    const room = rooms.value[roomKey]
    if (!room || !room.canEdit) return

    stopLeaseHeartbeat(roomKey)
    teardownRoomBroadcastChannel(roomKey, true)

    const url = `/api/projects/${room.projectId}/pages/${room.pageId}/annotations/${room.xmlId}/collaboration/lease/release`
    const payload = JSON.stringify({ instanceId: ensureInstanceId() })

    if (keepalive && typeof navigator !== 'undefined' && typeof navigator.sendBeacon === 'function') {
      navigator.sendBeacon(url, new Blob([payload], { type: 'application/json' }))
      return
    }

    try {
      const response = await $fetch<CollaborationLeaseResponse>(url, {
        method: 'POST',
        body: { instanceId: ensureInstanceId() }
      })
      applyLeaseState(roomKey, response.lease)
    } catch (error) {
      console.warn('[editor-collaboration] Failed to release lease:', error)
    }
  }

  const reconcileRoomHeartbeat = (roomKey: string) => {
    const room = rooms.value[roomKey]
    if (!room?.canEdit) {
      stopLeaseHeartbeat(roomKey)
      return
    }

    if (import.meta.client && typeof BroadcastChannel !== 'undefined') {
      setupRoomBroadcastChannel(roomKey)
    }

    const leaderInstance = typeof BroadcastChannel === 'undefined'
      ? ensureInstanceId()
      : electLeaderInstance(roomKey)

    roomLeaderInstances.set(roomKey, leaderInstance)

    if (leaderInstance === ensureInstanceId()) {
      startLeaseHeartbeat(roomKey)
      return
    }

    stopLeaseHeartbeat(roomKey)
  }

  if (import.meta.client && !unloadHandlerRegistered.value) {
    const releaseAllRooms = () => {
      const uniqueRoomKeys = [...new Set(Object.values(canvasRooms.value))]
      for (const roomKey of uniqueRoomKeys) {
        void releaseLeaseForRoom(roomKey, true)
      }
    }

    window.addEventListener('pagehide', releaseAllRooms)
    unloadHandlerRegistered.value = true
  }

  const sendMessage = (type: string, payload: Record<string, unknown>) => {
    const ws = wsConnection.value
    if (!ws || ws.readyState !== WebSocket.OPEN) return false

    ws.send(JSON.stringify({ type, payload }))
    return true
  }

  const getRoomKeyForCanvas = (canvasId: string): string | null => {
    return canvasRooms.value[canvasId] ?? null
  }

  const getRoomForCanvas = (canvasId: string): CollaborationRoomState | null => {
    const roomKey = canvasRooms.value[canvasId]
    return roomKey ? rooms.value[roomKey] ?? null : null
  }

  const canEditCanvas = (canvasId: string): boolean => {
    const room = getRoomForCanvas(canvasId)
    if (!room) return true
    if (!room.canEdit) return false
    if (!room.editor) return false
    return room.editor.user.id === currentUserId.value
  }

  const roomHasOtherViewers = (roomKey: string): boolean => {
    const room = rooms.value[roomKey]
    if (!room) return false
    const distinctRemoteUsers = new Set(
      room.members
        .map(member => member.user?.id)
        .filter((userId): userId is string => Boolean(userId) && userId !== currentUserId.value)
    )
    return distinctRemoteUsers.size > 0
  }

  const stopRevisionPolling = (roomKey: string) => {
    const timer = revisionPollers.get(roomKey)
    if (timer) {
      clearInterval(timer)
      revisionPollers.delete(roomKey)
    }
  }

  const startRevisionPolling = (room: CollaborationRoomState) => {
    if (import.meta.server || revisionPollers.has(room.roomKey)) return

    const poll = async () => {
      const currentRoom = rooms.value[room.roomKey]
      if (!currentRoom) {
        stopRevisionPolling(room.roomKey)
        return
      }

      try {
        const revision = await $fetch<CollaborationRevisionResponse>(
          `/api/projects/${currentRoom.projectId}/pages/${currentRoom.pageId}/annotations/${currentRoom.xmlId}/collaboration/revision`
        )

        const nextRooms = cloneRooms(rooms.value)
        const targetRoom = nextRooms[room.roomKey]
        if (!targetRoom) return

        if (revision.persistedRevision !== targetRoom.persistedRevision) {
          targetRoom.latestPersistedRevision = revision.persistedRevision
          targetRoom.resyncRequired = true
        }

        rooms.value = nextRooms
      } catch (error) {
        console.warn('[editor-collaboration] Revision poll failed:', error)
      }
    }

    revisionPollers.set(room.roomKey, setInterval(() => {
      void poll()
    }, 15000))
  }

  const applyRoomSnapshotToBoundCanvases = (roomKey: string) => {
    const snapshot = roomSnapshots.get(roomKey)
    const version = roomSnapshotVersions.get(roomKey)
    const canvasIds = roomCanvasIds.get(roomKey)
    if (!snapshot || version == null || !canvasIds || canvasIds.size === 0) return

    for (const canvasId of canvasIds) {
      if (canvasSnapshotVersions.get(canvasId) === version) continue

      const session = canvasSessions.get(canvasId)
      if (!session) continue

      canvasRemoteApply.add(canvasId)
      try {
        rebuildSessionDocument(session, convertPageDtoToPcGts(snapshotToPageDto(snapshot)))
        canvasSnapshotVersions.set(canvasId, version)
      } finally {
        canvasRemoteApply.delete(canvasId)
      }
    }
  }

  const storeRoomSnapshot = (roomKey: string, snapshot: CollaborationPageSnapshot, sourceCanvasId?: string) => {
    roomSnapshots.set(roomKey, snapshot)
    const nextVersion = (roomSnapshotVersions.get(roomKey) ?? 0) + 1
    roomSnapshotVersions.set(roomKey, nextVersion)
    if (sourceCanvasId) {
      canvasSnapshotVersions.set(sourceCanvasId, nextVersion)
    }
    applyRoomSnapshotToBoundCanvases(roomKey)
  }

  const trySeedRoomFromCanvas = (canvasId: string) => {
    const roomKey = getRoomKeyForCanvas(canvasId)
    if (!roomKey || !roomReady.has(roomKey) || !canEditCanvas(canvasId)) return false

    const session = canvasSessions.get(canvasId)
    const canvas = editorStore.canvases?.[canvasId]
    if (!session?.document.value || !canvas?.xmlFileId || canvas.isLoadingAnnotations) return false

    const existingSnapshot = roomSnapshots.get(roomKey)
    if (hasSnapshotAnnotationPayload(existingSnapshot)) return false

    const nextSnapshot = snapshotFromPageDto(convertPcGtsToPageDto(session.document.value))
    storeRoomSnapshot(roomKey, nextSnapshot, canvasId)
    sendMessage('SNAPSHOT_UPDATE', {
      roomKey,
      snapshot: nextSnapshot
    })
    return true
  }

  const flushCanvasToRoom = (canvasId: string) => {
    const roomKey = getRoomKeyForCanvas(canvasId)
    const session = canvasSessions.get(canvasId)
    if (!roomKey || !session?.document.value || canvasRemoteApply.has(canvasId) || !roomReady.has(roomKey)) {
      return
    }

    const room = rooms.value[roomKey]
    if (!room || room.resyncRequired || !canEditCanvas(canvasId)) {
      canvasPendingSync.delete(canvasId)
      return
    }
    if (!roomHasOtherViewers(roomKey)) {
      canvasPendingSync.delete(canvasId)
      return
    }
    if (canvasSyncSuspended.has(canvasId)) {
      canvasPendingSync.add(canvasId)
      return
    }

    const nextSnapshot = snapshotFromPageDto(convertPcGtsToPageDto(session.document.value))
    canvasPendingSync.delete(canvasId)
    storeRoomSnapshot(roomKey, nextSnapshot, canvasId)
    sendMessage('SNAPSHOT_UPDATE', {
      roomKey,
      snapshot: nextSnapshot
    })
  }

  const attachCanvasSession = (canvasId: string, session: EditorSession) => {
    canvasSessions.set(canvasId, session)

    const roomKey = getRoomKeyForCanvas(canvasId)
    if (roomKey) {
      const currentCanvasIds = roomCanvasIds.get(roomKey) ?? new Set<string>()
      currentCanvasIds.add(canvasId)
      roomCanvasIds.set(roomKey, currentCanvasIds)
    }

    canvasWatchStops.get(canvasId)?.()
    if (!canvasSyncRunners.has(canvasId)) {
      canvasSyncRunners.set(canvasId, useDebounceFn(() => {
        const run = () => {
          flushCanvasToRoom(canvasId)
        }

        if (typeof window !== 'undefined' && 'requestIdleCallback' in window) {
          window.requestIdleCallback(run, { timeout: 1000 })
          return
        }

        setTimeout(run, 0)
      }, 500))
    }

    const stop = watch(session.document, () => {
      canvasSyncRunners.get(canvasId)?.()
    }, { deep: false, flush: 'post' })
    canvasWatchStops.set(canvasId, stop)

    if (roomKey) {
      const snapshot = roomSnapshots.get(roomKey)
      if (hasSnapshotAnnotationPayload(snapshot)) {
        applyRoomSnapshotToBoundCanvases(roomKey)
      } else {
        trySeedRoomFromCanvas(canvasId)
      }
    }
  }

  const detachCanvasSession = (canvasId: string) => {
    canvasWatchStops.get(canvasId)?.()
    canvasWatchStops.delete(canvasId)
    canvasSyncRunners.delete(canvasId)
    canvasSessions.delete(canvasId)
    canvasSnapshotVersions.delete(canvasId)
    canvasRemoteApply.delete(canvasId)
    canvasSyncSuspended.delete(canvasId)
    canvasPendingSync.delete(canvasId)
    lastPresencePayloads.delete(canvasId)
  }

  const setCanvasSyncSuspended = (canvasId: string, suspended: boolean) => {
    if (suspended) {
      canvasSyncSuspended.add(canvasId)
      return
    }

    canvasSyncSuspended.delete(canvasId)
    if (canvasPendingSync.has(canvasId)) {
      canvasSyncRunners.get(canvasId)?.()
    }
  }

  const handleRoomState = (payload: CollaborationRoomStateMessage) => {
    const previousRoom = rooms.value[payload.roomKey] ?? null
    const previousEditor = previousRoom?.editor ?? null
    const previousTakeover = previousRoom?.pendingTakeover ?? null

    rooms.value = updateRoomState(rooms.value, payload.roomKey, room => ({
      ...room,
      members: payload.members,
      editor: payload.editor,
      pendingTakeover: payload.pendingTakeover
    }))

    const nextRoom = rooms.value[payload.roomKey] ?? null
    if (previousRoom && nextRoom) {
      notifyLeaseTransition(
        nextRoom,
        previousEditor,
        payload.editor,
        previousTakeover,
        payload.pendingTakeover
      )
    }
    reconcileRoomHeartbeat(payload.roomKey)
  }

  const handlePresenceUpdate = (payload: CollaborationPresenceMessage) => {
    rooms.value = updateRoomState(rooms.value, payload.roomKey, (room) => {
      const memberIndex = room.members.findIndex(member => member.peerId === payload.peerId)
      if (memberIndex < 0) return room

      const nextMembers = [...room.members]
      nextMembers[memberIndex] = {
        ...nextMembers[memberIndex]!,
        lastSeenAt: payload.lastSeenAt,
        presence: payload.presence
      }

      return {
        ...room,
        members: nextMembers
      }
    })
  }

  const reloadBoundCanvasesForRoom = async (roomKey: string) => {
    const canvasIds = Array.from(roomCanvasIds.get(roomKey) ?? [])
    if (canvasIds.length === 0) return

    roomSnapshots.delete(roomKey)
    roomSnapshotVersions.delete(roomKey)

    await Promise.all(canvasIds.map(async (canvasId) => {
      canvasSnapshotVersions.delete(canvasId)

      const canvas = editorStore.canvases?.[canvasId]
      if (!canvas?.projectId || !canvas.pageId) return

      await editorStore.loadPageIntoCanvas(
        canvasId,
        canvas.projectId,
        canvas.pageId,
        canvas.imageVariantId ?? undefined
      )
    }))
  }

  const handleSnapshotUpdate = (roomKey: string, rawSnapshot: unknown) => {
    if (!rawSnapshot || typeof rawSnapshot !== 'object') return

    const snapshot = rawSnapshot as CollaborationPageSnapshot
    roomReady.add(roomKey)

    if (!hasSnapshotAnnotationPayload(snapshot)) {
      if (hasSnapshotAnnotationPayload(roomSnapshots.get(roomKey))) {
        return
      }

      const seedCandidate = Array.from(roomCanvasIds.get(roomKey) ?? [])
        .find((canvasId) => {
          const canvas = editorStore.canvases?.[canvasId]
          return canEditCanvas(canvasId) && Boolean(canvas?.xmlFileId) && canvas.isLoadingAnnotations !== true
        })

      if (seedCandidate && trySeedRoomFromCanvas(seedCandidate)) {
        return
      }

      return
    }

    storeRoomSnapshot(roomKey, snapshot)
  }

  const handleReloadRequired = async (roomKey: string) => {
    const room = rooms.value[roomKey]
    if (!room) return

    await reloadBoundCanvasesForRoom(roomKey)

    const revision = await $fetch<CollaborationRevisionResponse>(
      `/api/projects/${room.projectId}/pages/${room.pageId}/annotations/${room.xmlId}/collaboration/revision`
    )

    const nextRooms = cloneRooms(rooms.value)
    const targetRoom = nextRooms[roomKey]
    if (!targetRoom) return

    targetRoom.persistedRevision = revision.persistedRevision
    targetRoom.latestPersistedRevision = revision.persistedRevision
    targetRoom.resyncRequired = false
    rooms.value = nextRooms
  }

  const rejoinOpenRooms = () => {
    for (const room of Object.values(rooms.value)) {
      sendMessage('JOIN_ROOM', { token: room.token })
    }
  }

  const connect = () => {
    if (import.meta.server || !loggedIn.value) return

    const existing = wsConnection.value
    if (existing?.readyState === WebSocket.OPEN || existing?.readyState === WebSocket.CONNECTING) {
      return
    }

    shouldReconnect.value = true
    clearReconnectTimeout()
    connectionStatus.value = 'connecting'

    const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:'
    const wsUrl = `${protocol}//${window.location.host}/collaboration/_ws`
    const ws = new WebSocket(wsUrl)

    ws.onopen = () => {
      connectionStatus.value = 'connected'
      rejoinOpenRooms()
    }

    ws.onmessage = (event) => {
      try {
        const message = JSON.parse(event.data) as {
          type?: string
          payload?: CollaborationRoomStateMessage | Record<string, unknown>
        }

        switch (message.type) {
          case 'COLLAB_ROOM_STATE':
            handleRoomState(message.payload as CollaborationRoomStateMessage)
            break
          case 'COLLAB_MEMBER_PRESENCE':
            handlePresenceUpdate(message.payload as CollaborationPresenceMessage)
            break
          case 'COLLAB_SNAPSHOT_SYNC':
          case 'COLLAB_SNAPSHOT_UPDATE': {
            const roomKey = typeof message.payload?.roomKey === 'string' ? message.payload.roomKey : null
            const snapshot = message.payload?.snapshot
            if (roomKey) {
              handleSnapshotUpdate(roomKey, snapshot ?? createEmptySnapshot())
            }
            break
          }
          case 'COLLAB_RELOAD_REQUIRED': {
            const roomKey = typeof message.payload?.roomKey === 'string' ? message.payload.roomKey : null
            if (roomKey) {
              void handleReloadRequired(roomKey)
            }
            break
          }
          case 'COLLAB_CONNECTED':
          case 'COLLAB_JOINED':
          case 'PONG':
            break
          case 'COLLAB_ERROR':
            console.warn('[editor-collaboration]', message.payload?.message ?? 'Collaboration error')
            break
          default:
            break
        }
      } catch (error) {
        console.error('[editor-collaboration] Failed to parse message:', error)
      }
    }

    ws.onclose = () => {
      connectionStatus.value = 'disconnected'
      if (wsConnection.value === ws) {
        wsConnection.value = null
      }

      if (!shouldReconnect.value || Object.keys(rooms.value).length === 0) {
        return
      }

      reconnectTimeout.value = setTimeout(() => {
        reconnectTimeout.value = null
        connect()
      }, 5000)
    }

    ws.onerror = () => {
      connectionStatus.value = 'error'
    }

    wsConnection.value = ws
  }

  const disconnect = () => {
    shouldReconnect.value = false
    clearReconnectTimeout()
    closeActiveConnection(wsConnection.value)
    wsConnection.value = null
    for (const roomKey of roomHeartbeatTimers.keys()) {
      stopLeaseHeartbeat(roomKey)
    }
    connectionStatus.value = 'idle'
  }

  const ensureRoom = async (
    projectId: string,
    pageId: string,
    xmlId: string,
    canvasId: string
  ): Promise<CollaborationRoomState | null> => {
    if (import.meta.server) return null

    const bootstrap = await $fetch<CollaborationRoomBootstrap>(
      `/api/projects/${projectId}/pages/${pageId}/annotations/${xmlId}/collaboration/token`
    )
    const lease = await $fetch<CollaborationLeaseResponse>(
      `/api/projects/${projectId}/pages/${pageId}/annotations/${xmlId}/collaboration/lease/join`,
      {
        method: 'POST',
        body: { instanceId: ensureInstanceId() }
      }
    )

    connect()

    const nextRooms = cloneRooms(rooms.value)
    nextRooms[bootstrap.roomKey] = {
      roomKey: bootstrap.roomKey,
      workspaceId: bootstrap.workspaceId,
      projectId: bootstrap.projectId,
      pageId: bootstrap.pageId,
      xmlId: bootstrap.xmlId,
      token: bootstrap.token,
      persistedRevision: bootstrap.persistedRevision,
      latestPersistedRevision: null,
      canEdit: bootstrap.canEdit,
      canForceTakeover: bootstrap.canForceTakeover,
      leaseEpoch: lease.lease.leaseEpoch,
      user: bootstrap.user,
      members: nextRooms[bootstrap.roomKey]?.members ?? [],
      editor: lease.lease.editor,
      pendingTakeover: lease.lease.pendingTakeover,
      resyncRequired: false
    }
    rooms.value = nextRooms
    reconcileRoomHeartbeat(bootstrap.roomKey)

    canvasRooms.value = {
      ...canvasRooms.value,
      [canvasId]: bootstrap.roomKey
    }

    const currentCanvasIds = roomCanvasIds.get(bootstrap.roomKey) ?? new Set<string>()
    currentCanvasIds.add(canvasId)
    roomCanvasIds.set(bootstrap.roomKey, currentCanvasIds)

    sendMessage('JOIN_ROOM', { token: bootstrap.token })
    startRevisionPolling(nextRooms[bootstrap.roomKey]!)

    const snapshot = roomSnapshots.get(bootstrap.roomKey)
    if (hasSnapshotAnnotationPayload(snapshot)) {
      applyRoomSnapshotToBoundCanvases(bootstrap.roomKey)
    } else {
      trySeedRoomFromCanvas(canvasId)
    }

    return nextRooms[bootstrap.roomKey]!
  }

  const ensureInitialPageXmlForCanvas = async (canvasId: string): Promise<string | null> => {
    const canvas = editorStore.getCanvas(canvasId)
    if (!canvas) return null

    if (canvas.xmlFileId) {
      return canvas.xmlFileId
    }

    const projectId = canvas.projectId
    const pageId = canvas.pageId
    if (!projectId || !pageId || canvas.isLoadingAnnotations) {
      return null
    }

    const page = editorStore.getPage(pageId, projectId)
    if (page?.locked) {
      return null
    }

    const pageKey = `${projectId}:${pageId}`
    const pendingCreation = pendingInitialXmlCreations.get(pageKey)
    if (pendingCreation) {
      return pendingCreation
    }

    const session = canvasSessions.get(canvasId) ?? getEditorSession(canvasId)
    const pcGts = session?.document.value
    if (!pcGts) {
      return null
    }

    const creation = (async () => {
      try {
        const pageDto = convertPcGtsToPageDto(pcGts)
        const created = await $fetch<{ xmlId: string, fileName?: string, schemaVersion?: string }>(
          `/api/projects/${projectId}/pages/${pageId}/annotations`,
          {
            method: 'POST',
            body: pageDto
          }
        )

        canvas.xmlFileId = created.xmlId

        if (page) {
          const hasPageXml = page.xmlFiles.some(xml => xml.id === created.xmlId)
          if (!hasPageXml) {
            page.xmlFiles = [
              ...page.xmlFiles,
              {
                id: created.xmlId,
                fileName: created.fileName || `${page.label}.xml`,
                schema: 'PAGE_XML',
                schemaVersion: created.schemaVersion || undefined,
                variant: 'original'
              }
            ]
            page.xmlFileCount = page.xmlFiles.length
          }
        }

        editorStore.resetCanvasHistoryBaseline(canvasId)
        return created.xmlId
      } catch (error) {
        console.error('[editor-collaboration] Failed to create initial PAGE XML:', error)
        return null
      } finally {
        pendingInitialXmlCreations.delete(pageKey)
      }
    })()

    pendingInitialXmlCreations.set(pageKey, creation)
    return creation
  }

  const ensureCanvasRoom = async (canvasId: string): Promise<CollaborationRoomState | null> => {
    const canvas = editorStore.getCanvas(canvasId)
    if (!canvas?.projectId || !canvas.pageId || canvas.isLoadingAnnotations) {
      return null
    }

    const xmlId = canvas.xmlFileId ?? await ensureInitialPageXmlForCanvas(canvasId)
    if (!xmlId) {
      return null
    }

    return ensureRoom(canvas.projectId, canvas.pageId, xmlId, canvasId)
  }

  const leaveCanvasRoom = (canvasId: string) => {
    const roomKey = canvasRooms.value[canvasId]
    if (!roomKey) {
      detachCanvasSession(canvasId)
      return
    }

    sendMessage('LEAVE_ROOM', { roomKey })

    detachCanvasSession(canvasId)

    const currentCanvasIds = roomCanvasIds.get(roomKey)
    currentCanvasIds?.delete(canvasId)
    if (currentCanvasIds && currentCanvasIds.size === 0) {
      roomCanvasIds.delete(roomKey)
    }

    const { [canvasId]: _removedCanvasRoom, ...nextCanvasRooms } = canvasRooms.value
    canvasRooms.value = nextCanvasRooms

    const stillJoined = Object.values(nextCanvasRooms).some(value => value === roomKey)
    if (!stillJoined) {
      void releaseLeaseForRoom(roomKey)
      const { [roomKey]: _removedRoom, ...nextRooms } = cloneRooms(rooms.value)
      rooms.value = nextRooms
      stopRevisionPolling(roomKey)
      stopLeaseHeartbeat(roomKey)
      teardownRoomBroadcastChannel(roomKey, false)
      roomReady.delete(roomKey)
      roomSnapshots.delete(roomKey)
      roomSnapshotVersions.delete(roomKey)
      roomCanvasIds.delete(roomKey)
    }

    if (Object.keys(nextCanvasRooms).length === 0) {
      disconnect()
    }
  }

  const updatePresence = useThrottleFn((canvasId: string, presence: Partial<CollaborationPresence>) => {
    const roomKey = canvasRooms.value[canvasId]
    if (!roomKey) return

    const serializedPresence = JSON.stringify(presence)
    if (lastPresencePayloads.get(canvasId) === serializedPresence) {
      return
    }

    const sent = sendMessage('UPDATE_PRESENCE', {
      roomKey,
      presence
    })
    if (sent) {
      lastPresencePayloads.set(canvasId, serializedPresence)
    }
  }, 200, true, true)

  const acceptCurrentRevisionForCanvas = async (canvasId: string) => {
    const roomKey = canvasRooms.value[canvasId]
    const room = roomKey ? rooms.value[roomKey] : null
    if (!room) return

    const revision = await $fetch<CollaborationRevisionResponse>(
      `/api/projects/${room.projectId}/pages/${room.pageId}/annotations/${room.xmlId}/collaboration/revision`
    )

    const nextRooms = cloneRooms(rooms.value)
    const targetRoom = nextRooms[roomKey]
    if (!targetRoom) return

    targetRoom.persistedRevision = revision.persistedRevision
    targetRoom.latestPersistedRevision = revision.persistedRevision
    targetRoom.resyncRequired = false
    rooms.value = nextRooms
  }

  const reloadRoomForCanvas = async (canvasId: string) => {
    const roomKey = canvasRooms.value[canvasId]
    const room = roomKey ? rooms.value[roomKey] : null
    if (!room) return null

    await reloadBoundCanvasesForRoom(roomKey)
    await acceptCurrentRevisionForCanvas(canvasId)
    return room
  }

  const getCanvasCollaborators = (canvasId: string): CollaborationRoomMember[] => {
    const room = getRoomForCanvas(canvasId)
    if (!room) return []
    return dedupeMembers(room.members, currentUserId.value)
  }

  const getPageCollaborators = (pageId: string | null | undefined, projectId?: string | null): CollaborationRoomMember[] => {
    if (!pageId) return []

    const matches = Object.values(rooms.value)
      .filter(room => room.pageId === pageId && (!projectId || room.projectId === projectId))
      .flatMap(room => room.members)

    return dedupeMembers(matches, currentUserId.value)
  }

  const isCanvasResyncRequired = (canvasId: string): boolean => {
    return getRoomForCanvas(canvasId)?.resyncRequired === true
  }

  const isCollaborativeCanvas = (canvasId: string): boolean => {
    const roomKey = getRoomKeyForCanvas(canvasId)
    return Boolean(roomKey && roomReady.has(roomKey))
  }

  const getCanvasEditor = (canvasId: string): CollaborationLeaseOwner | null => {
    return getRoomForCanvas(canvasId)?.editor ?? null
  }

  const getCanvasPendingTakeover = (canvasId: string): CollaborationTakeoverRequest | null => {
    return getRoomForCanvas(canvasId)?.pendingTakeover ?? null
  }

  const canForceTakeoverCanvas = (canvasId: string): boolean => {
    return getRoomForCanvas(canvasId)?.canForceTakeover === true
  }

  const requestTakeover = async (canvasId: string, force = false): Promise<boolean> => {
    const roomKey = getRoomKeyForCanvas(canvasId)
    const room = roomKey ? rooms.value[roomKey] : null
    if (!roomKey || !room) return false

    try {
      const response = await $fetch<CollaborationLeaseResponse>(
        `/api/projects/${room.projectId}/pages/${room.pageId}/annotations/${room.xmlId}/collaboration/lease/request`,
        {
          method: 'POST',
          body: { force }
        }
      )

      applyLeaseState(roomKey, response.lease)
      return true
    } catch (error) {
      console.error('[editor-collaboration] Failed to request takeover:', error)
      return false
    }
  }

  const respondToTakeover = async (
    canvasId: string,
    decision: 'accept' | 'decline',
    handoffMode: 'save' | 'discard' = 'save'
  ): Promise<boolean> => {
    const roomKey = getRoomKeyForCanvas(canvasId)
    const room = roomKey ? rooms.value[roomKey] : null
    if (!roomKey || !room) return false

    try {
      const response = await $fetch<CollaborationLeaseResponse>(
        `/api/projects/${room.projectId}/pages/${room.pageId}/annotations/${room.xmlId}/collaboration/lease/respond`,
        {
          method: 'POST',
          body: { decision, handoffMode }
        }
      )

      applyLeaseState(roomKey, response.lease)
      return true
    } catch (error) {
      console.error('[editor-collaboration] Failed to respond to takeover:', error)
      return false
    }
  }

  return {
    connectionStatus: readonly(connectionStatus),
    rooms,
    connect,
    disconnect,
    ensureCanvasRoom,
    ensureRoom,
    leaveCanvasRoom,
    attachCanvasSession,
    setCanvasSyncSuspended,
    updatePresence,
    acceptCurrentRevisionForCanvas,
    reloadRoomForCanvas,
    getRoomForCanvas,
    getCanvasCollaborators,
    getPageCollaborators,
    isCanvasResyncRequired,
    isCollaborativeCanvas,
    canEditCanvas,
    canForceTakeoverCanvas,
    getCanvasEditor,
    getCanvasPendingTakeover,
    requestTakeover,
    respondToTakeover
  }
}
