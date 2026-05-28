import type {
  UiMode,
  LayoutViewMode,
  UiModeScope,
  ToolbarLayout,
  GlobalSettings,
  ReadingOrderOverlaySettings,
  RelationsOverlaySettings,
  CommentsOverlaySettings,
  RelationsEditorState,
  VirtualKeyboardMode,
  LineWidthPreset,
  TextItemLayout,
  TextModeSubmode,
  ConfidenceHeatmapMode,
  ConfidenceHeatmapSettings
} from './types'
import { useEditorPreferences } from '@/composables/use-editor-preferences'
import { createEmptyRelationDraft } from '@/utils/editor/relations'

export const useEditorUiStore = defineStore('editor-ui', () => {
  const editorPreferences = useEditorPreferences()

  const uiMode = ref<UiMode>('layout')

  const uiModeScope = ref<UiModeScope>('global')
  const uiModeByCanvasId = ref<Record<string, UiMode>>({})

  const toolbarLayout = ref<ToolbarLayout>('floating')
  const toolbarCompact = ref(false)
  const toolbarFloatingPosition = ref<{ x: number, y: number } | null>(null)

  const globalSettings = ref<GlobalSettings>({
    constrainToImage: true,
    constrainToParent: true,
    autoSelect: false,
    showPolygonLabelFill: true,
    preventOverlapOnCreate: false,
    cutMinAreaThreshold: 0.0001,
    moveWithChildren: true,
    defaultLineWidth: 'normal'
  })

  const leftCollapsed = ref(false)
  const rightCollapsed = ref(false)
  const leftWidthPx = ref(300)
  const rightWidthPx = ref(300)

  const backgroundColor = ref<string>('#D9D9D9')
  const backgroundOpacity = ref<number>(1)

  const readingOrderOverlay = ref<ReadingOrderOverlaySettings>({
    visible: false,
    showArrows: true,
    showGroupBounds: true,
    showOrderNumbers: true,
    showAllRegions: true,
    showLabels: false
  })

  const readingOrderVersion = ref(0)

  const relationsOverlay = ref<RelationsOverlaySettings>({
    visible: false,
    showLabels: true
  })

  const commentsOverlay = ref<CommentsOverlaySettings>({
    visible: false
  })

  const relationsEditor = ref<RelationsEditorState>({
    pickerMode: 'idle',
    selectedRelationId: null,
    pickerRegionId: null,
    draft: createEmptyRelationDraft()
  })

  const temporaryHoverPolygonId = ref<string | null>(null)
  const temporaryHoverPolylineId = ref<string | null>(null)
  const actionWandActive = ref(false)

  const textViewFontSize = ref<number>(30)
  const textViewPadding = ref<number>(10)
  const textViewCutoutHeight = ref<number>(72)
  const textItemLayout = ref<TextItemLayout>('side-by-side')
  const textModeSubmode = ref<TextModeSubmode>('visual')
  const canvasTextCorrectionOverlaySnapToLine = ref<boolean>(true)
  const canvasTextCorrectionOverlayXRatio = ref<number | null>(null)
  const canvasTextCorrectionOverlayYRatio = ref<number | null>(null)
  const canvasTextCorrectionZoom = ref<number | null>(null)
  const highlightUnknownCodecChars = ref<boolean>(false)
  const includeWhitespaceInCodecHighlight = ref<boolean>(false)
  const highlightUnknownDictionaryTokens = ref<boolean>(false)
  const lastLayoutViewMode = ref<LayoutViewMode>('default')
  const confidenceHeatmap = ref<ConfidenceHeatmapSettings>({
    enabled: false,
    mode: 'average',
    selectedIndices: [],
    logScale: true,
    logScaleStrength: 8,
    fillOpacity: 0.35
  })

  const virtualKeyboardMode = ref<VirtualKeyboardMode>('off')
  const selectedVirtualKeyboardId = ref<string | null>(null)

  const shortcutsHelpOpen = ref(false)
  const shortcutSettingsOpen = ref(false)

  const preferencesLoaded = ref(false)

  function normalizeOverlayRatio(value: unknown): number | null {
    const parsed = Number(value)
    if (!Number.isFinite(parsed)) return null
    return Math.max(0, Math.min(1, parsed))
  }

  function coerceVirtualKeyboardMode(value: unknown): VirtualKeyboardMode {
    return value === 'floating' || value === 'slideover' ? 'floating' : 'off'
  }

  function normalizeToolbarFloatingCoordinate(value: unknown): number | null {
    const parsed = Number(value)
    if (!Number.isFinite(parsed)) return null
    return Math.max(0, Math.round(parsed))
  }

  async function loadPreferences() {
    if (import.meta.server || preferencesLoaded.value) return

    const prefs = await editorPreferences.fetchPreferences()
    if (!prefs) return

    if (prefs.toolbarLayout) toolbarLayout.value = prefs.toolbarLayout
    if (prefs.toolbarCompact !== null) toolbarCompact.value = prefs.toolbarCompact
    toolbarFloatingPosition.value = null
    if (prefs.leftCollapsed !== null) leftCollapsed.value = prefs.leftCollapsed
    if (prefs.rightCollapsed !== null) rightCollapsed.value = prefs.rightCollapsed
    if (prefs.leftWidthPx !== null) leftWidthPx.value = prefs.leftWidthPx
    if (prefs.rightWidthPx !== null) rightWidthPx.value = prefs.rightWidthPx
    if (prefs.backgroundColor) backgroundColor.value = prefs.backgroundColor
    if (prefs.backgroundOpacity !== null) backgroundOpacity.value = prefs.backgroundOpacity
    if (prefs.constrainToImage !== null) globalSettings.value.constrainToImage = prefs.constrainToImage
    if (prefs.constrainToParent !== null) globalSettings.value.constrainToParent = prefs.constrainToParent
    if (prefs.autoSelect !== null) globalSettings.value.autoSelect = prefs.autoSelect
    if (prefs.showPolygonLabelFill !== null) globalSettings.value.showPolygonLabelFill = prefs.showPolygonLabelFill
    if (prefs.preventOverlapOnCreate !== null) globalSettings.value.preventOverlapOnCreate = prefs.preventOverlapOnCreate
    if (prefs.moveWithChildren !== null) globalSettings.value.moveWithChildren = prefs.moveWithChildren
    if (prefs.cutMinAreaThreshold !== null) globalSettings.value.cutMinAreaThreshold = prefs.cutMinAreaThreshold
    if (prefs.defaultLineWidth) globalSettings.value.defaultLineWidth = prefs.defaultLineWidth as LineWidthPreset
    if (prefs.virtualKeyboardMode !== null) {
      const normalizedVirtualKeyboardMode = coerceVirtualKeyboardMode(prefs.virtualKeyboardMode)
      virtualKeyboardMode.value = normalizedVirtualKeyboardMode

      if (prefs.virtualKeyboardMode !== normalizedVirtualKeyboardMode) {
        editorPreferences.updatePreference('virtualKeyboardMode', normalizedVirtualKeyboardMode)
      }
    }
    if (prefs.selectedVirtualKeyboardId !== null) selectedVirtualKeyboardId.value = prefs.selectedVirtualKeyboardId
    if (prefs.textViewFontSize !== null) textViewFontSize.value = prefs.textViewFontSize
    if (prefs.textViewPadding !== null) textViewPadding.value = prefs.textViewPadding
    if (prefs.textViewCutoutHeight !== null) {
      const parsedCutoutHeight = Number(prefs.textViewCutoutHeight)
      textViewCutoutHeight.value = Number.isFinite(parsedCutoutHeight)
        ? Math.max(24, Math.min(220, Math.trunc(parsedCutoutHeight)))
        : 72
    }
    if (prefs.textItemLayout !== null) textItemLayout.value = prefs.textItemLayout
    if (prefs.textModeSubmode === 'expert' || prefs.textModeSubmode === 'visual') {
      textModeSubmode.value = prefs.textModeSubmode
    }
    if (prefs.canvasTextCorrectionOverlaySnapToLine !== null) {
      canvasTextCorrectionOverlaySnapToLine.value = Boolean(prefs.canvasTextCorrectionOverlaySnapToLine)
    }
    if (prefs.canvasTextCorrectionOverlayXRatio !== null) {
      canvasTextCorrectionOverlayXRatio.value = normalizeOverlayRatio(prefs.canvasTextCorrectionOverlayXRatio)
    }
    if (prefs.canvasTextCorrectionOverlayYRatio !== null) {
      canvasTextCorrectionOverlayYRatio.value = normalizeOverlayRatio(prefs.canvasTextCorrectionOverlayYRatio)
    }
    if (prefs.canvasTextCorrectionZoom !== null) {
      const parsedZoom = Number(prefs.canvasTextCorrectionZoom)
      canvasTextCorrectionZoom.value = Number.isFinite(parsedZoom) && parsedZoom > 0 ? parsedZoom : null
    }
    if (prefs.highlightUnknownCodecChars !== null) highlightUnknownCodecChars.value = prefs.highlightUnknownCodecChars

    preferencesLoaded.value = true
  }

  const effectiveUiMode = (canvasId?: string | null): UiMode => {
    if (uiModeScope.value === 'global') return uiMode.value
    if (!canvasId) return uiMode.value
    return uiModeByCanvasId.value[canvasId] ?? uiMode.value
  }

  function setUiMode(mode: UiMode, activeCanvasId?: string | null) {
    uiMode.value = mode

    if (uiModeScope.value === 'global') {
      return
    }
    if (!activeCanvasId) return
    uiModeByCanvasId.value[activeCanvasId] = mode
  }

  function setEffectiveUiMode(canvasId: string | null | undefined, mode: UiMode) {
    uiMode.value = mode

    if (uiModeScope.value === 'global' || !canvasId) {
      return
    }
    uiModeByCanvasId.value[canvasId] = mode
  }

  function setLastLayoutViewMode(mode: LayoutViewMode) {
    lastLayoutViewMode.value = mode
  }

  function initializeCanvasUiMode(canvasId: string) {
    if (uiModeScope.value === 'per-canvas' && !uiModeByCanvasId.value[canvasId]) {
      uiModeByCanvasId.value[canvasId] = uiMode.value
    }
  }

  function removeCanvasUiMode(canvasId: string) {
    const { [canvasId]: _removed, ...rest } = uiModeByCanvasId.value
    uiModeByCanvasId.value = rest
  }

  function setUiModeScope(scope: UiModeScope, activeCanvasId?: string | null, allCanvasIds: string[] = []) {
    if (scope === uiModeScope.value) return

    if (scope === 'per-canvas') {
      const globalMode = uiMode.value
      for (const id of allCanvasIds) {
        uiModeByCanvasId.value[id] = globalMode
      }
      uiModeScope.value = 'per-canvas'
      return
    }

    if (activeCanvasId) {
      uiMode.value = uiModeByCanvasId.value[activeCanvasId] ?? uiMode.value
    }
    uiModeScope.value = 'global'
  }

  function setToolbarLayout(layout: ToolbarLayout) {
    toolbarLayout.value = layout
    editorPreferences.updatePreference('toolbarLayout', layout)
  }

  function setToolbarFloatingPosition(x: number, y: number, _options: { persist?: boolean } = {}) {
    const normalizedX = normalizeToolbarFloatingCoordinate(x)
    const normalizedY = normalizeToolbarFloatingCoordinate(y)
    if (normalizedX === null || normalizedY === null) return

    toolbarFloatingPosition.value = { x: normalizedX, y: normalizedY }
  }

  function setToolbarCompact(compact: boolean) {
    toolbarCompact.value = compact
    editorPreferences.updatePreference('toolbarCompact', compact)
  }

  function toggleToolbarCompact() {
    setToolbarCompact(!toolbarCompact.value)
  }

  function updateGlobalSettings(settings: Partial<GlobalSettings>) {
    globalSettings.value = { ...globalSettings.value, ...settings }
    editorPreferences.updatePreferences({
      constrainToImage: globalSettings.value.constrainToImage,
      constrainToParent: globalSettings.value.constrainToParent,
      autoSelect: globalSettings.value.autoSelect,
      showPolygonLabelFill: globalSettings.value.showPolygonLabelFill,
      preventOverlapOnCreate: globalSettings.value.preventOverlapOnCreate,
      cutMinAreaThreshold: globalSettings.value.cutMinAreaThreshold
    })
  }

  function toggleConstrainToImage() {
    globalSettings.value.constrainToImage = !globalSettings.value.constrainToImage
    editorPreferences.updatePreference('constrainToImage', globalSettings.value.constrainToImage)
  }

  function toggleConstrainToParent() {
    globalSettings.value.constrainToParent = !globalSettings.value.constrainToParent
    editorPreferences.updatePreference('constrainToParent', globalSettings.value.constrainToParent)
  }

  function toggleAutoSelect() {
    globalSettings.value.autoSelect = !globalSettings.value.autoSelect
    editorPreferences.updatePreference('autoSelect', globalSettings.value.autoSelect)
  }

  function togglePolygonLabelFill() {
    globalSettings.value.showPolygonLabelFill = !globalSettings.value.showPolygonLabelFill
    editorPreferences.updatePreference('showPolygonLabelFill', globalSettings.value.showPolygonLabelFill)
  }

  function togglePreventOverlapOnCreate() {
    globalSettings.value.preventOverlapOnCreate = !globalSettings.value.preventOverlapOnCreate
    editorPreferences.updatePreference('preventOverlapOnCreate', globalSettings.value.preventOverlapOnCreate)
  }

  function toggleMoveWithChildren() {
    globalSettings.value.moveWithChildren = !globalSettings.value.moveWithChildren
    editorPreferences.updatePreference('moveWithChildren', globalSettings.value.moveWithChildren)
  }

  function setDefaultLineWidth(preset: LineWidthPreset) {
    globalSettings.value.defaultLineWidth = preset
    editorPreferences.updatePreference('defaultLineWidth', preset)
  }

  function setReadingOrderOverlayVisible(visible: boolean) {
    readingOrderOverlay.value.visible = visible
  }

  function setHighlightUnknownDictionaryTokens(enabled: boolean) {
    highlightUnknownDictionaryTokens.value = enabled
  }

  function toggleReadingOrderOverlay() {
    readingOrderOverlay.value.visible = !readingOrderOverlay.value.visible
  }

  function updateReadingOrderOverlaySettings(settings: Partial<ReadingOrderOverlaySettings>) {
    readingOrderOverlay.value = { ...readingOrderOverlay.value, ...settings }
  }

  function bumpReadingOrderVersion() {
    readingOrderVersion.value++
  }

  function setRelationsOverlayVisible(visible: boolean) {
    relationsOverlay.value.visible = visible
  }

  function toggleRelationsOverlay() {
    relationsOverlay.value.visible = !relationsOverlay.value.visible
  }

  function updateRelationsOverlaySettings(settings: Partial<RelationsOverlaySettings>) {
    relationsOverlay.value = { ...relationsOverlay.value, ...settings }
  }

  function setCommentsOverlayVisible(visible: boolean) {
    commentsOverlay.value.visible = visible
  }

  function toggleCommentsOverlay() {
    commentsOverlay.value.visible = !commentsOverlay.value.visible
  }

  function setRelationPickerMode(mode: RelationsEditorState['pickerMode']) {
    relationsEditor.value.pickerMode = mode
  }

  function setSelectedRelationId(relationId: string | null) {
    relationsEditor.value.selectedRelationId = relationId
  }

  function setRelationPickerRegionId(regionId: string | null) {
    relationsEditor.value.pickerRegionId = regionId
  }

  function updateRelationDraft(draft: Partial<RelationsEditorState['draft']>) {
    relationsEditor.value.draft = {
      ...relationsEditor.value.draft,
      ...draft
    }
  }

  function resetRelationDraft() {
    relationsEditor.value.draft = createEmptyRelationDraft()
  }

  function beginRelationCreation() {
    relationsEditor.value.pickerMode = 'pick-source'
    relationsEditor.value.pickerRegionId = null
    relationsEditor.value.draft = {
      ...relationsEditor.value.draft,
      sourceRegionRef: '',
      targetRegionRef: ''
    }
  }

  function beginRelationRepickSource(relationId: string) {
    relationsEditor.value.selectedRelationId = relationId
    relationsEditor.value.pickerMode = 'repick-source'
    relationsEditor.value.pickerRegionId = null
  }

  function beginRelationRepickTarget(relationId: string) {
    relationsEditor.value.selectedRelationId = relationId
    relationsEditor.value.pickerMode = 'repick-target'
    relationsEditor.value.pickerRegionId = null
  }

  function cancelRelationPicking() {
    relationsEditor.value.pickerMode = 'idle'
    relationsEditor.value.pickerRegionId = null
  }

  function clearRelationSelection() {
    relationsEditor.value.selectedRelationId = null
  }

  function toggleLeftCollapsed() {
    leftCollapsed.value = !leftCollapsed.value
    editorPreferences.updatePreference('leftCollapsed', leftCollapsed.value, { immediate: true })
  }

  function toggleRightCollapsed() {
    rightCollapsed.value = !rightCollapsed.value
    editorPreferences.updatePreference('rightCollapsed', rightCollapsed.value, { immediate: true })
  }

  function setLeftWidth(width: number) {
    leftWidthPx.value = width
    editorPreferences.updatePreference('leftWidthPx', width)
  }

  function setRightWidth(width: number) {
    rightWidthPx.value = width
    editorPreferences.updatePreference('rightWidthPx', width)
  }

  type BackgroundPreferenceUpdateOptions = {
    persist?: boolean
  }

  function setBackgroundColor(color: string, options: BackgroundPreferenceUpdateOptions = {}) {
    backgroundColor.value = color
    if (options.persist === false) return
    editorPreferences.updatePreference('backgroundColor', color)
  }

  function setBackgroundOpacity(opacity: number, options: BackgroundPreferenceUpdateOptions = {}) {
    backgroundOpacity.value = opacity
    if (options.persist === false) return
    editorPreferences.updatePreference('backgroundOpacity', opacity)
  }

  async function saveBackgroundAppearance() {
    return editorPreferences.savePreferences({
      backgroundColor: backgroundColor.value,
      backgroundOpacity: backgroundOpacity.value
    })
  }

  function setTemporaryHoverPolygonId(id: string | null) {
    temporaryHoverPolygonId.value = id
  }

  function setTemporaryHoverPolylineId(id: string | null) {
    temporaryHoverPolylineId.value = id
  }

  function setActionWandActive(active: boolean) {
    actionWandActive.value = active
    if (active) {
      cancelRelationPicking()
    }
  }

  function toggleActionWand() {
    setActionWandActive(!actionWandActive.value)
  }

  function setTextViewFontSize(size: number) {
    textViewFontSize.value = size
    editorPreferences.updatePreference('textViewFontSize', size)
  }

  function setTextViewPadding(padding: number) {
    const parsed = Number(padding)
    const next = Number.isFinite(parsed) ? Math.max(0, Math.trunc(parsed)) : 10
    textViewPadding.value = next
    editorPreferences.updatePreference('textViewPadding', next)
  }

  function setTextViewCutoutHeight(height: number) {
    const parsed = Number(height)
    const next = Number.isFinite(parsed) ? Math.max(24, Math.min(220, Math.trunc(parsed))) : 72
    textViewCutoutHeight.value = next
    editorPreferences.updatePreference('textViewCutoutHeight', next)
  }

  function setTextItemLayout(layout: TextItemLayout) {
    textItemLayout.value = layout
    editorPreferences.updatePreference('textItemLayout', layout)
  }

  function setTextModeSubmode(mode: TextModeSubmode) {
    textModeSubmode.value = mode
    editorPreferences.updatePreference('textModeSubmode', mode)
  }

  function setCanvasTextCorrectionOverlaySnapToLine(enabled: boolean) {
    const normalized = Boolean(enabled)
    canvasTextCorrectionOverlaySnapToLine.value = normalized
    editorPreferences.updatePreference('canvasTextCorrectionOverlaySnapToLine', normalized)
  }

  function setCanvasTextCorrectionOverlayPosition(xRatio: number, yRatio: number) {
    const normalizedX = normalizeOverlayRatio(xRatio)
    const normalizedY = normalizeOverlayRatio(yRatio)
    if (normalizedX === null || normalizedY === null) return

    canvasTextCorrectionOverlayXRatio.value = normalizedX
    canvasTextCorrectionOverlayYRatio.value = normalizedY

    editorPreferences.updatePreferences({
      canvasTextCorrectionOverlayXRatio: normalizedX,
      canvasTextCorrectionOverlayYRatio: normalizedY
    })
  }

  function setCanvasTextCorrectionZoom(zoom: number) {
    const parsed = Number(zoom)
    if (!Number.isFinite(parsed) || parsed <= 0) return
    canvasTextCorrectionZoom.value = parsed
    editorPreferences.updatePreference('canvasTextCorrectionZoom', parsed)
  }

  function setHighlightUnknownCodecChars(enabled: boolean) {
    highlightUnknownCodecChars.value = enabled
    editorPreferences.updatePreference('highlightUnknownCodecChars', enabled)
  }

  function toggleHighlightUnknownCodecChars() {
    setHighlightUnknownCodecChars(!highlightUnknownCodecChars.value)
  }

  function setIncludeWhitespaceInCodecHighlight(enabled: boolean) {
    includeWhitespaceInCodecHighlight.value = enabled
  }

  function toggleIncludeWhitespaceInCodecHighlight() {
    setIncludeWhitespaceInCodecHighlight(!includeWhitespaceInCodecHighlight.value)
  }

  function setVirtualKeyboardMode(mode: VirtualKeyboardMode) {
    const normalizedMode = coerceVirtualKeyboardMode(mode)
    virtualKeyboardMode.value = normalizedMode
    editorPreferences.updatePreference('virtualKeyboardMode', normalizedMode)
  }

  function setConfidenceHeatmapEnabled(enabled: boolean) {
    confidenceHeatmap.value = {
      ...confidenceHeatmap.value,
      enabled
    }
  }

  function setConfidenceHeatmapMode(mode: ConfidenceHeatmapMode) {
    confidenceHeatmap.value = {
      ...confidenceHeatmap.value,
      mode
    }
  }

  function setConfidenceHeatmapSelectedIndices(indices: number[]) {
    const normalized = [...new Set(
      (Array.isArray(indices) ? indices : [])
        .map(v => Number(v))
        .filter((v): v is number => Number.isFinite(v) && v >= 0)
        .map(v => Math.trunc(v))
    )].sort((a, b) => a - b)

    confidenceHeatmap.value = {
      ...confidenceHeatmap.value,
      selectedIndices: normalized
    }
  }

  function setConfidenceHeatmapLogScale(logScale: boolean) {
    confidenceHeatmap.value = {
      ...confidenceHeatmap.value,
      logScale
    }
  }

  function setConfidenceHeatmapLogScaleStrength(logScaleStrength: number) {
    const parsed = Number(logScaleStrength)
    const normalized = Number.isFinite(parsed)
      ? Math.max(1, Math.min(32, Math.trunc(parsed)))
      : 8

    confidenceHeatmap.value = {
      ...confidenceHeatmap.value,
      logScaleStrength: normalized
    }
  }

  function setConfidenceHeatmapFillOpacity(fillOpacity: number) {
    const parsed = Number(fillOpacity)
    const normalized = Number.isFinite(parsed)
      ? Math.max(0, Math.min(1, parsed))
      : 0.35

    confidenceHeatmap.value = {
      ...confidenceHeatmap.value,
      fillOpacity: normalized
    }
  }

  function toggleConfidenceHeatmap() {
    setConfidenceHeatmapEnabled(!confidenceHeatmap.value.enabled)
  }

  function setSelectedVirtualKeyboardId(id: string | null) {
    selectedVirtualKeyboardId.value = id
    editorPreferences.updatePreference('selectedVirtualKeyboardId', id)
  }

  function toggleShortcutsHelp() {
    shortcutsHelpOpen.value = !shortcutsHelpOpen.value
  }

  function openShortcutSettings() {
    shortcutSettingsOpen.value = true
  }

  function closeShortcutSettings() {
    shortcutSettingsOpen.value = false
  }

  function toggleShortcutSettings() {
    shortcutSettingsOpen.value = !shortcutSettingsOpen.value
  }

  return {
    uiMode,
    uiModeScope,
    uiModeByCanvasId,
    toolbarLayout,
    toolbarCompact,
    toolbarFloatingPosition,
    globalSettings,
    leftCollapsed,
    rightCollapsed,
    leftWidthPx,
    rightWidthPx,
    backgroundColor,
    backgroundOpacity,
    readingOrderOverlay,
    readingOrderVersion,
    relationsOverlay,
    commentsOverlay,
    relationsEditor,
    temporaryHoverPolygonId,
    temporaryHoverPolylineId,
    actionWandActive,
    textViewFontSize,
    textViewPadding,
    textViewCutoutHeight,
    textItemLayout,
    textModeSubmode,
    canvasTextCorrectionOverlaySnapToLine,
    canvasTextCorrectionOverlayXRatio,
    canvasTextCorrectionOverlayYRatio,
    canvasTextCorrectionZoom,
    highlightUnknownCodecChars,
    includeWhitespaceInCodecHighlight,
    highlightUnknownDictionaryTokens,
    lastLayoutViewMode,
    confidenceHeatmap,
    virtualKeyboardMode,
    selectedVirtualKeyboardId,
    shortcutsHelpOpen,
    shortcutSettingsOpen,
    preferencesLoaded,

    effectiveUiMode,

    loadPreferences,
    setUiMode,
    setEffectiveUiMode,
    initializeCanvasUiMode,
    removeCanvasUiMode,
    setUiModeScope,
    setToolbarLayout,
    setToolbarFloatingPosition,
    setToolbarCompact,
    toggleToolbarCompact,
    updateGlobalSettings,
    toggleConstrainToImage,
    toggleConstrainToParent,
    toggleAutoSelect,
    togglePolygonLabelFill,
    togglePreventOverlapOnCreate,
    toggleMoveWithChildren,
    setDefaultLineWidth,
    setReadingOrderOverlayVisible,
    setHighlightUnknownDictionaryTokens,
    toggleReadingOrderOverlay,
    updateReadingOrderOverlaySettings,
    bumpReadingOrderVersion,
    setRelationsOverlayVisible,
    toggleRelationsOverlay,
    updateRelationsOverlaySettings,
    setCommentsOverlayVisible,
    toggleCommentsOverlay,
    setRelationPickerMode,
    setSelectedRelationId,
    setRelationPickerRegionId,
    updateRelationDraft,
    resetRelationDraft,
    beginRelationCreation,
    beginRelationRepickSource,
    beginRelationRepickTarget,
    cancelRelationPicking,
    clearRelationSelection,
    toggleLeftCollapsed,
    toggleRightCollapsed,
    setLeftWidth,
    setRightWidth,
    setBackgroundColor,
    setBackgroundOpacity,
    saveBackgroundAppearance,
    setTemporaryHoverPolygonId,
    setTemporaryHoverPolylineId,
    setActionWandActive,
    toggleActionWand,
    setTextViewFontSize,
    setTextViewPadding,
    setTextViewCutoutHeight,
    setTextItemLayout,
    setTextModeSubmode,
    setCanvasTextCorrectionOverlaySnapToLine,
    setCanvasTextCorrectionOverlayPosition,
    setCanvasTextCorrectionZoom,
    setHighlightUnknownCodecChars,
    toggleHighlightUnknownCodecChars,
    setIncludeWhitespaceInCodecHighlight,
    toggleIncludeWhitespaceInCodecHighlight,
    setLastLayoutViewMode,
    setConfidenceHeatmapEnabled,
    setConfidenceHeatmapMode,
    setConfidenceHeatmapSelectedIndices,
    setConfidenceHeatmapLogScale,
    setConfidenceHeatmapLogScaleStrength,
    setConfidenceHeatmapFillOpacity,
    toggleConfidenceHeatmap,
    setVirtualKeyboardMode,
    setSelectedVirtualKeyboardId,
    toggleShortcutsHelp,
    openShortcutSettings,
    closeShortcutSettings,
    toggleShortcutSettings
  }
})
