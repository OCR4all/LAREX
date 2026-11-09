import DiffMatchPatch from 'diff-match-patch'
import type { Diff } from 'diff-match-patch'
import { onScopeDispose, type Ref } from 'vue'
import { useDebounceFn } from '@vueuse/core'
import { UpdateTextlineCommentCommand } from '@/commands'
import type { CommandContext } from '@/commands/editor/types'
import { serializeFullTextComment } from '@/components/editor/text/shared/full-text-comments'
import {
  handleSingleLineTextareaBeforeInput,
  handleSingleLineTextareaDrop,
  handleSingleLineTextareaPaste
} from '@/components/editor/text/shared/text-input-guards'
import { tokenizeForDictionary } from '@/components/editor/text/shared/text-highlighting'
import { createTextlineVariantsUpdateCommand } from '@/components/editor/text/shared/textline-variant-update'
import { useDictionaryTokenLookup } from '@/composables/use-dictionary-token-lookup'
import type { AspectRatioScale, TextContentVariantData, View } from '@/models/editor'
import type { EditorSession } from '@/session/editor/editor-session'
import { useEditorSessionStore } from '@/stores/editor/editor.session.store'
import { useEditorStore } from '@/stores/editor/editor.store'
import { useEditorUiStore } from '@/stores/editor/editor.ui.store'
import { useWorkspaceStore } from '@/stores/workspace.store'
import type { EditorCanvasControls } from '@/types/editor/canvas-controls'
import type { RenderablePolygon } from '@/types/editor/rendering'
import { computeCanvasTextCorrectionAnchorTarget, computeCanvasTextCorrectionPlacement } from '@/utils/editor/canvas-text-correction-placement'
import { clipToWorldCoords, imageToWorld } from '@/utils/editor/coordinates'
import { WORLD_COORD_THRESHOLD, ZOOM } from '@/utils/editor/editor-constants'
import { normalizeEditableTextVariants, setGtVariantUnicode } from '@/utils/editor/text-variants'
import { collectTextlineIdsInReadingOrder, getAdjacentTextlineId, isTextlinePolygonType } from '@/utils/editor/textline-navigation'

export const CORRECTION_FONT_MIN = 14
export const CORRECTION_FONT_MAX = 64
const CORRECTION_FONT_DEFAULT = 32
const CORRECTION_OVERLAY_MARGIN = 8
const CORRECTION_OVERLAY_GAP = 6

type RenderableTextlinePolygon = RenderablePolygon & {
  textContentVariants?: TextContentVariantData[] | undefined
}

export interface CanvasTextCorrectionOptions {
  session: EditorSession
  canvasControls: Pick<EditorCanvasControls, 'commander' | 'selectPolygonById' | 'selectPolylineById'>
  polygons: RenderablePolygon[]
  selectedPolygonIds: Ref<string[]>
  selectedPolygonIndex: Ref<number>
  selectedPolylineIds: Ref<string[]>
  selectedPolylineIndex: Ref<number>
  selectedRegionId: Ref<string | null>
  pageId: Ref<string | null | undefined>
  view: View
  aspectRatioScale: Ref<AspectRatioScale>
  canvasDimensions: Ref<{ width: number, height: number }>
  imageSize: Ref<{ width: number, height: number }>
  setView: (view: View) => void
  isTextVisualMode: Ref<boolean>
  isCanvasWritable: Ref<boolean>
  canShowCanvasContent: Ref<boolean>
  toScreenPoint: (point: { x: number, y: number }) => { x: number, y: number } | null
  getCommandContext: () => CommandContext
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

let dmpInstance: DiffMatchPatch | null = null
function getDmp(): DiffMatchPatch {
  if (!dmpInstance) {
    dmpInstance = new DiffMatchPatch()
  }
  return dmpInstance
}

type CorrectionOverlayDragState = {
  pointerId: number
  offsetX: number
  offsetY: number
  moved: boolean
}

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

type OverlayUnknownDictionarySegment = {
  text: string
  unknown: boolean
  start: number
  end: number
}

function normalizeSingleLineText(value: string): string {
  return value.replace(/[ \t]*\r?\n+[ \t]*/g, ' ')
}

export function useCanvasTextCorrection(options: CanvasTextCorrectionOptions) {
  const {
    session,
    canvasControls,
    polygons,
    selectedPolygonIds,
    selectedPolygonIndex,
    selectedPolylineIds,
    selectedPolylineIndex,
    selectedRegionId,
    pageId,
    view,
    aspectRatioScale,
    canvasDimensions,
    imageSize,
    setView,
    isTextVisualMode,
    isCanvasWritable,
    canShowCanvasContent,
    toScreenPoint,
    getCommandContext
  } = options

  const editorStore = useEditorStore()
  const editorUiStore = useEditorUiStore()
  const sessionStore = useEditorSessionStore()
  const workspaceStore = useWorkspaceStore()
  const {
    ensureTokenResults,
    getTokenResult,
    isTokenPending,
    hasSuggestionsLoaded
  } = useDictionaryTokenLookup()

  const canvasTextCorrectionSnapToLine = computed({
    get: () => editorUiStore.canvasTextCorrectionOverlaySnapToLine,
    set: (next: boolean) => editorUiStore.setCanvasTextCorrectionOverlaySnapToLine(Boolean(next))
  })
  const correctionOverlayContainerRef = ref<HTMLDivElement | null>(null)
  const suppressCorrectionZoomPreferenceUpdate = ref(false)
  const correctionViewInitialized = ref(false)
  // Screen position of the line left by Enter/Tab, so the next line lands at the same height.
  const correctionNavigationAnchor = ref<{ textlineId: string, x: number, y: number } | null>(null)

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
  const showRecognitionInCorrectionOverlay = computed(() => textViewSettings.value.showRecognition ?? true)
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
      margin: CORRECTION_OVERLAY_MARGIN,
      gap: CORRECTION_OVERLAY_GAP
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
    const { width, height } = imageSize.value
    if (!width || !height) return

    let minX = Infinity
    let maxX = -Infinity
    let minY = Infinity
    let maxY = -Infinity

    for (const point of polygon.points) {
      const looksLikeWorldCoords = Math.abs(point.x) <= WORLD_COORD_THRESHOLD
        && Math.abs(point.y) <= WORLD_COORD_THRESHOLD
      const worldPoint = looksLikeWorldCoords ? point : imageToWorld(point, { width, height })
      minX = Math.min(minX, worldPoint.x)
      maxX = Math.max(maxX, worldPoint.x)
      minY = Math.min(minY, worldPoint.y)
      maxY = Math.max(maxY, worldPoint.y)
    }

    const normalizedZoom = clamp(zoom, ZOOM.MIN, ZOOM.MAX)
    setView({
      zoom: normalizedZoom,
      offsetX: -(((minX + maxX) / 2) * normalizedZoom),
      offsetY: -(((minY + maxY) / 2) * normalizedZoom)
    })
  }

  function screenToWorldPoint(point: { x: number, y: number }): { x: number, y: number } {
    const { width, height } = canvasDimensions.value
    return clipToWorldCoords({
      x: (point.x / width) * 2 - 1,
      y: 1 - (point.y / height) * 2
    }, view, aspectRatioScale.value)
  }

  function rememberCorrectionNavigationAnchor(nextTextlineId: string): void {
    const bounds = selectedTextlineScreenBounds.value
    correctionNavigationAnchor.value = bounds
      ? { textlineId: nextTextlineId, x: (bounds.minX + bounds.maxX) / 2, y: (bounds.minY + bounds.maxY) / 2 }
      : null
  }

  /** Pans (without zooming) so the selected line takes the screen height of the line navigated away from. */
  function alignCorrectionViewToNavigationAnchor(textlineId: string): boolean {
    const anchor = correctionNavigationAnchor.value
    correctionNavigationAnchor.value = null
    if (!anchor || anchor.textlineId !== textlineId) return false

    const bounds = selectedTextlineScreenBounds.value
    const { width, height } = canvasDimensions.value
    if (!bounds || !width || !height) return false

    const overlayHeight = canvasTextCorrectionSnapToLine.value ? (correctionOverlayLayout.value?.height ?? 0) : 0
    const target = computeCanvasTextCorrectionAnchorTarget({
      lineBounds: bounds,
      anchor,
      viewport: { width, height },
      reservedBelow: overlayHeight > 0 ? overlayHeight + CORRECTION_OVERLAY_GAP : 0,
      margin: CORRECTION_OVERLAY_MARGIN
    })
    const lineCenter = { x: (bounds.minX + bounds.maxX) / 2, y: (bounds.minY + bounds.maxY) / 2 }

    const lineWorld = screenToWorldPoint(lineCenter)
    const targetWorld = screenToWorldPoint(target)
    setView({
      zoom: view.zoom,
      offsetX: view.offsetX + (targetWorld.x - lineWorld.x) * view.zoom,
      offsetY: view.offsetY + (targetWorld.y - lineWorld.y) * view.zoom
    })
    return true
  }

  const selectedTextlineGtText = computed(() => {
    const polygon = selectedTextlinePolygon.value
    if (!polygon) return ''
    const variants = normalizeEditableTextVariants(polygon.textContentVariants)
    const gt = variants.find(variant => variant.index === activeGtIndex.value)
    return gt?.unicode ?? ''
  })

  const selectedTextlineGtVariant = computed(() => {
    const polygon = selectedTextlinePolygon.value
    if (!polygon) return undefined
    return normalizeEditableTextVariants(polygon.textContentVariants)
      .find(variant => variant.index === activeGtIndex.value)
  })

  function findFirstRecognitionVariant(polygon: RenderableTextlinePolygon) {
    const variants = normalizeEditableTextVariants(polygon.textContentVariants)
    for (const recognitionIndex of activeRecognitionIndices.value) {
      const variant = variants.find(candidate => (
        (recognitionIndex === -1 ? candidate.index === undefined : candidate.index === recognitionIndex)
        && candidate.unicode.trim().length > 0
      ))
      if (variant) return variant
    }

    return undefined
  }

  const selectedTextlineFirstRecognitionVariant = computed(() => {
    const polygon = selectedTextlinePolygon.value
    return polygon ? findFirstRecognitionVariant(polygon) : undefined
  })

  const canCreateGtFromRecognition = computed(() => (
    isTextVisualMode.value
    && !!selectedTextlineFirstRecognitionVariant.value
    && !selectedTextlineGtVariant.value?.unicode.trim()
  ))

  const selectedTextlineComment = computed(() => {
    const comment = selectedTextlinePolygon.value?.comments
    return typeof comment === 'string' ? comment.trim() : ''
  })

  function updateSelectedTextlineComment(comment: string): void {
    const polygon = selectedTextlinePolygon.value
    if (!polygon || !isCanvasWritable.value) return
    flushPendingCorrectionCommit()
    const nextComment = serializeFullTextComment(comment)
    if (polygon.comments === nextComment) return
    canvasControls.commander.execute(
      new UpdateTextlineCommentCommand({ textlineId: polygon.id, comment: nextComment }),
      getCommandContext()
    )
  }

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

  const readingOrderTextlineIds = computed(() => collectTextlineIdsInReadingOrder(
    session.document.value?.page?.regions,
    session.document.value?.page?.readingOrder,
    editorUiStore.textlineSortOrder
  ))

  function getFirstVisualTextlineId(): string | null {
    const availableTextlineIds = new Set(
      polygons
        .filter(polygon => isTextlinePolygonType(polygon.type))
        .map(polygon => polygon.id)
    )

    for (const textlineId of readingOrderTextlineIds.value) {
      if (availableTextlineIds.has(textlineId)) return textlineId
    }

    return polygons.find(polygon => isTextlinePolygonType(polygon.type))?.id ?? null
  }

  function autoSelectFirstVisualTextline(): void {
    if (!isTextVisualMode.value || !editorUiStore.preferencesLoaded || !editorUiStore.textViewAutoSelectFirstLine) return
    if (correctionViewInitialized.value) return
    if (!canShowCanvasContent.value || selectedTextlinePolygon.value) return

    const firstTextlineId = getFirstVisualTextlineId()
    if (!firstTextlineId) return

    canvasControls.selectPolylineById?.(null, { focusMode: 'none' })
    canvasControls.selectPolygonById?.(firstTextlineId, { focusMode: 'fit-width' })
  }

  watch(
    () => [
      isTextVisualMode.value,
      editorUiStore.preferencesLoaded,
      editorUiStore.textViewAutoSelectFirstLine,
      canShowCanvasContent.value,
      selectedTextlinePolygon.value?.id ?? null,
      readingOrderTextlineIds.value.join('\u0000'),
      polygons.length
    ] as const,
    () => {
      nextTick(autoSelectFirstVisualTextline)
    },
    { immediate: true }
  )

  watch(pageId, () => {
    correctionViewInitialized.value = false
  })

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

    canvasControls.commander.execute(
      createTextlineVariantsUpdateCommand({
        pageRegions: session.document.value?.page?.regions,
        textlineId,
        nextTextContentVariants: variants,
        gtIndex: activeGtIndex.value
      }),
      getCommandContext()
    )
  }

  function createGtFromRecognition(): boolean {
    // Typed text is committed on blur, which a shortcut does not trigger.
    flushPendingCorrectionCommit()

    const polygon = selectedTextlinePolygon.value
    const source = selectedTextlineFirstRecognitionVariant.value
    if (!polygon || !source || !isCanvasWritable.value) return false
    if (!canCreateGtFromRecognition.value) return false

    const updated = setGtVariantUnicode(
      polygon.textContentVariants,
      activeGtIndex.value,
      normalizeSingleLineText(source.unicode)
    )
    if (!updated.changed && !updated.created) return false

    commitTextlineVariants(polygon.id, updated.variants)
    correctionInputValue.value = updated.variants[updated.gtPos]?.unicode ?? ''
    return true
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

  function navigateCanvasCorrectionTextline(direction: 1 | -1, populateGt = false): boolean {
    flushPendingCorrectionCommit()
    const currentId = selectedTextlinePolygon.value?.id ?? null
    const nextId = getAdjacentTextlineId(readingOrderTextlineIds.value, currentId, direction)
    if (!nextId || (nextId === currentId && !populateGt)) return false

    if (populateGt && isCanvasWritable.value) {
      const nextPolygon = polygons.find(polygon => polygon.id === nextId) as RenderableTextlinePolygon | undefined
      if (nextPolygon && !nextPolygon.textContentVariants?.some(variant => variant.index === activeGtIndex.value)) {
        const source = findFirstRecognitionVariant(nextPolygon)
        updateTextlineGtTextById(nextId, source?.unicode ?? '')
      }
    }

    if (nextId !== currentId) rememberCorrectionNavigationAnchor(nextId)
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
      if (event.isComposing || event.altKey || event.ctrlKey || event.metaKey) return
      event.stopPropagation()
      navigateCanvasCorrectionTextline(event.shiftKey ? -1 : 1, true)
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
      if (!enabled) {
        correctionViewInitialized.value = false
        return
      }
      if (!selectedId) return

      const selectionChanged = selectedId !== prevSelectedId
      const justEnabled = enabled && !prevEnabled
      const gtIndexChanged = gtIndex !== prevGtIndex
      if (!selectionChanged && !justEnabled && !gtIndexChanged) return

      // Selecting a line only loads its text. Input, Enter navigation and Copy to GT create GT on demand.
      correctionInputValue.value = selectedTextlineGtText.value

      if (selectionChanged || justEnabled) {
        const shouldUseDefaultFit = !correctionViewInitialized.value

        if (shouldUseDefaultFit) {
          canvasControls.selectPolygonById?.(selectedId, { focusMode: 'fit-width' })
        } else if (!alignCorrectionViewToNavigationAnchor(selectedId)) {
          const persistedCorrectionZoom = getPersistedCorrectionZoom()
          if (persistedCorrectionZoom !== null) {
            withSuppressedCorrectionZoomPreferenceUpdate(() => {
              canvasControls.selectPolygonById?.(selectedId, { focusMode: 'none' })
              const polygon = selectedTextlinePolygon.value
              centerCorrectionViewOnTextlineWithZoom(polygon, persistedCorrectionZoom)
            })
          } else {
            canvasControls.selectPolygonById?.(selectedId, { focusMode: 'fit-width' })
          }
        }
        correctionViewInitialized.value = true
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

  onScopeDispose(stopCorrectionOverlayDrag)

  return {
    activeGtIndex,
    activeRecognitionIndices,
    adjustCorrectionFontSize,
    applyDictionarySuggestionToGt,
    canCreateGtFromRecognition,
    canvasTextCorrectionSnapToLine,
    canvasTextCorrectionVisible,
    correctionFontSizePx,
    correctionInputValue,
    correctionOverlayContainerRef,
    correctionOverlayStyle,
    correctionTextareaMinHeightPx,
    correctionTextareaRef,
    createGtFromRecognition,
    flushPendingCorrectionCommit,
    getConfidenceClass,
    getConfidencePercent,
    getDictionarySuggestions,
    gtCodecUnknownCount,
    gtCodecUnknownSegments,
    gtUnknownDictionarySegments,
    gtUnknownDictionaryTokenCount,
    handleCorrectionOverlayKeydown,
    handleCorrectionOverlayPointerDown,
    handleCorrectionReadonlyKeydown,
    handleCorrectionTextareaBeforeInput,
    handleCorrectionTextareaBlur,
    handleCorrectionTextareaDrop,
    handleCorrectionTextareaInput,
    handleCorrectionTextareaKeydown,
    handleCorrectionTextareaPaste,
    handleUnknownDictionaryPopoverUpdate,
    hasProjectCodec,
    hasProjectDictionary,
    highlightUnknownCodecCharsModel,
    highlightUnknownDictionaryTokensModel,
    isDictionaryCheckLoadingForGt,
    isDictionarySuggestionLoading,
    navigateCanvasCorrectionTextline,
    queueCorrectionInputFocus,
    recognitionTextareaFontSizePx,
    recognitionTextareaLineHeightPx,
    recognitionTextareaMinHeightPx,
    resetCorrectionFontSize,
    selectedTextlineComment,
    selectedTextlinePolygon,
    selectedTextlineRecognitionVariants,
    showCommentsModel,
    showDiffModel,
    showRecognitionInCorrectionOverlay,
    toggleCorrectionOverlaySnapToLine,
    updateSelectedTextlineComment,
    zoomCorrectionOverlayToFit
  }
}
