<script setup lang="ts">
import type { WatchStopHandle } from 'vue'
import { LazyEditorCommentsLabelsOverlay, LazyEditorReadingOrderNumbersOverlay, LazyEditorRelationsLabelsOverlay } from '#components'
import { triangulatePolygon } from '@/utils/editor/hit-detection'
import { clipToWorldCoords, imageToWorld, pixelsToWorld, worldToClipCoords } from '@/utils/editor/coordinates'
import { getPagePanelId, parseCanvasId } from '@/stores/editor/editor.keys'
import { useEditorCollaboration } from '@/composables/editor/use-editor-collaboration'
import { useEditorStore } from '@/stores/editor/editor.store'
import { useEditorUiStore } from '@/stores/editor/editor.ui.store'
import { normalizeRelation } from '@/utils/editor/relations'
import { useEditorSession, usePageVisibilityState } from '@/session/editor/editor-session'
import { useReadingOrderVisualization } from '@/composables/editor/use-reading-order-visualization'
import { useRelationsVisualization } from '@/composables/editor/use-relations-visualization'
import { useCutDrawing } from '@/composables/editor/use-cut-drawing'
import { useMoveInteraction } from '@/composables/editor/use-move-interaction'
import type { ContextMenuItem as EditorContextMenuItem } from '@/composables/editor/use-editor-command'
import { CreateRelationCommand, UpdateRelationCommand } from '@/commands'
import type { Relation } from '@/models/editor'
import type { CommentOverlayLabel } from '@/types/editor/rendering'
import { visibilityService } from '@/services/editor/visibility-service'
import type { CollaborationPresence, CollaborationRoomMember, CollaborationUserIdentity } from '@/types/collaboration'
import { getCollaborationColor } from '@/types/collaboration'
import { getAvatarInitials, resolveManagedProfileAvatarSrc } from '@/utils/avatar'

const CommentsLabelsOverlay = LazyEditorCommentsLabelsOverlay
const ReadingOrderNumbersOverlay = LazyEditorReadingOrderNumbersOverlay
const RelationsLabelsOverlay = LazyEditorRelationsLabelsOverlay

const props = defineProps({
  src: { type: String, required: true },
  canvasId: { type: String, required: true }
})

const editorStore = useEditorStore()
const editorUiStore = useEditorUiStore()
const collaboration = useEditorCollaboration()
const session = useEditorSession(props.canvasId)
const toast = useToast()

const colorMode = useColorMode()

function hexToRgba(hex: string, opacity: number): string {
  const r = parseInt(hex.slice(1, 3), 16)
  const g = parseInt(hex.slice(3, 5), 16)
  const b = parseInt(hex.slice(5, 7), 16)
  return `rgba(${r}, ${g}, ${b}, ${opacity})`
}

const editorBackgroundColor = computed(() => {
  const color = editorUiStore.backgroundColor || (colorMode.value === 'dark' ? '#1c1c19' : '#f0eee6')
  const opacity = editorUiStore.backgroundColor ? editorUiStore.backgroundOpacity : 1
  return hexToRgba(color, opacity)
})

const showCheckerboard = computed(() => editorUiStore.backgroundOpacity < 1)

const isLoadingAnnotations = computed(() => {
  return editorStore.canvases?.[props.canvasId]?.isLoadingAnnotations ?? false
})

const pageId = computed(() => {
  const fromStore = editorStore.canvases?.[props.canvasId]?.pageId
  if (fromStore) return fromStore

  if (typeof props.canvasId === 'string') {
    const parsed = parseCanvasId(props.canvasId)
    if (parsed?.pageId) return parsed.pageId
    if (props.canvasId.startsWith('editor:')) {
      return props.canvasId.slice('editor:'.length)
    }
  }

  return props.canvasId
})

const projectId = computed(() => {
  const fromStore = editorStore.canvases?.[props.canvasId]?.projectId
  if (fromStore) return fromStore
  return parseCanvasId(props.canvasId)?.projectId ?? null
})

const canvasState = computed(() => editorStore.canvases?.[props.canvasId] ?? null)
const xmlFileId = computed(() => canvasState.value?.xmlFileId ?? null)
const selectedRegionId = computed(() => canvasState.value?.selectedRegionId ?? null)
const selectedBaselineId = computed(() => canvasState.value?.selectedBaselineId ?? null)
const remoteCollaborators = computed(() => collaboration.getCanvasCollaborators(props.canvasId))
const canvasEditor = computed(() => collaboration.getCanvasEditor(props.canvasId))
const isCanvasEditable = computed(() => collaboration.canEditCanvas(props.canvasId))
const isCollaborationResyncRequired = computed(() => collaboration.isCanvasResyncRequired(props.canvasId))
const isCanvasLeaseExpiringSoon = computed(() => collaboration.isCanvasLeaseExpiringSoon(props.canvasId))
const canvasLeaseSecondsUntilExpiry = computed(() => collaboration.getCanvasSecondsUntilExpiry(props.canvasId))
const hasCanvasLeaseExpiredLocally = computed(() => collaboration.hasCanvasLeaseExpiredLocally(props.canvasId))
const canReclaimCanvasEdit = computed(() => collaboration.canReclaimCanvasEdit(props.canvasId))
const pendingTakeover = computed(() => collaboration.getCanvasPendingTakeover(props.canvasId))
const canForceTakeover = computed(() => !isCanvasEditable.value && collaboration.canForceTakeoverCanvas(props.canvasId))
const collaborationSyncSuspended = ref(false)
const collaboratorsPopoverOpen = ref(false)

const hiddenPolygonIds = computed(() => usePageVisibilityState(pageId.value).value?.hiddenPolygonIds ?? [])
const hiddenPolylineIds = computed(() => usePageVisibilityState(pageId.value).value?.hiddenPolylineIds ?? [])

interface CollaborationDisplayParticipant {
  key: string
  user: CollaborationUserIdentity
  presence: CollaborationPresence | null
  role: 'editing' | 'viewing'
  isCurrentUser: boolean
}

function latestMember(current: CollaborationRoomMember | undefined, next: CollaborationRoomMember): CollaborationRoomMember {
  if (!current) return next
  return new Date(next.lastSeenAt).getTime() >= new Date(current.lastSeenAt).getTime() ? next : current
}

function avatarSrc(user: CollaborationUserIdentity): string | undefined {
  return resolveManagedProfileAvatarSrc(user.avatar)
}

function avatarFallback(user: CollaborationUserIdentity): string {
  return getAvatarInitials({
    name: user.displayName,
    username: user.username
  })
}

function collaborationAvatarStyle(userId: string): Record<string, string> {
  const color = getCollaborationColor(userId)
  return {
    backgroundColor: hexToRgba(color, 0.18),
    color,
    borderColor: hexToRgba(color, 0.4)
  }
}

function collaboratorActivityLabel(participant: CollaborationDisplayParticipant): string {
  const modeLabel = participant.presence?.uiMode === 'text' ? ' in text view' : ''
  if (participant.role === 'editing') {
    return participant.presence?.active ? `Editing${modeLabel}` : 'Idle'
  }

  return `Viewing${modeLabel}`
}

function collaboratorStatus(participant: CollaborationDisplayParticipant): { label: string, color: 'primary' | 'neutral' } | null {
  if (participant.role !== 'editing') return null

  return participant.presence?.active
    ? { label: 'Live', color: 'primary' }
    : { label: 'Idle', color: 'neutral' }
}

const collaborationRoom = computed(() => collaboration.getRoomForCanvas(props.canvasId))

const collaborationParticipants = computed<CollaborationDisplayParticipant[]>(() => {
  const room = collaborationRoom.value
  if (!room) return []

  const dedupedMembers = new Map<string, CollaborationRoomMember>()
  for (const member of room.presence.members) {
    dedupedMembers.set(member.user.id, latestMember(dedupedMembers.get(member.user.id), member))
  }

  if (!dedupedMembers.has(room.identity.user.id)) {
    dedupedMembers.set(room.identity.user.id, {
      peerId: `self:${room.identity.user.id}`,
      user: room.identity.user,
      presence: null,
      joinedAt: new Date().toISOString(),
      lastSeenAt: new Date().toISOString()
    })
  }

  const editorId = room.lease.editor?.user.id ?? null

  return [...dedupedMembers.values()]
    .map<CollaborationDisplayParticipant>(member => ({
      key: member.user.id,
      user: member.user,
      presence: member.presence,
      role: member.user.id === editorId ? 'editing' : 'viewing',
      isCurrentUser: member.user.id === room.identity.user.id
    }))
    .sort((left, right) => {
      if (left.role !== right.role) return left.role === 'editing' ? -1 : 1
      if (left.isCurrentUser !== right.isCurrentUser) return left.isCurrentUser ? -1 : 1
      return left.user.displayName.localeCompare(right.user.displayName)
    })
})

const collaborationVisibleParticipants = computed(() => collaborationParticipants.value.slice(0, 3))
const editingParticipants = computed(() => collaborationParticipants.value.filter(participant => participant.role === 'editing'))
const viewingParticipants = computed(() => collaborationParticipants.value.filter(participant => participant.role === 'viewing'))
const collaborationSummaryLabel = computed(() => {
  const count = collaborationParticipants.value.length
  return `${count} collaborator${count === 1 ? '' : 's'}`
})
const showCollaboratorsPopover = computed(() => collaborationParticipants.value.length > 1)

const activateEditor = () => editorStore.setActiveCanvas(props.canvasId)

const effectiveUiMode = computed(() => editorStore.effectiveUiMode(props.canvasId))
const renderEnabled = computed(() => effectiveUiMode.value !== 'text')

const constrainToImage = computed(() => editorStore.globalSettings.constrainToImage)
const constrainToParent = computed(() => editorStore.globalSettings.constrainToParent)
const autoSelect = computed(() => editorStore.globalSettings.autoSelect)
const moveWithChildren = computed(() => editorStore.globalSettings.moveWithChildren)
const preventOverlapOnCreate = computed(() => editorStore.globalSettings.preventOverlapOnCreate)
const overlapMinAreaThreshold = computed(() => editorStore.globalSettings.cutMinAreaThreshold)

const currentImageSrc = ref(props.src)

const canvasControls = useCanvasControl(props.canvasId)

const canvas = ref<HTMLCanvasElement | null>(null)
const webglRenderer = useWebglRenderer(canvas)

const editorState = useEditorState(session.spatialIndex)
const {
  polygons,
  polylines,
  spatialIndex,
  selectedPolygonIndex,
  selectedPolylineIndex,
  selectedPolygonIds,
  selectedPolylineIds,
  hoveredPolygonId,
  hoveredPolylineId,
  canvasDimensions,
  actions: stateActions
} = editorState

watch(() => editorStore.regionsByCanvasId(props.canvasId), (newRegions) => {
  polygons.splice(0, polygons.length, ...(newRegions || []))
}, { immediate: true, deep: true })

watch(() => editorStore.baselinesByCanvasId(props.canvasId), (newBaselines) => {
  polylines.splice(0, polylines.length, ...(newBaselines || []))
}, { immediate: true, deep: true })

canvasControls.polygons = polygons
canvasControls.polylines = polylines
canvasControls.spatialIndex = session.spatialIndex
canvasControls.selectedPolygonIndex = selectedPolygonIndex
canvasControls.selectedPolylineIndex = selectedPolylineIndex
canvasControls.hoveredPolygonId = hoveredPolygonId
canvasControls.hoveredPolylineId = hoveredPolylineId
canvasControls.selectedPolygonIds = selectedPolygonIds
canvasControls.selectedPolylineIds = selectedPolylineIds
canvasControls.hiddenPolygonIds = hiddenPolygonIds
canvasControls.hiddenPolylineIds = hiddenPolylineIds
canvasControls.pageId = pageId

const { isDrawingMode, isMoveMode, isPolygonMode, isRectangleMode, isPolylineMode, isCutLineMode, isCutPolygonMode, isCutRectangleMode, isCutMode } = canvasControls

const mouseInteraction = useMouseInteraction()
const view = mouseInteraction.view

const emitPresence = useThrottleFn(() => {
  const targetProjectId = projectId.value
  const targetPageId = pageId.value
  const targetXmlId = xmlFileId.value
  if (!targetProjectId || !targetPageId || !targetXmlId) return

  if (!isCanvasEditable.value) {
    collaboration.updatePresence(props.canvasId, {
      projectId: targetProjectId,
      pageId: targetPageId,
      xmlId: targetXmlId,
      panelId: getPagePanelId(targetProjectId, targetPageId),
      canvasId: props.canvasId,
      variantId: canvasState.value?.imageVariantId ?? null,
      active: editorStore.activeCanvasId === props.canvasId
    })
    return
  }

  if (collaborationSyncSuspended.value) {
    return
  }

  let cursor = null as { x: number, y: number } | null
  const canvasElement = canvas.value as HTMLCanvasElement | null
  const position = mouseInteraction.actionState.position
  if (canvasElement && Number.isFinite(position.x) && Number.isFinite(position.y)) {
    const rect = canvasElement.getBoundingClientRect()
    const withinCanvas = position.x >= rect.left
      && position.x <= rect.right
      && position.y >= rect.top
      && position.y <= rect.bottom

    if (withinCanvas && canvasElement.clientWidth > 0 && canvasElement.clientHeight > 0) {
      const clipX = ((position.x - rect.left) / canvasElement.clientWidth) * 2 - 1
      const clipY = -(((position.y - rect.top) / canvasElement.clientHeight) * 2 - 1)
      cursor = clipToWorldCoords({ x: clipX, y: clipY }, view, aspectRatioScale.value)
    }
  }

  collaboration.updatePresence(props.canvasId, {
    projectId: targetProjectId,
    pageId: targetPageId,
    xmlId: targetXmlId,
    panelId: getPagePanelId(targetProjectId, targetPageId),
    canvasId: props.canvasId,
    variantId: canvasState.value?.imageVariantId ?? null,
    uiMode: editorStore.effectiveUiMode(props.canvasId),
    selectionId: selectedRegionId.value ?? selectedBaselineId.value,
    selectionKind: selectedRegionId.value ? 'region' : (selectedBaselineId.value ? 'baseline' : null),
    viewport: {
      zoom: view.zoom,
      offsetX: view.offsetX,
      offsetY: view.offsetY
    },
    cursor,
    active: editorStore.activeCanvasId === props.canvasId
  })
}, 200, true, true)

const aspectRatioScale = computed(() => {
  const gl = webglRenderer.gl()
  const pageOrientation = session.document.value?.page?.orientation
  const orientationValue = Number(pageOrientation)
  const orientationDegrees = isFinite(orientationValue) ? orientationValue : 0
  const radians = -orientationDegrees * (Math.PI / 180)
  const rotationCos = Math.cos(radians)
  const rotationSin = Math.sin(radians)
  const glCanvas = gl?.canvas instanceof HTMLCanvasElement ? gl.canvas : null
  const canvasWidth = canvasDimensions.value.width || glCanvas?.clientWidth || 0
  const canvasHeight = canvasDimensions.value.height || glCanvas?.clientHeight || 0
  const rotationAspect = (canvasWidth > 0 && canvasHeight > 0) ? (canvasWidth / canvasHeight) : 1

  if (!gl) {
    return { scaleX: 1, scaleY: 1, rotationCos, rotationSin, rotationAspect }
  }

  if (canvasWidth === 0 || canvasHeight === 0) {
    return { scaleX: 1, scaleY: 1, rotationCos, rotationSin, rotationAspect }
  }

  const imageWidth = webglRenderer.imageSize.value.width
  const imageHeight = webglRenderer.imageSize.value.height

  if (imageWidth === 0 || imageHeight === 0) {
    return { scaleX: 1, scaleY: 1, rotationCos, rotationSin, rotationAspect }
  }

  const absCos = Math.abs(rotationCos)
  const absSin = Math.abs(rotationSin)
  const rotatedBoxWidth = imageWidth * absCos + imageHeight * absSin
  const rotatedBoxHeight = imageWidth * absSin + imageHeight * absCos

  if (rotatedBoxWidth === 0 || rotatedBoxHeight === 0) {
    return { scaleX: 1, scaleY: 1, rotationCos, rotationSin, rotationAspect }
  }

  const fitScale = Math.min(canvasWidth / rotatedBoxWidth, canvasHeight / rotatedBoxHeight)
  const scaleX = fitScale * (imageWidth / canvasWidth)
  const scaleY = fitScale * (imageHeight / canvasHeight)

  if (!isFinite(scaleX) || !isFinite(scaleY) || scaleX === 0 || scaleY === 0) {
    return { scaleX: 1, scaleY: 1, rotationCos, rotationSin, rotationAspect }
  }

  return { scaleX, scaleY, rotationCos, rotationSin, rotationAspect }
})

const polygonDrawing = usePolygonDraw(
  polygons, view, pixelsToWorld, constrainToImage, webglRenderer.imageSize,
  canvasControls.regionType, mouseInteraction, selectedPolygonIndex, constrainToParent,
  polygons, autoSelect, canvasControls.commander, props.canvasId, canvasControls.viewMode,
  preventOverlapOnCreate, overlapMinAreaThreshold
)

const rectangleDrawing = useRectangleDrawing(
  polygons, constrainToImage, webglRenderer.imageSize, canvasControls.regionType,
  selectedPolygonIndex, constrainToParent, polygons, autoSelect, canvasControls.commander, props.canvasId, canvasControls.viewMode,
  preventOverlapOnCreate, overlapMinAreaThreshold
)

const polygonEditing = usePolygonEditing(
  polygons, view, aspectRatioScale, constrainToImage, webglRenderer.imageSize,
  constrainToParent, spatialIndex, selectedPolylineIndex, canvasControls.viewMode, canvasControls.commander, props.canvasId,
  hiddenPolygonIds, hiddenPolylineIds
)

const polylineDrawing = usePolylineDrawing(
  polylines, view, pixelsToWorld, constrainToImage, webglRenderer.imageSize,
  selectedPolygonIndex, polygons, constrainToParent, autoSelect,
  selectedPolylineIndex, canvasControls.commander, props.canvasId, canvasControls.viewMode,
  preventOverlapOnCreate, overlapMinAreaThreshold
)

const polylineEditing = usePolylineEditing(
  polylines, view, aspectRatioScale, constrainToImage, webglRenderer.imageSize,
  polygons, constrainToParent, spatialIndex, selectedPolygonIndex, selectedPolylineIndex,
  canvasControls.viewMode, canvasControls.commander,
  props.canvasId, hiddenPolygonIds, hiddenPolylineIds
)

const cutDrawing = useCutDrawing(
  polygons, view, pixelsToWorld, constrainToImage, webglRenderer.imageSize,
  canvasControls.commander, props.canvasId
)

canvasControls.cutDrawing = cutDrawing

const moveInteraction = useMoveInteraction(
  polygons, polylines, constrainToImage, constrainToParent, webglRenderer.imageSize,
  moveWithChildren, canvasControls.commander, props.canvasId,
  hiddenPolygonIds, hiddenPolylineIds, canvasControls.viewMode
)

const isCollaborationHeavyInteraction = computed(() => {
  if (!isCanvasEditable.value) return false

  return mouseInteraction.actionState.action === 'drag'
    || mouseInteraction.actionState.action === 'panning'
    || mouseInteraction.actionState.action === 'scrolling'
    || polygonEditing.draggedNodeInfo.isDragging
    || polylineEditing.draggedNodeInfo.isDragging
    || moveInteraction.state.isMoving
})

const editorCommands = useEditorCommand(
  canvasControls.commander,
  props.canvasId,
  polygons,
  polylines,
  stateActions.clearHoverAndSelectionStates
)
const contextMenuOpen = ref(false)

type UiContextMenuItem = {
  label?: string
  icon?: string
  color?: 'error' | 'warning' | 'success' | 'primary' | 'neutral'
  disabled?: boolean
  children?: UiContextMenuItem[]
  dotColor?: string
  onSelect?: (e: Event) => void
}

const mapContextMenuItems = (items: EditorContextMenuItem[] = []): UiContextMenuItem[] => {
  return items.map(item => ({
    label: item.label,
    icon: item.icon,
    color: item.danger ? 'error' : undefined,
    disabled: item.disabled,
    dotColor: item.color,
    onSelect: async () => {
      await editorCommands.handleContextMenuSelect(item)
      editorCommands.closeContextMenu()
      contextMenuOpen.value = false
    },
    children: item.submenu ? mapContextMenuItems(item.submenu) : undefined
  }))
}

const contextMenuItems = computed(() => mapContextMenuItems(editorCommands.contextMenuItems.value || []))

watch(contextMenuOpen, (open) => {
  if (!open) {
    editorCommands.closeContextMenu()
  }
})

const bufferSlideoverRef = ref<{ previewPoints: { x: number, y: number }[] | null } | null>(null)
const bufferPreviewPoints = computed(() => bufferSlideoverRef.value?.previewPoints ?? null)
const bufferPreviewPolygonId = computed(() => editorCommands.pendingBufferPolygon.value?.id ?? null)

const propertiesTarget = computed(() => editorCommands.pendingPropertiesTarget.value)
const propertiesInReadingOrder = computed(() => {
  const target = propertiesTarget.value
  if (!target || target.type !== 'polygon') return false
  const polygon = target.element
  if (polygon.type !== 'region') return false
  return editorCommands.isRegionInCurrentReadingOrder(polygon.id)
})

function handlePropertiesClose() {
  editorCommands.closeProperties()
}

async function handlePropertiesDelete() {
  const target = propertiesTarget.value
  if (!target) return
  if (target.type === 'polygon') {
    await editorCommands.deletePolygon(target.element.id)
  } else {
    await editorCommands.deletePolyline(target.element.id)
  }
  editorCommands.closeProperties()
}

function handlePropertiesDuplicate() {
  const target = propertiesTarget.value
  if (!target) return
  if (target.type === 'polygon') {
    editorCommands.duplicatePolygon(target.element.id)
  } else {
    editorCommands.duplicatePolyline(target.element.id)
  }
  editorCommands.closeProperties()
}

function handlePropertiesToggleReadingOrder() {
  const target = propertiesTarget.value
  if (!target || target.type !== 'polygon') return
  const polygon = target.element
  if (polygon.type !== 'region') return
  editorCommands.toggleReadingOrder(polygon.id)
}

function handleBufferClose(result: { distance: number } | null) {
  if (result) {
    editorCommands.applyBuffer(result.distance)
  } else {
    editorCommands.cancelBuffer()
  }
}

const readingOrderOverlaySettings = computed(() => editorUiStore.readingOrderOverlay)
const relationsOverlaySettings = computed(() => editorUiStore.relationsOverlay)
const commentsOverlaySettings = computed(() => editorUiStore.commentsOverlay)

const readingOrder = computed(() => {
  void editorUiStore.readingOrderVersion
  return session.document.value?.page?.readingOrder
})

const readingOrderOptions = computed(() => ({
  showArrows: readingOrderOverlaySettings.value.showArrows,
  showGroupBounds: readingOrderOverlaySettings.value.showGroupBounds,
  showOrderNumbers: readingOrderOverlaySettings.value.showOrderNumbers,
  showAllRegions: readingOrderOverlaySettings.value.showAllRegions
}))

const polygonsRef = computed(() => polygons)
const { renderData: readingOrderRenderData } = useReadingOrderVisualization(
  readingOrder,
  polygonsRef,
  readingOrderOptions,
  hiddenPolygonIds
)

const showReadingOrderOverlay = computed(() => readingOrderOverlaySettings.value.visible)

const relations = computed(() => session.document.value?.page?.relations)
const { renderData: relationRenderData } = useRelationsVisualization(
  relations,
  polygonsRef,
  computed(() => editorUiStore.relationsEditor.selectedRelationId),
  computed(() => editorUiStore.relationsEditor.draft),
  computed(() => editorUiStore.relationsEditor.pickerMode)
)
const showRelationsOverlay = computed(() =>
  relationsOverlaySettings.value.visible || editorUiStore.relationsEditor.pickerMode !== 'idle'
)
const showCommentsOverlay = computed(() => commentsOverlaySettings.value.visible)

function normalizeCommentText(value: string | undefined): string | null {
  if (!value) return null
  const normalized = value.replace(/\s+/g, ' ').trim()
  return normalized.length > 0 ? normalized : null
}

function getPolygonLabelPosition(points: Array<{ x: number, y: number }>): { x: number, y: number } | null {
  if (points.length === 0) return null
  const totals = points.reduce(
    (acc, point) => ({ x: acc.x + point.x, y: acc.y + point.y }),
    { x: 0, y: 0 }
  )
  return {
    x: totals.x / points.length,
    y: totals.y / points.length
  }
}

const commentOverlayLabels = computed<CommentOverlayLabel[]>(() => {
  if (!showCommentsOverlay.value) return []

  const labels: CommentOverlayLabel[] = []
  const visibilityContext = {
    selectedPolygonIndex: selectedPolygonIndex.value,
    selectedPolylineIndex: selectedPolylineIndex.value,
    allPolygons: polygons,
    allPolylines: polylines,
    viewMode: canvasControls.viewMode?.value,
    hiddenPolygonIds: new Set(hiddenPolygonIds.value),
    hiddenPolylineIds: new Set(hiddenPolylineIds.value),
    temporaryHoverPolygonId: editorUiStore.temporaryHoverPolygonId,
    temporaryHoverPolylineId: editorUiStore.temporaryHoverPolylineId
  }

  for (const polygon of polygons) {
    const text = normalizeCommentText(polygon.comments)
    if (!text || !visibilityService.shouldShowPolygon(polygon, visibilityContext)) continue

    const position = getPolygonLabelPosition(polygon.points)
    if (!position) continue

    labels.push({
      id: polygon.id,
      position,
      text
    })
  }

  return labels
})

const editorInteractions = useEditorInteractions(
  canvas, view, aspectRatioScale, polygons, polylines, selectedPolygonIndex, selectedPolylineIndex,
  selectedPolygonIds, selectedPolylineIds,
  hiddenPolygonIds, hiddenPolylineIds,
  isPolygonMode, isRectangleMode, isPolylineMode, isDrawingMode, isMoveMode, canvasControls.regionType, mouseInteraction,
  polygonDrawing, polylineDrawing, rectangleDrawing, polygonEditing, polylineEditing,
  editorCommands, canvasControls, webglRenderer.imageSize, moveInteraction, stateActions
)

const bufferPreviewForRenderer = computed(() => {
  const polygonId = bufferPreviewPolygonId.value
  const points = bufferPreviewPoints.value
  if (!polygonId || !points) return null
  return { polygonId, points }
})

const editorRenderer = useEditorRenderer(
  canvas, polygons, polylines, selectedPolygonIndex, selectedPolylineIndex, selectedPolygonIds, selectedPolylineIds,
  hiddenPolygonIds, hiddenPolylineIds,
  hoveredPolygonId,
  isPolygonMode, isRectangleMode, isPolylineMode, isMoveMode, canvasDimensions, webglRenderer, aspectRatioScale, view,
  mouseInteraction, polygonDrawing, polylineDrawing, rectangleDrawing, polygonEditing,
  polylineEditing, triangulatePolygon, canvasControls.viewMode, canvasControls.regionType,
  renderEnabled,
  readingOrderRenderData,
  showReadingOrderOverlay,
  relationRenderData,
  showRelationsOverlay,
  cutDrawing,
  isCutMode,
  isCutLineMode,
  isCutPolygonMode,
  isCutRectangleMode,
  moveInteraction,
  bufferPreviewForRenderer
)
const renderStats = computed(() => editorRenderer.renderStats.value)

function getCommandContext() {
  return { canvasId: props.canvasId, session }
}

function executeCreateRelationFromDraft() {
  const result = canvasControls.commander.execute(
    new CreateRelationCommand({
      relation: normalizeRelation(editorUiStore.relationsEditor.draft)
    }),
    getCommandContext()
  )

  if (!result?.id) return

  editorUiStore.setSelectedRelationId(result.id)
  editorUiStore.resetRelationDraft()
  editorUiStore.cancelRelationPicking()
}

function executeRepickRelationEndpoint(regionId: string, field: 'sourceRegionRef' | 'targetRegionRef') {
  const selectedRelationId = editorUiStore.relationsEditor.selectedRelationId
  const currentRelation = session.document.value?.page?.relations?.find(relation => relation.id === selectedRelationId)
  if (!selectedRelationId || !currentRelation) {
    editorUiStore.setRelationPickerRegionId(null)
    editorUiStore.cancelRelationPicking()
    return
  }

  const updatedRelation: Relation = {
    ...currentRelation,
    [field]: regionId
  }

  const result = canvasControls.commander.execute(
    new UpdateRelationCommand({
      relationId: selectedRelationId,
      relation: updatedRelation
    }),
    getCommandContext()
  )

  editorUiStore.setRelationPickerRegionId(null)
  editorUiStore.cancelRelationPicking()

  if (result?.id) {
    editorUiStore.setSelectedRelationId(result.id)
  }
}

watch(
  () => [
    editorUiStore.relationsEditor.pickerMode,
    editorUiStore.relationsEditor.draft.sourceRegionRef,
    editorUiStore.relationsEditor.draft.targetRegionRef
  ],
  ([pickerMode, sourceRegionRef, targetRegionRef]) => {
    if (pickerMode !== 'pick-target') return
    if (!sourceRegionRef || !targetRegionRef) return

    executeCreateRelationFromDraft()
  }
)

watch(
  () => [
    editorUiStore.relationsEditor.pickerMode,
    editorUiStore.relationsEditor.pickerRegionId
  ],
  ([pickerMode, pickedRegionId]) => {
    if (!pickedRegionId) return

    if (pickerMode === 'repick-source') {
      executeRepickRelationEndpoint(pickedRegionId, 'sourceRegionRef')
      return
    }

    if (pickerMode === 'repick-target') {
      executeRepickRelationEndpoint(pickedRegionId, 'targetRegionRef')
    }
  }
)

const showRenderStats = ref(false)
if (import.meta.env.DEV) {
  const toggleStats = (e: KeyboardEvent) => {
    if (e.ctrlKey && e.shiftKey && e.key === 'R') {
      showRenderStats.value = !showRenderStats.value
    }
  }
  onMounted(() => window.addEventListener('keydown', toggleStats))
  onBeforeUnmount(() => window.removeEventListener('keydown', toggleStats))
}

watch(() => webglRenderer.imageSize.value, (newSize) => {
  if (newSize && newSize.width > 0 && newSize.height > 0) {
    editorStore.setImageSize(newSize.width, newSize.height)
  }
}, { deep: true })

watch(() => editorUiStore.temporaryHoverPolygonId, () => {
  if (renderEnabled.value) {
    nextTick(() => editorRenderer.render())
  }
})

watch(() => editorUiStore.temporaryHoverPolylineId, () => {
  if (renderEnabled.value) {
    nextTick(() => editorRenderer.render())
  }
})

function handleSelectPolygon(polygonId: string | null, options?: { zoomToFit?: boolean }) {
  if (!polygonId) {
    stateActions.clearSelection()
    return
  }
  const index = stateActions.selectPolygonById(polygonId)
  if (index >= 0 && options?.zoomToFit !== false) {
    const polygon = polygons[index]
    if (polygon) {
      editorInteractions.centerViewOnPolygon(polygon)
    }
  }
}

function handleSelectPolyline(polylineId: string | null, options?: { zoomToFit?: boolean }) {
  if (!polylineId) {
    stateActions.clearSelection()
    return
  }
  const index = stateActions.selectPolylineById(polylineId)
  if (index >= 0 && options?.zoomToFit !== false) {
    const polyline = polylines[index]
    if (polyline) {
      editorInteractions.centerViewOnPolyline(polyline)
    }
  }
}

function handleHoverPolygon(polygonId: string | null) {
  stateActions.setHoveredPolygonId(polygonId)
  const index = polygons.findIndex(p => p.id === polygonId)
  polygonEditing.hoveredPolygonIndex.value = index >= 0 ? index : -1
  editorUiStore.setTemporaryHoverPolygonId(polygonId)
}

function handleUnhoverPolygon() {
  stateActions.setHoveredPolygonId(null)
  polygonEditing.hoveredPolygonIndex.value = -1
  editorUiStore.setTemporaryHoverPolygonId(null)
}

function handleHoverPolyline(polylineId: string | null) {
  stateActions.setHoveredPolylineId(polylineId)
  editorUiStore.setTemporaryHoverPolylineId(polylineId)
}

function handleUnhoverPolyline() {
  stateActions.setHoveredPolylineId(null)
  editorUiStore.setTemporaryHoverPolylineId(null)
}

canvasControls.selectPolygonById = handleSelectPolygon
canvasControls.selectPolylineById = handleSelectPolyline
canvasControls.hoverPolygonById = handleHoverPolygon
canvasControls.unhoverPolygon = handleUnhoverPolygon
canvasControls.hoverPolylineById = handleHoverPolyline
canvasControls.unhoverPolyline = handleUnhoverPolyline

// Publish controls only after all runtime fields are attached.
// `session.controls` is a shallowRef, so late property mutations would not be reactive for consumers.
if (import.meta.client) {
  session.controls.value = canvasControls
}

useResizeObserver(canvas, (width, height) => {
  stateActions.updateCanvasDimensions(width, height)
  if (renderEnabled.value) {
    nextTick(() => editorRenderer.render())
  }
})

let interactionsAttached = false
let stopUiModeWatch: WatchStopHandle | null = null

function attachInteractions() {
  if (interactionsAttached) return
  const el = canvas.value
  if (!el) return

  el.addEventListener('wheel', editorInteractions.onWheel, { passive: false })
  el.addEventListener('mousedown', activateEditor)
  el.addEventListener('mousedown', editorInteractions.onMouseDown)
  el.addEventListener('dblclick', editorInteractions.onDoubleClick)
  window.addEventListener('mousemove', editorInteractions.onMouseMove)
  window.addEventListener('mouseup', editorInteractions.onMouseUp)
  window.addEventListener('mouseleave', editorInteractions.onMouseLeave)
  window.addEventListener('keydown', editorInteractions.onKeyDown, true)

  interactionsAttached = true
}

function detachInteractions() {
  if (!interactionsAttached) return
  const el = canvas.value

  if (el) {
    el.removeEventListener('wheel', editorInteractions.onWheel)
    el.removeEventListener('mousedown', editorInteractions.onMouseDown)
    el.removeEventListener('mousedown', activateEditor)
    el.removeEventListener('dblclick', editorInteractions.onDoubleClick)
  }
  window.removeEventListener('mousemove', editorInteractions.onMouseMove)
  window.removeEventListener('mouseup', editorInteractions.onMouseUp)
  window.removeEventListener('mouseleave', editorInteractions.onMouseLeave)
  window.removeEventListener('keydown', editorInteractions.onKeyDown, true)

  interactionsAttached = false
}

function toScreenPoint(point: { x: number, y: number }): { x: number, y: number } | null {
  const imageSize = webglRenderer.imageSize.value
  if (!imageSize.width || !imageSize.height || !canvasDimensions.value.width || !canvasDimensions.value.height) {
    return null
  }

  const worldPoint = imageToWorld(point, imageSize)
  const clipPoint = worldToClipCoords(worldPoint, view, aspectRatioScale.value)

  return {
    x: ((clipPoint.x + 1) / 2) * canvasDimensions.value.width,
    y: ((1 - clipPoint.y) / 2) * canvasDimensions.value.height
  }
}

function buildOverlayPath(points: { x: number, y: number }[], closed: boolean): { path: string, label: { x: number, y: number } } | null {
  const screenPoints = points
    .map(point => toScreenPoint(point))
    .filter((point): point is { x: number, y: number } => point !== null)

  if (screenPoints.length === 0) return null

  const path = screenPoints.map((point, index) => `${index === 0 ? 'M' : 'L'} ${point.x} ${point.y}`).join(' ')
  const closedPath = closed ? `${path} Z` : path
  const anchor = screenPoints[0] ?? { x: 0, y: 0 }

  return {
    path: closedPath,
    label: {
      x: anchor.x,
      y: anchor.y
    }
  }
}

const remoteSelectionOverlays = computed(() => {
  const editorId = canvasEditor.value?.user.id
  if (!editorId) return []

  return remoteCollaborators.value.flatMap((member) => {
    if (member.user.id !== editorId) return []

    const selectionId = member.presence?.selectionId
    const selectionKind = member.presence?.selectionKind
    if (!selectionId || !selectionKind) return []

    const color = getCollaborationColor(member.user.id)
    const label = member.user.displayName

    if (selectionKind === 'region') {
      const polygon = polygons.find(item => item.id === selectionId)
      if (!polygon) return []

      const overlay = buildOverlayPath(polygon.points, true)
      if (!overlay) return []

      return [{
        key: `${member.user.id}:${selectionId}`,
        color,
        label,
        path: overlay.path,
        labelX: overlay.label.x,
        labelY: overlay.label.y
      }]
    }

    const polyline = polylines.find(item => item.id === selectionId)
    if (!polyline) return []

    const overlay = buildOverlayPath(polyline.points, false)
    if (!overlay) return []

    return [{
      key: `${member.user.id}:${selectionId}`,
      color,
      label,
      path: overlay.path,
      labelX: overlay.label.x,
      labelY: overlay.label.y
    }]
  })
})

const remoteCursorOverlays = computed(() => {
  if (!isCanvasEditable.value) return []

  return remoteCollaborators.value.flatMap((member) => {
    if (canvasEditor.value?.user.id !== member.user.id) return []

    const cursor = member.presence?.cursor
    if (!cursor || typeof cursor.x !== 'number' || typeof cursor.y !== 'number') return []

    const clipPoint = worldToClipCoords(cursor, view, aspectRatioScale.value)
    const screenPoint = {
      x: ((clipPoint.x + 1) / 2) * canvasDimensions.value.width,
      y: ((1 - clipPoint.y) / 2) * canvasDimensions.value.height
    }

    if (
      !Number.isFinite(screenPoint.x)
      || !Number.isFinite(screenPoint.y)
      || screenPoint.x < 0
      || screenPoint.y < 0
      || screenPoint.x > canvasDimensions.value.width
      || screenPoint.y > canvasDimensions.value.height
    ) {
      return []
    }

    return [{
      key: `${member.user.id}:cursor`,
      color: getCollaborationColor(member.user.id),
      label: member.user.displayName,
      x: screenPoint.x,
      y: screenPoint.y
    }]
  })
})

async function handleRequestTakeover(force = false) {
  const sent = await collaboration.requestTakeover(props.canvasId, force)
  if (!sent) {
    toast.add({
      title: 'Request failed',
      description: 'Could not send the edit transfer request.',
      color: 'error'
    })
    return
  }

  toast.add({
    title: force ? 'Force takeover requested' : 'Edit request sent',
    description: force
      ? 'The edit lock will transfer as soon as the current lease updates.'
      : 'The current editor has been notified.',
    color: force ? 'warning' : 'info'
  })
}

async function handleRespondToTakeover(decision: 'accept' | 'decline', handoffMode: 'save' | 'discard' = 'save') {
  const canvas = canvasState.value
  if (!canvas) return

  if (decision === 'accept') {
    if (handoffMode === 'save') {
      await editorStore.saveAnnotations(props.canvasId)
    } else if (canvas.projectId && canvas.pageId) {
      await editorStore.loadPageIntoCanvas(
        props.canvasId,
        canvas.projectId,
        canvas.pageId,
        canvas.imageVariantId ?? undefined
      )
    }
  }

  await collaboration.respondToTakeover(props.canvasId, decision, handoffMode)
}

async function handleResyncRoom() {
  await collaboration.reloadRoomForCanvas(props.canvasId)
  emitPresence()
}

async function handleReclaimEdit() {
  const reclaimed = await collaboration.reclaimCanvasEdit(props.canvasId)
  if (!reclaimed) {
    toast.add({
      title: 'Reclaim failed',
      description: 'Could not reclaim the edit lock for this page.',
      color: 'error'
    })
    return
  }

  toast.add({
    title: 'Edit lock reclaimed',
    description: 'You can edit this page again.',
    color: 'success'
  })
}

watch(
  () => isCollaborationHeavyInteraction.value,
  (busy) => {
    collaborationSyncSuspended.value = busy
    collaboration.setCanvasSyncSuspended(props.canvasId, busy)

    if (!busy) {
      emitPresence()
    }
  },
  { immediate: true }
)

watch(
  () => collaboration.isCollaborativeCanvas(props.canvasId),
  (ready) => {
    if (ready) {
      emitPresence()
    }
  },
  { immediate: true }
)

watch(
  () => isCanvasEditable.value
    ? [
        view.zoom,
        view.offsetX,
        view.offsetY,
        mouseInteraction.actionState.position.x,
        mouseInteraction.actionState.position.y,
        mouseInteraction.actionState.action,
        selectedRegionId.value,
        selectedBaselineId.value,
        canvasState.value?.imageVariantId ?? null,
        editorStore.activeCanvasId === props.canvasId,
        effectiveUiMode.value
      ]
    : [
        canvasState.value?.imageVariantId ?? null,
        editorStore.activeCanvasId === props.canvasId,
        projectId.value,
        pageId.value,
        xmlFileId.value
      ],
  () => {
    emitPresence()
  }
)

onMounted(() => {
  editorStore.registerCanvas(props.canvasId)

  webglRenderer.initGL(triangulatePolygon)
  registerGeometryCacheManager(props.canvasId, {
    invalidate: webglRenderer.invalidateGeometry,
    invalidateMultiple: webglRenderer.invalidateMultipleGeometry,
    clear: webglRenderer.clearGeometryCache,
    prune: webglRenderer.pruneGeometryCache,
    getStats: webglRenderer.getGeometryCacheStats
  })

  webglRenderer.loadAndRender(currentImageSrc.value)

  if (canvas.value) {
    canvasDimensions.value = {
      width: canvas.value.clientWidth,
      height: canvas.value.clientHeight
    }
  }

  editorRenderer.setupRenderWatches()
  editorRenderer.setupCursorWatches()
  editorRenderer.setupReadingOrderAnimationWatch()

  stopUiModeWatch = watch(
    () => [effectiveUiMode.value, isCanvasEditable.value] as const,
    ([mode, editable]) => {
      if (mode === 'text' || !editable) {
        detachInteractions()
        webglRenderer.stopRenderLoop()
        stateActions.setHoveredPolygonId(null)
        editorUiStore.setTemporaryHoverPolygonId(null)
        editorUiStore.setTemporaryHoverPolylineId(null)
        return
      }

      attachInteractions()
      nextTick(() => editorRenderer.render())
    },
    { immediate: true }
  )

  if (renderEnabled.value) {
    nextTick(() => editorRenderer.render())
  }
})

onBeforeUnmount(() => {
  if (stopUiModeWatch) stopUiModeWatch()

  mouseInteraction.cleanup()
  webglRenderer.cleanup()

  detachInteractions()
  collaboration.setCanvasSyncSuspended(props.canvasId, false)

  unregisterGeometryCacheManager(props.canvasId)

  session.controls.value = null
})

watch(() => props.src, (newSrc) => {
  if (newSrc) {
    currentImageSrc.value = newSrc
    webglRenderer.loadAndRender(newSrc)
    if (renderEnabled.value) {
      nextTick(() => editorRenderer.render())
    }
  }
})
</script>

<template>
  <div class="w-full h-full flex flex-col min-h-0">
    <div
      v-if="!isCanvasEditable && hasCanvasLeaseExpiredLocally && canReclaimCanvasEdit"
      class="flex min-h-10 items-center justify-between gap-3 border-b border-amber-950/60 bg-[#2b1d12] px-3 py-2 text-[13px] text-amber-50"
    >
      <div class="flex min-w-0 items-center gap-2.5">
        <div class="flex h-6 w-6 shrink-0 items-center justify-center rounded-full bg-amber-500/12 text-amber-400">
          <Icon name="i-lucide-rotate-ccw" class="h-3.5 w-3.5" />
        </div>
        <p class="truncate text-[13px] text-amber-50/90">
          Your edit lock expired locally. This page is free again and you can reclaim edit access.
        </p>
      </div>

      <div class="flex shrink-0 items-center gap-2">
        <UButton
          size="xs"
          color="primary"
          variant="soft"
          class="h-7 px-2.5 text-[11px]"
          label="Reclaim Edit"
          @click="handleReclaimEdit"
        />
      </div>
    </div>

    <div
      v-else-if="!isCanvasEditable && canvasEditor"
      class="flex min-h-10 items-center justify-between gap-3 border-b border-amber-950/60 bg-[#2b1d12] px-3 py-2 text-[13px] text-amber-50"
    >
      <div class="flex min-w-0 items-center gap-2.5">
        <div class="flex h-6 w-6 shrink-0 items-center justify-center rounded-full bg-amber-500/12 text-amber-400">
          <Icon name="i-lucide-lock" class="h-3.5 w-3.5" />
        </div>
        <p class="truncate text-[13px] text-amber-50/90">
          <span class="text-amber-50/70">Read-only view.</span>
          {{ canvasEditor.user.displayName }} currently holds the edit lock.
        </p>
      </div>

      <div class="flex shrink-0 items-center gap-2">
        <UButton
          size="xs"
          color="neutral"
          variant="soft"
          class="h-7 px-2.5 text-[11px]"
          label="Request Edit"
          @click="handleRequestTakeover(false)"
        />
        <UButton
          v-if="canForceTakeover"
          size="xs"
          color="error"
          icon="i-lucide-octagon-alert"
          variant="soft"
          class="h-7 px-2.5 text-[11px]"
          label="Force Takeover"
          @click="handleRequestTakeover(true)"
        />
      </div>
    </div>

    <div
      v-if="isCanvasEditable && isCanvasLeaseExpiringSoon"
      class="flex min-h-10 items-center justify-between gap-3 border-b border-amber-950/60 bg-[#2b1d12] px-3 py-2 text-[13px] text-amber-50"
    >
      <div class="flex min-w-0 items-center gap-2.5">
        <div class="flex h-6 w-6 shrink-0 items-center justify-center rounded-full bg-amber-500/12 text-amber-400">
          <Icon name="i-lucide-clock-3" class="h-3.5 w-3.5" />
        </div>
        <p class="truncate text-[13px] text-amber-50/90">
          Your edit lock expires in {{ canvasLeaseSecondsUntilExpiry ?? 0 }}s unless the heartbeat resumes.
        </p>
      </div>
    </div>

    <div
      v-if="isCanvasEditable && pendingTakeover"
      class="flex min-h-10 items-center justify-between gap-3 border-b border-amber-950/60 bg-[#2b1d12] px-3 py-2 text-[13px] text-amber-50"
    >
      <div class="flex min-w-0 items-center gap-2.5">
        <div class="flex h-6 w-6 shrink-0 items-center justify-center rounded-full bg-amber-500/12 text-amber-400">
          <Icon name="i-lucide-arrow-right-left" class="h-3.5 w-3.5" />
        </div>
        <p class="truncate text-[13px] text-amber-50/90">
          {{ pendingTakeover.requester.displayName }} requested edit access for this page.
        </p>
      </div>

      <div class="flex shrink-0 items-center gap-2">
        <UButton
          size="xs"
          color="neutral"
          variant="soft"
          class="h-7 px-2.5 text-[11px]"
          label="Decline"
          @click="handleRespondToTakeover('decline')"
        />
        <UButton
          size="xs"
          color="neutral"
          variant="soft"
          class="h-7 px-2.5 text-[11px]"
          label="Discard + Transfer"
          @click="handleRespondToTakeover('accept', 'discard')"
        />
        <UButton
          size="xs"
          color="primary"
          variant="soft"
          class="h-7 px-2.5 text-[11px]"
          label="Save + Transfer"
          @click="handleRespondToTakeover('accept', 'save')"
        />
      </div>
    </div>

    <div
      v-if="isCollaborationResyncRequired"
      class="flex min-h-10 items-center justify-between gap-3 border-b border-amber-950/60 bg-[#2b1d12] px-3 py-2 text-[13px] text-amber-50"
    >
      <div class="flex min-w-0 items-center gap-2.5">
        <div class="flex h-6 w-6 shrink-0 items-center justify-center rounded-full bg-amber-500/12 text-amber-400">
          <Icon name="i-lucide-alert-triangle" class="h-3.5 w-3.5" />
        </div>
        <p class="truncate text-[13px] text-amber-50/90">
          Collaboration state is stale. Another save or restore changed the persisted XML revision.
        </p>
      </div>

      <div class="flex shrink-0 items-center gap-2">
        <UButton
          size="xs"
          color="neutral"
          variant="soft"
          class="h-7 px-2.5 text-[11px]"
          @click="handleResyncRoom"
        >
          Resync
        </UButton>
      </div>
    </div>

    <div class="relative flex-1 min-h-0" :class="{ 'editor-checkerboard': showCheckerboard }">
      <div class="absolute inset-0 pointer-events-none" :style="{ backgroundColor: editorBackgroundColor }" />
      <UContextMenu
        v-model:open="contextMenuOpen"
        :items="contextMenuItems"
      >
        <template #default>
          <canvas
            ref="canvas"
            class="block w-full h-full bg-transparent relative z-10"
            :class="isCanvasEditable ? 'cursor-grab' : 'cursor-default pointer-events-none'"
            @contextmenu="(event) => { if (isCanvasEditable) editorInteractions.handleCanvasContextMenu(event) }"
          />
        </template>
        <template #item-leading="{ item }">
          <div class="mr-2 flex items-center gap-1 shrink-0">
            <span v-if="item.dotColor" class="h-2.5 w-2.5 rounded-sm border border-neutral-300" :style="{ backgroundColor: item.dotColor }" />
            <Icon v-if="item.icon" :name="item.icon" class="h-4 w-4" />
            <span v-else class="w-4" />
          </div>
        </template>
      </UContextMenu>

      <Transition name="fade">
        <div
          v-if="isLoadingAnnotations"
          class="absolute inset-0 z-[999] flex items-center justify-center backdrop-blur-md bg-black/30"
        >
          <div class="flex items-center gap-3 px-5 py-3 rounded-xl bg-black/50 shadow-xl ring-1 ring-white/10">
            <Icon name="i-lucide-loader-2" class="h-5 w-5 text-white animate-spin" />
            <span class="text-sm font-medium text-white drop-shadow-md">Loading annotations...</span>
          </div>
        </div>
      </Transition>

      <UPopover
        v-if="showCollaboratorsPopover"
        v-model:open="collaboratorsPopoverOpen"
        :content="{
          side: 'bottom',
          align: 'start',
          sideOffset: 8
        }"
      >
        <UButton
          color="neutral"
          variant="outline"
          class="absolute top-2.5 left-2.5 z-[950] h-7 rounded border-neutral-700/50 bg-neutral-900/90 px-2 text-neutral-200 shadow-sm backdrop-blur-sm hover:bg-neutral-900"
        >
          <div class="flex items-center gap-2 min-w-0">
            <div class="flex items-center -space-x-1">
              <div
                v-for="participant in collaborationVisibleParticipants"
                :key="participant.key"
                class="relative"
              >
                <UAvatar
                  :src="avatarSrc(participant.user)"
                  :alt="participant.user.displayName"
                  :text="avatarFallback(participant.user)"
                  size="xs"
                  class="h-5 w-5 border text-[9px] font-medium"
                  :style="collaborationAvatarStyle(participant.user.id)"
                />
                <span
                  class="absolute -bottom-0.5 -right-0.5 h-2 w-2 rounded-full border border-neutral-950"
                  :class="participant.role === 'editing' ? 'bg-primary-500' : 'bg-emerald-500'"
                />
              </div>
            </div>

            <span class="text-[11px] font-medium text-neutral-200 truncate">
              {{ collaborationSummaryLabel }}
            </span>

            <Icon
              :name="collaboratorsPopoverOpen ? 'i-lucide-chevron-up' : 'i-lucide-chevron-down'"
              class="h-3 w-3 shrink-0 text-neutral-500"
            />
          </div>
        </UButton>

        <template #content>
          <div class="w-60 rounded border border-neutral-700/60 bg-neutral-900/95 text-neutral-100 shadow-lg backdrop-blur-sm">
            <div class="flex items-center gap-2 px-2.5 py-2">
              <Icon name="i-lucide-users" class="h-3.5 w-3.5 text-neutral-300" />
              <span class="text-xs font-medium">Active collaborators</span>
            </div>

            <div class="border-t border-neutral-700/60" />

            <div class="space-y-3 p-2.5">
              <div v-if="editingParticipants.length > 0" class="space-y-2">
                <div class="text-[10px] font-medium uppercase tracking-[0.18em] text-neutral-500">
                  Editing
                </div>

                <div
                  v-for="participant in editingParticipants"
                  :key="`${participant.key}:editing`"
                  class="flex items-center justify-between gap-2"
                >
                  <div class="flex items-center gap-2 min-w-0">
                    <div class="relative">
                      <UAvatar
                        :src="avatarSrc(participant.user)"
                        :alt="participant.user.displayName"
                        :text="avatarFallback(participant.user)"
                        size="xs"
                        class="h-6 w-6 border text-[9px] font-medium"
                        :style="collaborationAvatarStyle(participant.user.id)"
                      />
                      <span class="absolute -bottom-0.5 -right-0.5 h-2 w-2 rounded-full border border-neutral-950 bg-primary-500" />
                    </div>

                    <div class="min-w-0">
                      <div class="truncate text-xs font-medium text-neutral-100">
                        {{ participant.user.displayName }}
                      </div>
                      <div class="flex items-center gap-1 text-[11px] text-neutral-400">
                        <Icon :name="participant.presence?.active ? 'i-lucide-pencil-line' : 'i-lucide-pause'" class="h-3 w-3" />
                        <span class="truncate">{{ collaboratorActivityLabel(participant) }}</span>
                      </div>
                    </div>
                  </div>

                  <UBadge
                    v-if="collaboratorStatus(participant)"
                    :color="collaboratorStatus(participant)!.color"
                    variant="subtle"
                    size="xs"
                  >
                    {{ collaboratorStatus(participant)!.label }}
                  </UBadge>
                </div>
              </div>

              <div v-if="viewingParticipants.length > 0" class="space-y-2">
                <div class="text-[10px] font-medium uppercase tracking-[0.18em] text-neutral-500">
                  Viewing
                </div>

                <div
                  v-for="participant in viewingParticipants"
                  :key="`${participant.key}:viewing`"
                  class="flex items-center gap-2"
                >
                  <div class="relative">
                    <UAvatar
                      :src="avatarSrc(participant.user)"
                      :alt="participant.user.displayName"
                      :text="avatarFallback(participant.user)"
                      size="xs"
                      class="h-6 w-6 border text-[9px] font-medium"
                      :style="collaborationAvatarStyle(participant.user.id)"
                    />
                    <span class="absolute -bottom-0.5 -right-0.5 h-2 w-2 rounded-full border border-neutral-950 bg-emerald-500" />
                  </div>

                  <div class="min-w-0">
                    <div class="truncate text-xs font-medium text-neutral-100">
                      {{ participant.user.displayName }}
                    </div>
                    <div class="flex items-center gap-1 text-[11px] text-neutral-400">
                      <Icon name="i-lucide-eye" class="h-3 w-3" />
                      <span class="truncate">{{ collaboratorActivityLabel(participant) }}</span>
                    </div>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </template>
      </UPopover>

      <ReadingOrderNumbersOverlay
        v-if="showReadingOrderOverlay && readingOrderOverlaySettings.showOrderNumbers"
        :order-numbers="readingOrderRenderData.orderNumbers"
        :group-bounds="readingOrderRenderData.groupBounds"
        :view="view"
        :aspect-ratio-scale="aspectRatioScale"
        :canvas-dimensions="canvasDimensions"
        :visible="true"
        :show-labels="readingOrderOverlaySettings.showLabels"
      />

      <RelationsLabelsOverlay
        v-if="showRelationsOverlay"
        :labels="relationRenderData.labels"
        :view="view"
        :aspect-ratio-scale="aspectRatioScale"
        :canvas-dimensions="canvasDimensions"
        :visible="true"
        :show-labels="relationsOverlaySettings.showLabels"
      />

      <CommentsLabelsOverlay
        v-if="showCommentsOverlay"
        :labels="commentOverlayLabels"
        :view="view"
        :aspect-ratio-scale="aspectRatioScale"
        :canvas-dimensions="canvasDimensions"
        :visible="true"
      />

      <div
        v-if="editorInteractions.isMarqueeSelecting.value && editorInteractions.marqueeRectPx.value"
        class="absolute border border-primary/50 bg-primary/10 pointer-events-none z-[900]"
        :style="{
          left: editorInteractions.marqueeRectPx.value.x + 'px',
          top: editorInteractions.marqueeRectPx.value.y + 'px',
          width: editorInteractions.marqueeRectPx.value.width + 'px',
          height: editorInteractions.marqueeRectPx.value.height + 'px'
        }"
      />

      <svg
        v-if="remoteSelectionOverlays.length > 0 || remoteCursorOverlays.length > 0"
        class="absolute inset-0 z-[940] pointer-events-none"
        :width="canvasDimensions.width"
        :height="canvasDimensions.height"
      >
        <g v-for="overlay in remoteSelectionOverlays" :key="overlay.key">
          <path
            :d="overlay.path"
            fill="none"
            :stroke="overlay.color"
            stroke-width="2.5"
            stroke-linecap="round"
            stroke-linejoin="round"
            stroke-dasharray="8 5"
          />
          <rect
            :x="overlay.labelX"
            :y="overlay.labelY - 18"
            width="88"
            height="18"
            rx="4"
            :fill="overlay.color"
            fill-opacity="0.9"
          />
          <text
            :x="overlay.labelX + 6"
            :y="overlay.labelY - 5"
            fill="#ffffff"
            font-size="11"
            font-weight="600"
          >
            {{ overlay.label }}
          </text>
        </g>

        <g v-for="cursor in remoteCursorOverlays" :key="cursor.key">
          <circle
            :cx="cursor.x"
            :cy="cursor.y"
            r="5"
            :fill="cursor.color"
            fill-opacity="0.95"
          />
          <circle
            :cx="cursor.x"
            :cy="cursor.y"
            r="11"
            :stroke="cursor.color"
            stroke-width="1.5"
            stroke-opacity="0.45"
            fill="none"
          />
          <rect
            :x="cursor.x + 10"
            :y="cursor.y - 18"
            width="88"
            height="18"
            rx="4"
            :fill="cursor.color"
            fill-opacity="0.9"
          />
          <text
            :x="cursor.x + 16"
            :y="cursor.y - 5"
            fill="#ffffff"
            font-size="11"
            font-weight="600"
          >
            {{ cursor.label }}
          </text>
        </g>
      </svg>

      <div v-if="showRenderStats && renderStats" class="absolute top-2.5 right-2.5 bg-black/80 text-green-500 p-3 rounded-sm font-mono text-xs leading-relaxed min-w-[200px] pointer-events-none z-[1000]">
        <div class="font-bold mb-2 text-white border-b border-green-500 pb-1">
          Render Performance (Ctrl+Shift+R)
        </div>
        <div class="flex justify-between mb-1">
          <span class="text-neutral-400">FPS:</span>
          <span class="font-bold">{{ renderStats.rendersPerSecond }}</span>
        </div>
        <div class="flex justify-between mb-1">
          <span class="text-neutral-400">Avg Frame:</span>
          <span class="font-bold">{{ renderStats.averageFrameTime.toFixed(2) }}ms</span>
        </div>
        <div class="flex justify-between mb-1">
          <span class="text-neutral-400">Max Frame:</span>
          <span class="font-bold">{{ renderStats.maxFrameTime.toFixed(2) }}ms</span>
        </div>
        <div class="flex justify-between mb-1">
          <span class="text-neutral-400">Total Renders:</span>
          <span class="font-bold">{{ renderStats.totalRenders }}</span>
        </div>
        <div class="flex justify-between mb-1">
          <span class="text-neutral-400">Batched:</span>
          <span class="font-bold">{{ renderStats.batchedRenders }}</span>
        </div>
      </div>

      <LazyEditorSlideoverBufferPolygon
        v-if="editorCommands.pendingBufferPolygon.value"
        ref="bufferSlideoverRef"
        :polygon="editorCommands.pendingBufferPolygon.value"
        :polygons="polygons"
        :constrain-to-image="constrainToImage"
        :constrain-to-parent="constrainToParent"
        @close="handleBufferClose"
      />

      <LazyEditorSlideoverProperties
        v-if="editorCommands.pendingPropertiesTarget.value"
        :target="editorCommands.pendingPropertiesTarget.value"
        :in-reading-order="propertiesInReadingOrder"
        @close="handlePropertiesClose"
        @delete="handlePropertiesDelete"
        @duplicate="handlePropertiesDuplicate"
        @toggle-reading-order="handlePropertiesToggleReadingOrder"
      />
    </div>
  </div>
</template>

<style scoped>
.editor-checkerboard {
  background-image: linear-gradient(45deg, #808080 25%, transparent 25%),
    linear-gradient(-45deg, #808080 25%, transparent 25%),
    linear-gradient(45deg, transparent 75%, #808080 75%),
    linear-gradient(-45deg, transparent 75%, #808080 75%);
  background-size: 20px 20px;
  background-position: 0 0, 0 10px, 10px -10px, -10px 0px;
  background-color: #c0c0c0;
}

/* Fade transition for loading indicator */
.fade-enter-active,
.fade-leave-active {
  transition: opacity 0.2s ease;
}

.fade-enter-from,
.fade-leave-to {
  opacity: 0;
}
</style>
