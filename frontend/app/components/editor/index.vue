<script setup lang="ts">
import DiffMatchPatch from 'diff-match-patch'
import type { Diff } from 'diff-match-patch'
import type { WatchStopHandle } from 'vue'
import { LazyEditorCommentsLabelsOverlay, LazyEditorReadingOrderNumbersOverlay, LazyEditorRelationsLabelsOverlay, LazyEditorSlideoverMergeSettings } from '#components'
import { getVisiblePolygonAtPoint, triangulatePolygon } from '@/utils/editor/hit-detection'
import { clipToWorldCoords, getWorldCoordsFromEvent, imageToWorld, pixelsToWorld, worldToClipCoords } from '@/utils/editor/coordinates'
import { getPagePanelId, parseCanvasId } from '@/stores/editor/editor.keys'
import { useEditorCollaboration } from '@/composables/editor/use-editor-collaboration'
import { useEditorCanvasCollaborationDisplay } from '@/composables/editor/use-editor-canvas-collaboration-display'
import type { ContextMenuItem as EditorContextMenuItem } from '@/composables/editor/use-editor-command'
import { useEditorStore } from '@/stores/editor/editor.store'
import { useEditorUiStore } from '@/stores/editor/editor.ui.store'
import { useEditorSessionStore } from '@/stores/editor/editor.session.store'
import { useWorkspaceStore } from '@/stores/workspace.store'
import { normalizeRelation } from '@/utils/editor/relations'
import { useEditorSession, usePageVisibilityState } from '@/session/editor/editor-session'
import { useRelationsVisualization } from '@/composables/editor/use-relations-visualization'
import { useMoveInteraction } from '@/composables/editor/use-move-interaction'
import { useEditorCanvasInteractionBlocker } from '@/composables/editor/use-canvas-interaction-blocker'
import { CompoundCommand, CreateRelationCommand, UpdateRelationCommand, UpdateTextContentVariantsCommand } from '@/commands'
import { PolygonType, type RegionKind, type Relation, type TextContentVariantData } from '@/models/editor'
import type { MergeSettings } from '@/components/editor/slideover/merge-settings.vue'
import type { ActionProcessingRenderTarget, CommentOverlayLabel, RenderablePolygon } from '@/types/editor/rendering'
import type { ActionTargetSelection } from '@/types/action'
import type { SelectionFocusMode, SelectionFocusOptions } from '@/types/editor/canvas-controls'
import { visibilityService } from '@/services/editor/visibility-service'
import { getCollaborationColor } from '@/types/collaboration'
import {
  collectTextlineIdsInPageOrder,
  getAdjacentTextlineId
} from '@/utils/editor/textline-navigation'
import { findTextLineRecursive } from '@/utils/editor/pcgts-editor-primitives'
import { ensureGtVariantAtIndex, normalizeEditableTextVariants, setGtVariantUnicode } from '@/utils/editor/text-variants'
import { computeCanvasTextCorrectionPlacement } from '@/utils/editor/canvas-text-correction-placement'
import { ZOOM } from '@/utils/editor/editor-constants'
import { buildRegionGtSyncedVariants, composeRegionGtFromTextLines } from '@/components/editor/text/shared/region-gt-sync'
import {
  handleSingleLineTextareaBeforeInput,
  handleSingleLineTextareaDrop,
  handleSingleLineTextareaPaste
} from '@/components/editor/text/shared/text-input-guards'
import { tokenizeForDictionary } from '@/components/editor/text/shared/text-highlighting'

const CommentsLabelsOverlay = LazyEditorCommentsLabelsOverlay
const ReadingOrderNumbersOverlay = LazyEditorReadingOrderNumbersOverlay
const RelationsLabelsOverlay = LazyEditorRelationsLabelsOverlay

const props = defineProps({
  src: { type: String, required: true },
  canvasId: { type: String, required: true }
})

const editorStore = useEditorStore()
const editorUiStore = useEditorUiStore()
const sessionStore = useEditorSessionStore()
const workspaceStore = useWorkspaceStore()
const actionRunsStore = useActionRunsStore()
const collaboration = useEditorCollaboration()
const session = useEditorSession(props.canvasId)
const {
  ensureTokenResults,
  getTokenResult,
  isTokenPending,
  hasSuggestionsLoaded
} = useDictionaryTokenLookup()
const toast = useToast()
const editorOverlay = useOverlay()
const mergeSettingsSlideover = editorOverlay.create(LazyEditorSlideoverMergeSettings)
const { isCanvasInteractionBlocked } = useEditorCanvasInteractionBlocker()

const colorMode = useColorMode()
const WORLD_COORD_THRESHOLD = 2.5
const CORRECTION_FONT_MIN = 14
const CORRECTION_FONT_MAX = 64
const CORRECTION_FONT_DEFAULT = 32
const CORRECTION_OVERLAY_MARGIN = 8

function isTextlinePolygonType(type: unknown): boolean {
  return typeof type === 'string' && type.toLowerCase() === PolygonType.TEXTLINE
}

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
const canvasIdRef = computed(() => props.canvasId)
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
const {
  collaborationVisibleParticipants,
  editingParticipants,
  viewingParticipants,
  collaborationSummaryLabel,
  showCollaboratorsPopover,
  avatarSrc,
  avatarFallback,
  collaborationAvatarStyle,
  collaboratorActivityLabel,
  collaboratorStatus
} = useEditorCanvasCollaborationDisplay({
  canvasId: canvasIdRef,
  hexToRgba
})

const hiddenPolygonIds = computed(() => usePageVisibilityState(pageId.value).value?.hiddenPolygonIds ?? [])
const hiddenPolylineIds = computed(() => usePageVisibilityState(pageId.value).value?.hiddenPolylineIds ?? [])

const activateEditor = () => editorStore.setActiveCanvas(props.canvasId)

const effectiveUiMode = computed(() => editorStore.effectiveUiMode(props.canvasId))
const isTextVisualMode = computed(() =>
  effectiveUiMode.value === 'text' && editorUiStore.textModeSubmode === 'visual'
)
const renderEnabled = computed(() => effectiveUiMode.value !== 'text' || isTextVisualMode.value)
const canvasTextCorrectionSnapToLine = computed({
  get: () => editorUiStore.canvasTextCorrectionOverlaySnapToLine,
  set: (next: boolean) => editorUiStore.setCanvasTextCorrectionOverlaySnapToLine(Boolean(next))
})

const constrainToImage = computed(() => editorStore.globalSettings.constrainToImage)
const constrainToParent = computed(() => editorStore.globalSettings.constrainToParent)
const autoSelect = computed(() => editorStore.globalSettings.autoSelect)
const moveWithChildren = computed(() => editorStore.globalSettings.moveWithChildren)
const preventOverlapOnCreate = computed(() => editorStore.globalSettings.preventOverlapOnCreate)
const overlapMinAreaThreshold = computed(() => editorStore.globalSettings.cutMinAreaThreshold)

const currentImageSrc = ref(props.src)

const canvasControls = useCanvasControl(props.canvasId)
const isCanvasWritable = computed(() => canvasControls.isCanvasEditable.value)
const pageLockReason = computed(() => canvasControls.pageLockReason.value)
const pageLockActionName = computed(() => {
  const reason = pageLockReason.value
  const prefix = 'LAREX Action running:'
  if (!reason?.startsWith(prefix)) return null
  return reason.slice(prefix.length).trim() || 'Action'
})
const pageLockDescription = computed(() => {
  const reason = pageLockReason.value
  if (!reason) return null
  return pageLockActionName.value ? 'Action running' : reason
})

const canvas = ref<HTMLCanvasElement | null>(null)
const correctionOverlayContainerRef = ref<HTMLDivElement | null>(null)
const webglRenderer = useWebglRenderer(canvas)
const suppressCorrectionZoomPreferenceUpdate = ref(false)

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
  canvasControls.commander, props.canvasId, selectedPolygonIndex, canvasControls.viewMode
)

canvasControls.cutDrawing = cutDrawing

const moveInteraction = useMoveInteraction(
  polygons, polylines, constrainToImage, constrainToParent, webglRenderer.imageSize,
  moveWithChildren, canvasControls.commander, props.canvasId,
  hiddenPolygonIds, hiddenPolylineIds, canvasControls.viewMode
)

async function openContextMergeSettingsSlideover(kinds: RegionKind[]): Promise<MergeSettings | null> {
  const instance = mergeSettingsSlideover.open({ availableKinds: kinds, defaultKind: kinds[0] })
  return await instance.result
}

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
  stateActions.clearHoverAndSelectionStates,
  selectedPolygonIds,
  selectedPolylineIds,
  openContextMergeSettingsSlideover
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
  if (!polygon) return false
  if (polygon.type !== 'region') return false
  return editorCommands.isRegionInCurrentReadingOrder(polygon.id)
})

function handlePropertiesClose() {
  editorCommands.closeProperties()
}

async function handlePropertiesDelete() {
  const target = propertiesTarget.value
  if (!target) return
  const element = target.element
  if (!element) return
  if (target.type === 'polygon') {
    await editorCommands.deletePolygon(element.id)
  } else {
    await editorCommands.deletePolyline(element.id)
  }
  editorCommands.closeProperties()
}

function handlePropertiesDuplicate() {
  const target = propertiesTarget.value
  if (!target) return
  const element = target.element
  if (!element) return
  if (target.type === 'polygon') {
    editorCommands.duplicatePolygon(element.id)
  } else {
    editorCommands.duplicatePolyline(element.id)
  }
  editorCommands.closeProperties()
}

function handlePropertiesToggleReadingOrder() {
  const target = propertiesTarget.value
  if (!target || target.type !== 'polygon') return
  const polygon = target.element
  if (!polygon) return
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

watch(isCanvasWritable, (writable) => {
  if (writable) return
  editorUiStore.setActionWandActive(false)
  editorInteractions.cancelActiveOperation?.()
  canvasControls.toggleSelectMode?.()
})

const bufferPreviewForRenderer = computed(() => {
  const polygonId = bufferPreviewPolygonId.value
  const points = bufferPreviewPoints.value
  if (!polygonId || !points) return null
  return { polygonId, points }
})

const ACTION_ACTIVE_STATUSES = new Set(['PENDING', 'DISPATCHING', 'RUNNING', 'IMPORTING_RESULTS', 'CANCEL_REQUESTED'])

const actionProcessingTargets = computed<ActionProcessingRenderTarget | null>(() => {
  const currentProjectId = projectId.value
  const currentPageId = pageId.value
  if (!currentProjectId || !currentPageId) return null

  let page = false
  const polygonIds = new Set<string>()

  for (const run of actionRunsStore.runsArray) {
    if (run.projectId !== currentProjectId || !ACTION_ACTIVE_STATUSES.has(run.status)) continue
    if (!run.pageIds.includes(currentPageId)) continue

    const targetPage = run.targetSelection?.pages?.find(candidate => candidate.pageId === currentPageId)
    if (!run.targetSelection || run.targetSelection.type === 'PAGE' || !targetPage) {
      page = true
      continue
    }

    if (run.targetSelection.type === 'REGION') {
      for (const id of targetPage.regionIds ?? []) polygonIds.add(id)
    } else if (run.targetSelection.type === 'TEXT_LINE') {
      for (const id of targetPage.textLineIds ?? []) polygonIds.add(id)
    }
  }

  return page || polygonIds.size > 0
    ? { page, polygonIds: [...polygonIds] }
    : null
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
  bufferPreviewForRenderer,
  actionProcessingTargets
)
const renderStats = computed(() => editorRenderer.renderStats.value)
let actionProcessingAnimationFrame: number | null = null

function stopActionProcessingAnimation() {
  if (actionProcessingAnimationFrame === null) return
  cancelAnimationFrame(actionProcessingAnimationFrame)
  actionProcessingAnimationFrame = null
}

function startActionProcessingAnimation() {
  if (actionProcessingAnimationFrame !== null) return

  const animate = () => {
    if (!actionProcessingTargets.value) {
      actionProcessingAnimationFrame = null
      nextTick(() => editorRenderer.render())
      return
    }

    editorRenderer.render()
    actionProcessingAnimationFrame = requestAnimationFrame(animate)
  }

  actionProcessingAnimationFrame = requestAnimationFrame(animate)
}

watch(actionProcessingTargets, (targets) => {
  if (targets) {
    startActionProcessingAnimation()
  } else {
    stopActionProcessingAnimation()
    nextTick(() => editorRenderer.render())
  }
}, { immediate: true, deep: true })

function getCommandContext() {
  return { canvasId: props.canvasId, session }
}

function executeCreateRelationFromDraft() {
  if (!isCanvasWritable.value) {
    editorUiStore.cancelRelationPicking()
    return
  }

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
  if (!isCanvasWritable.value) {
    editorUiStore.cancelRelationPicking()
    return
  }

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

function resolveSelectionFocusMode(options?: SelectionFocusOptions): SelectionFocusMode {
  if (options?.focusMode) return options.focusMode
  return options?.zoomToFit === false ? 'none' : 'context'
}

function hasExplicitSelectionFocusMode(options?: SelectionFocusOptions): boolean {
  return typeof options?.focusMode === 'string' || options?.zoomToFit === false
}

function syncCanvasSelection(regionId: string | null, baselineId: string | null): void {
  const canvas = canvasState.value
  if (!canvas) return
  if (canvas.selectedRegionId !== regionId) canvas.selectedRegionId = regionId
  if (canvas.selectedBaselineId !== baselineId) canvas.selectedBaselineId = baselineId
}

function getLocalSingleSelectedPolygonId(): string | null {
  if (selectedPolygonIds.value.length > 1) return null
  if (selectedPolygonIds.value.length === 1) return selectedPolygonIds.value[0] ?? null
  const index = selectedPolygonIndex.value
  if (index < 0 || index >= polygons.length) return null
  return polygons[index]?.id ?? null
}

function getLocalSingleSelectedPolylineId(): string | null {
  if (selectedPolylineIds.value.length > 1) return null
  if (selectedPolylineIds.value.length === 1) return selectedPolylineIds.value[0] ?? null
  const index = selectedPolylineIndex.value
  if (index < 0 || index >= polylines.length) return null
  return polylines[index]?.id ?? null
}

function handleSelectPolygon(polygonId: string | null, options?: SelectionFocusOptions) {
  if (!polygonId) {
    stateActions.clearSelection()
    syncCanvasSelection(null, null)
    return
  }
  const index = stateActions.selectPolygonById(polygonId)
  if (index >= 0) {
    const polygon = polygons[index]
    syncCanvasSelection(polygon?.id ?? null, null)
    const defaultFocusMode = resolveSelectionFocusMode(options)
    const focusMode = (
      !hasExplicitSelectionFocusMode(options)
      && isTextVisualMode.value
      && isTextlinePolygonType(polygon?.type)
    )
      ? 'none'
      : defaultFocusMode
    if (polygon && focusMode === 'context') {
      editorInteractions.centerViewOnPolygon(polygon)
    } else if (polygon && focusMode === 'fit-width') {
      editorInteractions.centerViewOnPolygonFitWidth(polygon)
    }
  } else {
    syncCanvasSelection(null, null)
  }
}

function handleSelectPolyline(polylineId: string | null, options?: SelectionFocusOptions) {
  if (!polylineId) {
    stateActions.clearSelection()
    syncCanvasSelection(null, null)
    return
  }
  const index = stateActions.selectPolylineById(polylineId)
  if (index >= 0 && resolveSelectionFocusMode(options) !== 'none') {
    const polyline = polylines[index]
    syncCanvasSelection(null, polyline?.id ?? null)
    if (polyline) {
      editorInteractions.centerViewOnPolyline(polyline)
    }
  } else if (index >= 0) {
    syncCanvasSelection(null, polylines[index]?.id ?? null)
  } else {
    syncCanvasSelection(null, null)
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

watch(
  () => [getLocalSingleSelectedPolygonId(), getLocalSingleSelectedPolylineId()] as const,
  ([polygonId, polylineId]) => {
    if (polygonId) {
      syncCanvasSelection(polygonId, null)
      return
    }
    if (polylineId) {
      syncCanvasSelection(null, polylineId)
      return
    }
    syncCanvasSelection(null, null)
  },
  { immediate: true }
)

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

function normalizeActionTargetViewMode(): 'default' | 'textline' | 'baseline' | undefined {
  const mode = canvasControls.viewMode?.value
  if (mode === 'default' || mode === 'textline' || mode === 'baseline') return mode
  return undefined
}

function buildActionTargetSelectionFromPolygon(polygon: RenderablePolygon): { targetSelection: ActionTargetSelection, targetSummary: string } | null {
  const currentPageId = pageId.value
  if (!currentPageId) return null

  if (isTextlinePolygonType(polygon.type)) {
    return {
      targetSelection: {
        type: 'TEXT_LINE',
        pages: [{ pageId: currentPageId, regionIds: [], textLineIds: [polygon.id] }]
      },
      targetSummary: `Textline ${polygon.label || polygon.id}`
    }
  }

  return {
    targetSelection: {
      type: 'REGION',
      pages: [{ pageId: currentPageId, regionIds: [polygon.id], textLineIds: [] }]
    },
    targetSummary: `${polygon.label || polygon.regionKind || 'Region'} ${polygon.id}`
  }
}

function buildPageActionTargetSelection(): { targetSelection: ActionTargetSelection, targetSummary: string } | null {
  const currentPageId = pageId.value
  if (!currentPageId) return null
  return {
    targetSelection: {
      type: 'PAGE',
      pages: [{ pageId: currentPageId, regionIds: [], textLineIds: [] }]
    },
    targetSummary: 'Current page'
  }
}

function dispatchActionTargetPicked(payload: { targetSelection: ActionTargetSelection, targetSummary: string }) {
  window.dispatchEvent(new CustomEvent('larex:editor-action-target', { detail: payload }))
}

function handleActionWandMouseDown(event: MouseEvent) {
  if (isCanvasInteractionBlocked.value) return
  if (!editorUiStore.actionWandActive || event.button !== 0 || !canvas.value) return
  if (!isCanvasWritable.value) {
    editorUiStore.setActionWandActive(false)
    return
  }

  event.preventDefault()
  event.stopImmediatePropagation()
  activateEditor()

  const point = getWorldCoordsFromEvent(event, canvas.value, view, aspectRatioScale.value)
  const clickedPolygonIndex = getVisiblePolygonAtPoint(
    polygons,
    point,
    -1,
    spatialIndex,
    normalizeActionTargetViewMode(),
    new Set(hiddenPolygonIds.value)
  )

  let payload: { targetSelection: ActionTargetSelection, targetSummary: string } | null = null

  if (clickedPolygonIndex >= 0) {
    const polygon = polygons[clickedPolygonIndex]
    if (polygon) {
      payload = buildActionTargetSelectionFromPolygon(polygon)
    }
  } else {
    payload = buildPageActionTargetSelection()
  }

  editorUiStore.setActionWandActive(false)
  if (payload) {
    dispatchActionTargetPicked(payload)
  } else {
    toast.add({
      title: 'Action target unavailable',
      description: 'Open a page before running an Action.',
      color: 'warning'
    })
  }
}

function handleActionWandKeyDown(event: KeyboardEvent) {
  if (isCanvasInteractionBlocked.value) return
  if (!editorUiStore.actionWandActive || event.key !== 'Escape') return
  event.preventDefault()
  event.stopImmediatePropagation()
  editorUiStore.setActionWandActive(false)
}

function handleBlockedCanvasPointerEvent(event: Event) {
  if (!isCanvasInteractionBlocked.value) return false
  event.preventDefault()
  event.stopPropagation()
  return true
}

function handleEditorWheel(event: WheelEvent) {
  if (handleBlockedCanvasPointerEvent(event)) return
  editorInteractions.onWheel(event)
}

function handleEditorMouseDown(event: MouseEvent) {
  if (handleBlockedCanvasPointerEvent(event)) return
  editorInteractions.onMouseDown(event)
}

function handleEditorDoubleClick(event: MouseEvent) {
  if (handleBlockedCanvasPointerEvent(event)) return
  editorInteractions.onDoubleClick(event)
}

function handleEditorMouseMove(event: MouseEvent) {
  if (isCanvasInteractionBlocked.value) return
  editorInteractions.onMouseMove(event)
}

function handleEditorMouseUp(event: MouseEvent) {
  if (isCanvasInteractionBlocked.value) return
  editorInteractions.onMouseUp(event)
}

function handleEditorMouseLeave() {
  if (isCanvasInteractionBlocked.value) return
  editorInteractions.onMouseLeave()
}

function handleEditorKeyDown(event: KeyboardEvent) {
  if (isCanvasInteractionBlocked.value) return
  editorInteractions.onKeyDown(event)
}

function attachInteractions() {
  if (interactionsAttached) return
  const el = canvas.value
  if (!el) return

  el.addEventListener('wheel', handleEditorWheel, { passive: false })
  el.addEventListener('mousedown', handleActionWandMouseDown, { capture: true })
  el.addEventListener('mousedown', activateEditor)
  el.addEventListener('mousedown', handleEditorMouseDown)
  el.addEventListener('dblclick', handleEditorDoubleClick)
  window.addEventListener('mousemove', handleEditorMouseMove)
  window.addEventListener('mouseup', handleEditorMouseUp)
  window.addEventListener('mouseleave', handleEditorMouseLeave)
  window.addEventListener('keydown', handleActionWandKeyDown, true)
  window.addEventListener('keydown', handleEditorKeyDown, true)

  interactionsAttached = true
}

function detachInteractions() {
  if (!interactionsAttached) return
  const el = canvas.value

  if (el) {
    el.removeEventListener('wheel', handleEditorWheel)
    el.removeEventListener('mousedown', handleActionWandMouseDown, { capture: true })
    el.removeEventListener('mousedown', handleEditorMouseDown)
    el.removeEventListener('mousedown', activateEditor)
    el.removeEventListener('dblclick', handleEditorDoubleClick)
  }
  window.removeEventListener('mousemove', handleEditorMouseMove)
  window.removeEventListener('mouseup', handleEditorMouseUp)
  window.removeEventListener('mouseleave', handleEditorMouseLeave)
  window.removeEventListener('keydown', handleActionWandKeyDown, true)
  window.removeEventListener('keydown', handleEditorKeyDown, true)

  interactionsAttached = false
}

function toScreenPoint(point: { x: number, y: number }): { x: number, y: number } | null {
  const imageSize = webglRenderer.imageSize.value
  if (!imageSize.width || !imageSize.height || !canvasDimensions.value.width || !canvasDimensions.value.height) {
    return null
  }

  const looksLikeWorldCoords = Math.abs(point.x) <= WORLD_COORD_THRESHOLD
    && Math.abs(point.y) <= WORLD_COORD_THRESHOLD
  const worldPoint = looksLikeWorldCoords ? point : imageToWorld(point, imageSize)
  const clipPoint = worldToClipCoords(worldPoint, view, aspectRatioScale.value)
  if (!Number.isFinite(clipPoint.x) || !Number.isFinite(clipPoint.y)) return null

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

function clamp(value: number, min: number, max: number): number {
  if (max < min) return min
  return Math.min(Math.max(value, min), max)
}

function normalizeCorrectionOverlayPosition(input: {
  left: number
  top: number
  viewportWidth: number
  viewportHeight: number
  overlayWidth: number
  overlayHeight: number
}) {
  const minLeft = CORRECTION_OVERLAY_MARGIN
  const maxLeft = input.viewportWidth - input.overlayWidth - CORRECTION_OVERLAY_MARGIN
  const minTop = CORRECTION_OVERLAY_MARGIN
  const maxTop = input.viewportHeight - input.overlayHeight - CORRECTION_OVERLAY_MARGIN

  const left = clamp(input.left, minLeft, maxLeft)
  const top = clamp(input.top, minTop, maxTop)
  const leftRange = Math.max(0, maxLeft - minLeft)
  const topRange = Math.max(0, maxTop - minTop)
  const xRatio = leftRange > 0 ? (left - minLeft) / leftRange : 0
  const yRatio = topRange > 0 ? (top - minTop) / topRange : 0

  return {
    left,
    top,
    xRatio,
    yRatio
  }
}

let textMeasureCanvasContext: CanvasRenderingContext2D | null = null

function getTextMeasureContext(): CanvasRenderingContext2D | null {
  if (!import.meta.client) return null
  if (textMeasureCanvasContext) return textMeasureCanvasContext

  const canvas = document.createElement('canvas')
  textMeasureCanvasContext = canvas.getContext('2d')
  return textMeasureCanvasContext
}

function measureOverlayTextWidthPx(text: string, fontSizePx: number): number {
  const normalizedText = text.length > 0 ? text : ' '
  const context = getTextMeasureContext()
  if (!context) {
    return normalizedText.length * fontSizePx * 0.62
  }

  context.font = `${Math.max(12, Math.round(fontSizePx))}px Junicode, serif`
  return context.measureText(normalizedText).width
}

const correctionTextareaRef = ref<HTMLTextAreaElement | null>(null)
const correctionInputValue = ref('')
const focusCorrectionInputQueued = ref(false)
const pendingCorrectionCommit = ref<{ textlineId: string, text: string } | null>(null)
const activeGtIndex = computed(() => editorStore.projectTextDefaultGtIndex ?? 0)
const activeRecognitionIndices = computed(() => editorStore.projectTextDefaultRecognitionIndices ?? [1])
const textViewSettings = computed(() => sessionStore.textViewSettings)
const selectedWorkspaceId = computed(() => workspaceStore.selectedWorkspaceId as string | null)
const hasProjectCodec = computed(() => {
  return Boolean(editorStore.projectCodecId) || (editorStore.projectCodecCharacters?.length ?? 0) > 0
})
const projectCodecCharacterSet = computed(() => new Set(editorStore.projectCodecCharacters ?? []))
const hasProjectDictionary = computed(() => Boolean(editorStore.projectDictionaryId))
const showDiffModel = computed({
  get: () => textViewSettings.value.showDiff,
  set: (next: boolean) => {
    sessionStore.updateTextViewSettings(current => ({
      ...current,
      showDiff: Boolean(next)
    }))
  }
})
const showCommentsModel = computed({
  get: () => textViewSettings.value.showComments,
  set: (next: boolean) => {
    sessionStore.updateTextViewSettings(current => ({
      ...current,
      showComments: Boolean(next)
    }))
  }
})
const showRecognitionInCorrectionOverlay = ref(true)
const highlightUnknownCodecCharsModel = computed({
  get: () => editorUiStore.highlightUnknownCodecChars,
  set: (next: boolean) => editorUiStore.setHighlightUnknownCodecChars(Boolean(next))
})
const highlightUnknownDictionaryTokensModel = computed({
  get: () => editorUiStore.highlightUnknownDictionaryTokens,
  set: (next: boolean) => editorUiStore.setHighlightUnknownDictionaryTokens(Boolean(next))
})
const canCheckDictionaryTokens = computed(() => {
  return Boolean(selectedWorkspaceId.value && editorStore.projectDictionaryId)
})

let dmpInstance: DiffMatchPatch | null = null
function getDmp(): DiffMatchPatch {
  if (!dmpInstance) {
    dmpInstance = new DiffMatchPatch()
  }
  return dmpInstance
}

const correctionFontSizePx = computed({
  get: () => {
    const current = Number(editorUiStore.textViewFontSize)
    if (!Number.isFinite(current)) return CORRECTION_FONT_DEFAULT
    return Math.min(CORRECTION_FONT_MAX, Math.max(CORRECTION_FONT_MIN, Math.trunc(current)))
  },
  set: (next: number) => {
    const clamped = Math.min(CORRECTION_FONT_MAX, Math.max(CORRECTION_FONT_MIN, Math.trunc(Number(next) || CORRECTION_FONT_DEFAULT)))
    editorUiStore.setTextViewFontSize(clamped)
  }
})

function adjustCorrectionFontSize(delta: number): void {
  correctionFontSizePx.value = correctionFontSizePx.value + delta
}

function resetCorrectionFontSize(): void {
  correctionFontSizePx.value = CORRECTION_FONT_DEFAULT
}

const correctionTextareaMinHeightPx = computed(() => {
  const lineHeightPx = Math.round(correctionFontSizePx.value * 1.2)
  return Math.max(40, lineHeightPx + 16)
})

const recognitionTextareaFontSizePx = computed(() => correctionFontSizePx.value)
const recognitionTextareaLineHeightPx = computed(() => Math.round(correctionFontSizePx.value * 1.2))
const recognitionTextareaMinHeightPx = computed(() => Math.max(40, recognitionTextareaLineHeightPx.value + 16))

function handleOpenKeyboardEditor(): void {
  const keyboardId = editorUiStore.selectedVirtualKeyboardId
  if (!keyboardId) {
    toast.add({ title: 'No virtual keyboard selected', color: 'warning' })
    return
  }
  navigateTo(`/virtual-keyboard/${keyboardId}`)
}

type RenderableTextlinePolygon = RenderablePolygon & {
  textContentVariants?: TextContentVariantData[] | undefined
}

const selectedTextlinePolygon = computed<RenderableTextlinePolygon | null>(() => {
  const selectedId = selectedPolygonIds.value.length === 1
    ? (selectedPolygonIds.value[0] ?? null)
    : null

  if (selectedId) {
    const selectedById = polygons.find(polygon => polygon.id === selectedId) ?? null
    if (isTextlinePolygonType(selectedById?.type)) return selectedById
  }

  const index = selectedPolygonIndex.value
  const hasIndexSelection = index >= 0 && index < polygons.length
  if (hasIndexSelection) {
    const polygon = polygons[index]
    if (!polygon || !isTextlinePolygonType(polygon.type)) return null
    return polygon
  }

  const hasPolylineSelection = selectedPolylineIds.value.length > 0 || selectedPolylineIndex.value >= 0
  if (hasPolylineSelection) return null

  const selectedFromCanvasState = selectedRegionId.value
  if (!selectedFromCanvasState) return null
  const fromCanvasState = polygons.find(polygon => polygon.id === selectedFromCanvasState) ?? null
  if (!isTextlinePolygonType(fromCanvasState?.type)) return null
  return fromCanvasState
})

const selectedTextlineScreenBounds = computed(() => {
  const polygon = selectedTextlinePolygon.value
  if (!polygon || polygon.points.length === 0) return null

  const screenPoints = polygon.points
    .map(point => toScreenPoint(point))
    .filter((point): point is { x: number, y: number } => point !== null)
  if (screenPoints.length === 0) return null

  let minX = Infinity
  let maxX = -Infinity
  let minY = Infinity
  let maxY = -Infinity

  for (const point of screenPoints) {
    minX = Math.min(minX, point.x)
    maxX = Math.max(maxX, point.x)
    minY = Math.min(minY, point.y)
    maxY = Math.max(maxY, point.y)
  }

  return { minX, maxX, minY, maxY }
})

const canvasTextCorrectionVisible = computed(() => {
  return isTextVisualMode.value && !!selectedTextlinePolygon.value
})

const correctionOverlayLayout = computed(() => {
  if (!canvasTextCorrectionVisible.value) return null

  const viewportWidth = canvasDimensions.value.width
  const viewportHeight = canvasDimensions.value.height
  if (!viewportWidth || !viewportHeight) return null

  const bounds = selectedTextlineScreenBounds.value
  if (!bounds) return null

  const maxOverlayWidth = Math.max(320, viewportWidth - 24)
  const preferredOverlayWidth = Math.max(
    420,
    (bounds.maxX - bounds.minX) + 24,
    correctionOverlayPreferredTextWidth.value
  )
  const overlayWidth = Math.min(preferredOverlayWidth, maxOverlayWidth)
  const recognitionRows = showRecognitionInCorrectionOverlay.value
    ? selectedTextlineRecognitionVariants.value.length
    : 0
  const hasLineComment = showCommentsModel.value && selectedTextlineComment.value.length > 0
  const hasCodecCheck = highlightUnknownCodecCharsModel.value && hasProjectCodec.value
  const hasDictionaryCheck = highlightUnknownDictionaryTokensModel.value && hasProjectDictionary.value
  const perRecognitionRowHeight = 78
    + (showDiffModel.value ? 58 : 0)
    + (showCommentsModel.value ? 42 : 0)
  const overlayHeightEstimate = 146
    + (hasLineComment ? 56 : 0)
    + (hasCodecCheck ? 64 : 0)
    + (hasDictionaryCheck ? 64 : 0)
    + (recognitionRows * perRecognitionRowHeight)
  const overlayHeight = Math.max(118, Math.min(viewportHeight - 12, overlayHeightEstimate))

  const overlayXRatio = Number(editorUiStore.canvasTextCorrectionOverlayXRatio)
  const overlayYRatio = Number(editorUiStore.canvasTextCorrectionOverlayYRatio)
  const hasStoredOverlayPosition = Number.isFinite(overlayXRatio) && Number.isFinite(overlayYRatio)
  const snapToLine = canvasTextCorrectionSnapToLine.value

  if (!snapToLine && hasStoredOverlayPosition) {
    const leftRange = Math.max(0, viewportWidth - overlayWidth - (CORRECTION_OVERLAY_MARGIN * 2))
    const topRange = Math.max(0, viewportHeight - overlayHeight - (CORRECTION_OVERLAY_MARGIN * 2))
    const normalized = normalizeCorrectionOverlayPosition({
      left: CORRECTION_OVERLAY_MARGIN + (leftRange * clamp(overlayXRatio, 0, 1)),
      top: CORRECTION_OVERLAY_MARGIN + (topRange * clamp(overlayYRatio, 0, 1)),
      viewportWidth,
      viewportHeight,
      overlayWidth,
      overlayHeight
    })

    return {
      left: normalized.left,
      top: normalized.top,
      width: overlayWidth,
      height: overlayHeight
    }
  }

  const placement = computeCanvasTextCorrectionPlacement({
    anchorBounds: bounds,
    viewport: {
      width: viewportWidth,
      height: viewportHeight
    },
    overlay: {
      width: overlayWidth,
      height: overlayHeight
    },
    margin: CORRECTION_OVERLAY_MARGIN
  })

  return {
    left: placement.left,
    top: placement.top,
    width: overlayWidth,
    height: overlayHeight
  }
})

const correctionOverlayStyle = computed(() => {
  const layout = correctionOverlayLayout.value
  if (!layout) return null
  return {
    left: `${layout.left}px`,
    top: `${layout.top}px`,
    width: `${layout.width}px`
  }
})

type CorrectionOverlayDragState = {
  pointerId: number
  offsetX: number
  offsetY: number
  moved: boolean
}

const correctionOverlayDragState = ref<CorrectionOverlayDragState | null>(null)

function stopCorrectionOverlayDrag(): void {
  correctionOverlayDragState.value = null
  window.removeEventListener('pointermove', handleCorrectionOverlayPointerMove)
  window.removeEventListener('pointerup', handleCorrectionOverlayPointerUp)
  window.removeEventListener('pointercancel', handleCorrectionOverlayPointerUp)
}

function updateCorrectionOverlayPositionFromPointer(clientX: number, clientY: number): void {
  const dragState = correctionOverlayDragState.value
  const layout = correctionOverlayLayout.value
  const container = correctionOverlayContainerRef.value
  if (!dragState || !layout || !container) return

  const rect = container.getBoundingClientRect()
  const normalized = normalizeCorrectionOverlayPosition({
    left: clientX - rect.left - dragState.offsetX,
    top: clientY - rect.top - dragState.offsetY,
    viewportWidth: canvasDimensions.value.width,
    viewportHeight: canvasDimensions.value.height,
    overlayWidth: layout.width,
    overlayHeight: layout.height
  })

  editorUiStore.setCanvasTextCorrectionOverlayPosition(normalized.xRatio, normalized.yRatio)
}

function handleCorrectionOverlayPointerMove(event: PointerEvent): void {
  const dragState = correctionOverlayDragState.value
  if (!dragState || event.pointerId !== dragState.pointerId) return

  correctionOverlayDragState.value = {
    ...dragState,
    moved: true
  }
  updateCorrectionOverlayPositionFromPointer(event.clientX, event.clientY)
  event.preventDefault()
}

function handleCorrectionOverlayPointerUp(event: PointerEvent): void {
  const dragState = correctionOverlayDragState.value
  if (!dragState || event.pointerId !== dragState.pointerId) return

  if (dragState.moved) {
    updateCorrectionOverlayPositionFromPointer(event.clientX, event.clientY)
  }
  stopCorrectionOverlayDrag()
}

function handleCorrectionOverlayPointerDown(event: PointerEvent): void {
  if (event.button !== 0) return
  const layout = correctionOverlayLayout.value
  const container = correctionOverlayContainerRef.value
  if (!layout || !container) return

  if (canvasTextCorrectionSnapToLine.value) {
    const normalized = normalizeCorrectionOverlayPosition({
      left: layout.left,
      top: layout.top,
      viewportWidth: canvasDimensions.value.width,
      viewportHeight: canvasDimensions.value.height,
      overlayWidth: layout.width,
      overlayHeight: layout.height
    })
    editorUiStore.setCanvasTextCorrectionOverlayPosition(normalized.xRatio, normalized.yRatio)
    canvasTextCorrectionSnapToLine.value = false
  }

  const rect = container.getBoundingClientRect()
  correctionOverlayDragState.value = {
    pointerId: event.pointerId,
    offsetX: event.clientX - rect.left - layout.left,
    offsetY: event.clientY - rect.top - layout.top,
    moved: false
  }

  window.addEventListener('pointermove', handleCorrectionOverlayPointerMove)
  window.addEventListener('pointerup', handleCorrectionOverlayPointerUp)
  window.addEventListener('pointercancel', handleCorrectionOverlayPointerUp)
  event.preventDefault()
}

function toggleCorrectionOverlaySnapToLine(): void {
  const next = !canvasTextCorrectionSnapToLine.value
  if (!next) {
    const layout = correctionOverlayLayout.value
    if (layout) {
      const normalized = normalizeCorrectionOverlayPosition({
        left: layout.left,
        top: layout.top,
        viewportWidth: canvasDimensions.value.width,
        viewportHeight: canvasDimensions.value.height,
        overlayWidth: layout.width,
        overlayHeight: layout.height
      })
      editorUiStore.setCanvasTextCorrectionOverlayPosition(normalized.xRatio, normalized.yRatio)
    }
  }
  canvasTextCorrectionSnapToLine.value = next
}

function zoomCorrectionOverlayToFit(): void {
  const textlineId = selectedTextlinePolygon.value?.id ?? null
  if (!textlineId) return
  canvasControls.selectPolygonById?.(textlineId, { focusMode: 'fit-width' })
}

function getPersistedCorrectionZoom(): number | null {
  const parsed = Number(editorUiStore.canvasTextCorrectionZoom)
  if (!Number.isFinite(parsed)) return null
  return clamp(parsed, ZOOM.MIN, ZOOM.MAX)
}

function withSuppressedCorrectionZoomPreferenceUpdate(action: () => void): void {
  suppressCorrectionZoomPreferenceUpdate.value = true
  try {
    action()
  } finally {
    nextTick(() => {
      suppressCorrectionZoomPreferenceUpdate.value = false
    })
  }
}

function centerCorrectionViewOnTextlineWithZoom(
  polygon: { points: Array<{ x: number, y: number }> } | null | undefined,
  zoom: number
): void {
  if (!polygon || !Array.isArray(polygon.points) || polygon.points.length === 0) return
  const imageSize = webglRenderer.imageSize.value
  if (!imageSize.width || !imageSize.height) return

  let minX = Infinity
  let maxX = -Infinity
  let minY = Infinity
  let maxY = -Infinity

  for (const point of polygon.points) {
    const looksLikeWorldCoords = Math.abs(point.x) <= WORLD_COORD_THRESHOLD
      && Math.abs(point.y) <= WORLD_COORD_THRESHOLD
    const worldPoint = looksLikeWorldCoords ? point : imageToWorld(point, imageSize)
    minX = Math.min(minX, worldPoint.x)
    maxX = Math.max(maxX, worldPoint.x)
    minY = Math.min(minY, worldPoint.y)
    maxY = Math.max(maxY, worldPoint.y)
  }

  const normalizedZoom = clamp(zoom, ZOOM.MIN, ZOOM.MAX)
  mouseInteraction.setView({
    zoom: normalizedZoom,
    offsetX: -(((minX + maxX) / 2) * normalizedZoom),
    offsetY: -(((minY + maxY) / 2) * normalizedZoom)
  })
}

const selectedTextlineGtText = computed(() => {
  const polygon = selectedTextlinePolygon.value
  if (!polygon) return ''
  const variants = normalizeEditableTextVariants(polygon.textContentVariants)
  const gt = variants.find(variant => variant.index === activeGtIndex.value)
  return gt?.unicode ?? ''
})

type OverlayDiffSegment = {
  text: string
  type: 'equal' | 'insert' | 'delete'
}

type OverlayRecognitionVariant = {
  key: string
  label: string
  unicode: string
  confidence?: number
  comments?: string
  diff: OverlayDiffSegment[]
}

function getConfidencePercent(confidence: number | undefined): number | undefined {
  if (typeof confidence !== 'number' || !Number.isFinite(confidence)) return undefined
  return Math.round(confidence * 100)
}

function getConfidenceClass(confidence: number | undefined): string {
  if (typeof confidence !== 'number' || !Number.isFinite(confidence)) return ''
  if (confidence > 0.9) return 'text-emerald-600 border-emerald-200 bg-emerald-50'
  if (confidence > 0.7) return 'text-amber-600 border-amber-200 bg-amber-50'
  return 'text-rose-600 border-rose-200 bg-rose-50'
}

function renderDiff(diffs: Diff[] | undefined): OverlayDiffSegment[] {
  if (!diffs) return []
  return diffs.map(diff => ({
    text: diff[1],
    type: diff[0] === 0 ? 'equal' : diff[0] === 1 ? 'insert' : 'delete'
  }))
}

const selectedTextlineComment = computed(() => {
  const comment = selectedTextlinePolygon.value?.comments
  return typeof comment === 'string' ? comment.trim() : ''
})

const selectedTextlineRecognitionVariants = computed<OverlayRecognitionVariant[]>(() => {
  const polygon = selectedTextlinePolygon.value
  if (!polygon) return []

  const recognitionIndices = [...new Set(activeRecognitionIndices.value)]
  if (recognitionIndices.length === 0) return []

  const variants = normalizeEditableTextVariants(polygon.textContentVariants)
  const gtText = normalizeSingleLineText(correctionInputValue.value)
  const dmp = getDmp()

  return variants
    .filter((variant) => {
      if (typeof variant.index === 'number') {
        if (variant.index === activeGtIndex.value) return false
        return recognitionIndices.includes(variant.index)
      }
      return recognitionIndices.includes(-1)
    })
    .map((variant, pos) => {
      const unicode = variant.unicode ?? ''
      const diffs = dmp.diff_main(gtText, unicode)
      dmp.diff_cleanupSemantic(diffs)
      const label = typeof variant.index === 'number' ? `REC #${variant.index}` : 'REC (unindexed)'

      return {
        key: `${typeof variant.index === 'number' ? variant.index : 'u'}_${pos}`,
        label,
        unicode,
        confidence: variant.confidence,
        comments: variant.comments?.trim(),
        diff: renderDiff(diffs)
      }
    })
})

const correctionOverlayPreferredTextWidth = computed(() => {
  const textSamples: string[] = [normalizeSingleLineText(correctionInputValue.value)]
  if (showRecognitionInCorrectionOverlay.value) {
    for (const recognition of selectedTextlineRecognitionVariants.value) {
      textSamples.push(normalizeSingleLineText(recognition.unicode))
    }
  }

  const widestTextPx = textSamples.reduce((maxWidth, text) =>
    Math.max(maxWidth, measureOverlayTextWidthPx(text, correctionFontSizePx.value)), 0)

  // Padding + caret/scrollbar room so single-line text remains visible.
  return Math.max(420, Math.ceil(widestTextPx + 84))
})

type OverlayUnknownSegment = {
  text: string
  unknown: boolean
}

function splitCodepoints(text: string): string[] {
  return Array.from(text ?? '')
}

function isWhitespaceCharacter(char: string): boolean {
  return /\s/u.test(char)
}

function isUnknownCodecCharacter(char: string): boolean {
  if (!highlightUnknownCodecCharsModel.value) return false
  if (!editorUiStore.includeWhitespaceInCodecHighlight && isWhitespaceCharacter(char)) return false
  return !projectCodecCharacterSet.value.has(char)
}

function getUnknownCodecSegments(text: string): OverlayUnknownSegment[] {
  if (!highlightUnknownCodecCharsModel.value) {
    return [{ text, unknown: false }]
  }

  const chars = splitCodepoints(text)
  if (chars.length === 0) return [{ text: '', unknown: false }]

  const segments: OverlayUnknownSegment[] = []
  let currentUnknown = isUnknownCodecCharacter(chars[0] ?? '')
  let buffer = chars[0] ?? ''

  for (let i = 1; i < chars.length; i += 1) {
    const char = chars[i] ?? ''
    const unknown = isUnknownCodecCharacter(char)
    if (unknown === currentUnknown) {
      buffer += char
      continue
    }
    segments.push({ text: buffer, unknown: currentUnknown })
    buffer = char
    currentUnknown = unknown
  }

  segments.push({ text: buffer, unknown: currentUnknown })
  return segments
}

const gtCodecUnknownCount = computed(() => {
  return splitCodepoints(correctionInputValue.value).filter(char => isUnknownCodecCharacter(char)).length
})

const gtCodecUnknownSegments = computed(() => getUnknownCodecSegments(correctionInputValue.value))

type OverlayUnknownDictionarySegment = {
  text: string
  unknown: boolean
  start: number
  end: number
}

function getUnknownDictionaryTokenCount(text: string): number {
  const workspaceId = selectedWorkspaceId.value
  const dictionaryId = editorStore.projectDictionaryId
  if (!canCheckDictionaryTokens.value || !workspaceId || !dictionaryId) return 0

  return tokenizeForDictionary(text)
    .map(token => getTokenResult(workspaceId, dictionaryId, token))
    .filter(result => result && !result.known)
    .length
}

function getUnknownDictionaryTokenSegmentsFromLookup(text: string): OverlayUnknownDictionarySegment[] {
  const workspaceId = selectedWorkspaceId.value
  const dictionaryId = editorStore.projectDictionaryId
  if (!canCheckDictionaryTokens.value || !workspaceId || !dictionaryId) {
    return [{ text, unknown: false, start: 0, end: text.length }]
  }

  const segments: OverlayUnknownDictionarySegment[] = []
  let cursor = 0

  for (const token of tokenizeForDictionary(text)) {
    const index = text.indexOf(token, cursor)
    if (index < 0) continue

    if (index > cursor) {
      segments.push({ text: text.slice(cursor, index), unknown: false, start: cursor, end: index })
    }

    const result = getTokenResult(workspaceId, dictionaryId, token)
    segments.push({
      text: token,
      unknown: Boolean(result && !result.known),
      start: index,
      end: index + token.length
    })
    cursor = index + token.length
  }

  if (cursor < text.length) {
    segments.push({ text: text.slice(cursor), unknown: false, start: cursor, end: text.length })
  }

  return segments.length > 0 ? segments : [{ text, unknown: false, start: 0, end: text.length }]
}

const gtDictionaryTokens = computed(() => {
  return [...new Set(tokenizeForDictionary(correctionInputValue.value))]
})

const gtUnknownDictionaryTokenCount = computed(() => {
  return getUnknownDictionaryTokenCount(correctionInputValue.value)
})

const gtUnknownDictionarySegments = computed(() => {
  return getUnknownDictionaryTokenSegmentsFromLookup(correctionInputValue.value)
})

function handleUnknownDictionaryPopoverUpdate(open: boolean, token: string): void {
  if (!open) return
  const workspaceId = selectedWorkspaceId.value
  const dictionaryId = editorStore.projectDictionaryId
  if (!workspaceId || !dictionaryId) return

  void ensureTokenResults({
    workspaceId,
    dictionaryId,
    tokens: [token],
    includeSuggestions: true,
    limit: 5
  })
}

function isDictionarySuggestionLoading(token: string): boolean {
  const workspaceId = selectedWorkspaceId.value
  const dictionaryId = editorStore.projectDictionaryId
  if (!workspaceId || !dictionaryId) return false

  const result = getTokenResult(workspaceId, dictionaryId, token)
  if (!result) return true
  if (result.known) return false
  if (isTokenPending(workspaceId, dictionaryId, token)) return true
  return !hasSuggestionsLoaded(workspaceId, dictionaryId, token)
}

function getDictionarySuggestions(token: string): string[] {
  const workspaceId = selectedWorkspaceId.value
  const dictionaryId = editorStore.projectDictionaryId
  if (!workspaceId || !dictionaryId) return []

  const result = getTokenResult(workspaceId, dictionaryId, token)
  if (!result || result.known) return []
  const suggestions = result.suggestions ?? []
  return suggestions.map(suggestion => suggestion.display)
}

function applyDictionarySuggestionToGt(start: number, end: number, replacement: string): void {
  if (!isCanvasWritable.value) return
  const current = correctionInputValue.value
  const nextText = `${current.slice(0, start)}${replacement}${current.slice(end)}`
  correctionInputValue.value = nextText

  const textlineId = selectedTextlinePolygon.value?.id ?? null
  if (!textlineId) return
  pendingCorrectionCommit.value = {
    textlineId,
    text: nextText
  }
  commitCorrectionInputDebounced()
  queueCorrectionInputFocus()
}

const isDictionaryCheckLoadingForGt = computed(() => {
  const workspaceId = selectedWorkspaceId.value
  const dictionaryId = editorStore.projectDictionaryId
  if (!canCheckDictionaryTokens.value || !workspaceId || !dictionaryId) return false
  const tokens = gtDictionaryTokens.value
  if (tokens.length === 0) return false

  return tokens.some((token) => {
    const result = getTokenResult(workspaceId, dictionaryId, token)
    if (result) return false
    return isTokenPending(workspaceId, dictionaryId, token)
  })
})

const pageOrderTextlineIds = computed(() => collectTextlineIdsInPageOrder(session.document.value?.page?.regions))

function queueCorrectionInputFocus() {
  focusCorrectionInputQueued.value = true
  nextTick(() => {
    if (!focusCorrectionInputQueued.value) return
    const textarea = correctionTextareaRef.value
    if (!textarea) return
    textarea.focus()
    const end = textarea.value.length
    textarea.setSelectionRange(end, end)
    focusCorrectionInputQueued.value = false
  })
}

function commitTextlineVariants(textlineId: string, variants: Array<{
  unicode: string
  plainText?: string
  confidence?: number
  index?: number
  dataType?: string
  dataTypeDetails?: string
  comments?: string
}>): void {
  if (!isCanvasWritable.value) return

  const textlineCommand = new UpdateTextContentVariantsCommand({
    elementId: textlineId,
    nextTextContentVariants: variants
  })
  const parentRegionSyncCommand = buildParentRegionSyncCommandForTextline(textlineId, variants)

  if (parentRegionSyncCommand) {
    canvasControls.commander.execute(
      new CompoundCommand(
        [textlineCommand, parentRegionSyncCommand],
        'Update textline GT and sync parent region GT'
      ),
      getCommandContext()
    )
    return
  }

  canvasControls.commander.execute(textlineCommand, getCommandContext())
}

function buildParentRegionSyncCommandForTextline(
  textlineId: string,
  nextTextlineVariants: TextContentVariantData[] | undefined
): UpdateTextContentVariantsCommand | null {
  const pageRegions = session.document.value?.page?.regions
  if (!pageRegions) return null

  const textlineHit = findTextLineRecursive(pageRegions, textlineId)
  if (!textlineHit) return null

  const parentTextRegion = textlineHit.parentTextRegion
  const parentRegionId = parentTextRegion.id
  if (!parentRegionId) return null

  const syncedTextLines = (parentTextRegion.textLines ?? []).map((textline) => {
    if (textline.id !== textlineId) return textline
    return {
      ...textline,
      textContentVariants: nextTextlineVariants
    }
  })

  const nextGtText = composeRegionGtFromTextLines(syncedTextLines, activeGtIndex.value)
  const nextRegionVariants = buildRegionGtSyncedVariants(
    parentTextRegion.textContentVariants as TextContentVariantData[] | undefined,
    nextGtText,
    activeGtIndex.value
  )
  const currentNormalized = normalizeEditableTextVariants(parentTextRegion.textContentVariants)
  const nextNormalized = normalizeEditableTextVariants(nextRegionVariants)
  if (JSON.stringify(currentNormalized) === JSON.stringify(nextNormalized)) return null

  return new UpdateTextContentVariantsCommand({
    elementId: parentRegionId,
    nextTextContentVariants: nextRegionVariants
  })
}

function ensureSelectedTextlineGtVariant(): boolean {
  const polygon = selectedTextlinePolygon.value
  if (!polygon || !isCanvasWritable.value) return false

  const ensured = ensureGtVariantAtIndex(
    polygon.textContentVariants,
    activeGtIndex.value
  )
  if (!ensured.created) return false

  commitTextlineVariants(polygon.id, ensured.variants)
  correctionInputValue.value = ensured.variants[ensured.gtPos]?.unicode ?? ''
  return true
}

function normalizeSingleLineText(value: string): string {
  return value.replace(/[ \t]*\r?\n+[ \t]*/g, ' ')
}

function updateTextlineGtTextById(textlineId: string, nextText: string) {
  const polygon = polygons.find(item => item.id === textlineId) as RenderableTextlinePolygon | undefined
  if (!polygon || !isCanvasWritable.value) return

  const normalizedText = normalizeSingleLineText(nextText)
  const updated = setGtVariantUnicode(
    polygon.textContentVariants,
    activeGtIndex.value,
    normalizedText
  )
  if (!updated.changed && !updated.created) return

  commitTextlineVariants(textlineId, updated.variants)
}

const commitCorrectionInputDebounced = useDebounceFn(() => {
  const pending = pendingCorrectionCommit.value
  if (!pending) return
  updateTextlineGtTextById(pending.textlineId, pending.text)
  pendingCorrectionCommit.value = null
}, 80)

function flushPendingCorrectionCommit() {
  const pending = pendingCorrectionCommit.value
  if (!pending) return
  updateTextlineGtTextById(pending.textlineId, pending.text)
  pendingCorrectionCommit.value = null
}

function navigateCanvasCorrectionTextline(direction: 1 | -1): boolean {
  flushPendingCorrectionCommit()
  const currentId = selectedTextlinePolygon.value?.id ?? null
  const nextId = getAdjacentTextlineId(pageOrderTextlineIds.value, currentId, direction)
  if (!nextId || nextId === currentId) return false

  canvasControls.selectPolylineById?.(null, { focusMode: 'none' })
  canvasControls.selectPolygonById?.(nextId, { focusMode: 'none' })
  queueCorrectionInputFocus()
  return true
}

function handleCorrectionTextareaKeydown(event: KeyboardEvent) {
  if (event.key === 'Tab') {
    event.preventDefault()
    navigateCanvasCorrectionTextline(event.shiftKey ? -1 : 1)
    return
  }

  if (event.key === 'Enter') {
    event.preventDefault()
  }
}

function handleCorrectionReadonlyKeydown(event: KeyboardEvent) {
  if (event.key !== 'Tab') return
  event.preventDefault()
  navigateCanvasCorrectionTextline(event.shiftKey ? -1 : 1)
}

function handleCorrectionOverlayKeydown(event: KeyboardEvent) {
  if (event.key !== 'Tab') return
  event.preventDefault()
  event.stopPropagation()
  navigateCanvasCorrectionTextline(event.shiftKey ? -1 : 1)
}

function handleCorrectionTextareaBeforeInput(event: Event) {
  if (event instanceof InputEvent) {
    handleSingleLineTextareaBeforeInput(event, true)
  }
}

function handleCorrectionTextareaPaste(event: ClipboardEvent) {
  handleSingleLineTextareaPaste(event, true)
}

function handleCorrectionTextareaDrop(event: DragEvent) {
  handleSingleLineTextareaDrop(event, true)
}

function handleCorrectionTextareaInput(event: Event) {
  const target = event.target
  if (!(target instanceof HTMLTextAreaElement)) return
  correctionInputValue.value = target.value

  const textlineId = selectedTextlinePolygon.value?.id ?? null
  if (!textlineId) return
  pendingCorrectionCommit.value = {
    textlineId,
    text: target.value
  }
  commitCorrectionInputDebounced()
}

function handleCorrectionTextareaBlur() {
  flushPendingCorrectionCommit()
}

watch(selectedTextlineGtText, (nextText) => {
  correctionInputValue.value = nextText
}, { immediate: true })

watch(
  [
    highlightUnknownDictionaryTokensModel,
    canCheckDictionaryTokens,
    selectedWorkspaceId,
    () => editorStore.projectDictionaryId,
    gtDictionaryTokens
  ],
  async ([dictionaryHighlightEnabled, canCheck, workspaceId, dictionaryId, tokens]) => {
    if (!dictionaryHighlightEnabled || !canCheck || !workspaceId || !dictionaryId || !Array.isArray(tokens) || tokens.length === 0) {
      return
    }
    try {
      await ensureTokenResults({
        workspaceId,
        dictionaryId,
        tokens,
        includeSuggestions: false
      })
    } catch {
      // Keep correction input responsive even if dictionary checks fail.
    }
  },
  { immediate: true }
)

watch(
  () => [isTextVisualMode.value, selectedTextlinePolygon.value?.id ?? null, activeGtIndex.value] as const,
  ([enabled, selectedId, gtIndex], [prevEnabled, prevSelectedId, prevGtIndex]) => {
    if (!enabled || !selectedId) return

    const selectionChanged = selectedId !== prevSelectedId
    const justEnabled = enabled && !prevEnabled
    const gtIndexChanged = gtIndex !== prevGtIndex
    if (!selectionChanged && !justEnabled && !gtIndexChanged) return

    if (isCanvasWritable.value && (selectionChanged || justEnabled || gtIndexChanged)) {
      ensureSelectedTextlineGtVariant()
    }

    if (selectionChanged || justEnabled) {
      const persistedCorrectionZoom = getPersistedCorrectionZoom()
      if (persistedCorrectionZoom !== null) {
        withSuppressedCorrectionZoomPreferenceUpdate(() => {
          canvasControls.selectPolygonById?.(selectedId, { focusMode: 'none' })
          const polygon = selectedTextlinePolygon.value
          centerCorrectionViewOnTextlineWithZoom(polygon, persistedCorrectionZoom)
        })
      } else {
        withSuppressedCorrectionZoomPreferenceUpdate(() => {
          canvasControls.selectPolygonById?.(selectedId, { focusMode: 'fit-width' })
        })
      }
      queueCorrectionInputFocus()
    }
  }
)

watch(
  () => [canvasTextCorrectionVisible.value, correctionOverlayStyle.value, view.zoom] as const,
  ([visible, overlayStyle, zoom]) => {
    if (!visible || !overlayStyle || suppressCorrectionZoomPreferenceUpdate.value) return
    if (!Number.isFinite(zoom)) return
    editorUiStore.setCanvasTextCorrectionZoom(clamp(zoom, ZOOM.MIN, ZOOM.MAX))
  }
)

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
    () => [effectiveUiMode.value, isTextVisualMode.value, isCanvasEditable.value] as const,
    ([mode, visualTextMode, editable]) => {
      if ((mode === 'text' && !visualTextMode) || !editable) {
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

  stopCorrectionOverlayDrag()
  stopActionProcessingAnimation()
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
      v-else-if="pageLockReason"
      class="flex min-h-10 items-center justify-between gap-3 border-b border-amber-950/60 bg-[#2b1d12] px-3 py-2 text-[13px] text-amber-50"
    >
      <div class="flex min-w-0 items-center gap-2.5">
        <div class="flex h-6 w-6 shrink-0 items-center justify-center rounded-full bg-amber-500/12 text-amber-400">
          <Icon name="i-lucide-lock" class="h-3.5 w-3.5" />
        </div>
        <p class="truncate text-[13px] text-amber-50/90">
          Read-only view
        </p>
      </div>

      <div class="flex min-w-0 shrink items-center justify-end gap-2">
        <span class="truncate text-[13px] text-amber-50/70">{{ pageLockDescription }}</span>
        <UBadge
          v-if="pageLockActionName"
          color="warning"
          variant="subtle"
          size="sm"
          class="max-w-80 truncate"
        >
          {{ pageLockActionName }}
        </UBadge>
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

    <div ref="correctionOverlayContainerRef" class="relative flex-1 min-h-0" :class="{ 'editor-checkerboard': showCheckerboard }">
      <div class="absolute inset-0 pointer-events-none" :style="{ backgroundColor: editorBackgroundColor }" />
      <UContextMenu
        v-model:open="contextMenuOpen"
        :items="contextMenuItems"
      >
        <template #default>
          <canvas
            ref="canvas"
            class="block w-full h-full bg-transparent relative z-10"
            :class="[
              isCanvasEditable ? (isCanvasWritable ? 'cursor-grab' : 'cursor-default') : 'cursor-default pointer-events-none'
            ]"
            @contextmenu="(event) => { if (isCanvasWritable && !isCanvasInteractionBlocked) editorInteractions.handleCanvasContextMenu(event) }"
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

      <div
        v-if="isCanvasInteractionBlocked"
        class="absolute inset-0 z-[960] cursor-default"
        aria-hidden="true"
        @wheel.prevent.stop
        @mousedown.prevent.stop
        @mouseup.prevent.stop
        @mousemove.prevent.stop
        @dblclick.prevent.stop
        @contextmenu.prevent.stop
        @pointerdown.prevent.stop
        @pointermove.prevent.stop
        @pointerup.prevent.stop
        @pointercancel.prevent.stop
        @scroll.prevent.stop
      />

      <div
        v-if="canvasTextCorrectionVisible && correctionOverlayStyle"
        class="absolute z-[930] max-h-[72vh] overflow-y-auto rounded-md border border-default bg-default p-2.5 shadow-xl"
        :style="correctionOverlayStyle"
        @mousedown.stop
        @click.stop
        @keydown.capture="handleCorrectionOverlayKeydown"
      >
        <div class="mb-1.5 flex items-center justify-between gap-2 text-[11px]">
          <div class="flex items-center gap-2">
            <button
              type="button"
              class="inline-flex h-6 w-6 items-center justify-center rounded-sm text-muted transition hover:bg-muted/60 hover:text-default cursor-move"
              title="Drag overlay"
              @pointerdown.stop.prevent="handleCorrectionOverlayPointerDown"
            >
              <Icon name="i-lucide-grip-horizontal" class="h-3.5 w-3.5" />
            </button>
            <UButton
              size="xs"
              color="neutral"
              :variant="canvasTextCorrectionSnapToLine ? 'soft' : 'ghost'"
              :icon="canvasTextCorrectionSnapToLine ? 'i-lucide-link-2' : 'i-lucide-unlink-2'"
              :title="canvasTextCorrectionSnapToLine ? 'Snap to selected line is enabled' : 'Snap to selected line is disabled'"
              @click="toggleCorrectionOverlaySnapToLine"
            >
              Snap
            </UButton>
            <UButton
              size="xs"
              color="neutral"
              variant="ghost"
              icon="i-lucide-scan-search"
              title="Zoom to fit the selected line"
              @click="zoomCorrectionOverlayToFit"
            >
              Fit
            </UButton>
            <span class="font-medium text-muted">GT #{{ activeGtIndex }}</span>
            <span class="text-muted">{{ correctionFontSizePx }}px</span>
            <div class="flex items-center gap-1">
              <UButton
                size="xs"
                variant="ghost"
                color="neutral"
                icon="i-lucide-minus"
                title="Decrease font size"
                :disabled="correctionFontSizePx <= CORRECTION_FONT_MIN"
                @click="adjustCorrectionFontSize(-2)"
              />
              <UButton
                size="xs"
                variant="ghost"
                color="neutral"
                icon="i-lucide-plus"
                title="Increase font size"
                :disabled="correctionFontSizePx >= CORRECTION_FONT_MAX"
                @click="adjustCorrectionFontSize(2)"
              />
              <UButton
                size="xs"
                variant="ghost"
                color="neutral"
                icon="i-lucide-rotate-ccw"
                title="Reset font size"
                @click="resetCorrectionFontSize"
              />
            </div>
          </div>
          <div class="flex items-center gap-2 min-w-0">
            <UButton
              size="xs"
              variant="ghost"
              color="neutral"
              icon="i-lucide-keyboard"
              title="Open virtual keyboard editor"
              @click="handleOpenKeyboardEditor"
            />
            <span class="truncate text-muted max-w-56">{{ selectedTextlinePolygon?.id }}</span>
          </div>
        </div>
        <div class="mb-2 flex flex-wrap items-center gap-1.5">
          <UButton
            size="xs"
            color="neutral"
            :variant="showDiffModel ? 'soft' : 'ghost'"
            @click="showDiffModel = !showDiffModel"
          >
            Diff
          </UButton>
          <UButton
            size="xs"
            color="neutral"
            :variant="showCommentsModel ? 'soft' : 'ghost'"
            @click="showCommentsModel = !showCommentsModel"
          >
            Comments
          </UButton>
          <UButton
            size="xs"
            color="neutral"
            :variant="showRecognitionInCorrectionOverlay ? 'soft' : 'ghost'"
            @click="showRecognitionInCorrectionOverlay = !showRecognitionInCorrectionOverlay"
          >
            Recognition
          </UButton>
          <UButton
            size="xs"
            color="neutral"
            :variant="highlightUnknownCodecCharsModel ? 'soft' : 'ghost'"
            :disabled="!hasProjectCodec"
            :title="hasProjectCodec ? 'Toggle codec checks' : 'No project codec configured'"
            @click="highlightUnknownCodecCharsModel = !highlightUnknownCodecCharsModel"
          >
            Codec
          </UButton>
          <UButton
            size="xs"
            color="neutral"
            :variant="highlightUnknownDictionaryTokensModel ? 'soft' : 'ghost'"
            :disabled="!hasProjectDictionary"
            :title="hasProjectDictionary ? 'Toggle dictionary checks' : 'No project dictionary configured'"
            @click="highlightUnknownDictionaryTokensModel = !highlightUnknownDictionaryTokensModel"
          >
            Dictionary
          </UButton>
        </div>
        <textarea
          ref="correctionTextareaRef"
          :value="correctionInputValue"
          rows="1"
          wrap="off"
          class="w-full min-h-10 resize-none overflow-x-auto whitespace-nowrap rounded-sm border border-emerald-300 bg-emerald-100/95 px-2.5 py-2 font-junicode text-default outline-none transition focus:border-primary/50 focus:ring-1 focus:ring-primary/20 dark:bg-emerald-900/90"
          :style="{ fontSize: `${correctionFontSizePx}px`, lineHeight: `${Math.round(correctionFontSizePx * 1.2)}px`, minHeight: `${correctionTextareaMinHeightPx}px` }"
          :readonly="!isCanvasWritable"
          :disabled="!isCanvasWritable"
          spellcheck="false"
          @keydown="handleCorrectionTextareaKeydown"
          @beforeinput="handleCorrectionTextareaBeforeInput"
          @paste="handleCorrectionTextareaPaste"
          @drop="handleCorrectionTextareaDrop"
          @input="handleCorrectionTextareaInput"
          @blur="handleCorrectionTextareaBlur"
        />

        <div
          v-if="highlightUnknownCodecCharsModel && hasProjectCodec"
          class="mt-2 rounded-sm border border-default bg-muted/20 p-2"
        >
          <div class="mb-1 flex items-center justify-between gap-2">
            <span class="text-xs text-muted">Codec check</span>
            <UBadge
              :color="gtCodecUnknownCount > 0 ? 'warning' : 'success'"
              variant="soft"
              size="xs"
            >
              {{ gtCodecUnknownCount }} unknown
            </UBadge>
          </div>
          <div
            class="break-all font-junicode text-sm"
            :style="{ fontSize: `${Math.max(14, Math.round(correctionFontSizePx * 0.68))}px`, lineHeight: `${Math.max(18, Math.round(correctionFontSizePx * 0.8))}px` }"
          >
            <template v-for="(segment, segmentIndex) in gtCodecUnknownSegments" :key="`gt_codec_${segmentIndex}`">
              <span
                v-if="segment.unknown"
                class="rounded-sm bg-warning/20 px-0.5 text-warning-700 dark:text-warning-300"
              >
                {{ segment.text }}
              </span>
              <span v-else>{{ segment.text }}</span>
            </template>
          </div>
        </div>

        <div
          v-if="highlightUnknownDictionaryTokensModel && hasProjectDictionary"
          class="mt-2 rounded-sm border border-default bg-muted/20 p-2"
        >
          <div class="mb-1 flex items-center justify-between gap-2">
            <span class="text-xs text-muted">Dictionary check</span>
            <USkeleton v-if="isDictionaryCheckLoadingForGt" class="h-5 w-16" />
            <UBadge
              v-else
              :color="gtUnknownDictionaryTokenCount > 0 ? 'warning' : 'success'"
              variant="soft"
              size="xs"
            >
              {{ gtUnknownDictionaryTokenCount }} unknown
            </UBadge>
          </div>
          <div
            class="break-words font-junicode text-sm"
            :style="{ fontSize: `${Math.max(14, Math.round(correctionFontSizePx * 0.68))}px`, lineHeight: `${Math.max(18, Math.round(correctionFontSizePx * 0.8))}px` }"
          >
            <template v-for="(segment, segmentIndex) in gtUnknownDictionarySegments" :key="`gt_dict_${segmentIndex}`">
              <UPopover
                v-if="segment.unknown"
                mode="hover"
                :content="{ side: 'top', align: 'start', sideOffset: 8 }"
                @update:open="(open: boolean) => handleUnknownDictionaryPopoverUpdate(open, segment.text)"
              >
                <span class="cursor-help text-warning-700 underline decoration-warning decoration-2 underline-offset-2 dark:text-warning-300">
                  {{ segment.text }}
                </span>
                <template #content>
                  <div class="min-w-56 max-w-96 space-y-2 p-2">
                    <div class="text-xs font-medium text-muted">
                      Dictionary suggestions
                    </div>
                    <div v-if="isDictionarySuggestionLoading(segment.text)" class="space-y-2">
                      <USkeleton class="h-6 w-full" />
                      <USkeleton class="h-6 w-2/3" />
                    </div>
                    <div v-else-if="getDictionarySuggestions(segment.text).length > 0" class="flex flex-wrap gap-1">
                      <UButton
                        v-for="suggestion in getDictionarySuggestions(segment.text)"
                        :key="`${segment.text}_${suggestion}`"
                        color="neutral"
                        variant="soft"
                        size="xs"
                        :disabled="!isCanvasWritable"
                        @click.stop="applyDictionarySuggestionToGt(segment.start, segment.end, suggestion)"
                      >
                        {{ suggestion }}
                      </UButton>
                    </div>
                    <div v-else class="text-xs text-muted">
                      No suggestions available.
                    </div>
                  </div>
                </template>
              </UPopover>
              <span v-else>{{ segment.text }}</span>
            </template>
          </div>
        </div>

        <div
          v-if="showCommentsModel && selectedTextlineComment.length > 0"
          class="mt-2 rounded-sm border border-amber-200/70 bg-amber-50/70 px-2 py-1.5 dark:border-amber-800/70 dark:bg-amber-950/25"
        >
          <p class="text-[11px] font-medium uppercase tracking-wide text-amber-800/90 dark:text-amber-200/90">
            Comment
          </p>
          <p class="mt-0.5 whitespace-pre-wrap break-words text-xs text-amber-900/90 dark:text-amber-100/90">
            {{ selectedTextlineComment }}
          </p>
        </div>

        <div v-if="showRecognitionInCorrectionOverlay" class="mt-2 space-y-2">
          <div
            v-for="recognition in selectedTextlineRecognitionVariants"
            :key="recognition.key"
            class="rounded-sm border border-default bg-elevated/60 p-2"
          >
            <div class="mb-1 flex items-center justify-between gap-2 text-[11px]">
              <span class="font-medium text-muted">{{ recognition.label }}</span>
              <UBadge
                v-if="getConfidencePercent(recognition.confidence) !== undefined"
                variant="outline"
                class="text-[10px] px-1.5 py-0"
                :class="getConfidenceClass(recognition.confidence)"
              >
                {{ getConfidencePercent(recognition.confidence) }}%
              </UBadge>
            </div>

            <textarea
              :value="recognition.unicode"
              rows="1"
              wrap="off"
              class="w-full min-h-9 resize-none overflow-x-auto whitespace-nowrap rounded-sm border border-default bg-default px-2 py-1.5 font-junicode text-default/95 outline-none"
              :style="{ fontSize: `${recognitionTextareaFontSizePx}px`, lineHeight: `${recognitionTextareaLineHeightPx}px`, minHeight: `${recognitionTextareaMinHeightPx}px` }"
              readonly
              spellcheck="false"
              @keydown="handleCorrectionReadonlyKeydown"
            />

            <div
              v-if="showDiffModel && recognition.diff.length > 0"
              class="mt-1 rounded-sm border border-default bg-muted/30 p-1.5 font-mono text-xs"
            >
              <template v-for="(segment, segmentIndex) in recognition.diff" :key="`${recognition.key}_${segment.type}_${segmentIndex}`">
                <span v-if="segment.type === 'equal'" class="text-default">{{ segment.text }}</span>
                <span v-else-if="segment.type === 'delete'" class="rounded bg-red-500/10 px-0.5 text-red-500 line-through">
                  {{ segment.text }}
                </span>
                <span v-else class="rounded-sm bg-green-500/10 px-0.5 font-semibold text-green-500">
                  +{{ segment.text }}
                </span>
              </template>
            </div>

            <div
              v-if="showCommentsModel && recognition.comments && recognition.comments.length > 0"
              class="mt-1 rounded-sm border border-amber-200/70 bg-amber-50/70 px-2 py-1 dark:border-amber-800/70 dark:bg-amber-950/25"
            >
              <p class="text-[11px] font-medium uppercase tracking-wide text-amber-800/90 dark:text-amber-200/90">
                Variant Comment
              </p>
              <p class="mt-0.5 whitespace-pre-wrap break-words text-xs text-amber-900/90 dark:text-amber-100/90">
                {{ recognition.comments }}
              </p>
            </div>
          </div>

          <div
            v-if="selectedTextlineRecognitionVariants.length === 0"
            class="rounded-sm border border-default bg-muted/20 px-2 py-1.5 text-xs text-muted"
          >
            No recognition variants found for indices {{ activeRecognitionIndices.join(', ') }}.
          </div>
        </div>
      </div>

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
        v-if="editorInteractions?.isMarqueeSelecting?.value && editorInteractions?.marqueeRectPx?.value"
        class="absolute border border-primary/50 bg-primary/10 pointer-events-none z-[900]"
        :style="{
          left: editorInteractions?.marqueeRectPx?.value?.x + 'px',
          top: editorInteractions?.marqueeRectPx?.value?.y + 'px',
          width: editorInteractions?.marqueeRectPx?.value?.width + 'px',
          height: editorInteractions?.marqueeRectPx?.value?.height + 'px'
        }"
      />

      <svg
        v-if="(remoteSelectionOverlays?.length ?? 0) > 0 || (remoteCursorOverlays?.length ?? 0) > 0"
        class="absolute inset-0 z-[940] pointer-events-none"
        :width="canvasDimensions.width"
        :height="canvasDimensions.height"
      >
        <g v-for="overlay in remoteSelectionOverlays ?? []" :key="overlay.key">
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

        <g v-for="cursor in remoteCursorOverlays ?? []" :key="cursor.key">
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
