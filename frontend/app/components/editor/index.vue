<script setup lang="ts">
import { LazyEditorReadingOrderNumbersOverlay } from '#components'
import { triangulatePolygon } from '@/utils/editor/hit-detection'
import { pixelsToWorld } from '@/utils/editor/coordinates'
import { parseCanvasId } from '@/stores/editor/editor.keys'
import { useEditorStore } from '@/stores/editor/editor.store'
import { useEditorUiStore } from '@/stores/editor/editor.ui.store'
import { useEditorSession, usePageVisibilityState } from '@/session/editor/editor-session'
import { useReadingOrderVisualization } from '@/composables/editor/use-reading-order-visualization'
import { useCutDrawing } from '@/composables/editor/use-cut-drawing'
import { useMoveInteraction } from '@/composables/editor/use-move-interaction'
import type { ContextMenuItem as EditorContextMenuItem } from '@/composables/editor/use-editor-command'

const ReadingOrderNumbersOverlay = LazyEditorReadingOrderNumbersOverlay

const props = defineProps({
  src: { type: String, required: true },
  canvasId: { type: String, required: true }
})

const editorStore = useEditorStore()
const editorUiStore = useEditorUiStore()
const session = useEditorSession(props.canvasId)

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

const hiddenPolygonIds = computed(() => usePageVisibilityState(pageId.value).value?.hiddenPolygonIds ?? [])
const hiddenPolylineIds = computed(() => usePageVisibilityState(pageId.value).value?.hiddenPolylineIds ?? [])

const activateEditor = () => editorStore.setActiveCanvas(props.canvasId)

const effectiveUiMode = computed(() => editorStore.effectiveUiMode(props.canvasId))
const renderEnabled = computed(() => effectiveUiMode.value !== 'text')

const constrainToImage = computed(() => editorStore.globalSettings.constrainToImage)
const constrainToParent = computed(() => editorStore.globalSettings.constrainToParent)
const autoSelect = computed(() => editorStore.globalSettings.autoSelect)
const moveWithChildren = computed(() => editorStore.globalSettings.moveWithChildren)

const currentImageSrc = ref(props.src)

const canvasControls = useCanvasControl(props.canvasId)

const canvas = ref(null)
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

const aspectRatioScale = computed(() => {
  const gl = webglRenderer.gl()
  const pageOrientation = session.document.value?.page?.orientation
  const orientationValue = Number(pageOrientation)
  const orientationDegrees = isFinite(orientationValue) ? orientationValue : 0
  const radians = -orientationDegrees * (Math.PI / 180)
  const rotationCos = Math.cos(radians)
  const rotationSin = Math.sin(radians)
  const canvasWidth = canvasDimensions.value.width || gl?.canvas.clientWidth || 0
  const canvasHeight = canvasDimensions.value.height || gl?.canvas.clientHeight || 0
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
  polygons, autoSelect, canvasControls.commander, props.canvasId, canvasControls.viewMode
)

const rectangleDrawing = useRectangleDrawing(
  polygons, constrainToImage, webglRenderer.imageSize, canvasControls.regionType,
  selectedPolygonIndex, constrainToParent, polygons, autoSelect, canvasControls.commander, props.canvasId, canvasControls.viewMode
)

const polygonEditing = usePolygonEditing(
  polygons, view, aspectRatioScale, constrainToImage, webglRenderer.imageSize,
  constrainToParent, spatialIndex, selectedPolylineIndex, canvasControls.viewMode, canvasControls.commander, props.canvasId,
  hiddenPolygonIds, hiddenPolylineIds
)

const polylineDrawing = usePolylineDrawing(
  polylines, view, pixelsToWorld, constrainToImage, webglRenderer.imageSize,
  selectedPolygonIndex, polygons, constrainToParent, autoSelect,
  selectedPolylineIndex, canvasControls.commander, props.canvasId, canvasControls.viewMode
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

const editorCommands = useEditorCommand(canvasControls.commander, props.canvasId, polygons, polylines, stateActions.clearHoverAndSelectionStates)
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

const bufferSlideoverRef = ref<{ previewPoints: ComputedRef<{ x: number, y: number }[] | null> } | null>(null)
const bufferPreviewPoints = computed(() => bufferSlideoverRef.value?.previewPoints?.value ?? null)
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
  cutDrawing,
  isCutMode,
  isCutLineMode,
  isCutPolygonMode,
  isCutRectangleMode,
  moveInteraction,
  bufferPreviewForRenderer
)

const showRenderStats = ref(false)
if (import.meta.env.DEV) {
  const toggleStats = (e) => {
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

function handleSelectPolygon(polygonId, options = { zoomToFit: true }) {
  const index = stateActions.selectPolygonById(polygonId)
  if (index >= 0 && options.zoomToFit) {
    editorInteractions.centerViewOnPolygon(polygons[index])
  }
}

function handleSelectPolyline(polylineId, options = { zoomToFit: true }) {
  const index = stateActions.selectPolylineById(polylineId)
  if (index >= 0 && options.zoomToFit) {
    editorInteractions.centerViewOnPolyline(polylines[index])
  }
}

function handleHoverPolygon(polygonId) {
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

function handleHoverPolyline(polylineId) {
  editorUiStore.setTemporaryHoverPolylineId(polylineId)
}

function handleUnhoverPolyline() {
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
let stopUiModeWatch = null

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
  window.addEventListener('keydown', editorInteractions.onKeyDown)

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
  window.removeEventListener('keydown', editorInteractions.onKeyDown)

  interactionsAttached = false
}

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
    () => effectiveUiMode.value,
    (mode) => {
      if (mode === 'text') {
        detachInteractions()
        webglRenderer.stopRenderLoop()
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
  <div class="w-full h-full relative" :class="{ 'editor-checkerboard': showCheckerboard }">
    <div class="absolute inset-0 pointer-events-none" :style="{ backgroundColor: editorBackgroundColor }" />
    <UContextMenu
      v-model:open="contextMenuOpen"
      :items="contextMenuItems"
    >
      <template #default>
        <canvas
          ref="canvas"
          class="block w-full h-full cursor-grab bg-transparent relative z-10"
          @contextmenu="editorInteractions.handleCanvasContextMenu"
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

    <div v-if="showRenderStats && editorRenderer.renderStats" class="absolute top-2.5 right-2.5 bg-black/80 text-green-500 p-3 rounded-sm font-mono text-xs leading-relaxed min-w-[200px] pointer-events-none z-[1000]">
      <div class="font-bold mb-2 text-white border-b border-green-500 pb-1">
        Render Performance (Ctrl+Shift+R)
      </div>
      <div class="flex justify-between mb-1">
        <span class="text-gray-400">FPS:</span>
        <span class="font-bold">{{ editorRenderer.renderStats.rendersPerSecond }}</span>
      </div>
      <div class="flex justify-between mb-1">
        <span class="text-gray-400">Avg Frame:</span>
        <span class="font-bold">{{ editorRenderer.renderStats.averageFrameTime.toFixed(2) }}ms</span>
      </div>
      <div class="flex justify-between mb-1">
        <span class="text-gray-400">Max Frame:</span>
        <span class="font-bold">{{ editorRenderer.renderStats.maxFrameTime.toFixed(2) }}ms</span>
      </div>
      <div class="flex justify-between mb-1">
        <span class="text-gray-400">Total Renders:</span>
        <span class="font-bold">{{ editorRenderer.renderStats.totalRenders }}</span>
      </div>
      <div class="flex justify-between mb-1">
        <span class="text-gray-400">Batched:</span>
        <span class="font-bold">{{ editorRenderer.renderStats.batchedRenders }}</span>
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
