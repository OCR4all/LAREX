import type {
  UiMode,
  LayoutViewMode,
  UiModeScope,
  ToolbarLayout,
  GlobalSettings,
  ReadingOrderOverlaySettings,
  VirtualKeyboardMode,
  LineWidthPreset,
  TextItemLayout,
  ConfidenceHeatmapMode,
  ConfidenceHeatmapSettings
} from './types'
import { useEditorPreferences } from '@/composables/use-editor-preferences'

export const useEditorUiStore = defineStore('editor-ui', () => {
  const editorPreferences = useEditorPreferences()

  const uiMode = ref<UiMode>('layout')

  const uiModeScope = ref<UiModeScope>('global')
  const uiModeByCanvasId = ref<Record<string, UiMode>>({})

  const toolbarLayout = ref<ToolbarLayout>('docked-top')
  const toolbarCompact = ref(false)

  const globalSettings = ref<GlobalSettings>({
    constrainToImage: true,
    constrainToParent: true,
    autoSelect: false,
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

  const temporaryHoverPolygonId = ref<string | null>(null)
  const temporaryHoverPolylineId = ref<string | null>(null)

  const textViewFontSize = ref<number>(18)
  const textViewPadding = ref<number>(10)
  const textItemLayout = ref<TextItemLayout>('side-by-side')
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

  async function loadPreferences() {
    if (import.meta.server || preferencesLoaded.value) return

    const prefs = await editorPreferences.fetchPreferences()
    if (!prefs) return

    if (prefs.toolbarLayout) toolbarLayout.value = prefs.toolbarLayout
    if (prefs.toolbarCompact !== null) toolbarCompact.value = prefs.toolbarCompact
    if (prefs.leftCollapsed !== null) leftCollapsed.value = prefs.leftCollapsed
    if (prefs.rightCollapsed !== null) rightCollapsed.value = prefs.rightCollapsed
    if (prefs.leftWidthPx !== null) leftWidthPx.value = prefs.leftWidthPx
    if (prefs.rightWidthPx !== null) rightWidthPx.value = prefs.rightWidthPx
    if (prefs.backgroundColor) backgroundColor.value = prefs.backgroundColor
    if (prefs.backgroundOpacity !== null) backgroundOpacity.value = prefs.backgroundOpacity
    if (prefs.constrainToImage !== null) globalSettings.value.constrainToImage = prefs.constrainToImage
    if (prefs.constrainToParent !== null) globalSettings.value.constrainToParent = prefs.constrainToParent
    if (prefs.autoSelect !== null) globalSettings.value.autoSelect = prefs.autoSelect
    if (prefs.moveWithChildren !== null) globalSettings.value.moveWithChildren = prefs.moveWithChildren
    if (prefs.cutMinAreaThreshold !== null) globalSettings.value.cutMinAreaThreshold = prefs.cutMinAreaThreshold
    if (prefs.defaultLineWidth) globalSettings.value.defaultLineWidth = prefs.defaultLineWidth as LineWidthPreset
    if (prefs.virtualKeyboardMode) virtualKeyboardMode.value = prefs.virtualKeyboardMode
    if (prefs.selectedVirtualKeyboardId !== null) selectedVirtualKeyboardId.value = prefs.selectedVirtualKeyboardId
    if (prefs.textViewFontSize !== null) textViewFontSize.value = prefs.textViewFontSize
    if (prefs.textViewPadding !== null) textViewPadding.value = prefs.textViewPadding
    if (prefs.textItemLayout !== null) textItemLayout.value = prefs.textItemLayout
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

  function toggleLeftCollapsed() {
    leftCollapsed.value = !leftCollapsed.value
    editorPreferences.updatePreference('leftCollapsed', leftCollapsed.value)
  }

  function toggleRightCollapsed() {
    rightCollapsed.value = !rightCollapsed.value
    editorPreferences.updatePreference('rightCollapsed', rightCollapsed.value)
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

  function setTextItemLayout(layout: TextItemLayout) {
    textItemLayout.value = layout
    editorPreferences.updatePreference('textItemLayout', layout)
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
    virtualKeyboardMode.value = mode
    editorPreferences.updatePreference('virtualKeyboardMode', mode)
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
    globalSettings,
    leftCollapsed,
    rightCollapsed,
    leftWidthPx,
    rightWidthPx,
    backgroundColor,
    backgroundOpacity,
    readingOrderOverlay,
    readingOrderVersion,
    temporaryHoverPolygonId,
    temporaryHoverPolylineId,
    textViewFontSize,
    textViewPadding,
    textItemLayout,
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
    setToolbarCompact,
    toggleToolbarCompact,
    updateGlobalSettings,
    toggleConstrainToImage,
    toggleConstrainToParent,
    toggleAutoSelect,
    toggleMoveWithChildren,
    setDefaultLineWidth,
    setReadingOrderOverlayVisible,
    setHighlightUnknownDictionaryTokens,
    toggleReadingOrderOverlay,
    updateReadingOrderOverlaySettings,
    bumpReadingOrderVersion,
    toggleLeftCollapsed,
    toggleRightCollapsed,
    setLeftWidth,
    setRightWidth,
    setBackgroundColor,
    setBackgroundOpacity,
    saveBackgroundAppearance,
    setTemporaryHoverPolygonId,
    setTemporaryHoverPolylineId,
    setTextViewFontSize,
    setTextViewPadding,
    setTextItemLayout,
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
