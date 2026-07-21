<script setup lang="ts">
import type { ComponentPublicInstance } from 'vue'
import { useDebounceFn } from '@vueuse/core'
import { TooltipProvider } from 'reka-ui'
import { LazyDictionarySlideoverBrowser } from '#components'
import type { TextContentVariantData } from '@/models/editor'
import type { TextLine } from '@/models/editor/text'
import type { Region } from '@/models/editor/region'
import { UpdateTextlineCommentCommand } from '@/commands/editor'
import { getEditorSession } from '@/session/editor/editor-session'
import { useEditorCollaboration } from '@/composables/editor/use-editor-collaboration'
import { useEditorSessionStore } from '@/stores/editor/editor.session.store'
import { collectTextlineIdsInReadingOrder, getAdjacentTextlineId } from '@/utils/editor/textline-navigation'
import { findTextLineRecursive } from '@/utils/editor/pcgts-editor-primitives'
import { setGtVariantUnicode } from '@/utils/editor/text-variants'
import { wsKey } from '@/utils/fetch-keys'
import { GlyphService } from '@/utils/glyph-service'
import {
  handleSingleLineTextareaBeforeInput,
  handleSingleLineTextareaDrop,
  handleSingleLineTextareaKeydownEnter,
  handleSingleLineTextareaPaste,
  normalizeSingleLineText
} from './shared/text-input-guards'
import {
  computeTextLineReadingDirectionMap,
  getReadingDirectionTextAttributes
} from './line/reading-direction'
import {
  resolveFullTextDraft,
  resolveFullTextLineValue,
  type FullTextLineSource
} from './shared/full-text-lines'
import {
  createTextViewCommandContext,
  getRequestErrorMessage,
  getTextViewRuntimeControls
} from './shared/text-view-runtime'
import { createTextlineVariantsUpdateCommand } from './shared/textline-variant-update'
import { tokenizeForDictionary } from './shared/text-highlighting'
import {
  getFullTextDictionarySegments,
  getMissingFullTextDictionaryTokens,
  getUnknownFullTextDictionarySegmentAtOffset,
  type FullTextDictionarySegment
} from './shared/full-text-dictionary'
import {
  getFullTextCodecSegments,
  getUnknownCodecCharacters,
  type FullTextCodecSegment
} from './shared/full-text-codec'
import {
  normalizeFullTextComment,
  serializeFullTextComment
} from './shared/full-text-comments'

type FullTextLine = {
  id: string
  text: string
  source: FullTextLineSource
  sourceIndex?: number
  comments?: string
  readingDirection: ReturnType<typeof computeTextLineReadingDirectionMap>[string]
  textContentVariants?: TextContentVariantData[]
}

const props = defineProps<{ canvasId?: string | null }>()

const editorStore = useEditorStore()
const uiStore = useEditorUiStore()
const workspaceStore = useWorkspaceStore()
const sessionStore = useEditorSessionStore()
const collaboration = useEditorCollaboration()
const toast = useToast()
const overlay = useOverlay()
const dictionaryBrowserSlideover = overlay.create(LazyDictionarySlideoverBrowser)
const {
  ensureTokenResults,
  getTokenResult,
  hasSuggestionsLoaded,
  invalidateToken: invalidateDictionaryToken,
  isTokenPending
} = useDictionaryTokenLookup()

const effectiveCanvasId = computed(() => props.canvasId ?? editorStore.activeCanvasId)
const editorDocument = computed(() => {
  const canvasId = effectiveCanvasId.value
  return canvasId ? getEditorSession(canvasId)?.document.value ?? null : null
})
const page = computed(() => editorDocument.value?.page ?? null)
const activeGtIndex = computed(() => editorStore.projectTextDefaultGtIndex ?? 0)
const recognitionIndices = computed(() => editorStore.projectTextDefaultRecognitionIndices ?? [1])
const fontSize = computed(() => Math.max(12, Number(uiStore.textViewFontSize) || 30))
const showComments = computed(() => sessionStore.textViewSettings.showComments)
const selectedWorkspaceId = computed(() => workspaceStore.selectedWorkspaceId as string | null)
const codecCharacters = computed(() => editorStore.projectCodecCharacters ?? [])
const codecCharacterSet = computed(() => new Set(codecCharacters.value))
const hasProjectCodec = computed(() =>
  Boolean(editorStore.projectCodecId) || codecCharacters.value.length > 0
)
const codecEnabled = computed(() =>
  Boolean(uiStore.highlightUnknownCodecChars && hasProjectCodec.value)
)
const includeWhitespaceInCodecHighlight = computed(() =>
  uiStore.includeWhitespaceInCodecHighlight
)
const canQuickAddToCodec = computed(() => Boolean(
  selectedWorkspaceId.value
  && editorStore.projectCodecId
  && sessionStore.activeProjectId
))
const projectDictionaryId = computed(() => editorStore.projectDictionaryId)
const dictionaryEnabled = computed(() => Boolean(
  uiStore.highlightUnknownDictionaryTokens
  && selectedWorkspaceId.value
  && projectDictionaryId.value
))
const canQuickAddToDictionary = computed(() =>
  Boolean(editorStore.projectDictionaryCanEdit) && !editorStore.projectDictionaryLocked
)
const isLoading = computed(() => {
  const canvasId = effectiveCanvasId.value
  return canvasId ? editorStore.canvases[canvasId]?.isLoadingAnnotations === true : false
})
const isEditable = computed(() => {
  const canvasId = effectiveCanvasId.value
  if (!canvasId) return false
  const controls = getEditorSession(canvasId)?.controls.value
  return controls?.isCanvasEditable?.value ?? collaboration.canEditCanvas(canvasId)
})

function collectTextlines(regions: Region[] | undefined): Map<string, TextLine> {
  const byId = new Map<string, TextLine>()
  const visit = (currentRegions: Region[] | undefined) => {
    for (const region of currentRegions ?? []) {
      if (region.kind === 'TextRegion') {
        for (const textline of region.textLines ?? []) {
          byId.set(textline.id, textline)
        }
      }
      visit(region.regions)
    }
  }
  visit(regions)
  return byId
}

const fullTextLines = computed<FullTextLine[]>(() => {
  const currentPage = editorDocument.value?.page
  if (!currentPage) return []

  const textlineById = collectTextlines(currentPage.regions)
  const directionById = computeTextLineReadingDirectionMap(currentPage)
  return collectTextlineIdsInReadingOrder(currentPage.regions, currentPage.readingOrder)
    .flatMap((id) => {
      const textline = textlineById.get(id)
      if (!textline) return []
      const resolved = resolveFullTextLineValue(
        textline.textContentVariants as TextContentVariantData[] | undefined,
        activeGtIndex.value,
        recognitionIndices.value
      )
      return [{
        id,
        text: resolved.text,
        source: resolved.source,
        sourceIndex: resolved.sourceIndex,
        comments: textline.comments,
        readingDirection: directionById[id],
        textContentVariants: textline.textContentVariants as TextContentVariantData[] | undefined
      }]
    })
})
const fullTextLineById = computed(() =>
  new Map(fullTextLines.value.map(line => [line.id, line]))
)
const orderedTextlineIds = computed(() => fullTextLines.value.map(line => line.id))
const orderedTextlineIdSet = computed(() => new Set(orderedTextlineIds.value))

const selectedTextlineId = computed(() => {
  const canvasId = effectiveCanvasId.value
  const runtime = getTextViewRuntimeControls(canvasId, editorStore)
  const selectedId = runtime?.selectedPolygonId?.value
    ?? (canvasId ? editorStore.canvases[canvasId]?.selectedRegionId : null)
    ?? null
  return selectedId && orderedTextlineIdSet.value.has(selectedId) ? selectedId : null
})

const drafts = ref<Record<string, string>>({})
const pendingCommit = ref<{ textlineId: string, text: string } | null>(null)
const editingTextlineId = ref<string | null>(null)
const commentEditorTextlineId = ref<string | null>(null)
const commentDraft = ref('')
const dictionaryWordPopover = ref<{
  textlineId: string
  segment: FullTextDictionarySegment
  left: number
  top: number
} | null>(null)
const textareaById = new Map<string, HTMLTextAreaElement>()

function setTextareaRef(textlineId: string, element: Element | ComponentPublicInstance | null): void {
  if (element instanceof HTMLTextAreaElement) {
    textareaById.set(textlineId, element)
    resizeTextarea(element)
    return
  }
  textareaById.delete(textlineId)
}

function resizeTextarea(textarea: HTMLTextAreaElement): void {
  textarea.style.height = '0px'
  textarea.style.height = `${Math.max(Math.ceil(fontSize.value * 1.5), textarea.scrollHeight)}px`
}

function resizeAllTextareas(): void {
  for (const textarea of textareaById.values()) {
    resizeTextarea(textarea)
  }
}

watch(fullTextLines, (lines) => {
  const nextDrafts: Record<string, string> = {}
  for (const line of lines) {
    const keepLocalDraft = editingTextlineId.value === line.id
      || pendingCommit.value?.textlineId === line.id
    nextDrafts[line.id] = resolveFullTextDraft(
      line.text,
      drafts.value[line.id],
      keepLocalDraft
    )
  }
  drafts.value = nextDrafts
  nextTick(resizeAllTextareas)
}, { immediate: true })

watch(fontSize, () => nextTick(resizeAllTextareas))

function getDisplayedText(textlineId: string): string {
  return drafts.value[textlineId]
    ?? fullTextLineById.value.get(textlineId)?.text
    ?? ''
}

function getTextlineComment(textlineId: string): string {
  return normalizeFullTextComment(fullTextLineById.value.get(textlineId)?.comments)
}

function hasTextlineComment(textlineId: string): boolean {
  return getTextlineComment(textlineId).length > 0
}

function handleCommentPopoverUpdate(textlineId: string, open: boolean): void {
  if (open) {
    flushPendingCommit()
    selectTextline(textlineId)
    commentDraft.value = fullTextLineById.value.get(textlineId)?.comments ?? ''
    commentEditorTextlineId.value = textlineId
    return
  }

  if (commentEditorTextlineId.value === textlineId) {
    commentEditorTextlineId.value = null
    commentDraft.value = ''
  }
}

function updateTextlineComment(textlineId: string, comment: string): void {
  if (!isEditable.value) return
  const canvasId = effectiveCanvasId.value
  if (!canvasId) return

  const nextComment = serializeFullTextComment(comment)
  if (fullTextLineById.value.get(textlineId)?.comments === nextComment) return

  const runtime = getTextViewRuntimeControls(canvasId, editorStore)
  if (!runtime?.commander) return

  runtime.commander.execute(
    new UpdateTextlineCommentCommand({ textlineId, comment: nextComment }),
    createTextViewCommandContext(canvasId)
  )
}

function saveCommentEdit(): void {
  const textlineId = commentEditorTextlineId.value
  if (!textlineId || !isEditable.value) return
  updateTextlineComment(textlineId, commentDraft.value)
  commentEditorTextlineId.value = null
  commentDraft.value = ''
}

function cancelCommentEdit(): void {
  commentEditorTextlineId.value = null
  commentDraft.value = ''
}

function handleCommentKeydown(event: KeyboardEvent): void {
  if (event.key !== 'Enter' || (!event.metaKey && !event.ctrlKey)) return
  event.preventDefault()
  saveCommentEdit()
}

type FullTextCodecCharacterMeta = {
  description: string | null
  source: 'unicode' | 'mufi' | null
  loading: boolean
}

const codecCharacterMeta = ref<Record<string, FullTextCodecCharacterMeta>>({})

const codecSegmentsByTextlineId = computed(() => {
  const segmentsByTextlineId = new Map<string, FullTextCodecSegment[]>()
  if (!codecEnabled.value) return segmentsByTextlineId

  for (const line of fullTextLines.value) {
    segmentsByTextlineId.set(
      line.id,
      getFullTextCodecSegments(
        drafts.value[line.id] ?? line.text,
        codecCharacterSet.value,
        includeWhitespaceInCodecHighlight.value
      )
    )
  }
  return segmentsByTextlineId
})

const unknownCodecCharactersByTextlineId = computed(() => {
  const unknownByTextlineId = new Map<string, string[]>()
  for (const [textlineId, segments] of codecSegmentsByTextlineId.value) {
    const characters = getUnknownCodecCharacters(segments)
    if (characters.length > 0) unknownByTextlineId.set(textlineId, characters)
  }
  return unknownByTextlineId
})

function getCodecSegments(textlineId: string): FullTextCodecSegment[] {
  return codecSegmentsByTextlineId.value.get(textlineId) ?? []
}

function getUnknownCodecCharactersForLine(textlineId: string): string[] {
  return unknownCodecCharactersByTextlineId.value.get(textlineId) ?? []
}

function getUnknownCodecCharacterCount(textlineId: string): number {
  return getCodecSegments(textlineId)
    .filter(segment => segment.unknown)
    .reduce((count, segment) => count + Array.from(segment.text).length, 0)
}

function toCodecCodepoint(character: string): string {
  const codepoint = character.codePointAt(0)
  if (codepoint === undefined) return 'N/A'
  const minWidth = codepoint > 0xFFFF ? 6 : 4
  return `U+${codepoint.toString(16).toUpperCase().padStart(minWidth, '0')}`
}

function getCodecCharacterLabel(character: string): string {
  if (character === ' ') return 'Space'
  if (character === '\t') return 'Tab'
  if (character === '\n') return 'Line feed'
  if (character === '\r') return 'Carriage return'
  return character
}

async function loadCodecCharacterMeta(characters: string[]): Promise<void> {
  const pending = characters.filter(character => !codecCharacterMeta.value[character])
  if (pending.length === 0) return

  await Promise.all(pending.map(async (character) => {
    codecCharacterMeta.value[character] = {
      description: null,
      source: null,
      loading: true
    }

    try {
      const response = await GlyphService.search(character, { mufi: true, unicode: true }, 0, 20)
      const exactMatch = response.data.find(item => item.utf8 === character) ?? null
      codecCharacterMeta.value[character] = {
        description: exactMatch?.description ?? null,
        source: exactMatch?.source ?? null,
        loading: false
      }
    } catch {
      codecCharacterMeta.value[character] = {
        description: null,
        source: null,
        loading: false
      }
    }
  }))
}

function handleCodecPopoverOpen(textlineId: string, open: boolean): void {
  if (!open) return
  const characters = getUnknownCodecCharactersForLine(textlineId)
  if (characters.length === 0) return
  void loadCodecCharacterMeta(characters)
}

async function handleQuickAddCodecCharacter(character: string): Promise<void> {
  const workspaceId = selectedWorkspaceId.value
  const codecId = editorStore.projectCodecId
  const projectId = sessionStore.activeProjectId
  if (!workspaceId || !codecId || !projectId) return

  try {
    const updated = await $fetch<{ id: string, codec?: string[] }>(
      `/api/workspaces/${workspaceId}/codecs/${codecId}/characters`,
      {
        method: 'POST',
        body: { character }
      }
    )
    editorStore.setProjectCodec(updated.id ?? codecId, updated.codec ?? [], projectId)
    await refreshNuxtData(wsKey(workspaceId, 'codecs', codecId))
    await refreshNuxtData(wsKey(workspaceId, 'codecs', 'list'))
    toast.add({
      title: 'Added to codec',
      description: `Character "${getCodecCharacterLabel(character)}" appended to the project codec.`,
      color: 'success'
    })
  } catch (error: unknown) {
    toast.add({
      title: 'Could not add to codec',
      description: getRequestErrorMessage(error),
      color: 'error'
    })
  }
}

function handleOpenCodecEditor(): void {
  const codecId = editorStore.projectCodecId
  if (!codecId) {
    toast.add({ title: 'No project codec configured', color: 'warning' })
    return
  }
  void navigateTo(`/codecs/${codecId}`)
}

const dictionarySegmentsByTextlineId = computed(() => {
  const segmentsByTextlineId = new Map<string, FullTextDictionarySegment[]>()
  const workspaceId = selectedWorkspaceId.value
  const dictionaryId = projectDictionaryId.value
  if (!dictionaryEnabled.value || !workspaceId || !dictionaryId) return segmentsByTextlineId

  for (const line of fullTextLines.value) {
    const text = drafts.value[line.id] ?? line.text
    segmentsByTextlineId.set(
      line.id,
      getFullTextDictionarySegments(
        text,
        token => getTokenResult(workspaceId, dictionaryId, token)
      )
    )
  }
  return segmentsByTextlineId
})

const unknownDictionarySegmentsByTextlineId = computed(() => {
  const unknownByTextlineId = new Map<string, FullTextDictionarySegment[]>()
  for (const [textlineId, segments] of dictionarySegmentsByTextlineId.value) {
    const unknown = segments.filter(segment => segment.unknown)
    if (unknown.length > 0) unknownByTextlineId.set(textlineId, unknown)
  }
  return unknownByTextlineId
})

function getDictionarySegments(textlineId: string): FullTextDictionarySegment[] {
  return dictionarySegmentsByTextlineId.value.get(textlineId) ?? []
}

function getUnknownDictionarySegments(textlineId: string): FullTextDictionarySegment[] {
  return unknownDictionarySegmentsByTextlineId.value.get(textlineId) ?? []
}

function getUnknownDictionaryTokenCount(textlineId: string): number {
  return getUnknownDictionarySegments(textlineId).length
}

function hasTextlineChecks(textlineId: string): boolean {
  return getUnknownCodecCharacterCount(textlineId) > 0
    || getUnknownDictionaryTokenCount(textlineId) > 0
}

function getTextlineCheckCount(textlineId: string): number {
  return getUnknownCodecCharacterCount(textlineId)
    + getUnknownDictionaryTokenCount(textlineId)
}

function handleLineDetailsPopoverOpen(textlineId: string, open: boolean): void {
  handleCommentPopoverUpdate(textlineId, open)
  if (!open) return
  dictionaryWordPopover.value = null
  handleCodecPopoverOpen(textlineId, true)
  void handleDictionaryPopoverOpen(textlineId, true)
}

function getDictionarySuggestions(token: string) {
  const workspaceId = selectedWorkspaceId.value
  const dictionaryId = projectDictionaryId.value
  if (!workspaceId || !dictionaryId) return []
  return getTokenResult(workspaceId, dictionaryId, token)?.suggestions ?? []
}

function isDictionarySuggestionLoading(token: string): boolean {
  const workspaceId = selectedWorkspaceId.value
  const dictionaryId = projectDictionaryId.value
  if (!workspaceId || !dictionaryId) return false

  const result = getTokenResult(workspaceId, dictionaryId, token)
  return isTokenPending(workspaceId, dictionaryId, token)
    || Boolean(result && !result.known && !hasSuggestionsLoaded(workspaceId, dictionaryId, token))
}

const dictionaryTokens = computed(() => {
  if (!dictionaryEnabled.value) return []
  const tokens = fullTextLines.value.flatMap(line =>
    tokenizeForDictionary(drafts.value[line.id] ?? line.text)
  )
  return [...new Set(tokens)]
})

const dictionaryActivationKey = computed(() => {
  const workspaceId = selectedWorkspaceId.value
  const dictionaryId = projectDictionaryId.value
  return dictionaryEnabled.value && workspaceId && dictionaryId
    ? `${workspaceId}:${dictionaryId}`
    : null
})

let dictionaryLoadGeneration = 0
let dictionaryLoadingToastId: ReturnType<typeof toast.add>['id'] | null = null
const dictionaryInitialLoadPending = ref(false)

function removeDictionaryLoadingToast(): void {
  if (dictionaryLoadingToastId === null) return
  toast.remove(dictionaryLoadingToastId)
  dictionaryLoadingToastId = null
}

watch(dictionaryActivationKey, (key) => {
  dictionaryLoadGeneration += 1
  removeDictionaryLoadingToast()
  dictionaryInitialLoadPending.value = Boolean(key)
}, { immediate: true })

const requestDictionaryResults = useDebounceFn(async () => {
  const workspaceId = selectedWorkspaceId.value
  const dictionaryId = projectDictionaryId.value
  const tokens = [...dictionaryTokens.value]
  if (!dictionaryEnabled.value || !workspaceId || !dictionaryId || tokens.length === 0) return

  const generation = dictionaryLoadGeneration
  const reportInitialLoad = dictionaryInitialLoadPending.value
  if (reportInitialLoad) dictionaryInitialLoadPending.value = false

  const missingTokens = getMissingFullTextDictionaryTokens(
    tokens,
    token => getTokenResult(workspaceId, dictionaryId, token)
  )
  const showLoadingToast = reportInitialLoad && missingTokens.length > 0

  if (showLoadingToast) {
    const description = missingTokens.length === tokens.length
      ? `Checking ${tokens.length} unique ${tokens.length === 1 ? 'token' : 'tokens'} in the full transcription.`
      : `Checking ${missingTokens.length} uncached of ${tokens.length} unique tokens.`
    const loadingToast = toast.add({
      title: 'Checking dictionary',
      description,
      color: 'neutral',
      icon: 'i-lucide-loader-circle',
      ui: { icon: 'animate-spin' },
      close: false,
      progress: false,
      duration: 0
    })
    dictionaryLoadingToastId = loadingToast.id
  }

  try {
    await ensureTokenResults({
      workspaceId,
      dictionaryId,
      tokens,
      includeSuggestions: false
    })
    if (showLoadingToast && generation === dictionaryLoadGeneration && dictionaryEnabled.value) {
      removeDictionaryLoadingToast()
      toast.add({
        title: 'Dictionary check ready',
        description: `${tokens.length} unique ${tokens.length === 1 ? 'token is' : 'tokens are'} ready.`,
        color: 'success',
        icon: 'i-lucide-circle-check'
      })
    }
  } catch (error: unknown) {
    if (showLoadingToast && generation === dictionaryLoadGeneration && dictionaryEnabled.value) {
      removeDictionaryLoadingToast()
      toast.add({
        title: 'Dictionary check failed',
        description: getRequestErrorMessage(error),
        color: 'error',
        icon: 'i-lucide-circle-alert'
      })
    }
    // Dictionary availability must never interrupt transcription editing.
  }
}, 180, { maxWait: 800 })

watch(
  [dictionaryEnabled, dictionaryTokens, selectedWorkspaceId, projectDictionaryId],
  () => {
    if (!import.meta.client || !dictionaryEnabled.value) return
    void requestDictionaryResults()
  },
  { immediate: true }
)

async function loadDictionarySuggestions(tokens: string[]): Promise<void> {
  const workspaceId = selectedWorkspaceId.value
  const dictionaryId = projectDictionaryId.value
  if (!workspaceId || !dictionaryId || tokens.length === 0) return

  try {
    await ensureTokenResults({
      workspaceId,
      dictionaryId,
      tokens,
      includeSuggestions: true,
      limit: 5
    })
  } catch {
    toast.add({
      title: 'Could not load dictionary suggestions',
      color: 'warning'
    })
  }
}

function handleDictionaryPopoverOpen(textlineId: string, open: boolean): void {
  if (!open) return
  const tokens = [...new Set(getUnknownDictionarySegments(textlineId).map(segment => segment.text))]
  void loadDictionarySuggestions(tokens)
}

function handleDictionaryWordPopoverUpdate(open: boolean): void {
  if (!open) dictionaryWordPopover.value = null
}

function handleTextareaClick(event: MouseEvent, textlineId: string): void {
  if (!event.metaKey && !event.ctrlKey) {
    dictionaryWordPopover.value = null
    return
  }
  if (!dictionaryEnabled.value) return

  const textarea = event.currentTarget
  if (!(textarea instanceof HTMLTextAreaElement)) return

  const segment = getUnknownFullTextDictionarySegmentAtOffset(
    getDictionarySegments(textlineId),
    textarea.selectionStart
  )
  if (!segment) {
    dictionaryWordPopover.value = null
    return
  }

  event.preventDefault()
  const host = textarea.parentElement
  if (!host) return
  const bounds = host.getBoundingClientRect()
  dictionaryWordPopover.value = {
    textlineId,
    segment,
    left: Math.max(4, Math.min(bounds.width - 4, event.clientX - bounds.left)),
    top: Math.max(4, Math.min(bounds.height - 4, event.clientY - bounds.top))
  }
  void loadDictionarySuggestions([segment.text])
}

function applyDictionaryWordSuggestion(replacement: string): void {
  const active = dictionaryWordPopover.value
  if (!active) return
  dictionaryWordPopover.value = null
  applyDictionarySuggestion(active.textlineId, active.segment, replacement)
}

async function quickAddDictionaryWord(): Promise<void> {
  const active = dictionaryWordPopover.value
  if (!active) return
  await handleQuickAddDictionaryToken(active.segment.text)
  dictionaryWordPopover.value = null
}

function openDictionaryEditorFromWord(): void {
  dictionaryWordPopover.value = null
  handleOpenDictionaryEditor()
}

function applyDictionarySuggestion(
  textlineId: string,
  segment: FullTextDictionarySegment,
  replacement: string
): void {
  if (!isEditable.value) return
  const currentText = getDisplayedText(textlineId)
  if (currentText.slice(segment.start, segment.end) !== segment.text) return

  const nextText = `${currentText.slice(0, segment.start)}${replacement}${currentText.slice(segment.end)}`
  drafts.value = {
    ...drafts.value,
    [textlineId]: nextText
  }
  pendingCommit.value = { textlineId, text: nextText }

  const textarea = textareaById.get(textlineId)
  if (textarea) {
    textarea.value = nextText
    resizeTextarea(textarea)
  }
  flushPendingCommit()

  nextTick(() => {
    const target = textareaById.get(textlineId)
    if (!target) return
    target.focus()
    const caret = segment.start + replacement.length
    target.setSelectionRange(caret, caret)
  })
  void requestDictionaryResults()
}

async function handleQuickAddDictionaryToken(token: string): Promise<void> {
  const workspaceId = selectedWorkspaceId.value
  const dictionaryId = projectDictionaryId.value
  if (!workspaceId || !dictionaryId || !canQuickAddToDictionary.value) return

  try {
    await $fetch(`/api/workspaces/${workspaceId}/dictionaries/${dictionaryId}/entries`, {
      method: 'POST',
      body: { form: token, fromEditor: true }
    })
    invalidateDictionaryToken(workspaceId, dictionaryId, token)
    await ensureTokenResults({
      workspaceId,
      dictionaryId,
      tokens: [token],
      includeSuggestions: false
    })
    await refreshNuxtData(wsKey(workspaceId, 'dictionaries', dictionaryId))
    await refreshNuxtData(wsKey(workspaceId, 'dictionaries', 'list'))
    toast.add({
      title: 'Added to dictionary',
      description: `Token "${token}" appended to the project dictionary.`,
      color: 'success'
    })
  } catch (error: unknown) {
    toast.add({
      title: 'Could not add to dictionary',
      description: getRequestErrorMessage(error),
      color: 'error'
    })
  }
}

function handleOpenDictionaryEditor(): void {
  const workspaceId = selectedWorkspaceId.value
  const dictionaryId = projectDictionaryId.value
  if (!workspaceId || !dictionaryId) {
    toast.add({ title: 'No project dictionary configured', color: 'warning' })
    return
  }
  dictionaryBrowserSlideover.open({
    workspaceId,
    dictionaryId
  })
}

function commitTextline(textlineId: string, nextText: string): void {
  if (!isEditable.value) return
  const canvasId = effectiveCanvasId.value
  const currentPage = page.value
  if (!canvasId || !currentPage) return

  const runtime = getTextViewRuntimeControls(canvasId, editorStore)
  if (!runtime?.commander) return
  const hit = findTextLineRecursive(currentPage.regions, textlineId)
  if (!hit) return

  const normalizedText = normalizeSingleLineText(nextText)
  const updated = setGtVariantUnicode(
    hit.textLine.textContentVariants as TextContentVariantData[] | undefined,
    activeGtIndex.value,
    normalizedText
  )
  if (!updated.changed && !updated.created) return

  runtime.commander.execute(
    createTextlineVariantsUpdateCommand({
      pageRegions: currentPage.regions,
      textlineId,
      nextTextContentVariants: updated.variants,
      gtIndex: activeGtIndex.value
    }),
    createTextViewCommandContext(canvasId)
  )
}

function flushPendingCommit(): void {
  const pending = pendingCommit.value
  if (!pending) return
  pendingCommit.value = null
  commitTextline(pending.textlineId, pending.text)
}

function selectTextline(textlineId: string): void {
  if (pendingCommit.value?.textlineId !== textlineId) {
    flushPendingCommit()
  }
  const canvasId = effectiveCanvasId.value
  if (canvasId) {
    editorStore.setActiveCanvas(canvasId)
  }
  const runtime = getTextViewRuntimeControls(effectiveCanvasId.value, editorStore)
  runtime?.selectPolylineById?.(null, { focusMode: 'none' })
  runtime?.selectPolygonById?.(textlineId, { focusMode: 'fit-width' })
}

function handleRowClick(textlineId: string): void {
  selectTextline(textlineId)
  focusTextline(textlineId)
}

function handleTextareaFocus(textlineId: string): void {
  editingTextlineId.value = textlineId
  selectTextline(textlineId)
}

function handleTextareaBlur(textlineId: string): void {
  flushPendingCommit()
  if (editingTextlineId.value === textlineId) {
    editingTextlineId.value = null
  }
}

function focusTextline(textlineId: string): void {
  nextTick(() => {
    const textarea = textareaById.get(textlineId)
    if (!textarea) return
    textarea.focus()
    const end = textarea.value.length
    textarea.setSelectionRange(end, end)
  })
}

function navigateTextlines(currentId: string, direction: 1 | -1): void {
  flushPendingCommit()
  const nextId = getAdjacentTextlineId(orderedTextlineIds.value, currentId, direction)
  if (!nextId) return
  selectTextline(nextId)
  focusTextline(nextId)
}

function handleTextareaKeydown(event: KeyboardEvent, textlineId: string): void {
  if (event.key === 'Tab') {
    event.preventDefault()
    navigateTextlines(textlineId, event.shiftKey ? -1 : 1)
    return
  }
  handleSingleLineTextareaKeydownEnter(event, isEditable.value)
}

function handleTextareaInput(event: Event, textlineId: string): void {
  if (!isEditable.value) return
  const textarea = event.target
  if (!(textarea instanceof HTMLTextAreaElement)) return

  dictionaryWordPopover.value = null
  const normalizedText = normalizeSingleLineText(textarea.value)
  if (textarea.value !== normalizedText) {
    textarea.value = normalizedText
  }
  drafts.value = {
    ...drafts.value,
    [textlineId]: normalizedText
  }
  pendingCommit.value = { textlineId, text: normalizedText }
  resizeTextarea(textarea)
}

watch(selectedTextlineId, (nextId, previousId) => {
  if (previousId && previousId !== nextId) {
    flushPendingCommit()
  }
  if (dictionaryWordPopover.value?.textlineId !== nextId) {
    dictionaryWordPopover.value = null
  }
  if (!nextId) return
  nextTick(() => {
    textareaById.get(nextId)?.scrollIntoView({
      block: 'center',
      inline: 'nearest',
      behavior: 'smooth'
    })
  })
}, { immediate: true })

watch(dictionaryEnabled, (enabled) => {
  if (!enabled) dictionaryWordPopover.value = null
})

onBeforeUnmount(() => {
  dictionaryLoadGeneration += 1
  removeDictionaryLoadingToast()
  flushPendingCommit()
  textareaById.clear()
})
</script>

<template>
  <TooltipProvider :delay-duration="200">
    <section
      class="flex h-full min-h-0 w-full flex-col bg-default dark:bg-[color-mix(in_srgb,var(--ui-bg)_72%,black)]"
      aria-label="Full transcription"
    >
      <div
        v-if="isLoading && fullTextLines.length === 0"
        class="flex flex-1 flex-col gap-3 overflow-hidden p-4"
      >
        <USkeleton v-for="line in 8" :key="line" class="h-9 w-full" />
      </div>

      <div
        v-else-if="fullTextLines.length === 0"
        class="flex flex-1 flex-col items-center justify-center gap-2 p-8 text-center text-muted"
      >
        <Icon name="i-lucide-text-cursor-input" class="size-8 opacity-50" />
        <p class="text-sm font-medium text-default">
          No text lines on this page
        </p>
        <p class="text-xs">
          Create text-line annotations to start the transcription.
        </p>
      </div>

      <div v-else class="min-h-0 flex-1 overflow-y-auto p-2">
        <div
          v-for="line in fullTextLines"
          :key="line.id"
          :data-textline-id="line.id"
          class="group relative flex min-w-0 cursor-text items-start rounded-md px-2 py-0.5 transition-colors"
          :class="selectedTextlineId === line.id
            ? 'bg-primary/10 ring-1 ring-inset ring-primary/20'
            : 'hover:bg-muted/45'"
          @click="handleRowClick(line.id)"
        >
          <div class="flex w-5 shrink-0 self-stretch items-center justify-center">
            <UPopover
              :open="commentEditorTextlineId === line.id"
              :content="{ side: 'left', align: 'start', sideOffset: 8 }"
              @update:open="(open: boolean) => handleLineDetailsPopoverOpen(line.id, open)"
            >
              <button
                type="button"
                class="flex size-5 items-center justify-center rounded-sm text-muted transition-[color,background-color,opacity] hover:bg-muted hover:text-default focus-visible:opacity-100 focus-visible:outline-2 focus-visible:outline-offset-1 focus-visible:outline-primary data-[state=open]:opacity-100"
                :class="hasTextlineComment(line.id)
                  ? 'opacity-70 group-hover:opacity-100'
                  : 'opacity-0 group-hover:opacity-100'"
                :aria-label="`Open line details for ${line.id}`"
                @click.stop
              >
                <Icon
                  :name="hasTextlineComment(line.id) ? 'i-lucide-message-square' : 'i-lucide-ellipsis'"
                  class="size-3.5"
                />
              </button>

              <template #content>
                <div class="max-h-[min(75vh,36rem)] w-80 max-w-[calc(100vw-2rem)] space-y-4 overflow-y-auto p-3" @click.stop>
                  <div class="flex items-center justify-between gap-2">
                    <div>
                      <p class="text-sm font-medium text-highlighted">
                        Line details
                      </p>
                      <p class="text-xs text-muted">
                        Comment and transcription checks
                      </p>
                    </div>
                    <UBadge color="neutral" variant="soft" size="xs">
                      {{ line.id }}
                    </UBadge>
                  </div>

                  <div
                    v-if="line.source === 'prediction'"
                    class="flex items-start gap-2 rounded-md bg-muted/40 p-2 text-xs text-muted"
                  >
                    <Icon name="i-lucide-wand-sparkles" class="mt-0.5 size-3.5 shrink-0" />
                    <span>
                      {{ line.sourceIndex === undefined ? 'Unindexed prediction' : `Prediction #${line.sourceIndex}` }};
                      the first text edit creates GT.
                    </span>
                  </div>

                  <section v-if="hasTextlineComment(line.id) || isEditable" class="space-y-2">
                    <p class="flex items-center gap-1.5 text-xs font-medium text-highlighted">
                      <Icon name="i-lucide-message-square" class="size-3.5 text-muted" />
                      Comment
                    </p>
                    <UTextarea
                      v-model="commentDraft"
                      :rows="2"
                      autoresize
                      placeholder="Enter comment..."
                      :disabled="!isEditable"
                      aria-label="Text-line comment"
                      @keydown="handleCommentKeydown"
                    />
                    <div class="flex items-center justify-end gap-2">
                      <UButton
                        type="button"
                        color="neutral"
                        variant="ghost"
                        size="xs"
                        @click.stop="cancelCommentEdit"
                      >
                        {{ isEditable ? 'Cancel' : 'Close' }}
                      </UButton>
                      <UButton
                        v-if="isEditable"
                        type="button"
                        color="primary"
                        size="xs"
                        icon="i-lucide-save"
                        @click.stop="saveCommentEdit"
                      >
                        Save
                      </UButton>
                    </div>
                  </section>

                  <USeparator v-if="hasTextlineChecks(line.id)" />

                  <section v-if="hasTextlineChecks(line.id)" class="space-y-4">
                    <div class="flex items-center justify-between gap-2">
                      <p class="flex items-center gap-1.5 text-xs font-medium text-highlighted">
                        <Icon name="i-lucide-scan-text" class="size-3.5 text-muted" />
                        Text checks
                      </p>
                      <span class="text-xs text-muted">
                        {{ getTextlineCheckCount(line.id) }}
                        {{ getTextlineCheckCount(line.id) === 1 ? 'issue' : 'issues' }}
                      </span>
                    </div>

                    <section v-if="codecEnabled && getUnknownCodecCharacterCount(line.id) > 0" class="space-y-2">
                      <div class="flex items-center justify-between gap-2">
                        <p class="flex items-center gap-1.5 text-xs text-muted">
                          <Icon name="i-lucide-badge-alert" class="size-3.5" />
                          Codec
                        </p>
                        <span class="text-xs text-muted">
                          {{ getUnknownCodecCharacterCount(line.id) }} unknown
                        </span>
                      </div>

                      <div
                        v-for="character in getUnknownCodecCharactersForLine(line.id)"
                        :key="`${line.id}:codec:${toCodecCodepoint(character)}`"
                        class="space-y-2 rounded-md border border-default bg-muted/20 p-2"
                      >
                        <div class="flex min-w-0 items-center gap-2">
                          <span class="font-junicode text-lg leading-none text-highlighted">
                            {{ getCodecCharacterLabel(character) }}
                          </span>
                          <span class="font-mono text-xs text-muted">
                            {{ toCodecCodepoint(character) }}
                          </span>
                          <USkeleton
                            v-if="codecCharacterMeta[character]?.loading"
                            class="h-4 min-w-16 flex-1"
                          />
                          <span
                            v-else-if="codecCharacterMeta[character]?.description"
                            class="min-w-0 flex-1 truncate text-xs text-muted"
                          >
                            {{ codecCharacterMeta[character]?.description }}
                          </span>
                          <UBadge
                            v-if="codecCharacterMeta[character]?.source"
                            color="neutral"
                            variant="soft"
                            size="xs"
                            class="uppercase"
                          >
                            {{ codecCharacterMeta[character]?.source }}
                          </UBadge>
                        </div>

                        <div class="flex flex-wrap gap-1">
                          <UButton
                            type="button"
                            size="xs"
                            color="neutral"
                            variant="soft"
                            icon="i-lucide-badge-plus"
                            :disabled="!canQuickAddToCodec"
                            @click.stop="handleQuickAddCodecCharacter(character)"
                          >
                            Add to Codec
                          </UButton>
                          <UButton
                            type="button"
                            size="xs"
                            color="neutral"
                            variant="ghost"
                            icon="i-lucide-external-link"
                            :disabled="!editorStore.projectCodecId"
                            @click.stop="handleOpenCodecEditor"
                          >
                            Open Codec
                          </UButton>
                        </div>
                      </div>
                    </section>

                    <USeparator
                      v-if="getUnknownCodecCharacterCount(line.id) > 0 && getUnknownDictionaryTokenCount(line.id) > 0"
                    />

                    <section v-if="dictionaryEnabled && getUnknownDictionaryTokenCount(line.id) > 0" class="space-y-2">
                      <div class="flex items-center justify-between gap-2">
                        <p class="flex items-center gap-1.5 text-xs text-muted">
                          <Icon name="i-lucide-book-open-check" class="size-3.5" />
                          Dictionary
                        </p>
                        <span class="text-xs text-muted">
                          {{ getUnknownDictionaryTokenCount(line.id) }} unknown
                        </span>
                      </div>

                      <div
                        v-for="segment in getUnknownDictionarySegments(line.id)"
                        :key="`${line.id}:${segment.start}:${segment.end}`"
                        class="space-y-2 rounded-md border border-default bg-muted/20 p-2"
                      >
                        <p class="font-junicode text-sm font-medium text-highlighted">
                          {{ segment.text }}
                        </p>

                        <div v-if="isDictionarySuggestionLoading(segment.text)" class="flex flex-wrap gap-1">
                          <USkeleton class="h-7 w-20" />
                          <USkeleton class="h-7 w-24" />
                        </div>
                        <div v-else-if="getDictionarySuggestions(segment.text).length > 0" class="flex flex-wrap gap-1">
                          <UButton
                            v-for="suggestion in getDictionarySuggestions(segment.text)"
                            :key="`${segment.start}:${suggestion.normalized}`"
                            size="xs"
                            color="neutral"
                            variant="soft"
                            :disabled="!isEditable"
                            @click.stop="applyDictionarySuggestion(line.id, segment, suggestion.display)"
                          >
                            {{ suggestion.display }}
                          </UButton>
                        </div>
                        <p v-else class="text-xs text-muted">
                          No suggestions available.
                        </p>

                        <UButton
                          v-if="!editorStore.projectDictionaryLocked"
                          type="button"
                          size="xs"
                          color="neutral"
                          variant="ghost"
                          icon="i-lucide-book-plus"
                          :disabled="!canQuickAddToDictionary"
                          @click.stop="handleQuickAddDictionaryToken(segment.text)"
                        >
                          Add to Dictionary
                        </UButton>
                      </div>

                      <div class="flex justify-end">
                        <UButton
                          type="button"
                          size="xs"
                          color="neutral"
                          variant="ghost"
                          icon="i-lucide-external-link"
                          @click.stop="handleOpenDictionaryEditor"
                        >
                          Open Dictionary
                        </UButton>
                      </div>
                    </section>
                  </section>
                </div>
              </template>
            </UPopover>
          </div>

          <div class="min-w-0 flex-1">
            <div
              v-if="showComments && hasTextlineComment(line.id)"
              class="mb-0.5 flex items-start gap-1.5 rounded-sm bg-info/10 px-2 py-1 text-xs text-info"
            >
              <Icon name="i-lucide-message-square-quote" class="mt-0.5 size-3.5 shrink-0" />
              <p class="min-w-0 whitespace-pre-wrap break-words">
                {{ getTextlineComment(line.id) }}
              </p>
            </div>

            <div class="relative min-w-0">
              <div
                v-if="codecEnabled"
                aria-hidden="true"
                class="pointer-events-none absolute inset-0 z-0 min-h-10 overflow-hidden whitespace-pre-wrap break-words px-1 py-1 font-junicode text-transparent"
                :dir="getReadingDirectionTextAttributes(line.readingDirection).dir"
                :style="{
                  ...getReadingDirectionTextAttributes(line.readingDirection).style,
                  fontSize: `${fontSize}px`,
                  lineHeight: '1.4'
                }"
              >
                <template
                  v-for="segment in getCodecSegments(line.id)"
                  :key="`${line.id}:codec-overlay:${segment.start}:${segment.end}`"
                >
                  <span
                    :class="segment.unknown
                      ? 'rounded-sm bg-warning/25 box-decoration-clone'
                      : undefined"
                  >{{ segment.text }}</span>
                </template>
              </div>

              <div
                v-if="dictionaryEnabled"
                aria-hidden="true"
                class="pointer-events-none absolute inset-0 z-[1] min-h-10 overflow-hidden whitespace-pre-wrap break-words px-1 py-1 font-junicode text-transparent"
                :dir="getReadingDirectionTextAttributes(line.readingDirection).dir"
                :style="{
                  ...getReadingDirectionTextAttributes(line.readingDirection).style,
                  fontSize: `${fontSize}px`,
                  lineHeight: '1.4'
                }"
              >
                <template
                  v-for="segment in getDictionarySegments(line.id)"
                  :key="`${line.id}:overlay:${segment.start}:${segment.end}`"
                >
                  <span
                    :class="segment.unknown
                      ? 'underline decoration-warning decoration-2 underline-offset-2'
                      : undefined"
                  >{{ segment.text }}</span>
                </template>
              </div>

              <UPopover
                v-if="dictionaryWordPopover?.textlineId === line.id"
                :open="true"
                :content="{ side: 'top', align: 'start', sideOffset: 8 }"
                @update:open="handleDictionaryWordPopoverUpdate"
              >
                <button
                  type="button"
                  tabindex="-1"
                  class="pointer-events-none absolute z-20 size-px opacity-0"
                  :aria-label="`Dictionary suggestions for ${dictionaryWordPopover.segment.text}`"
                  :style="{
                    left: `${dictionaryWordPopover.left}px`,
                    top: `${dictionaryWordPopover.top}px`
                  }"
                />

                <template #content>
                  <div class="w-64 max-w-[calc(100vw-2rem)] space-y-2 p-2">
                    <div class="text-xs font-medium text-muted">
                      Dictionary suggestions
                    </div>
                    <p class="text-xs">
                      <span class="font-medium text-highlighted">{{ dictionaryWordPopover.segment.text }}</span>
                      <span class="text-muted"> is not in the dictionary.</span>
                    </p>

                    <div v-if="isDictionarySuggestionLoading(dictionaryWordPopover.segment.text)" class="flex flex-wrap gap-1">
                      <USkeleton class="h-7 w-20" />
                      <USkeleton class="h-7 w-24" />
                    </div>
                    <div v-else-if="getDictionarySuggestions(dictionaryWordPopover.segment.text).length > 0" class="flex flex-wrap gap-1">
                      <UButton
                        v-for="suggestion in getDictionarySuggestions(dictionaryWordPopover.segment.text)"
                        :key="suggestion.normalized"
                        size="xs"
                        color="neutral"
                        variant="soft"
                        :disabled="!isEditable"
                        @click.stop="applyDictionaryWordSuggestion(suggestion.display)"
                      >
                        {{ suggestion.display }}
                      </UButton>
                    </div>
                    <p v-else class="text-xs text-muted">
                      No suggestions available.
                    </p>

                    <div class="flex flex-wrap gap-1">
                      <UButton
                        v-if="!editorStore.projectDictionaryLocked"
                        type="button"
                        size="xs"
                        color="neutral"
                        variant="soft"
                        icon="i-lucide-book-plus"
                        :disabled="!canQuickAddToDictionary"
                        @click.stop="quickAddDictionaryWord"
                      >
                        Add to Dictionary
                      </UButton>
                      <UButton
                        type="button"
                        size="xs"
                        color="neutral"
                        variant="ghost"
                        icon="i-lucide-external-link"
                        @click.stop="openDictionaryEditorFromWord"
                      >
                        Open Dictionary
                      </UButton>
                    </div>
                  </div>
                </template>
              </UPopover>

              <textarea
                :ref="(element) => setTextareaRef(line.id, element)"
                :value="drafts[line.id] ?? line.text"
                :readonly="!isEditable"
                :aria-label="`Transcription for text line ${line.id}`"
                :dir="getReadingDirectionTextAttributes(line.readingDirection).dir"
                :style="{
                  ...getReadingDirectionTextAttributes(line.readingDirection).style,
                  fontSize: `${fontSize}px`,
                  lineHeight: '1.4'
                }"
                rows="1"
                wrap="soft"
                spellcheck="false"
                class="relative z-10 min-h-10 w-full min-w-0 resize-none overflow-hidden border-0 bg-transparent px-1 py-1 font-junicode text-highlighted outline-none placeholder:text-muted/50"
                placeholder="…"
                @click.stop="handleTextareaClick($event, line.id)"
                @focus="handleTextareaFocus(line.id)"
                @input="handleTextareaInput($event, line.id)"
                @blur="handleTextareaBlur(line.id)"
                @keydown="handleTextareaKeydown($event, line.id)"
                @beforeinput="handleSingleLineTextareaBeforeInput($event as InputEvent, isEditable)"
                @paste="handleSingleLineTextareaPaste($event, isEditable)"
                @drop="handleSingleLineTextareaDrop($event, isEditable)"
              />
            </div>
          </div>
        </div>
      </div>
    </section>
  </TooltipProvider>
</template>
