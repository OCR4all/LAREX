<script setup lang="ts">
import { LazyDictionarySlideoverBrowser } from '#components'
import { getEditorSession } from '@/session/editor/editor-session'
import { PolygonType, isTextRegion, type TextContentVariantData } from '@/models/editor'
import type { Point } from '@/models/editor/types'
import { worldToImage } from '@/utils/editor/coordinates'
import { getRegionColor } from '@/utils/editor/region-colors'
import { findRegionLabelDefinitionForRegion } from '@/utils/editor/page-label-mapping'
import type { RegionKind } from '@/models/editor/region'
import type { LabelDefinition } from '@/types/label-set'
import type { KeyboardItem, KeyboardLayout } from '@/types/virtual-keyboard'
import { useTextViewShortcutScope } from '@/composables/editor/use-keyboard-shortcuts'
import { useEditorCollaboration } from '@/composables/editor/use-editor-collaboration'
import { createScopedLogger } from '@/services/editor/logger-service'
import { useEditorSessionStore } from '@/stores/editor/editor.session.store'
import { usePageFilter } from '@/composables/use-page-filter'
import { wsKey } from '@/utils/fetch-keys'
import { tokenizeForDictionary } from '../shared/text-highlighting'
import {
  compareConfidenceLowFirst,
  createVariantFilterState,
  filterTextContentVariants,
  getMinVariantConfidence
} from '../line/variant-filtering'
import {
  buildRegionGtSyncedVariants,
  composeRegionGtFromTextLines
} from '../shared/region-gt-sync'
import {
  focusNextSameIndex,
  focusTextContentVariantAtOffset
} from '../shared/text-field-navigation'
import {
  createTextViewCommandContext,
  getRequestErrorMessage,
  getTextViewRuntimeControls,
  lowestFreeIndex,
  normalizeTextContentVariants,
  sortByIndex
} from '../shared/text-view-runtime'
import { CompoundCommand, UpdateTextContentVariantsCommand } from '@/commands'

const log = createScopedLogger('RegionTextView')

const props = defineProps<{ canvasId?: string | null }>()

const editorStore = useEditorStore()
const uiStore = useEditorUiStore()
const sessionStore = useEditorSessionStore()
const workspaceStore = useWorkspaceStore()
const collaboration = useEditorCollaboration()
const toast = useToast()
const overlay = useOverlay()
const dictionaryBrowserSlideover = overlay.create(LazyDictionarySlideoverBrowser)
const {
  ensureTokenResults,
  getTokenResult,
  invalidateToken: invalidateDictionaryToken,
  isTokenPending
} = useDictionaryTokenLookup()

const projectId = computed(() => sessionStore.projectId ?? undefined)
const {
  textContent: filterTextContent,
  getMatchingTextRegionIds
} = usePageFilter(projectId)

const rootEl = ref<HTMLElement | null>(null)
const searchQuery = computed({
  get: () => textViewSettings.value.searchQuery ?? '',
  set: (next: string) => {
    sessionStore.updateTextViewSettings(current => ({
      ...current,
      searchQuery: next
    }))
  }
})
const sortOrder = ref<'asc' | 'desc' | 'confidence'>('asc')
const filterMode = ref<'all' | 'empty' | 'lowConfidence' | 'matchingFilter' | 'dictionaryMismatch'>('all')
const selectedRegionId = ref<string | null>(null)
const matchingTextRegionIds = ref<Set<string>>(new Set())
const isLoadingMatchingTextRegions = ref(false)

const virtualKeyboardMode = computed(() => uiStore.virtualKeyboardMode)
const { keyboards, selectedLayout, selectedTheme, selectedKeyboardId } = useVirtualKeyboards()
const selectedWorkspaceId = computed(() => workspaceStore.selectedWorkspaceId as string | null)

const effectiveCanvasId = computed(() => props.canvasId ?? editorStore.activeCanvasId)
const isCanvasEditable = computed(() => {
  const canvasId = effectiveCanvasId.value
  return canvasId ? collaboration.canEditCanvas(canvasId) : true
})
const isLoadingAnnotations = computed(() => {
  const canvasId = effectiveCanvasId.value
  if (!canvasId) return false
  return editorStore.canvases[canvasId]?.isLoadingAnnotations === true
})

const effectiveImageUrl = computed(() => {
  const canvasId = effectiveCanvasId.value
  if (!canvasId) return ''
  return editorStore.canvases[canvasId]?.imageSrc ?? ''
})

const effectiveImageSize = computed(() => {
  const canvasId = effectiveCanvasId.value
  if (!canvasId) return undefined

  const canvasImageSize = editorStore.canvases[canvasId]?.imageSize
  if (canvasImageSize && canvasImageSize.width > 1 && canvasImageSize.height > 1) {
    return canvasImageSize
  }

  const session = getEditorSession(canvasId)
  const doc = session?.document.value
  if (doc?.page?.imageWidth && doc?.page?.imageHeight) {
    return { width: doc.page.imageWidth, height: doc.page.imageHeight }
  }

  return undefined
})

const textViewSettings = computed(() => sessionStore.textViewSettings)
const padding = computed(() => uiStore.textViewPadding)
const fontSize = computed(() => uiStore.textViewFontSize)
const textItemLayout = computed(() => uiStore.textItemLayout)
const codecCharacters = computed(() => editorStore.projectCodecCharacters ?? [])
const hasProjectCodec = computed(() => Boolean(editorStore.projectCodecId) || (codecCharacters.value?.length ?? 0) > 0)
const highlightUnknownCodecChars = computed(() => uiStore.highlightUnknownCodecChars && hasProjectCodec.value)
const includeWhitespaceInCodecHighlight = computed(() => uiStore.includeWhitespaceInCodecHighlight)
const hasProjectDictionary = computed(() => {
  return Boolean(editorStore.projectDictionaryId)
})
const highlightUnknownDictionaryTokens = computed(() => uiStore.highlightUnknownDictionaryTokens && hasProjectDictionary.value)
const canQuickAddToDictionary = computed(() => Boolean(editorStore.projectDictionaryCanEdit) && !editorStore.projectDictionaryLocked)
const canCheckDictionaryTokens = computed(() => {
  return Boolean(
    selectedWorkspaceId.value
    && editorStore.projectDictionaryId
  )
})

const gtIndexModel = computed(() => editorStore.projectTextDefaultGtIndex ?? 0)
const recognitionIndicesModel = computed(() => editorStore.projectTextDefaultRecognitionIndices ?? [1])

const showDiffModel = computed({
  get: () => textViewSettings.value.showDiff,
  set: (next) => {
    sessionStore.updateTextViewSettings(current => ({
      ...current,
      showDiff: Boolean(next)
    }))
  }
})

const confidenceRangeModel = computed({
  get: () => textViewSettings.value.confidenceRange,
  set: (next) => {
    const min = Math.max(0, Math.min(1, Number(next?.[0] ?? 0)))
    const max = Math.max(0, Math.min(1, Number(next?.[1] ?? 1)))
    sessionStore.updateTextViewSettings(current => ({
      ...current,
      confidenceRange: min <= max ? [min, max] : [max, min]
    }))
  }
})

const selectedIndicesModel = computed({
  get: () => textViewSettings.value.selectedIndices,
  set: (next) => {
    const parsed = (Array.isArray(next) ? next : [])
      .map(v => Number.parseInt(String(v), 10))
      .filter(v => Number.isFinite(v) && v >= 0)
    const newIndices = [...new Set(parsed)].sort((a, b) => a - b)
    sessionStore.updateTextViewSettings(current => ({
      ...current,
      selectedIndices: newIndices
    }))
  }
})

const filterUnindexedModel = computed(() => textViewSettings.value.filterUnindexed)

const showNonAssignedIndicesModel = computed({
  get: () => textViewSettings.value.showNonAssignedIndices,
  set: (next: boolean) => {
    sessionStore.updateTextViewSettings(current => ({
      ...current,
      showNonAssignedIndices: Boolean(next)
    }))
  }
})

const onlyMissingGtModel = computed({
  get: () => textViewSettings.value.onlyMissingGt,
  set: (next: boolean) => {
    sessionStore.updateTextViewSettings(current => ({
      ...current,
      onlyMissingGt: Boolean(next)
    }))
  }
})

function looksLikeWorldCoords(points: Point[]): boolean {
  if (points.length === 0) return false
  return points.every(p => Math.abs(p.x) <= 2 && Math.abs(p.y) <= 2)
}

function toImagePoints(points: Point[]): Point[] {
  const imageSize = effectiveImageSize.value
  if (!imageSize) return points
  if (!looksLikeWorldCoords(points)) return points
  return points.map(p => worldToImage(p, imageSize))
}

function variantRole(index: number | undefined): 'gt' | 'recognition' | 'nonAssigned' {
  if (typeof index === 'number' && index === gtIndexModel.value) return 'gt'
  if (index === undefined && recognitionIndicesModel.value.includes(-1)) return 'recognition'
  if (typeof index === 'number' && recognitionIndicesModel.value.includes(index)) return 'recognition'
  return 'nonAssigned'
}

useTextViewShortcutScope({
  canvasId: effectiveCanvasId,
  rootEl,
  handlers: {
    nextTextField: () => {
      focusTextContentVariantAtOffset(rootEl, 1)
      return true
    },
    prevTextField: () => {
      focusTextContentVariantAtOffset(rootEl, -1)
      return true
    },
    blurTextField: () => {
      const active = document.activeElement
      if (!(active instanceof HTMLElement)) return false
      active.blur()
      return true
    },
    nextSameIndexField: () => {
      focusNextSameIndex(rootEl)
      return true
    },
    createGtFromRecognition: () => triggerCreateGtForSelectedRegion()
  }
})

const currentPageId = computed(() => {
  const canvasId = effectiveCanvasId.value
  if (!canvasId) return null
  return editorStore.canvases[canvasId]?.pageId ?? null
})

watch([filterMode, currentPageId, filterTextContent], async ([mode, pageId, textContentFilter]) => {
  if (mode !== 'matchingFilter' || !pageId || !textContentFilter?.trim()) {
    matchingTextRegionIds.value = new Set()
    return
  }

  isLoadingMatchingTextRegions.value = true
  try {
    const ids = await getMatchingTextRegionIds(pageId)
    matchingTextRegionIds.value = new Set(ids)
  } catch (error) {
    log.error('Failed to fetch matching text region IDs:', error)
    matchingTextRegionIds.value = new Set()
  } finally {
    isLoadingMatchingTextRegions.value = false
  }
}, { immediate: true })

const hasTextContentFilter = computed(() => Boolean(filterTextContent.value?.trim()))
const activeTextHighlightQuery = computed(() => {
  const backendQuery = filterTextContent.value?.trim() ?? ''
  if (backendQuery) return backendQuery
  return searchQuery.value.trim()
})

const renderableRegionsById = computed(() => {
  const runtime = getTextViewRuntimeControls(effectiveCanvasId.value, editorStore)
  return new Map((runtime?.polygons ?? [])
    .filter(p => p.type === PolygonType.REGION)
    .map(region => [region.id, region]))
})

const topLevelTextRegions = computed<TextRegion[]>(() => {
  const canvasId = effectiveCanvasId.value
  if (!canvasId) return []
  const session = getEditorSession(canvasId)
  const regions = session?.document.value?.page?.regions ?? []
  return regions.filter(isTextRegion)
})

function regionColor(regionKind: string | undefined, regionSubtype: string | undefined, regionCustom?: string): string {
  if (!regionKind) return '#666'

  const labelSet = editorStore.labelSet
  if (labelSet) {
    const match = findRegionLabelDefinitionForRegion(labelSet.labels as LabelDefinition[], {
      regionKind,
      regionSubtype,
      regionCustom
    })
    if (match?.color) {
      return match.color
    }
  }

  return getRegionColor(regionKind as RegionKind, regionSubtype ?? undefined)
}

const regions = computed(() => {
  const renderableById = renderableRegionsById.value
  const variantFilterState = createVariantFilterState({
    selectedIndices: selectedIndicesModel.value,
    filterUnindexed: filterUnindexedModel.value,
    confidenceRange: confidenceRangeModel.value
  })

  return topLevelTextRegions.value.map((region) => {
    const renderable = renderableById.get(region.id)
    const allTextContentVariants = normalizeTextContentVariants(region.textContentVariants as TextContentVariantData[] | undefined)
      .map((variant, pos) => ({
        pos,
        index: typeof variant.index === 'number' && Number.isFinite(variant.index) && variant.index >= 0 ? variant.index : undefined,
        text: variant.unicode ?? '',
        confidence: variant.confidence
      }))

    const filteredTextContentVariants = filterTextContentVariants(allTextContentVariants, variantFilterState)
      .sort((a, b) => {
        const ai = typeof a.index === 'number' ? a.index : -1
        const bi = typeof b.index === 'number' ? b.index : -1
        return ai - bi
      })

    const visibleTextContentVariants = showNonAssignedIndicesModel.value
      ? filteredTextContentVariants
      : filteredTextContentVariants.filter(variant => variantRole(variant.index) !== 'nonAssigned')

    const regionConfidence = getMinVariantConfidence(visibleTextContentVariants)
    const matchesVariantFilter = !variantFilterState.hasVariantFilter || filteredTextContentVariants.length > 0
    const matchesAssignedVisibility = !variantFilterState.hasVariantFilter || visibleTextContentVariants.length > 0
    const hasGtVariant = allTextContentVariants.some(variant => variant.index === gtIndexModel.value)
    const recognitionCandidates = recognitionIndicesModel.value.flatMap((idx: number) => {
      if (idx === -1) {
        return allTextContentVariants.filter(variant => variant.index === undefined && variant.text.trim().length > 0)
      }
      return allTextContentVariants.filter(variant => variant.index === idx && variant.text.trim().length > 0)
    })

    return {
      id: region.id,
      label: renderable?.label ?? region.type ?? region.id,
      points: toImagePoints(renderable?.points ?? []),
      readingDirection: region.readingDirection as ReadingDirection | undefined,
      textContentVariants: visibleTextContentVariants,
      allTextContentVariants,
      hasAnyText: allTextContentVariants.some(variant => variant.text.trim().length > 0),
      hasGtVariant,
      recognitionCandidates,
      regionConfidence,
      matchesVariantFilter,
      matchesAssignedVisibility,
      regionSubtype: renderable?.regionSubtype ?? region.type,
      regionKind: renderable?.regionKind ?? region.kind,
      regionCustom: renderable?.regionCustom ?? region.custom,
      color: regionColor(renderable?.regionKind ?? region.kind, renderable?.regionSubtype ?? region.type, renderable?.regionCustom ?? region.custom),
      textLineCount: region.textLines?.length ?? 0
    }
  }).filter(region => region.matchesVariantFilter && region.matchesAssignedVisibility)
})

const dictionaryTokensOnPage = computed(() => {
  const tokens = regions.value.flatMap(region =>
    region.textContentVariants
      .filter(variant => variantRole(variant.index) === 'gt')
      .flatMap(variant => tokenizeForDictionary(variant.text))
  )
  return [...new Set(tokens)]
})

watch([canCheckDictionaryTokens, selectedWorkspaceId, () => editorStore.projectDictionaryId, dictionaryTokensOnPage], async ([enabled, workspaceId, dictionaryId, tokens]) => {
  if (!enabled || !workspaceId || !dictionaryId || !Array.isArray(tokens) || tokens.length === 0) {
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
    // Keep the text editable even if dictionary checks fail.
  }
}, { immediate: true })

function regionHasDictionaryMismatch(region: { textContentVariants: Array<{ index?: number, text: string }> }): boolean {
  const workspaceId = selectedWorkspaceId.value
  const dictionaryId = editorStore.projectDictionaryId
  if (!canCheckDictionaryTokens.value || !workspaceId || !dictionaryId) return false

  const tokens = region.textContentVariants
    .filter(variant => variantRole(variant.index) === 'gt')
    .flatMap(variant => tokenizeForDictionary(variant.text))

  if (tokens.length === 0) return false

  let hasLoadedResult = false
  for (const token of tokens) {
    if (isTokenPending(workspaceId, dictionaryId, token)) continue
    const result = getTokenResult(workspaceId, dictionaryId, token)
    if (!result) continue
    hasLoadedResult = true
    if (!result.known) return true
  }

  return hasLoadedResult ? false : false
}

const selectedRegionIdFromSharedSelection = computed(() => {
  const runtime = getTextViewRuntimeControls(effectiveCanvasId.value, editorStore)
  const polygonSelection = runtime?.selectedPolygonId?.value ?? null
  if (typeof polygonSelection === 'string' && polygonSelection.length > 0) {
    return polygonSelection
  }

  const fallback = editorStore.activeSelectedRegionId
  return typeof fallback === 'string' && fallback.length > 0 ? fallback : null
})

watch([selectedRegionIdFromSharedSelection, regions], ([selectedId, regionItems]) => {
  if (!selectedId) {
    selectedRegionId.value = null
    return
  }
  selectedRegionId.value = regionItems.some(region => region.id === selectedId) ? selectedId : null
}, { immediate: true })

const totalRegionCount = computed(() => topLevelTextRegions.value.length)

const displayRegions = computed(() => {
  const q = searchQuery.value.trim().toLowerCase()
  let items = [...regions.value]

  if (onlyMissingGtModel.value) {
    items = items.filter(region => !region.hasGtVariant)
  }

  if (filterMode.value === 'empty') {
    items = items.filter(region => !region.hasAnyText)
  } else if (filterMode.value === 'lowConfidence') {
    items = items.filter(region => typeof region.regionConfidence === 'number' && region.regionConfidence < 0.8)
  } else if (filterMode.value === 'dictionaryMismatch') {
    items = items.filter(regionHasDictionaryMismatch)
  } else if (filterMode.value === 'matchingFilter') {
    if (matchingTextRegionIds.value.size > 0) {
      items = items.filter(region => matchingTextRegionIds.value.has(region.id))
    } else if (!isLoadingMatchingTextRegions.value) {
      items = []
    }
  }

  if (q) {
    items = items.filter((region) => {
      if (region.id.toLowerCase().includes(q)) return true
      if ((region.label ?? '').toLowerCase().includes(q)) return true
      return region.textContentVariants.some(variant => variant.text.toLowerCase().includes(q))
    })
  }

  if (sortOrder.value === 'confidence') {
    items.sort((a, b) => compareConfidenceLowFirst(a.regionConfidence, b.regionConfidence))
  } else if (sortOrder.value === 'desc') {
    items.reverse()
  }

  return items
})

function handleSelectRegion(regionId: string): void {
  selectedRegionId.value = regionId
  const runtime = getTextViewRuntimeControls(effectiveCanvasId.value, editorStore)
  runtime?.selectPolylineById?.(null, { zoomToFit: false })
  runtime?.selectPolygonById?.(regionId, { zoomToFit: false })

  if (!runtime?.selectPolygonById && effectiveCanvasId.value === editorStore.activeCanvasId) {
    editorStore.clearBaselineSelection()
    editorStore.selectRegionById(regionId)
  }
}

function commitTextContentVariants(regionId: string, nextTextContentVariants: TextContentVariantData[] | undefined): void {
  if (!isCanvasEditable.value) return
  const runtime = getTextViewRuntimeControls(effectiveCanvasId.value, editorStore)
  if (!runtime?.commander) return

  const command = new UpdateTextContentVariantsCommand({
    elementId: regionId,
    nextTextContentVariants
  })
  runtime.commander.execute(command, createTextViewCommandContext(effectiveCanvasId.value))
}

function handleAddTextContentVariant(regionId: string): void {
  if (!isCanvasEditable.value) return
  const region = displayRegions.value.find(item => item.id === regionId)
  if (!region) return

  const current = normalizeTextContentVariants(region.textContentVariants as unknown as TextContentVariantData[] | undefined)
  current.push({ unicode: '', index: lowestFreeIndex(current) })
  current.sort(sortByIndex)
  commitTextContentVariants(regionId, current)
}

function handleRemoveTextContentVariant(regionId: string, arrayPos: number): void {
  if (!isCanvasEditable.value) return
  const region = displayRegions.value.find(item => item.id === regionId)
  if (!region) return

  const current = normalizeTextContentVariants(region.textContentVariants as unknown as TextContentVariantData[] | undefined)
  if (arrayPos < 0 || arrayPos >= current.length) return
  current.splice(arrayPos, 1)
  commitTextContentVariants(regionId, current)
}

function handleCommitTextContentVariant(regionId: string, pos: number, text: string): void {
  if (!isCanvasEditable.value) return
  const region = displayRegions.value.find(item => item.id === regionId)
  if (!region) return

  const current = normalizeTextContentVariants(region.textContentVariants as unknown as TextContentVariantData[] | undefined)
  const variant = current[pos]
  if (!variant) return
  variant.unicode = text
  current.sort(sortByIndex)
  commitTextContentVariants(regionId, current)
}

function handleCreateGtFromRecognition(regionId: string, payload: { gtIndex: number, sourceRecognitionIndex?: number }) {
  if (!isCanvasEditable.value) return
  const region = displayRegions.value.find(item => item.id === regionId)
  if (!region) return

  const current = normalizeTextContentVariants(region.textContentVariants as unknown as TextContentVariantData[] | undefined)
  if (current.some(variant => variant.index === payload.gtIndex)) return

  const source = current.find(variant => variant.index === payload.sourceRecognitionIndex)
  if (!source) return

  current.push({
    unicode: source.unicode ?? '',
    index: payload.gtIndex
  })
  current.sort(sortByIndex)
  commitTextContentVariants(regionId, current)
}

function handleCommitTextContentVariantIndex(regionId: string, pos: number, toIndex: number | undefined): void {
  if (!isCanvasEditable.value) return
  if (toIndex !== undefined && (!Number.isInteger(toIndex) || toIndex < 0)) return

  const region = displayRegions.value.find(item => item.id === regionId)
  if (!region) return

  const current = normalizeTextContentVariants(region.textContentVariants as unknown as TextContentVariantData[] | undefined)
  const from = current[pos]
  if (!from) return

  if (toIndex === undefined) {
    from.index = undefined
    current.sort(sortByIndex)
    commitTextContentVariants(regionId, current)
    return
  }

  const toPos = current.findIndex(variant => variant.index === toIndex)
  if (toPos >= 0 && toPos !== pos) {
    const tmp = from.index
    current[toPos]!.index = tmp
  }
  from.index = toIndex
  current.sort(sortByIndex)
  commitTextContentVariants(regionId, current)
}

function findTopLevelTextRegion(regionId: string): TextRegion | undefined {
  return topLevelTextRegions.value.find(region => region.id === regionId)
}

function buildRegionSyncCommand(regionId: string): UpdateTextContentVariantsCommand | null {
  const region = findTopLevelTextRegion(regionId)
  if (!region) return null

  const nextGtText = composeRegionGtFromTextLines(region.textLines, gtIndexModel.value)
  const nextVariants = buildRegionGtSyncedVariants(region.textContentVariants as TextContentVariantData[] | undefined, nextGtText, gtIndexModel.value)
  const currentVariants = normalizeTextContentVariants(region.textContentVariants as TextContentVariantData[] | undefined)
  const normalizedNext = normalizeTextContentVariants(nextVariants)
  if (JSON.stringify(currentVariants) === JSON.stringify(normalizedNext)) return null

  return new UpdateTextContentVariantsCommand({
    elementId: regionId,
    nextTextContentVariants: nextVariants
  })
}

function syncRegionGtFromTextLines(regionId: string): boolean {
  if (!isCanvasEditable.value) return false
  const runtime = getTextViewRuntimeControls(effectiveCanvasId.value, editorStore)
  if (!runtime?.commander) return false
  const command = buildRegionSyncCommand(regionId)
  if (!command) return false
  runtime.commander.execute(command, createTextViewCommandContext(effectiveCanvasId.value))
  return true
}

function syncVisibleRegionsFromTextLines(): boolean {
  if (!isCanvasEditable.value) return false
  const runtime = getTextViewRuntimeControls(effectiveCanvasId.value, editorStore)
  if (!runtime?.commander) return false

  const commands = displayRegions.value
    .map(region => buildRegionSyncCommand(region.id))
    .filter((command): command is UpdateTextContentVariantsCommand => Boolean(command))

  if (commands.length === 0) return false

  runtime.commander.execute(
    new CompoundCommand(commands, `Sync region GT from textlines (${commands.length})`),
    createTextViewCommandContext(effectiveCanvasId.value)
  )
  return true
}

function triggerCreateGtForSelectedRegion(): boolean {
  if (!isCanvasEditable.value) return false
  const selectedId = selectedRegionId.value
  if (!selectedId) return false

  const region = displayRegions.value.find(item => item.id === selectedId)
  if (!region || region.hasGtVariant) return false

  const source = region.recognitionCandidates?.find(candidate => candidate.text.trim().length > 0)
  if (!source) return false

  handleCreateGtFromRecognition(region.id, {
    gtIndex: gtIndexModel.value,
    sourceRecognitionIndex: source.index
  })
  return true
}

function firstFreeKeyboardCell(layout: KeyboardLayout): { x: number, y: number } | null {
  const occupied = new Set<string>()
  for (const item of layout.items ?? []) {
    const width = Math.max(1, Number(item.w ?? 1))
    for (let offset = 0; offset < width; offset++) {
      occupied.add(`${item.x + offset}:${item.y}`)
    }
  }

  for (let y = 0; y < layout.rows; y++) {
    for (let x = 0; x < layout.cols; x++) {
      if (!occupied.has(`${x}:${y}`)) return { x, y }
    }
  }

  return null
}

async function handleQuickAddCodecCharacter(char: string) {
  const workspaceId = selectedWorkspaceId.value
  const codecId = editorStore.projectCodecId
  const activeProjectId = sessionStore.projectId
  if (!workspaceId || !codecId || !activeProjectId) return

  try {
    const updated = await $fetch<{ id: string, codec?: string[] }>(`/api/workspaces/${workspaceId}/codecs/${codecId}/characters`, {
      method: 'POST',
      body: { character: char }
    })
    editorStore.setProjectCodec(updated.id ?? codecId, updated.codec ?? [], activeProjectId)
    await refreshNuxtData(wsKey(workspaceId, 'codecs', codecId))
    await refreshNuxtData(wsKey(workspaceId, 'codecs', 'list'))
    toast.add({ title: 'Added to codec', description: `Character "${char}" appended to the project codec.`, color: 'success' })
  } catch (error: unknown) {
    toast.add({
      title: 'Could not add to codec',
      description: getRequestErrorMessage(error),
      color: 'error'
    })
  }
}

async function handleQuickAddDictionaryToken(token: string) {
  const workspaceId = selectedWorkspaceId.value
  const dictionaryId = editorStore.projectDictionaryId
  if (!workspaceId || !dictionaryId || !canQuickAddToDictionary.value) return

  try {
    await $fetch(`/api/workspaces/${workspaceId}/dictionaries/${dictionaryId}/entries`, {
      method: 'POST',
      body: { form: token, fromEditor: true }
    })
    invalidateDictionaryToken(workspaceId, dictionaryId, token)
    await refreshNuxtData(wsKey(workspaceId, 'dictionaries', dictionaryId))
    await refreshNuxtData(wsKey(workspaceId, 'dictionaries', 'list'))
    toast.add({ title: 'Added to dictionary', description: `Token "${token}" appended to the project dictionary.`, color: 'success' })
  } catch (error: unknown) {
    toast.add({
      title: 'Could not add to dictionary',
      description: getRequestErrorMessage(error),
      color: 'error'
    })
  }
}

async function handleQuickAddKeyboardCharacter(char: string) {
  const workspaceId = selectedWorkspaceId.value
  const keyboardId = selectedKeyboardId.value
  if (!workspaceId || !keyboardId) return

  try {
    const layout = await $fetch<KeyboardLayout>(`/api/workspaces/${workspaceId}/virtual-keyboards/${keyboardId}`)
    const freeCell = firstFreeKeyboardCell(layout)
    if (!freeCell) {
      toast.add({
        title: 'Keyboard is full',
        description: 'No free key cell found. Open the keyboard editor to rearrange keys.',
        color: 'warning'
      })
      return
    }

    const nextItems: KeyboardItem[] = [...(layout.items ?? []), {
      id: Date.now(),
      x: freeCell.x,
      y: freeCell.y,
      w: 1,
      char,
      shiftChar: char
    }]

    await $fetch(`/api/workspaces/${workspaceId}/virtual-keyboards/${keyboardId}`, {
      method: 'PUT',
      body: {
        ...layout,
        items: nextItems
      }
    })
    await refreshNuxtData(wsKey(workspaceId, 'virtual-keyboards', 'list'))
    await refreshNuxtData(wsKey(workspaceId, 'virtual-keyboards', keyboardId))
    toast.add({ title: 'Added to keyboard', description: `Character "${char}" appended to the selected keyboard.`, color: 'success' })
  } catch (error: unknown) {
    toast.add({
      title: 'Could not add to keyboard',
      description: getRequestErrorMessage(error),
      color: 'error'
    })
  }
}

function handleOpenCodecEditor() {
  if (!editorStore.projectCodecId) {
    toast.add({ title: 'No project codec configured', color: 'warning' })
    return
  }
  navigateTo(`/codecs/${editorStore.projectCodecId}`)
}

function handleOpenDictionaryEditor() {
  const dictionaryId = editorStore.projectDictionaryId
  const workspaceId = selectedWorkspaceId.value
  if (!dictionaryId || !workspaceId) {
    toast.add({ title: 'No project dictionary configured', color: 'warning' })
    return
  }
  dictionaryBrowserSlideover.open({
    workspaceId,
    dictionaryId
  })
}

function handleOpenKeyboardEditor() {
  if (!selectedKeyboardId.value) {
    toast.add({ title: 'No virtual keyboard selected', color: 'warning' })
    return
  }
  navigateTo(`/virtual-keyboard/${selectedKeyboardId.value}`)
}

const sortMenuItems = computed(() => [[
  {
    label: 'Reading order (asc)',
    icon: 'i-lucide-arrow-down-1-0',
    active: sortOrder.value === 'asc',
    activeColor: 'primary',
    activeVariant: 'solid',
    onSelect: () => { sortOrder.value = 'asc' }
  },
  {
    label: 'Reading order (desc)',
    icon: 'i-lucide-arrow-up-1-0',
    active: sortOrder.value === 'desc',
    activeColor: 'primary',
    activeVariant: 'solid',
    onSelect: () => { sortOrder.value = 'desc' }
  },
  {
    label: 'Confidence (low first)',
    icon: 'i-lucide-badge-percent',
    active: sortOrder.value === 'confidence',
    activeColor: 'primary',
    activeVariant: 'solid',
    onSelect: () => { sortOrder.value = 'confidence' }
  }
]])

const filterMenuItems = computed(() => {
  const items = [[
    {
      label: 'Show all',
      icon: 'i-lucide-list',
      active: filterMode.value === 'all',
      activeColor: 'primary',
      activeVariant: 'solid',
      onSelect: () => { filterMode.value = 'all' }
    },
    {
      label: 'Empty transcriptions',
      icon: 'i-lucide-type',
      active: filterMode.value === 'empty',
      activeColor: 'primary',
      activeVariant: 'solid',
      onSelect: () => { filterMode.value = 'empty' }
    },
    {
      label: 'Low confidence',
      icon: 'i-lucide-triangle-alert',
      active: filterMode.value === 'lowConfidence',
      activeColor: 'primary',
      activeVariant: 'solid',
      onSelect: () => { filterMode.value = 'lowConfidence' }
    },
    {
      label: 'Dictionary mismatches',
      icon: 'i-lucide-book-x',
      active: filterMode.value === 'dictionaryMismatch',
      activeColor: 'primary',
      activeVariant: 'solid',
      disabled: !hasProjectDictionary.value,
      onSelect: () => { filterMode.value = 'dictionaryMismatch' }
    },
    {
      label: 'Only regions without GT',
      icon: 'i-lucide-leaf',
      active: onlyMissingGtModel.value,
      activeColor: 'primary',
      activeVariant: 'solid',
      onSelect: () => { onlyMissingGtModel.value = !onlyMissingGtModel.value }
    }
  ]]

  if (hasTextContentFilter.value && items[0]) {
    items[0].push({
      label: `Matching "${filterTextContent.value?.substring(0, 20)}${(filterTextContent.value?.length ?? 0) > 20 ? '…' : ''}"`,
      icon: 'i-lucide-filter',
      active: filterMode.value === 'matchingFilter',
      activeColor: 'primary',
      activeVariant: 'solid',
      onSelect: () => { filterMode.value = 'matchingFilter' }
    })
  }

  return items
})

const actionMenuItems = computed(() => [[
  {
    label: 'Sync visible regions from textlines',
    icon: 'i-lucide-refresh-cw',
    disabled: displayRegions.value.length === 0,
    onSelect: () => {
      const changed = syncVisibleRegionsFromTextLines()
      if (!changed) {
        toast.add({ title: 'Nothing to sync', color: 'neutral' })
      }
    }
  }
]])

const currentSortLabel = computed(() => {
  switch (sortOrder.value) {
    case 'desc':
      return 'Reading order (desc)'
    case 'confidence':
      return 'Confidence (low first)'
    case 'asc':
    default:
      return 'Reading order (asc)'
  }
})

const hasActiveLocalFilters = computed(() => filterMode.value !== 'all' || onlyMissingGtModel.value)
</script>

<template>
  <div ref="rootEl" data-shortcut-scope="text-view" class="flex flex-col h-full">
    <div class="sticky top-0 z-10 border-b bg-background/95 px-3 py-2 backdrop-blur supports-[backdrop-filter]:bg-background/85">
      <div class="flex flex-wrap items-center gap-2 md:flex-nowrap">
        <div class="flex min-w-0 shrink-0 items-center gap-2.5">
          <div class="flex items-center gap-2 min-w-0">
            <Icon name="i-lucide-square-stack" class="h-5 w-5 text-primary" />
            <h2 class="truncate text-base font-semibold">
              Regions
            </h2>
          </div>
          <UBadge
            variant="solid"
            color="neutral"
            size="sm"
            class="shrink-0 font-mono"
          >
            {{ displayRegions.length }}/{{ totalRegionCount }}
          </UBadge>
        </div>

        <div class="order-3 basis-full md:order-2 md:min-w-[18rem] md:flex-1">
          <UInput
            v-model="searchQuery"
            icon="i-lucide-search"
            placeholder="Search regions..."
            size="sm"
            class="w-full"
          />
        </div>

        <div class="order-2 ml-auto flex shrink-0 items-center gap-1 md:order-3">
          <UDropdownMenu :items="sortMenuItems" :content="{ align: 'end' }">
            <UButton
              color="neutral"
              :variant="sortOrder === 'asc' ? 'ghost' : 'soft'"
              size="sm"
              icon="i-lucide-arrow-up-down"
              class="h-8 w-8"
              :title="`Sort regions (${currentSortLabel})`"
              :aria-label="`Sort regions (${currentSortLabel})`"
            />
          </UDropdownMenu>

          <UDropdownMenu :items="filterMenuItems" :content="{ align: 'end' }">
            <UButton
              color="neutral"
              :variant="hasActiveLocalFilters ? 'soft' : 'ghost'"
              size="sm"
              icon="i-lucide-filter"
              class="h-8 w-8"
              :title="hasActiveLocalFilters ? 'Filters active' : 'Filter regions'"
              :aria-label="hasActiveLocalFilters ? 'Filters active' : 'Filter regions'"
            />
          </UDropdownMenu>

          <UDropdownMenu :items="actionMenuItems" :content="{ align: 'end' }">
            <UButton
              color="neutral"
              variant="ghost"
              size="sm"
              icon="i-lucide-ellipsis-vertical"
              class="h-8 w-8"
              title="Region actions"
              aria-label="Region actions"
            />
          </UDropdownMenu>
        </div>
      </div>
    </div>

    <div class="min-h-0 flex-1 overflow-y-auto px-3 py-2">
      <div
        v-if="isLoadingAnnotations && displayRegions.length === 0"
        class="flex flex-col items-center justify-center py-8 text-muted-foreground"
      >
        <div class="flex items-center gap-2 mb-3">
          <Icon name="i-lucide-loader-2" class="h-5 w-5 animate-spin text-primary" />
          <p class="text-sm font-medium text-foreground">
            Loading annotations...
          </p>
        </div>
        <p class="text-xs mb-4">
          Regions will appear as soon as the PAGE XML annotation is loaded.
        </p>
      </div>
      <div v-else-if="displayRegions.length === 0" class="flex flex-col items-center justify-center py-12 text-muted-foreground">
        <Icon name="i-lucide-search" class="h-8 w-8 mb-2 opacity-50" />
        <p class="text-sm">
          No regions found
        </p>
        <p class="text-xs">
          Try adjusting your search
        </p>
      </div>
      <div v-else class="flex flex-col gap-3">
        <div v-for="region in displayRegions" :key="region.id" class="flex flex-col gap-2">
          <div class="flex items-center gap-3 p-2 rounded-sm bg-background/95 backdrop-blur-sm border border-border/40">
            <div class="w-1 h-8 rounded-sm" :style="{ backgroundColor: region.color }" />
            <div class="flex-1 min-w-0 flex items-center gap-2">
              <Icon name="i-lucide-map-pin" class="h-4 w-4 text-muted shrink-0" />
              <span class="font-medium text-sm truncate">{{ region.label }}</span>
              <UBadge
                variant="solid"
                color="neutral"
                size="xs"
                class="font-mono shrink-0"
              >
                {{ region.textLineCount }} lines
              </UBadge>
              <span v-if="region.regionSubtype || region.regionKind" class="text-xs text-muted capitalize truncate">
                {{ region.regionSubtype ?? region.regionKind }}
              </span>
            </div>
            <UButton
              color="neutral"
              variant="soft"
              size="xs"
              icon="i-lucide-refresh-cw"
              :disabled="!isCanvasEditable"
              @click="syncRegionGtFromTextLines(region.id)"
            >
              Sync GT From Textlines
            </UButton>
          </div>

          <EditorTextRegionItem
            :region="region"
            :image-url="effectiveImageUrl"
            :padding="padding"
            :font-size="fontSize"
            :layout="textItemLayout"
            :codec-characters="codecCharacters"
            :highlight-unknown-codec-chars="highlightUnknownCodecChars"
            :include-whitespace-in-codec-highlight="includeWhitespaceInCodecHighlight"
            :highlight-unknown-dictionary-tokens="highlightUnknownDictionaryTokens"
            :gt-index="gtIndexModel"
            :recognition-indices="recognitionIndicesModel"
            :show-diff="showDiffModel"
            :is-selected="selectedRegionId === region.id"
            :text-highlight-query="activeTextHighlightQuery"
            :project-codec-id="editorStore.projectCodecId"
            :project-dictionary-id="editorStore.projectDictionaryId"
            :can-quick-add-to-dictionary="canQuickAddToDictionary"
            :project-dictionary-locked="editorStore.projectDictionaryLocked"
            :project-dictionary-case-sensitive="editorStore.projectDictionaryCaseSensitive"
            :project-dictionary-unicode-normalization="editorStore.projectDictionaryUnicodeNormalization"
            :selected-keyboard-id="selectedKeyboardId"
            :has-virtual-keyboard="Boolean(selectedLayout)"
            :read-only="!isCanvasEditable"
            @select-region="handleSelectRegion"
            @add-text-content-variant="handleAddTextContentVariant"
            @remove-text-content-variant="handleRemoveTextContentVariant"
            @update-text-content-variant="handleCommitTextContentVariant"
            @update-text-content-variant-index="handleCommitTextContentVariantIndex"
            @create-gt-from-recognition="handleCreateGtFromRecognition"
            @quick-add-codec-char="handleQuickAddCodecCharacter"
            @quick-add-dictionary-token="handleQuickAddDictionaryToken"
            @quick-add-keyboard-char="handleQuickAddKeyboardCharacter"
            @open-codec-editor="handleOpenCodecEditor"
            @open-dictionary-editor="handleOpenDictionaryEditor"
            @open-keyboard-editor="handleOpenKeyboardEditor"
          />
        </div>
      </div>
    </div>

    <VirtualKeyboard
      v-if="virtualKeyboardMode === 'floating' && selectedLayout"
      :layout="selectedLayout"
      :theme="selectedTheme"
      :layouts="keyboards ?? []"
      @update:layout-id="selectedKeyboardId = $event"
    />

    <VirtualKeyboardSlideover
      v-if="virtualKeyboardMode === 'slideover' && selectedLayout"
      :layout="selectedLayout"
      :theme="selectedTheme"
      :layouts="keyboards ?? []"
      @update:layout-id="selectedKeyboardId = $event"
    />
  </div>
</template>
