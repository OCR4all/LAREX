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
  CollaborationLeaseState,
  CollaborationLeaseResponse,
  CollaborationLeaseOwner,
  CollaborationPresence,
  CollaborationPresenceMessage,
  CollaborationRevisionResponse,
  CollaborationRoomBootstrap,
  CollaborationRoomMember,
  CollaborationRoomSession,
  CollaborationRoomStateMessage,
  CollaborationTakeoverRequest
} from '@/types/collaboration'

type ConnectionStatus = 'idle' | 'connecting' | 'connected' | 'disconnected' | 'error'

const revisionPollers = new Map<string, ReturnType<typeof setInterval>>()
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
const leaseWarningTimeouts = new Map<string, ReturnType<typeof setTimeout>>()
const leaseWarningShownForExpiry = new Map<string, string>()
const pendingInitialXmlCreations = new Map<string, Promise<string | null>>()

const INSTANCE_ALIVE_INTERVAL_MS = 5000
const INSTANCE_STALE_AFTER_MS = 15000
const LEASE_HEARTBEAT_INTERVAL_MS = 10000
const LEASE_EXPIRY_WARNING_MS = 15000

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

function cloneLeaseState(lease: CollaborationLeaseState): CollaborationLeaseState {
  return {
    ...lease,
    editor: lease.editor
      ? {
          ...lease.editor,
          user: { ...lease.editor.user }
        }
      : null,
    pendingTakeover: lease.pendingTakeover
      ? {
          ...lease.pendingTakeover,
          requester: { ...lease.pendingTakeover.requester }
        }
      : null
  }
}

function cloneRooms(value: Record<string, CollaborationRoomSession>): Record<string, CollaborationRoomSession> {
  return Object.fromEntries(Object.entries(value).map(([key, room]) => [key, {
    identity: {
      ...room.identity,
      user: { ...room.identity.user }
    },
    lease: cloneLeaseState(room.lease),
    presence: {
      members: [...room.presence.members]
    },
    viewerSync: {
      ...room.viewerSync
    }
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
  currentRooms: Record<string, CollaborationRoomSession>,
  roomKey: string,
  updater: (room: CollaborationRoomSession) => CollaborationRoomSession
): Record<string, CollaborationRoomSession> {
  const existing = currentRooms[roomKey]
  if (!existing) return currentRooms

  return {
    ...currentRooms,
    [roomKey]: updater({
      identity: {
        ...existing.identity,
        user: { ...existing.identity.user }
      },
      lease: cloneLeaseState(existing.lease),
      presence: {
        members: [...existing.presence.members]
      },
      viewerSync: {
        ...existing.viewerSync
      }
    })
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

function roomHasRemoteParticipants(
  room: CollaborationRoomSession | null | undefined,
  currentUserId?: string | null
): boolean {
  if (!room) return false

  const distinctRemoteUsers = new Set(
    room.presence.members
      .map(member => member.user?.id)
      .filter((userId): userId is string => Boolean(userId) && userId !== currentUserId)
  )

  return distinctRemoteUsers.size > 0
}

export function useEditorCollaboration() {
  const { user, loggedIn } = useUserSession()
  const editorStore = useEditorStore()
  const toast = import.meta.client ? useToast() : null
  const realtime = useRealtimeSocket()

  const connectionStatus = useState<ConnectionStatus>('editor-collaboration.status', () => 'idle')
  const rooms = useState<Record<string, CollaborationRoomSession>>('editor-collaboration.rooms', () => ({}))
  const canvasRooms = useState<Record<string, string>>('editor-collaboration.canvas-rooms', () => ({}))
  const roomLeaseWarnings = useState<Record<string, boolean>>('editor-collaboration.lease-warnings', () => ({}))
  const roomLocallyExpired = useState<Record<string, boolean>>('editor-collaboration.lease-locally-expired', () => ({}))
  const leaseNow = useState<number>('editor-collaboration.lease-now', () => Date.now())
  const currentInstanceId = useState<string>('editor-collaboration.instance-id', () => getBrowserInstanceId())
  const unloadHandlerRegistered = useState<boolean>('editor-collaboration.unload-handler-registered', () => false)
  const messageHandlerRegistered = useState<boolean>('editor-collaboration.message-handler-registered', () => false)
  const statusWatcherRegistered = useState<boolean>('editor-collaboration.status-watcher-registered', () => false)
  const leaseTickerRegistered = useState<boolean>('editor-collaboration.lease-ticker-registered', () => false)

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

  if (import.meta.client && !leaseTickerRegistered.value) {
    setInterval(() => {
      leaseNow.value = Date.now()
    }, 1000)
    leaseTickerRegistered.value = true
  }

  const pageLabel = (room: CollaborationRoomSession) => {
    const page = editorStore.getPage(room.identity.pageId, room.identity.projectId)
    const resolvedProjectLabel = page?.projectName?.trim() || room.identity.projectId
    const resolvedPageLabel = page?.label?.trim() || room.identity.pageId
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

  const setLocallyExpired = (roomKey: string, expired: boolean) => {
    if (roomLocallyExpired.value[roomKey] === expired) {
      return
    }

    roomLocallyExpired.value = expired
      ? {
          ...roomLocallyExpired.value,
          [roomKey]: true
        }
      : Object.fromEntries(Object.entries(roomLocallyExpired.value).filter(([key]) => key !== roomKey))
  }

  const notifyLeaseTransition = (
    room: CollaborationRoomSession,
    previousEditor: CollaborationLeaseOwner | null,
    nextEditor: CollaborationLeaseOwner | null,
    previousTakeover: CollaborationTakeoverRequest | null,
    nextTakeover: CollaborationTakeoverRequest | null
  ) => {
    if (!toast) return

    const currentId = currentUserId.value
    const roomPageLabel = pageLabel(room)
    const hasRemoteParticipants = roomHasRemoteParticipants(room, currentId)

    if (nextEditor?.user.id === currentId) {
      setLocallyExpired(room.identity.roomKey, false)
    } else if (previousEditor?.user.id === currentId && nextEditor?.user.id !== currentId) {
      setLocallyExpired(room.identity.roomKey, true)
    }

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
      if (nextEditor?.user.id === currentId && hasRemoteParticipants) {
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

      if (previousEditor?.user.id === currentId && !nextEditor && hasRemoteParticipants) {
        toast.add({
          title: 'Edit lock expired',
          description: `Your edit lock for ${roomPageLabel} expired after the lease heartbeat stopped.`,
          color: 'warning'
        })
      }
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

  const setLeaseExpiringSoon = (roomKey: string, expiringSoon: boolean) => {
    if (roomLeaseWarnings.value[roomKey] === expiringSoon) {
      return
    }

    roomLeaseWarnings.value = expiringSoon
      ? {
          ...roomLeaseWarnings.value,
          [roomKey]: true
        }
      : Object.fromEntries(Object.entries(roomLeaseWarnings.value).filter(([key]) => key !== roomKey))
  }

  const clearLeaseWarningTimer = (roomKey: string) => {
    const timer = leaseWarningTimeouts.get(roomKey)
    if (timer) {
      clearTimeout(timer)
      leaseWarningTimeouts.delete(roomKey)
    }
  }

  const isLocalRoomLeader = (roomKey: string): boolean => {
    return roomLeaderInstances.get(roomKey) === ensureInstanceId()
  }

  const syncLeaseExpiryWarning = (roomKey: string) => {
    clearLeaseWarningTimer(roomKey)

    const room = rooms.value[roomKey]
    const expiresAt = room?.lease.expiresAt ?? null
    if (
      !room
      || !expiresAt
      || room.lease.editor?.user.id !== currentUserId.value
      || !isLocalRoomLeader(roomKey)
      || !roomHasRemoteParticipants(room, currentUserId.value)
    ) {
      setLeaseExpiringSoon(roomKey, false)
      return
    }

    const expiresAtMs = new Date(expiresAt).getTime()
    if (!Number.isFinite(expiresAtMs)) {
      setLeaseExpiringSoon(roomKey, false)
      return
    }

    const warnInMs = expiresAtMs - Date.now() - LEASE_EXPIRY_WARNING_MS
    if (warnInMs <= 0) {
      setLeaseExpiringSoon(roomKey, true)

      if (toast && leaseWarningShownForExpiry.get(roomKey) !== expiresAt) {
        toast.add({
          title: 'Edit lock expiring soon',
          description: `Your edit lock for ${pageLabel(room)} will expire soon unless the lease heartbeat resumes.`,
          color: 'warning'
        })
        leaseWarningShownForExpiry.set(roomKey, expiresAt)
      }
      return
    }

    setLeaseExpiringSoon(roomKey, false)
    leaseWarningTimeouts.set(roomKey, setTimeout(() => {
      syncLeaseExpiryWarning(roomKey)
    }, warnInMs))
  }

  const applyLeaseState = (
    roomKey: string,
    lease: CollaborationRoomBootstrap['lease'] | CollaborationLeaseResponse['lease']
  ) => {
    const previousRoom = rooms.value[roomKey] ?? null
    const previousEditor = previousRoom?.lease.editor ?? null
    const previousTakeover = previousRoom?.lease.pendingTakeover ?? null

    rooms.value = updateRoomState(rooms.value, roomKey, room => ({
      ...room,
      lease: {
        ...room.lease,
        editor: lease.editor,
        pendingTakeover: lease.pendingTakeover,
        leaseEpoch: lease.leaseEpoch,
        leaseOwner: lease.leaseOwner,
        expiresAt: lease.expiresAt ?? null
      }
    }))

    const nextRoom = rooms.value[roomKey] ?? null
    if (previousRoom && nextRoom) {
      notifyLeaseTransition(
        nextRoom,
        previousEditor,
        nextRoom.lease.editor,
        previousTakeover,
        nextRoom.lease.pendingTakeover
      )
    }
    reconcileRoomHeartbeat(roomKey)
    syncLeaseExpiryWarning(roomKey)
  }

  const heartbeatLease = async (roomKey: string) => {
    const room = rooms.value[roomKey]
    if (!room || !room.identity.canEdit || !isLocalRoomLeader(roomKey)) {
      stopLeaseHeartbeat(roomKey)
      return
    }

    try {
      const lease = await $fetch<CollaborationLeaseResponse>(
        `/api/projects/${room.identity.projectId}/pages/${room.identity.pageId}/annotations/${room.identity.xmlId}/collaboration/lease/heartbeat`,
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
    if (!room || !room.identity.canEdit) return

    stopLeaseHeartbeat(roomKey)
    clearLeaseWarningTimer(roomKey)
    setLeaseExpiringSoon(roomKey, false)
    teardownRoomBroadcastChannel(roomKey, true)

    const url = `/api/projects/${room.identity.projectId}/pages/${room.identity.pageId}/annotations/${room.identity.xmlId}/collaboration/lease/release`
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
    if (!room?.identity.canEdit) {
      stopLeaseHeartbeat(roomKey)
      syncLeaseExpiryWarning(roomKey)
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
      syncLeaseExpiryWarning(roomKey)
      return
    }

    stopLeaseHeartbeat(roomKey)
    syncLeaseExpiryWarning(roomKey)
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

  if (!statusWatcherRegistered.value) {
    watch(() => realtime.connectionStatus.value, (status) => {
      connectionStatus.value = Object.keys(rooms.value).length === 0 && status === 'connected'
        ? 'idle'
        : status
      if (status === 'connected') {
        rejoinOpenRooms()
      }
    }, { immediate: true })
    statusWatcherRegistered.value = true
  }

  const sendMessage = (type: string, payload: Record<string, unknown>) => {
    return realtime.send({ type, payload })
  }

  const getRoomKeyForCanvas = (canvasId: string): string | null => {
    return canvasRooms.value[canvasId] ?? null
  }

  const getRoomForCanvas = (canvasId: string): CollaborationRoomSession | null => {
    const roomKey = canvasRooms.value[canvasId]
    return roomKey ? rooms.value[roomKey] ?? null : null
  }

  const canEditCanvas = (canvasId: string): boolean => {
    const room = getRoomForCanvas(canvasId)
    if (!room) return true
    if (!room.identity.canEdit) return false
    if (!room.lease.editor) return false
    return room.lease.editor.user.id === currentUserId.value
  }

  const roomHasOtherViewers = (roomKey: string): boolean => {
    const room = rooms.value[roomKey]
    return roomHasRemoteParticipants(room, currentUserId.value)
  }

  const stopRevisionPolling = (roomKey: string) => {
    const timer = revisionPollers.get(roomKey)
    if (timer) {
      clearInterval(timer)
      revisionPollers.delete(roomKey)
    }
  }

  const startRevisionPolling = (room: CollaborationRoomSession) => {
    if (import.meta.server || revisionPollers.has(room.identity.roomKey)) return

    const poll = async () => {
      const currentRoom = rooms.value[room.identity.roomKey]
      if (!currentRoom) {
        stopRevisionPolling(room.identity.roomKey)
        return
      }

      try {
        const revision = await $fetch<CollaborationRevisionResponse>(
          `/api/projects/${currentRoom.identity.projectId}/pages/${currentRoom.identity.pageId}/annotations/${currentRoom.identity.xmlId}/collaboration/revision`
        )

        const nextRooms = cloneRooms(rooms.value)
        const targetRoom = nextRooms[room.identity.roomKey]
        if (!targetRoom) return

        if (revision.persistedRevision !== targetRoom.viewerSync.persistedRevision) {
          targetRoom.viewerSync.latestPersistedRevision = revision.persistedRevision
          targetRoom.viewerSync.resyncRequired = true
        }

        rooms.value = nextRooms
      } catch (error) {
        console.warn('[editor-collaboration] Revision poll failed:', error)
      }
    }

    revisionPollers.set(room.identity.roomKey, setInterval(() => {
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
    const room = roomKey ? rooms.value[roomKey] : null
    if (!roomKey || !room?.viewerSync.snapshotReady || !canEditCanvas(canvasId)) return false

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
    const room = roomKey ? rooms.value[roomKey] : null
    if (!roomKey || !session?.document.value || canvasRemoteApply.has(canvasId) || !room?.viewerSync.snapshotReady) {
      return
    }

    if (!room || room.viewerSync.resyncRequired || !canEditCanvas(canvasId)) {
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
    const previousEditor = previousRoom?.lease.editor ?? null
    const previousTakeover = previousRoom?.lease.pendingTakeover ?? null

    rooms.value = updateRoomState(rooms.value, payload.roomKey, room => ({
      ...room,
      presence: {
        ...room.presence,
        members: payload.members
      },
      lease: {
        ...room.lease,
        editor: payload.lease.editor,
        pendingTakeover: payload.lease.pendingTakeover,
        leaseOwner: payload.lease.leaseOwner,
        leaseEpoch: payload.lease.leaseEpoch,
        expiresAt: payload.lease.expiresAt ?? null
      }
    }))

    const nextRoom = rooms.value[payload.roomKey] ?? null
    if (previousRoom && nextRoom) {
      notifyLeaseTransition(
        nextRoom,
        previousEditor,
        nextRoom.lease.editor,
        previousTakeover,
        nextRoom.lease.pendingTakeover
      )
    }
    reconcileRoomHeartbeat(payload.roomKey)
    syncLeaseExpiryWarning(payload.roomKey)
  }

  const handlePresenceUpdate = (payload: CollaborationPresenceMessage) => {
    rooms.value = updateRoomState(rooms.value, payload.roomKey, (room) => {
      const memberIndex = room.presence.members.findIndex(member => member.peerId === payload.peerId)
      if (memberIndex < 0) return room

      const nextMembers = [...room.presence.members]
      nextMembers[memberIndex] = {
        ...nextMembers[memberIndex]!,
        lastSeenAt: payload.lastSeenAt,
        presence: payload.presence
      }

      return {
        ...room,
        presence: {
          ...room.presence,
          members: nextMembers
        }
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
    rooms.value = updateRoomState(rooms.value, roomKey, room => ({
      ...room,
      viewerSync: {
        ...room.viewerSync,
        snapshotReady: true
      }
    }))

    if (!hasSnapshotAnnotationPayload(snapshot)) {
      if (hasSnapshotAnnotationPayload(roomSnapshots.get(roomKey))) {
        return
      }

      const seedCandidate = Array.from(roomCanvasIds.get(roomKey) ?? [])
        .find((canvasId) => {
          const canvas = editorStore.canvases?.[canvasId]
          return canEditCanvas(canvasId) && Boolean(canvas?.xmlFileId) && canvas?.isLoadingAnnotations !== true
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
      `/api/projects/${room.identity.projectId}/pages/${room.identity.pageId}/annotations/${room.identity.xmlId}/collaboration/revision`
    )

    const nextRooms = cloneRooms(rooms.value)
    const targetRoom = nextRooms[roomKey]
    if (!targetRoom) return

    targetRoom.viewerSync.persistedRevision = revision.persistedRevision
    targetRoom.viewerSync.latestPersistedRevision = revision.persistedRevision
    targetRoom.viewerSync.resyncRequired = false
    rooms.value = nextRooms
  }

  function rejoinOpenRooms() {
    for (const room of Object.values(rooms.value)) {
      sendMessage('JOIN_ROOM', { token: room.identity.token })
    }
  }

  const connect = () => {
    if (import.meta.server || !loggedIn.value) return
    if (!messageHandlerRegistered.value) {
      realtime.subscribe((message) => {
        try {
          switch (message.type) {
            case 'COLLAB_ROOM_STATE':
              handleRoomState(message.payload as CollaborationRoomStateMessage)
              break
            case 'COLLAB_MEMBER_PRESENCE':
              handlePresenceUpdate(message.payload as unknown as CollaborationPresenceMessage)
              break
            case 'COLLAB_SNAPSHOT_SYNC':
            case 'COLLAB_SNAPSHOT_UPDATE': {
              const payload = message.payload as Record<string, unknown> | undefined
              const roomKey = typeof payload?.roomKey === 'string' ? payload.roomKey : null
              const snapshot = payload?.snapshot
              if (roomKey) {
                handleSnapshotUpdate(roomKey, snapshot ?? createEmptySnapshot())
              }
              break
            }
            case 'COLLAB_RELOAD_REQUIRED': {
              const payload = message.payload as Record<string, unknown> | undefined
              const roomKey = typeof payload?.roomKey === 'string' ? payload.roomKey : null
              if (roomKey) {
                void handleReloadRequired(roomKey)
              }
              break
            }
            case 'COLLAB_CONNECTED':
            case 'COLLAB_JOINED':
            case 'CONNECTED':
            case 'AUTH_ACK':
            case 'PONG':
              break
            case 'COLLAB_ERROR': {
              const payload = message.payload as Record<string, unknown> | undefined
              console.warn('[editor-collaboration]', typeof payload?.message === 'string' ? payload.message : 'Collaboration error')
              break
            }
            default:
              break
          }
        } catch (error) {
          console.error('[editor-collaboration] Failed to process message:', error)
        }
      })
      messageHandlerRegistered.value = true
    }

    realtime.connect()
    if (realtime.connectionStatus.value === 'connected') {
      rejoinOpenRooms()
    }
  }

  const disconnect = () => {
    for (const roomKey of roomHeartbeatTimers.keys()) {
      stopLeaseHeartbeat(roomKey)
      clearLeaseWarningTimer(roomKey)
      setLeaseExpiringSoon(roomKey, false)
    }
    connectionStatus.value = 'idle'
  }

  const ensureRoom = async (
    projectId: string,
    pageId: string,
    xmlId: string,
    canvasId: string
  ): Promise<CollaborationRoomSession | null> => {
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
      identity: {
        roomKey: bootstrap.roomKey,
        workspaceId: bootstrap.workspaceId,
        projectId: bootstrap.projectId,
        pageId: bootstrap.pageId,
        xmlId: bootstrap.xmlId,
        token: bootstrap.token,
        canEdit: bootstrap.canEdit,
        canForceTakeover: bootstrap.canForceTakeover,
        user: bootstrap.user
      },
      lease: {
        ...lease.lease,
        expiresAt: lease.lease.expiresAt ?? null
      },
      presence: {
        members: nextRooms[bootstrap.roomKey]?.presence.members ?? []
      },
      viewerSync: {
        persistedRevision: bootstrap.persistedRevision,
        latestPersistedRevision: null,
        resyncRequired: false,
        snapshotReady: false
      }
    }
    rooms.value = nextRooms
    reconcileRoomHeartbeat(bootstrap.roomKey)
    syncLeaseExpiryWarning(bootstrap.roomKey)

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

  const ensureCanvasRoom = async (canvasId: string): Promise<CollaborationRoomSession | null> => {
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
      clearLeaseWarningTimer(roomKey)
      setLeaseExpiringSoon(roomKey, false)
      teardownRoomBroadcastChannel(roomKey, false)
      leaseWarningShownForExpiry.delete(roomKey)
      roomSnapshots.delete(roomKey)
      roomSnapshotVersions.delete(roomKey)
      roomCanvasIds.delete(roomKey)
      setLocallyExpired(roomKey, false)
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
    if (!roomKey) return
    const room = roomKey ? rooms.value[roomKey] : null
    if (!room) return

    const revision = await $fetch<CollaborationRevisionResponse>(
      `/api/projects/${room.identity.projectId}/pages/${room.identity.pageId}/annotations/${room.identity.xmlId}/collaboration/revision`
    )

    const nextRooms = cloneRooms(rooms.value)
    const targetRoom = nextRooms[roomKey]
    if (!targetRoom) return

    targetRoom.viewerSync.persistedRevision = revision.persistedRevision
    targetRoom.viewerSync.latestPersistedRevision = revision.persistedRevision
    targetRoom.viewerSync.resyncRequired = false
    rooms.value = nextRooms
  }

  const reloadRoomForCanvas = async (canvasId: string) => {
    const roomKey = canvasRooms.value[canvasId]
    if (!roomKey) return null
    const room = roomKey ? rooms.value[roomKey] : null
    if (!room) return null

    await reloadBoundCanvasesForRoom(roomKey)
    await acceptCurrentRevisionForCanvas(canvasId)
    return room
  }

  const getCanvasCollaborators = (canvasId: string): CollaborationRoomMember[] => {
    const room = getRoomForCanvas(canvasId)
    if (!room) return []
    return dedupeMembers(room.presence.members, currentUserId.value)
  }

  const getPageCollaborators = (pageId: string | null | undefined, projectId?: string | null): CollaborationRoomMember[] => {
    if (!pageId) return []

    const matches = Object.values(rooms.value)
      .filter(room => room.identity.pageId === pageId && (!projectId || room.identity.projectId === projectId))
      .flatMap(room => room.presence.members)

    return dedupeMembers(matches, currentUserId.value)
  }

  const getRoomForPage = (pageId: string | null | undefined, projectId?: string | null): CollaborationRoomSession | null => {
    if (!pageId) return null

    return Object.values(rooms.value).find(room => room.identity.pageId === pageId && (!projectId || room.identity.projectId === projectId)) ?? null
  }

  const pageHasRemoteParticipants = (pageId: string | null | undefined, projectId?: string | null): boolean => {
    return roomHasRemoteParticipants(getRoomForPage(pageId, projectId), currentUserId.value)
  }

  const isPageLeaseExpiringSoon = (pageId: string | null | undefined, projectId?: string | null): boolean => {
    const room = getRoomForPage(pageId, projectId)
    if (!room) return false
    return roomLeaseWarnings.value[room.identity.roomKey] === true
  }

  const isCanvasResyncRequired = (canvasId: string): boolean => {
    return getRoomForCanvas(canvasId)?.viewerSync.resyncRequired === true
  }

  const isCollaborativeCanvas = (canvasId: string): boolean => {
    return getRoomForCanvas(canvasId)?.viewerSync.snapshotReady === true
  }

  const getCanvasEditor = (canvasId: string): CollaborationLeaseOwner | null => {
    return getRoomForCanvas(canvasId)?.lease.editor ?? null
  }

  const getCanvasPendingTakeover = (canvasId: string): CollaborationTakeoverRequest | null => {
    return getRoomForCanvas(canvasId)?.lease.pendingTakeover ?? null
  }

  const canForceTakeoverCanvas = (canvasId: string): boolean => {
    return getRoomForCanvas(canvasId)?.identity.canForceTakeover === true
  }

  const isCanvasLeaseExpiringSoon = (canvasId: string): boolean => {
    const roomKey = getRoomKeyForCanvas(canvasId)
    return roomKey ? roomLeaseWarnings.value[roomKey] === true : false
  }

  const getCanvasLeaseExpiresAt = (canvasId: string): string | null => {
    return getRoomForCanvas(canvasId)?.lease.expiresAt ?? null
  }

  const getCanvasSecondsUntilExpiry = (canvasId: string): number | null => {
    const room = getRoomForCanvas(canvasId)
    if (!room?.lease.expiresAt || room.lease.editor?.user.id !== currentUserId.value) {
      return null
    }

    const expiresAtMs = new Date(room.lease.expiresAt).getTime()
    if (!Number.isFinite(expiresAtMs)) {
      return null
    }

    return Math.max(0, Math.ceil((expiresAtMs - leaseNow.value) / 1000))
  }

  const hasCanvasLeaseExpiredLocally = (canvasId: string): boolean => {
    const roomKey = getRoomKeyForCanvas(canvasId)
    return roomKey ? roomLocallyExpired.value[roomKey] === true : false
  }

  const canReclaimCanvasEdit = (canvasId: string): boolean => {
    const room = getRoomForCanvas(canvasId)
    const roomKey = getRoomKeyForCanvas(canvasId)
    if (!room || !roomKey) return false

    return room.identity.canEdit
      && !room.lease.editor
      && roomLocallyExpired.value[roomKey] === true
  }

  const reclaimCanvasEdit = async (canvasId: string): Promise<boolean> => {
    const reclaimed = await requestTakeover(canvasId, false)
    if (reclaimed) {
      const roomKey = getRoomKeyForCanvas(canvasId)
      if (roomKey) {
        setLocallyExpired(roomKey, false)
      }
    }
    return reclaimed
  }

  const requestTakeover = async (canvasId: string, force = false): Promise<boolean> => {
    const roomKey = getRoomKeyForCanvas(canvasId)
    const room = roomKey ? rooms.value[roomKey] : null
    if (!roomKey || !room) return false

    try {
      const response = await $fetch<CollaborationLeaseResponse>(
        `/api/projects/${room.identity.projectId}/pages/${room.identity.pageId}/annotations/${room.identity.xmlId}/collaboration/lease/request`,
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
        `/api/projects/${room.identity.projectId}/pages/${room.identity.pageId}/annotations/${room.identity.xmlId}/collaboration/lease/respond`,
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
    pageHasRemoteParticipants,
    isPageLeaseExpiringSoon,
    isCanvasResyncRequired,
    isCollaborativeCanvas,
    isCanvasLeaseExpiringSoon,
    getCanvasLeaseExpiresAt,
    getCanvasSecondsUntilExpiry,
    hasCanvasLeaseExpiredLocally,
    canReclaimCanvasEdit,
    canEditCanvas,
    canForceTakeoverCanvas,
    getCanvasEditor,
    getCanvasPendingTakeover,
    reclaimCanvasEdit,
    requestTakeover,
    respondToTakeover
  }
}
