<script setup lang="ts">
import { LazyDictionarySlideoverBrowser, LazyUiConfirmModal } from '#components'
import { getEditorSession } from '@/session/editor/editor-session'
import { PolygonType } from '@/models/editor'
import type { TextContentVariantData } from '@/models/editor'
import type { Point } from '@/models/editor/types'
import { worldToImage } from '@/utils/editor/coordinates'
import { getRegionColor } from '@/utils/editor/region-colors'
import { findRegionLabelDefinitionForRegion } from '@/utils/editor/page-label-mapping'
import type { RegionKind } from '@/models/editor/region'
import { CompoundCommand, DeletePolygonCommand, ReorderTextLinesCommand, UpdateTextContentVariantsCommand } from '@/commands'
import { useTextViewShortcutScope } from '@/composables/editor/use-keyboard-shortcuts'
import { useEditorCollaboration } from '@/composables/editor/use-editor-collaboration'
import { createScopedLogger } from '@/services/editor/logger-service'
import { useEditorSessionStore } from '@/stores/editor/editor.session.store'
import { usePageFilter } from '@/composables/use-page-filter'
import type { KeyboardItem, KeyboardLayout } from '@/types/virtual-keyboard'
import type { LabelDefinition } from '@/types/label-set'
import { wsKey } from '@/utils/fetch-keys'
import { findTextLineRecursive } from '@/utils/editor/pcgts-editor-primitives'
import { collectRegionIdsInReadingOrder } from '@/utils/editor/textline-navigation'
import { tokenizeForDictionary } from '../shared/text-highlighting'
import { computeTextLineReadingDirectionMap } from './reading-direction'
import {
  compareConfidenceLowFirst,
  createVariantFilterState,
  filterTextContentVariants,
  getMinVariantConfidence
} from './variant-filtering'
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

const log = createScopedLogger('TextView')

const props = defineProps<{ canvasId?: string | null }>()

const editorStore = useEditorStore()
const uiStore = useEditorUiStore()
const sessionStore = useEditorSessionStore()
const workspaceStore = useWorkspaceStore()
const collaboration = useEditorCollaboration()
const toast = useToast()

const projectId = computed(() => sessionStore.projectId ?? undefined)
const {
  textContent: filterTextContent,
  getMatchingTextLineIds
} = usePageFilter(projectId)

const overlay = useOverlay()
const confirmModal = overlay.create(LazyUiConfirmModal)
const dictionaryBrowserSlideover = overlay.create(LazyDictionarySlideoverBrowser)
const {
  ensureTokenResults,
  getTokenResult,
  invalidateToken: invalidateDictionaryToken,
  isTokenPending
} = useDictionaryTokenLookup()

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
const filterMode = ref<'all' | 'empty' | 'withComments' | 'lowConfidence' | 'matchingFilter' | 'dictionaryMismatch' | 'diffMismatch'>('all')

const collapsedRegionIds = ref<Set<string>>(new Set())
const orderOverrideByRegion = ref<Record<string, string[]>>({})
const selectedTextlineId = ref<string | null>(null)

const matchingTextLineIds = ref<Set<string>>(new Set())
const isLoadingMatchingTextLines = ref(false)

const isCanvasEditable = computed(() => {
  const canvasId = effectiveCanvasId.value
  return canvasId ? collaboration.canEditCanvas(canvasId) : true
})

const reorderEnabled = computed(() => {
  return isCanvasEditable.value
    && sortOrder.value === 'asc'
    && filterMode.value === 'all'
    && searchQuery.value.trim().length === 0
})

const virtualKeyboardMode = computed(() => uiStore.virtualKeyboardMode)
const { keyboards, selectedLayout, selectedKeyboardId } = useVirtualKeyboards()
const selectedWorkspaceId = computed(() => workspaceStore.selectedWorkspaceId as string | null)

const effectiveCanvasId = computed(() => props.canvasId ?? editorStore.activeCanvasId)
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
const hasProjectCodec = computed(() => {
  return Boolean(editorStore.projectCodecId) || (codecCharacters.value?.length ?? 0) > 0
})
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

const showCommentsModel = computed(() => textViewSettings.value.showComments)

function normalizeSingleLineText(value: string): string {
  return value.replace(/[ \t]*\r?\n+[ \t]*/g, ' ')
}

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

function triggerCreateGtForSelectedTextline(): boolean {
  if (!isCanvasEditable.value) return false
  const selectedId = selectedTextlineId.value
  if (!selectedId) return false

  const selectedLine = displayTextlines.value.find(tl => tl.id === selectedId)
  if (!selectedLine) return false

  const regionId = selectedLine.parentId ?? '__unassigned__'
  if (collapsedRegionIds.value.has(regionId)) return false
  if (selectedLine.hasGtVariant) return false

  const source = selectedLine.recognitionCandidates?.find(candidate => candidate.text.trim().length > 0)
  if (!source) return false

  handleCreateGtFromRecognition(selectedLine.id, {
    gtIndex: gtIndexModel.value,
    sourceRecognitionIndex: source.index
  })
  return true
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
    createGtFromRecognition: () => triggerCreateGtForSelectedTextline()
  }
})

const currentPageId = computed(() => {
  const canvasId = effectiveCanvasId.value
  if (!canvasId) return null
  return editorStore.canvases[canvasId]?.pageId ?? null
})

watch([filterMode, currentPageId, filterTextContent], async ([mode, pageId, textContentFilter]) => {
  if (mode !== 'matchingFilter' || !pageId || !textContentFilter?.trim()) {
    matchingTextLineIds.value = new Set()
    return
  }

  isLoadingMatchingTextLines.value = true
  try {
    const ids = await getMatchingTextLineIds(pageId)
    matchingTextLineIds.value = new Set(ids)
  } catch (error) {
    log.error('Failed to fetch matching text line IDs:', error)
    matchingTextLineIds.value = new Set()
  } finally {
    isLoadingMatchingTextLines.value = false
  }
}, { immediate: true })

const hasTextContentFilter = computed(() => Boolean(filterTextContent.value?.trim()))
const activeTextHighlightQuery = computed(() => {
  const backendQuery = filterTextContent.value?.trim() ?? ''
  if (backendQuery) return backendQuery
  return searchQuery.value.trim()
})

const textLineReadingDirectionById = computed(() => {
  const canvasId = effectiveCanvasId.value
  if (!canvasId) return {}
  const session = getEditorSession(canvasId)
  return computeTextLineReadingDirectionMap(session?.document.value?.page)
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

function toggleRegion(regionId: string): void {
  const next = new Set(collapsedRegionIds.value)
  if (next.has(regionId)) next.delete(regionId)
  else next.add(regionId)
  collapsedRegionIds.value = next
}

function expandAll(): void {
  collapsedRegionIds.value = new Set()
}

function collapseAll(regionIds: string[]): void {
  collapsedRegionIds.value = new Set(regionIds)
}

function setRegionOrderOverride(regionId: string, orderedIds: string[]): void {
  orderOverrideByRegion.value = {
    ...orderOverrideByRegion.value,
    [regionId]: [...orderedIds]
  }
}

function handleSelectTextline(textlineId: string): void {
  selectedTextlineId.value = textlineId

  const runtime = getTextViewRuntimeControls(effectiveCanvasId.value, editorStore)
  runtime?.selectPolylineById?.(null, { zoomToFit: false })
  runtime?.selectPolygonById?.(textlineId, { zoomToFit: false })

  if (!runtime?.selectPolygonById) {
    if (effectiveCanvasId.value === editorStore.activeCanvasId) {
      editorStore.clearBaselineSelection()
      editorStore.selectRegionById(textlineId)
    }
  }
}

function persistRegionOrder(regionId: string, orderedTextLineIds: string[]): void {
  if (!isCanvasEditable.value) return
  if (regionId === '__unassigned__') return

  const runtime = getTextViewRuntimeControls(effectiveCanvasId.value, editorStore)
  if (!runtime?.commander) return

  runtime.commander.execute(
    new ReorderTextLinesCommand({
      parentTextRegionId: regionId,
      orderedTextLineIds
    }),
    createTextViewCommandContext(effectiveCanvasId.value)
  )
}

async function handleDeleteTextline(textlineId: string): Promise<void> {
  if (!isCanvasEditable.value) return
  const instance = confirmModal.open({
    title: 'Delete Textline?',
    description: `Are you sure you want to delete "${textlineId}"? This action cannot be undone.`,
    confirmLabel: 'Delete',
    confirmColor: 'error'
  })
  const confirmed = await instance.result
  if (!confirmed) return

  const runtime = getTextViewRuntimeControls(effectiveCanvasId.value, editorStore)
  if (!runtime?.commander) return

  runtime.commander.execute(new DeletePolygonCommand({ polygonId: textlineId }), createTextViewCommandContext(effectiveCanvasId.value))
  if (selectedTextlineId.value === textlineId) selectedTextlineId.value = null
}

function handleUpdateTextlineComment(textlineId: string, comment: string): void {
  if (!isCanvasEditable.value) return
  const canvasId = effectiveCanvasId.value
  if (!canvasId) return

  const session = getEditorSession(canvasId)
  const pcGts = session?.document.value
  if (!session || !pcGts) return

  const hit = findTextLineRecursive(pcGts.page.regions, textlineId)
  if (!hit) return

  const normalizedComment = comment.trim()
  hit.textLine.comments = normalizedComment.length > 0 ? normalizedComment : undefined
  pcGts.metadata?.touch?.()
  triggerRef(session.document)
}

function canMoveTextline(regionId: string, textlineId: string, direction: -1 | 1): boolean {
  if (!reorderEnabled.value) return false
  if (regionId === '__unassigned__') return false

  const list = textlinesByRegion.value.get(regionId) ?? []
  const fromIndex = list.findIndex(item => item.id === textlineId)
  if (fromIndex < 0) return false

  const toIndex = fromIndex + direction
  return toIndex >= 0 && toIndex < list.length
}

function handleMoveTextline(regionId: string, textlineId: string, direction: -1 | 1): void {
  if (!canMoveTextline(regionId, textlineId, direction)) return

  const list = textlinesByRegion.value.get(regionId) ?? []
  const orderedIds = list.map(item => item.id)
  const fromIndex = orderedIds.indexOf(textlineId)
  if (fromIndex < 0) return
  const toIndex = fromIndex + direction
  if (toIndex < 0 || toIndex >= orderedIds.length) return

  const [movedId] = orderedIds.splice(fromIndex, 1)
  if (!movedId) return
  orderedIds.splice(toIndex, 0, movedId)

  setRegionOrderOverride(regionId, orderedIds)
  persistRegionOrder(regionId, orderedIds)
}

function commitTextContentVariants(textlineId: string, nextTextContentVariants: TextContentVariantData[] | undefined): void {
  if (!isCanvasEditable.value) return
  const runtime = getTextViewRuntimeControls(effectiveCanvasId.value, editorStore)
  if (!runtime?.commander) return

  const textlineCommand = new UpdateTextContentVariantsCommand({
    elementId: textlineId,
    nextTextContentVariants
  })
  const regionSyncCommand = buildParentRegionSyncCommand(textlineId, nextTextContentVariants)

  if (regionSyncCommand) {
    runtime.commander.execute(
      new CompoundCommand(
        [textlineCommand, regionSyncCommand],
        'Update textline GT and sync parent region GT'
      ),
      createTextViewCommandContext(effectiveCanvasId.value)
    )
    return
  }

  runtime.commander.execute(textlineCommand, createTextViewCommandContext(effectiveCanvasId.value))
}

function buildParentRegionSyncCommand(
  textlineId: string,
  nextTextlineVariants: TextContentVariantData[] | undefined
): UpdateTextContentVariantsCommand | null {
  const canvasId = effectiveCanvasId.value
  if (!canvasId) return null

  const session = getEditorSession(canvasId)
  const pageRegions = session?.document.value?.page?.regions
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

  const nextGtText = composeRegionGtFromTextLines(syncedTextLines, gtIndexModel.value)
  const nextRegionVariants = buildRegionGtSyncedVariants(
    parentTextRegion.textContentVariants as TextContentVariantData[] | undefined,
    nextGtText,
    gtIndexModel.value
  )
  const currentNormalized = normalizeTextContentVariants(parentTextRegion.textContentVariants as TextContentVariantData[] | undefined)
  const nextNormalized = normalizeTextContentVariants(nextRegionVariants)
  if (JSON.stringify(currentNormalized) === JSON.stringify(nextNormalized)) return null

  return new UpdateTextContentVariantsCommand({
    elementId: parentRegionId,
    nextTextContentVariants: nextRegionVariants
  })
}

function handleAddTextContentVariant(textlineId: string): void {
  if (!isCanvasEditable.value) return
  const runtime = getTextViewRuntimeControls(effectiveCanvasId.value, editorStore)
  const regions = runtime?.polygons ?? []
  const region = regions.find(r => r.id === textlineId)
  if (!region) return

  const current = normalizeTextContentVariants(region.textContentVariants)
  const newIndex = lowestFreeIndex(current)
  current.push({ unicode: '', index: newIndex })
  current.sort(sortByIndex)

  commitTextContentVariants(textlineId, current)
}

function handleRemoveTextContentVariant(textlineId: string, arrayPos: number): void {
  if (!isCanvasEditable.value) return
  const runtime = getTextViewRuntimeControls(effectiveCanvasId.value, editorStore)
  const regions = runtime?.polygons ?? []
  const region = regions.find(r => r.id === textlineId)
  if (!region) return

  const current = normalizeTextContentVariants(region.textContentVariants)
  if (arrayPos < 0 || arrayPos >= current.length) return

  current.splice(arrayPos, 1)
  commitTextContentVariants(textlineId, current)
}

function handleCommitTextContentVariant(textlineId: string, pos: number, text: string): void {
  if (!isCanvasEditable.value) return
  const runtime = getTextViewRuntimeControls(effectiveCanvasId.value, editorStore)
  const regions = runtime?.polygons ?? []
  const region = regions.find(r => r.id === textlineId)
  if (!region) return

  const current = normalizeTextContentVariants(region.textContentVariants)
  const te = current[pos]
  if (!te) return
  te.unicode = normalizeSingleLineText(text)
  current.sort(sortByIndex)

  commitTextContentVariants(textlineId, current)
}

function handleCreateGtFromRecognition(textlineId: string, payload: { gtIndex: number, sourceRecognitionIndex?: number }) {
  if (!isCanvasEditable.value) return
  const runtime = getTextViewRuntimeControls(effectiveCanvasId.value, editorStore)
  const regions = runtime?.polygons ?? []
  const region = regions.find(r => r.id === textlineId)
  if (!region) return

  const current = normalizeTextContentVariants(region.textContentVariants)
  if (current.some(t => t.index === payload.gtIndex)) return

  const source = current.find(t => t.index === payload.sourceRecognitionIndex)
  if (!source) return

  current.push({
    unicode: normalizeSingleLineText(source.unicode ?? ''),
    index: payload.gtIndex
  })
  current.sort(sortByIndex)
  commitTextContentVariants(textlineId, current)
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

function handleCommitTextContentVariantIndex(textlineId: string, pos: number, toIndex: number | undefined): void {
  if (!isCanvasEditable.value) return
  if (toIndex !== undefined && (!Number.isInteger(toIndex) || toIndex < 0)) return

  const runtime = getTextViewRuntimeControls(effectiveCanvasId.value, editorStore)
  const regions = runtime?.polygons ?? []
  const region = regions.find(r => r.id === textlineId)
  if (!region) return

  const current = normalizeTextContentVariants(region.textContentVariants)
  const from = current[pos]
  if (!from) return

  if (toIndex === undefined) {
    from.index = undefined
    current.sort(sortByIndex)
    commitTextContentVariants(textlineId, current)
    return
  }

  const toPos = current.findIndex(t => t.index === toIndex)
  if (toPos >= 0 && toPos !== pos) {
    const tmp = from.index
    current[toPos]!.index = tmp
  }
  from.index = toIndex
  current.sort(sortByIndex)

  commitTextContentVariants(textlineId, current)
}

function variantRole(index: number | undefined): 'gt' | 'recognition' | 'nonAssigned' {
  if (typeof index === 'number' && index === gtIndexModel.value) return 'gt'
  if (index === undefined && recognitionIndicesModel.value.includes(-1)) return 'recognition'
  if (typeof index === 'number' && recognitionIndicesModel.value.includes(index)) return 'recognition'
  return 'nonAssigned'
}

const textlines = computed(() => {
  const canvasId = effectiveCanvasId.value
  if (!canvasId) return []

  const runtime = getTextViewRuntimeControls(canvasId, editorStore)
  const regions = runtime?.polygons ?? []
  const variantFilterState = createVariantFilterState({
    selectedIndices: selectedIndicesModel.value,
    filterUnindexed: filterUnindexedModel.value,
    confidenceRange: confidenceRangeModel.value
  })

  const textlineRegions = regions.filter(r => r.type === PolygonType.TEXTLINE)

  const result = textlineRegions.map((tl) => {
    const allTextContentVariants = normalizeTextContentVariants(tl.textContentVariants)
      .map((te, pos) => ({
        pos,
        index: typeof te.index === 'number' && Number.isFinite(te.index) && te.index >= 0 ? te.index : undefined,
        text: te.unicode ?? '',
        confidence: te.confidence
      }))

    const filteredTextContentVariants = filterTextContentVariants(allTextContentVariants, variantFilterState)
      .sort((a, b) => {
        const ai = typeof a.index === 'number' ? a.index : -1
        const bi = typeof b.index === 'number' ? b.index : -1
        return ai - bi
      })

    const visibleTextContentVariants = showNonAssignedIndicesModel.value
      ? filteredTextContentVariants
      : filteredTextContentVariants.filter(te => variantRole(te.index) !== 'nonAssigned')

    const lineConfidence = getMinVariantConfidence(visibleTextContentVariants)
    const matchesVariantFilter = !variantFilterState.hasVariantFilter || filteredTextContentVariants.length > 0
    const matchesAssignedVisibility = !variantFilterState.hasVariantFilter || visibleTextContentVariants.length > 0
    const hasGtVariant = allTextContentVariants.some(te => te.index === gtIndexModel.value)
    const recognitionCandidates = recognitionIndicesModel.value.flatMap((idx: number) => {
      if (idx === -1) {
        return allTextContentVariants.filter(te => te.index === undefined && te.text.trim().length > 0)
      }
      return allTextContentVariants.filter(te => te.index === idx && te.text.trim().length > 0)
    })

    return {
      id: tl.id,
      label: tl.label ?? tl.id,
      parentId: tl.parentId,
      comments: tl.comments,
      points: toImagePoints(tl.points),
      readingDirection: textLineReadingDirectionById.value[tl.id],
      textContentVariants: visibleTextContentVariants,
      allTextContentVariants,
      hasAnyText: allTextContentVariants.some(te => te.text.trim().length > 0),
      hasGtVariant,
      recognitionCandidates,
      lineConfidence,
      matchesVariantFilter,
      matchesAssignedVisibility
    }
  })

  return result.filter(tl => tl.matchesVariantFilter && tl.matchesAssignedVisibility)
})

const dictionaryTokensOnPage = computed(() => {
  const tokens = textlines.value.flatMap(textline =>
    textline.textContentVariants
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
    // Leave the text visible even if dictionary checks fail.
  }
}, { immediate: true })

function textlineHasDictionaryMismatch(textline: { textContentVariants: Array<{ index?: number, text: string }> }): boolean {
  const workspaceId = selectedWorkspaceId.value
  const dictionaryId = editorStore.projectDictionaryId
  if (!canCheckDictionaryTokens.value || !workspaceId || !dictionaryId) return false

  const tokens = textline.textContentVariants
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

function textlineHasDiffMismatch(textline: { textContentVariants: Array<{ index?: number, text: string }> }): boolean {
  const gtText = textline.textContentVariants.find(variant => variantRole(variant.index) === 'gt')?.text ?? ''
  return textline.textContentVariants.some(variant => variantRole(variant.index) !== 'gt' && variant.text !== gtText)
}

const selectedTextlineIdFromSharedSelection = computed(() => {
  const runtime = getTextViewRuntimeControls(effectiveCanvasId.value, editorStore)
  const polygonSelection = runtime?.selectedPolygonId?.value ?? null
  if (typeof polygonSelection === 'string' && polygonSelection.length > 0) {
    return polygonSelection
  }

  const fallback = editorStore.activeSelectedRegionId
  return typeof fallback === 'string' && fallback.length > 0 ? fallback : null
})

watch([selectedTextlineIdFromSharedSelection, textlines], ([selectedId, lines]) => {
  if (!selectedId) {
    selectedTextlineId.value = null
    return
  }
  selectedTextlineId.value = lines.some(line => line.id === selectedId) ? selectedId : null
}, { immediate: true })

const totalTextlineCount = computed(() => {
  const runtime = getTextViewRuntimeControls(effectiveCanvasId.value, editorStore)
  const polygons = runtime?.polygons ?? []
  return polygons.filter(p => p.type === PolygonType.TEXTLINE).length
})

const displayTextlines = computed(() => {
  const q = searchQuery.value.trim().toLowerCase()
  let items = [...textlines.value]

  if (onlyMissingGtModel.value) {
    items = items.filter(tl => !tl.hasGtVariant)
  }

  if (filterMode.value === 'empty') {
    items = items.filter(tl => !tl.hasAnyText)
  } else if (filterMode.value === 'withComments') {
    items = items.filter(tl => (tl.comments ?? '').trim().length > 0)
  } else if (filterMode.value === 'lowConfidence') {
    items = items.filter(tl => typeof tl.lineConfidence === 'number' && tl.lineConfidence < 0.8)
  } else if (filterMode.value === 'dictionaryMismatch') {
    items = items.filter(textlineHasDictionaryMismatch)
  } else if (filterMode.value === 'diffMismatch') {
    items = items.filter(textlineHasDiffMismatch)
  } else if (filterMode.value === 'matchingFilter') {
    if (matchingTextLineIds.value.size > 0) {
      items = items.filter(tl => matchingTextLineIds.value.has(tl.id))
    } else if (!isLoadingMatchingTextLines.value) {
      items = []
    }
  }

  if (q) {
    items = items.filter((tl) => {
      if (tl.id.toLowerCase().includes(q)) return true
      if ((tl.label ?? '').toLowerCase().includes(q)) return true
      return tl.textContentVariants.some(te => te.text.toLowerCase().includes(q))
    })
  }

  if (sortOrder.value === 'confidence') {
    items.sort((a, b) => compareConfidenceLowFirst(a.lineConfidence, b.lineConfidence))
  } else if (sortOrder.value === 'desc') {
    items.reverse()
  }

  return items
})

const readingOrderRegionRank = computed(() => {
  void uiStore.readingOrderVersion

  const canvasId = effectiveCanvasId.value
  const readingOrder = canvasId
    ? getEditorSession(canvasId)?.document.value?.page?.readingOrder
    : undefined
  const orderedRegionIds = collectRegionIdsInReadingOrder(readingOrder)

  return new Map(orderedRegionIds.map((id, index) => [id, index]))
})

const regionMeta = computed(() => {
  const runtime = getTextViewRuntimeControls(effectiveCanvasId.value, editorStore)
  const polygons = runtime?.polygons ?? []
  const regionPolygons = polygons.filter(p => p.type === PolygonType.REGION)
  const regionById = new Map(regionPolygons.map(r => [r.id, r]))
  const regionRank = readingOrderRegionRank.value

  const parentIds = new Set<string>()
  for (const tl of textlines.value) {
    if (tl.parentId) parentIds.add(tl.parentId)
  }

  const regions = [...parentIds].map((id) => {
    const region = regionById.get(id)
    return {
      id,
      label: region?.label ?? id,
      regionSubtype: region?.regionSubtype,
      regionKind: region?.regionKind,
      color: regionColor(region?.regionKind, region?.regionSubtype, region?.regionCustom)
    }
  }).sort((a, b) => {
    if (sortOrder.value === 'asc' || sortOrder.value === 'desc') {
      const rankA = regionRank.get(a.id)
      const rankB = regionRank.get(b.id)
      if (rankA !== undefined && rankB !== undefined && rankA !== rankB) {
        return sortOrder.value === 'desc' ? rankB - rankA : rankA - rankB
      }
      if (rankA !== undefined && rankB === undefined) return -1
      if (rankA === undefined && rankB !== undefined) return 1
    }

    return a.label.localeCompare(b.label)
  })

  if (textlines.value.some(tl => !tl.parentId)) {
    regions.unshift({
      id: '__unassigned__',
      label: 'Unassigned',
      regionSubtype: undefined,
      regionKind: undefined,
      color: '#666'
    })
  }

  return regions
})

const textlinesByRegion = computed(() => {
  const map = new Map<string, typeof displayTextlines.value>()
  for (const region of regionMeta.value) map.set(region.id, [])

  for (const tl of displayTextlines.value) {
    const regionId = tl.parentId ?? '__unassigned__'
    const list = map.get(regionId) ?? []
    list.push(tl)
    map.set(regionId, list)
  }

  if (reorderEnabled.value) {
    for (const [regionId, list] of map) {
      const override = orderOverrideByRegion.value[regionId]
      if (!override || override.length === 0) continue
      const byId = new Map(list.map(tl => [tl.id, tl]))
      const next: typeof list = []
      for (const id of override) {
        const item = byId.get(id)
        if (!item) continue
        next.push(item)
        byId.delete(id)
      }
      for (const remaining of byId.values()) next.push(remaining)
      map.set(regionId, next)
    }
  }

  return map
})

const sortMenuItems = computed(() => [
  [
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
  ]
])

const filterMenuItems = computed(() => {
  const items = [
    [
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
        label: 'With comments',
        icon: 'i-lucide-message-square-text',
        active: filterMode.value === 'withComments',
        activeColor: 'primary',
        activeVariant: 'solid',
        onSelect: () => { filterMode.value = 'withComments' }
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
        label: 'Diff mismatches',
        icon: 'i-lucide-git-compare',
        active: filterMode.value === 'diffMismatch',
        activeColor: 'primary',
        activeVariant: 'solid',
        onSelect: () => { filterMode.value = 'diffMismatch' }
      },
      {
        label: 'Only lines without GT',
        icon: 'i-lucide-leaf',
        active: onlyMissingGtModel.value,
        activeColor: 'primary',
        activeVariant: 'solid',
        onSelect: () => { onlyMissingGtModel.value = !onlyMissingGtModel.value }
      }
    ]
  ]

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

const hasCollapsedRegions = computed(() => {
  return regionMeta.value.some(region => collapsedRegionIds.value.has(region.id))
})

const hasActiveLocalFilters = computed(() => {
  return filterMode.value !== 'all' || onlyMissingGtModel.value
})

const sectionMenuItems = computed(() => {
  const items = [[
    {
      label: 'Expand all sections',
      icon: 'i-lucide-unfold-vertical',
      active: !hasCollapsedRegions.value,
      activeColor: 'primary',
      activeVariant: 'solid',
      onSelect: () => { expandAll() }
    },
    {
      label: 'Collapse all sections',
      icon: 'i-lucide-fold-vertical',
      active: hasCollapsedRegions.value,
      activeColor: 'primary',
      activeVariant: 'solid',
      onSelect: () => { collapseAll(regionMeta.value.map(r => r.id)) }
    }
  ]]

  return items
})
</script>

<template>
  <div ref="rootEl" data-shortcut-scope="text-view" class="flex flex-col h-full">
    <div
      data-tour="editor-textline-list-toolbar"
      class="sticky top-0 z-30 border-b bg-default/95 px-3 py-2 backdrop-blur supports-[backdrop-filter]:bg-default/85"
    >
      <div class="flex flex-wrap items-center gap-2 md:flex-nowrap">
        <div class="flex min-w-0 shrink-0 items-center gap-2.5">
          <div class="flex items-center gap-2 min-w-0">
            <Icon name="i-lucide-layers" class="h-5 w-5 text-primary" />
            <h2 class="truncate text-base font-semibold">
              Textlines
            </h2>
          </div>
          <UBadge
            variant="solid"
            color="neutral"
            size="sm"
            class="shrink-0 font-mono"
          >
            {{ displayTextlines.length }}/{{ totalTextlineCount }}
          </UBadge>
        </div>

        <div class="order-3 basis-full md:order-2 md:min-w-[18rem] md:flex-1">
          <UInput
            v-model="searchQuery"
            icon="i-lucide-search"
            placeholder="Search textlines..."
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
              :title="`Sort textlines (${currentSortLabel})`"
              :aria-label="`Sort textlines (${currentSortLabel})`"
            />
          </UDropdownMenu>

          <UDropdownMenu :items="filterMenuItems" :content="{ align: 'end' }">
            <UButton
              color="neutral"
              :variant="hasActiveLocalFilters ? 'soft' : 'ghost'"
              size="sm"
              icon="i-lucide-filter"
              :title="hasActiveLocalFilters ? 'Filters active' : 'Filter textlines'"
              :aria-label="hasActiveLocalFilters ? 'Filters active' : 'Filter textlines'"
            />
          </UDropdownMenu>

          <UDropdownMenu :items="sectionMenuItems" :content="{ align: 'end' }">
            <UButton
              color="neutral"
              :variant="hasCollapsedRegions ? 'soft' : 'ghost'"
              size="sm"
              icon="i-lucide-ellipsis-vertical"
              title="Textline actions"
              aria-label="Textline actions"
            />
          </UDropdownMenu>
        </div>
      </div>
    </div>

    <div class="min-h-0 flex-1 overflow-y-auto px-3 py-2">
      <div
        v-if="isLoadingAnnotations && displayTextlines.length === 0"
        class="flex flex-col items-center justify-center py-8 text-muted"
      >
        <div class="flex items-center gap-2 mb-3">
          <Icon name="i-lucide-loader-2" class="h-5 w-5 animate-spin text-primary" />
          <p class="text-sm font-medium text-default">
            Loading annotations...
          </p>
        </div>
        <p class="text-xs mb-4">
          Textlines will appear as soon as the PAGE XML annotation is loaded.
        </p>
        <div class="w-full max-w-3xl space-y-3">
          <div
            v-for="i in 3"
            :key="`textview-loading-${i}`"
            class="rounded-lg border border-default/50 bg-elevated/40 p-3 space-y-2"
          >
            <div class="flex items-center gap-2">
              <USkeleton class="h-5 w-20" />
              <USkeleton class="h-4 w-14" />
            </div>
            <div class="grid grid-cols-1 gap-2 @md:grid-cols-2">
              <USkeleton class="h-20 w-full" />
              <USkeleton class="h-20 w-full" />
            </div>
          </div>
        </div>
      </div>
      <div v-else-if="displayTextlines.length === 0" class="flex flex-col items-center justify-center py-12 text-muted">
        <Icon name="i-lucide-search" class="h-8 w-8 mb-2 opacity-50" />
        <p class="text-sm">
          No textlines found
        </p>
        <p class="text-xs">
          Try adjusting your search
        </p>
      </div>
      <div v-else class="flex flex-col gap-3">
        <div
          v-for="region in regionMeta"
          :key="region.id"
        >
          <template v-if="!searchQuery || (textlinesByRegion.get(region.id)?.length ?? 0) > 0">
            <button
              type="button"
              class="w-full flex items-center gap-3 p-2 rounded-sm hover:bg-muted/50 transition-colors group sticky top-0 z-20 bg-default/95 backdrop-blur-sm"
            >
              <div class="w-1 h-8 rounded-sm" :style="{ backgroundColor: region.color }" />
              <div class="flex-1 flex items-center gap-2 min-w-0">
                <Icon name="i-lucide-map-pin" class="h-4 w-4 text-muted shrink-0" />
                <span class="font-medium text-sm truncate">{{ region.label }}</span>
                <UBadge
                  variant="solid"
                  color="neutral"
                  size="xs"
                  class="font-mono shrink-0"
                >
                  {{
                    (textlinesByRegion.get(region.id) ?? []).filter(tl => tl.hasAnyText).length
                  }}/{{ (textlinesByRegion.get(region.id) ?? []).length }}
                </UBadge>
                <span v-if="region.regionSubtype || region.regionKind" class="text-xs text-muted capitalize truncate">
                  {{ region.regionSubtype ?? region.regionKind }}
                </span>
              </div>
              <UButton
                color="neutral"
                variant="ghost"
                size="sm"
                class="h-7 w-7 p-0"
                @click.stop="toggleRegion(region.id)"
              >
                <Icon
                  name="i-lucide-chevron-down"
                  :class="[
                    'h-4 w-4 text-muted transition-transform duration-200 shrink-0',
                    collapsedRegionIds.has(region.id) && '-rotate-90'
                  ]"
                />
              </UButton>
            </button>

            <div
              v-show="!collapsedRegionIds.has(region.id)"
              class="ml-3 pl-3 border-l-2 mt-2 flex flex-col gap-2"
              :style="{ borderLeftColor: region.color }"
            >
              <div
                v-if="(textlinesByRegion.get(region.id) ?? []).length === 0"
                class="py-4 text-center text-sm text-muted"
              >
                No textlines in this region
              </div>
              <div
                v-else
                class="flex flex-col gap-2"
              >
                <template v-for="textline in (textlinesByRegion.get(region.id) ?? [])" :key="textline.id">
                  <EditorTextLineItem
                    :textline="textline"
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
                    :has-gt-variant="textline.hasGtVariant"
                    :recognition-candidates="textline.recognitionCandidates"
                    :show-diff="showDiffModel"
                    :is-selected="selectedTextlineId === textline.id"
                    :text-highlight-query="activeTextHighlightQuery"
                    :show-comments="showCommentsModel"
                    :project-codec-id="editorStore.projectCodecId"
                    :project-dictionary-id="editorStore.projectDictionaryId"
                    :can-quick-add-to-dictionary="canQuickAddToDictionary"
                    :project-dictionary-locked="editorStore.projectDictionaryLocked"
                    :project-dictionary-case-sensitive="editorStore.projectDictionaryCaseSensitive"
                    :project-dictionary-unicode-normalization="editorStore.projectDictionaryUnicodeNormalization"
                    :selected-keyboard-id="selectedKeyboardId"
                    :has-virtual-keyboard="Boolean(selectedLayout)"
                    :show-reorder-buttons="reorderEnabled && region.id !== '__unassigned__'"
                    :can-move-up="canMoveTextline(region.id, textline.id, -1)"
                    :can-move-down="canMoveTextline(region.id, textline.id, 1)"
                    :read-only="!isCanvasEditable"
                    @select-textline="handleSelectTextline"
                    @move-up-textline="handleMoveTextline(region.id, textline.id, -1)"
                    @move-down-textline="handleMoveTextline(region.id, textline.id, 1)"
                    @delete-textline="handleDeleteTextline"
                    @add-text-content-variant="handleAddTextContentVariant"
                    @remove-text-content-variant="handleRemoveTextContentVariant"
                    @update-text-content-variant="handleCommitTextContentVariant"
                    @update-text-content-variant-index="handleCommitTextContentVariantIndex"
                    @update-element-comment="handleUpdateTextlineComment"
                    @create-gt-from-recognition="handleCreateGtFromRecognition"
                    @quick-add-codec-char="handleQuickAddCodecCharacter"
                    @quick-add-dictionary-token="handleQuickAddDictionaryToken"
                    @quick-add-keyboard-char="handleQuickAddKeyboardCharacter"
                    @open-codec-editor="handleOpenCodecEditor"
                    @open-dictionary-editor="handleOpenDictionaryEditor"
                    @open-keyboard-editor="handleOpenKeyboardEditor"
                  />
                </template>
              </div>
            </div>
          </template>
        </div>
      </div>
    </div>

    <VirtualKeyboard
      v-if="virtualKeyboardMode === 'floating' && selectedLayout"
      :layout="selectedLayout"
      :layouts="keyboards ?? []"
      @update:layout-id="selectedKeyboardId = $event"
    />
  </div>
</template>
