<script setup lang="ts">
import { LazyUiConfirmModal } from '#components'
import type { Point } from '@/models/editor/types'
import type { TextItemLayout } from '@/stores/editor/types'
import DiffMatchPatch from 'diff-match-patch'
import type { Diff } from 'diff-match-patch'
import { GlyphService } from '@/utils/glyph-service'
import type { DictionarySuggestion } from '@/types/dictionary'
import { TooltipProvider } from 'reka-ui'
import {
  getHighlightedSegments,
  getTextHighlightShellClass,
  hasTextHighlight,
  tokenizeForDictionary
} from '../shared/text-highlighting'
import {
  handleSingleLineTextareaBeforeInput,
  handleSingleLineTextareaDrop,
  handleSingleLineTextareaKeydownEnter,
  handleSingleLineTextareaPaste
} from '../shared/text-input-guards'
import {
  getReadingDirectionTextAttributes,
  normalizeReadingDirection,
  type ReadingDirection
} from './reading-direction'
import { getTextContentVariantRenderKey } from './variant-render-key'

interface TextContentVariantData {
  pos: number
  index?: number
  text: string
  confidence?: number
  diffs?: Diff[]
}

interface Props {
  textline: {
    id: string
    label?: string
    comments?: string
    points: Point[]
    readingDirection?: ReadingDirection
    textContentVariants: TextContentVariantData[]
    allTextContentVariants?: TextContentVariantData[]
    hasGtVariant?: boolean
    recognitionCandidates?: Array<{ index?: number, text: string }>
  }
  imageUrl: string
  padding: number
  fontSize?: number
  layout?: TextItemLayout
  codecCharacters?: string[]
  highlightUnknownCodecChars?: boolean
  includeWhitespaceInCodecHighlight?: boolean
  highlightUnknownDictionaryTokens?: boolean
  gtIndex?: number | undefined
  recognitionIndices?: number[]
  showDiff?: boolean
  showComments?: boolean
  isSelected?: boolean
  textHighlightQuery?: string | null
  projectCodecId?: string | null
  projectDictionaryId?: string | null
  canQuickAddToDictionary?: boolean
  projectDictionaryLocked?: boolean
  projectDictionaryCaseSensitive?: boolean
  projectDictionaryUnicodeNormalization?: string
  selectedKeyboardId?: string | number | null
  hasVirtualKeyboard?: boolean
  allowMultiline?: boolean
  showDragHandle?: boolean
  showDeleteButton?: boolean
  showReorderButtons?: boolean
  canMoveUp?: boolean
  canMoveDown?: boolean
  cutoutMaxHeightClass?: string | null
  readOnly?: boolean
}

const props = withDefaults(defineProps<Props>(), {
  fontSize: 18,
  layout: 'side-by-side',
  codecCharacters: () => [],
  highlightUnknownCodecChars: false,
  includeWhitespaceInCodecHighlight: false,
  highlightUnknownDictionaryTokens: false,
  textHighlightQuery: '',
  recognitionIndices: () => [],
  projectCodecId: null,
  projectDictionaryId: null,
  canQuickAddToDictionary: false,
  projectDictionaryLocked: false,
  projectDictionaryCaseSensitive: false,
  projectDictionaryUnicodeNormalization: 'NFC',
  selectedKeyboardId: null,
  hasVirtualKeyboard: false,
  allowMultiline: false,
  showDragHandle: true,
  showDeleteButton: true,
  showReorderButtons: false,
  canMoveUp: false,
  canMoveDown: false,
  cutoutMaxHeightClass: null,
  readOnly: false,
  showComments: false
})
const emit = defineEmits<{
  updateTextContentVariant: [id: string, arrayPos: number, text: string]
  updateTextContentVariantIndex: [id: string, arrayPos: number, index: number | undefined]
  updateElementComment: [id: string, comment: string]
  addTextContentVariant: [id: string]
  removeTextContentVariant: [id: string, arrayPos: number]
  selectTextline: [id: string]
  moveUpTextline: [id: string]
  moveDownTextline: [id: string]
  deleteTextline: [id: string]
  createGtFromRecognition: [id: string, payload: { gtIndex: number, sourceRecognitionIndex?: number }]
  quickAddCodecChar: [char: string]
  quickAddDictionaryToken: [token: string]
  quickAddKeyboardChar: [char: string]
  openCodecEditor: []
  openDictionaryEditor: []
  openKeyboardEditor: []
}>()

const overlay = useOverlay()
const confirmModal = overlay.create(LazyUiConfirmModal)
const workspaceStore = useWorkspaceStore()
const {
  ensureTokenResults,
  hasSuggestionsLoaded,
  getTokenResult,
  isTokenPending
} = useDictionaryTokenLookup()

const canvasRef = ref<HTMLCanvasElement | null>(null)
const localTextContentVariants = ref<TextContentVariantData[]>([...props.textline.textContentVariants])

let dmpInstance: DiffMatchPatch | null = null
function getDmp(): DiffMatchPatch {
  if (!dmpInstance) {
    dmpInstance = new DiffMatchPatch()
  }
  return dmpInstance
}

const isVisible = ref(false)
const rootRef = ref<HTMLElement | null>(null)
const cutoutContainerRef = ref<HTMLElement | null>(null)
const cutoutRenderWidth = ref<number | null>(null)
const cutoutRenderHeight = ref<number | null>(null)
const isCutoutLoading = ref(false)
const cutoutLoadFailed = ref(false)
let cutoutRequestId = 0
const maxCutoutScale = 4

const isVertical = computed(() => props.layout === 'vertical')
const canMutateAnnotation = computed(() => !props.readOnly)
const baseTextFontSize = computed(() => Math.max(10, Number(props.fontSize ?? 18)))
function scaleText(multiplier: number, min: number): number {
  return Math.max(min, Math.round(baseTextFontSize.value * multiplier))
}
const textViewFontVars = computed(() => ({
  '--text-font-size': `${baseTextFontSize.value}px`,
  '--text-font-size-sm': `${scaleText(0.8, 11)}px`,
  '--text-font-size-xs': `${scaleText(0.68, 10)}px`,
  '--text-font-size-2xs': `${scaleText(0.58, 9)}px`,
  '--text-font-size-char': `${scaleText(0.9, 13)}px`
}))
// Keep cutout start aligned with the transcription textarea start rail in vertical/isolated layout.
// 5.5rem = index button (1.25rem) + two gaps (0.375rem each) + confidence column (3.5rem).
const cutoutStartOffsetRem = 5.5
const cutoutWrapperStyle = computed(() => {
  if (!isVertical.value) return undefined
  return {
    paddingInlineStart: `${cutoutStartOffsetRem}rem`
  }
})
const effectiveCutoutMaxHeightClass = computed(() => {
  if (props.cutoutMaxHeightClass) return props.cutoutMaxHeightClass
  return isVertical.value ? 'max-h-56' : 'max-h-40'
})

function remToPx(rem: number): number {
  if (typeof window === 'undefined') return rem * 16
  const rootFontSize = Number.parseFloat(window.getComputedStyle(document.documentElement).fontSize)
  return rem * (Number.isFinite(rootFontSize) ? rootFontSize : 16)
}

function resolveCutoutMaxHeightPx(className: string): number | null {
  if (!className) return null
  if (className === 'max-h-56') return remToPx(14)
  if (className === 'max-h-40') return remToPx(10)

  const arbitraryMatch = className.match(/max-h-\[([^\]]+)\]/)
  if (!arbitraryMatch) return null
  const rawValue = arbitraryMatch[1]?.trim()
  if (!rawValue) return null

  if (rawValue.endsWith('px')) {
    const parsed = Number.parseFloat(rawValue)
    return Number.isFinite(parsed) ? parsed : null
  }
  if (rawValue.endsWith('rem')) {
    const parsed = Number.parseFloat(rawValue)
    return Number.isFinite(parsed) ? remToPx(parsed) : null
  }
  if (rawValue.endsWith('vh')) {
    if (typeof window === 'undefined') return null
    const parsed = Number.parseFloat(rawValue)
    return Number.isFinite(parsed) ? (window.innerHeight * parsed) / 100 : null
  }
  return null
}

const cutoutMaxHeightPx = computed(() => resolveCutoutMaxHeightPx(effectiveCutoutMaxHeightClass.value))
const codecCharacterSet = computed(() => new Set(props.codecCharacters ?? []))
const codecPreviewStyle = computed(() => {
  const nextFontSize = baseTextFontSize.value
  return {
    fontSize: `${nextFontSize}px`,
    lineHeight: `${Math.max(16, Math.round(nextFontSize * 1.4))}px`
  }
})
const textReadingDirection = computed(() => normalizeReadingDirection(props.textline.readingDirection))
const textDirectionAttributes = computed(() => getReadingDirectionTextAttributes(textReadingDirection.value))
const textDirectionStyle = computed(() => textDirectionAttributes.value.style)
const textDirectionDir = computed(() => textDirectionAttributes.value.dir)
const normalizedTextHighlightQuery = computed(() => props.textHighlightQuery?.trim() ?? '')
const normalizedComment = computed(() => (props.textline.comments ?? '').trim())
const hasElementComment = computed(() => normalizedComment.value.length > 0)
const editableTextContentVariants = computed(() => props.textline.allTextContentVariants ?? props.textline.textContentVariants)
const commentEditorOpen = ref(false)
const commentDraft = ref(props.textline.comments ?? '')

watch(() => props.textline.comments, (nextComment) => {
  if (!commentEditorOpen.value) {
    commentDraft.value = nextComment ?? ''
  }
}, { immediate: true })

function variantRole(index: number | undefined): 'gt' | 'recognition' | 'nonAssigned' {
  if (typeof index === 'number' && index === props.gtIndex) return 'gt'
  if (index === undefined && (props.recognitionIndices ?? []).includes(-1)) return 'recognition'
  if (typeof index === 'number' && (props.recognitionIndices ?? []).includes(index)) return 'recognition'
  return 'nonAssigned'
}

function isGtVariant(index: number | undefined): boolean {
  return variantRole(index) === 'gt'
}

function isEditableVariant(index: number | undefined): boolean {
  return isGtVariant(index)
}

const recognitionCandidates = computed(() => {
  const candidates = (props.textline.recognitionCandidates ?? [])
    .filter(c => c.text.trim().length > 0)
  return candidates
})

const hasGtVariant = computed(() => {
  if (typeof props.textline.hasGtVariant === 'boolean') return props.textline.hasGtVariant
  return props.textline.textContentVariants.some(v => v.index === props.gtIndex)
})

const canCreateGtFromRecognition = computed(() => !hasGtVariant.value && recognitionCandidates.value.length > 0)
const canAddTextContentVariant = computed(() => canMutateAnnotation.value && !hasGtVariant.value)

function createGtFromRecognition() {
  if (!canCreateGtFromRecognition.value) return
  const gtIndex = typeof props.gtIndex === 'number' && props.gtIndex >= 0 ? props.gtIndex : 0
  const source = recognitionCandidates.value[0]
  if (!source) return
  emit('createGtFromRecognition', props.textline.id, { gtIndex, sourceRecognitionIndex: source.index })
}

function openCommentEditor() {
  if (!canMutateAnnotation.value) return
  commentDraft.value = props.textline.comments ?? ''
  commentEditorOpen.value = true
}

function cancelCommentEdit() {
  commentDraft.value = props.textline.comments ?? ''
  commentEditorOpen.value = false
}

function saveCommentEdit() {
  if (!canMutateAnnotation.value) return
  emit('updateElementComment', props.textline.id, commentDraft.value)
  commentEditorOpen.value = false
}

function handleTextareaKeydownEnter(event: KeyboardEvent, variantIndex: number | undefined) {
  if (props.allowMultiline) return
  handleSingleLineTextareaKeydownEnter(event, isEditableVariant(variantIndex))
}

function handleTextareaBeforeInput(event: InputEvent, variantIndex: number | undefined) {
  if (props.allowMultiline) return
  handleSingleLineTextareaBeforeInput(event, isEditableVariant(variantIndex))
}

function handleTextareaPaste(event: ClipboardEvent, variantIndex: number | undefined) {
  if (props.allowMultiline) return
  handleSingleLineTextareaPaste(event, isEditableVariant(variantIndex))
}

function handleTextareaDrop(event: DragEvent, variantIndex: number | undefined) {
  if (props.allowMultiline) return
  handleSingleLineTextareaDrop(event, isEditableVariant(variantIndex))
}

function getConfidencePercent(confidence: number | undefined): number | undefined {
  if (typeof confidence !== 'number' || !Number.isFinite(confidence)) return undefined
  return Math.round(confidence * 100)
}

function getConfidenceClass(confidence: number | undefined): string {
  if (typeof confidence !== 'number' || !Number.isFinite(confidence)) return ''
  const c = confidence
  if (c > 0.9) return 'text-emerald-600 border-emerald-200 bg-emerald-50'
  if (c > 0.7) return 'text-amber-600 border-amber-200 bg-amber-50'
  return 'text-rose-600 border-rose-200 bg-rose-50'
}

const editingIndexPos = ref<number | null>(null)
const editingUnindexed = ref(true)
const editingIndexValue = ref<number>(0)

function hasIndexConflict(pos: number, newIndex: number | undefined): boolean {
  const variants = editableTextContentVariants.value
  if (newIndex === undefined) {
    for (const variant of variants) {
      if (variant.pos === pos) continue
      if (variant.index === undefined) return true
    }
    return false
  }
  for (const variant of variants) {
    if (variant.pos === pos) continue
    if (variant.index === newIndex) return true
  }
  return false
}

const editingEffectiveIndex = computed<number | undefined>(() => {
  return editingUnindexed.value ? undefined : editingIndexValue.value
})

function openIndexEditor(pos: number, currentIndex: number | undefined) {
  editingIndexPos.value = pos
  editingUnindexed.value = typeof currentIndex !== 'number'
  editingIndexValue.value = typeof currentIndex === 'number' ? currentIndex : 0
}

function closeIndexEditor() {
  editingIndexPos.value = null
}

async function saveIndex(pos: number) {
  const newIndex = editingEffectiveIndex.value

  if (newIndex !== undefined && (!Number.isInteger(newIndex) || newIndex < 0)) return

  if (hasIndexConflict(pos, newIndex)) {
    const desc = newIndex === undefined
      ? 'Another variant already has no index. Saving will swap: it will receive this variant\'s current index.'
      : `Index ${newIndex} is already used by another variant in this textline. Saving will swap the indices between the two variants.`
    const instance = confirmModal.open({
      title: 'Swap Indices?',
      description: desc,
      confirmLabel: 'Swap',
      confirmColor: 'warning'
    })
    const confirmed = await instance.result
    if (!confirmed) return
  }

  emit('updateTextContentVariantIndex', props.textline.id, pos, newIndex)
  closeIndexEditor()
}

const gtText = computed(() => {
  if (!props.showDiff) return ''
  const gt = localTextContentVariants.value.find(te => te.index === props.gtIndex)
  return gt?.text ?? ''
})

const textContentVariantsWithDiff = computed(() => {
  if (!props.showDiff) {
    return localTextContentVariants.value.map(te => ({ ...te }))
  }

  const dmp = getDmp()
  const gt = gtText.value

  return localTextContentVariants.value.map((te) => {
    const diffs = dmp.diff_main(gt, te.text)
    dmp.diff_cleanupSemantic(diffs)
    return {
      ...te,
      diffs
    }
  })
})

const boundingBox = computed(() => {
  const points = props.textline.points
  if (points.length === 0) return null

  const xs = points.map(p => p.x)
  const ys = points.map(p => p.y)

  const minX = Math.floor(Math.min(...xs) - props.padding)
  const minY = Math.floor(Math.min(...ys) - props.padding)
  const maxX = Math.ceil(Math.max(...xs) + props.padding)
  const maxY = Math.ceil(Math.max(...ys) + props.padding)

  return {
    x: minX,
    y: minY,
    width: Math.max(1, maxX - minX),
    height: Math.max(1, maxY - minY)
  }
})

const adjustedPolygonPoints = computed(() => {
  const box = boundingBox.value
  if (!box || props.textline.points.length === 0) return []
  return props.textline.points.map(p => ({
    x: p.x - box.x,
    y: p.y - box.y
  }))
})

const polygonPath = computed(() => {
  const points = adjustedPolygonPoints.value
  if (points.length === 0) return ''
  const [first, ...rest] = points
  if (!first) return ''
  const commands = [`M ${first.x} ${first.y}`]
  for (const point of rest) {
    commands.push(`L ${point.x} ${point.y}`)
  }
  commands.push('Z')
  return commands.join(' ')
})

const cutoutMaskPath = computed(() => {
  const box = boundingBox.value
  const polygon = polygonPath.value
  if (!box || !polygon) return ''
  return `M 0 0 H ${box.width} V ${box.height} H 0 Z ${polygon}`
})

const cutoutFrameStyle = computed(() => {
  const box = boundingBox.value
  if (!box) return undefined
  const width = cutoutRenderWidth.value ?? box.width
  const height = cutoutRenderHeight.value ?? box.height
  return {
    width: `${width}px`,
    height: `${height}px`
  }
})

const drawCutout = () => {
  if (!isVisible.value) return

  const canvas = canvasRef.value
  const box = boundingBox.value

  if (!canvas || !box) return

  const ctx = canvas.getContext('2d')
  if (!ctx) return

  let containerWidth = cutoutContainerRef.value?.clientWidth ?? box.width
  if (cutoutContainerRef.value && typeof window !== 'undefined') {
    const style = window.getComputedStyle(cutoutContainerRef.value)
    const paddingStart = Number.parseFloat(style.paddingInlineStart || style.paddingLeft || '0')
    const paddingEnd = Number.parseFloat(style.paddingInlineEnd || style.paddingRight || '0')
    containerWidth -= (Number.isFinite(paddingStart) ? paddingStart : 0) + (Number.isFinite(paddingEnd) ? paddingEnd : 0)
  }
  containerWidth = Math.max(1, containerWidth)
  const maxHeightPx = cutoutMaxHeightPx.value
  let scale = Math.min(maxCutoutScale, Math.max(1, containerWidth / box.width))
  if (typeof maxHeightPx === 'number' && Number.isFinite(maxHeightPx) && maxHeightPx > 0) {
    const heightLimitedScale = maxHeightPx / box.height
    if (heightLimitedScale < scale) {
      scale = Math.max(0.1, heightLimitedScale)
    }
  }
  const displayWidth = Math.max(1, Math.round(box.width * scale))
  const displayHeight = Math.max(1, Math.round(box.height * scale))
  cutoutRenderWidth.value = displayWidth
  cutoutRenderHeight.value = displayHeight

  const dpr = Math.max(1, window.devicePixelRatio || 1)
  canvas.width = Math.max(1, Math.round(displayWidth * dpr))
  canvas.height = Math.max(1, Math.round(displayHeight * dpr))
  canvas.style.width = `${displayWidth}px`
  canvas.style.height = `${displayHeight}px`
  ctx.setTransform(dpr, 0, 0, dpr, 0, 0)
  ctx.clearRect(0, 0, displayWidth, displayHeight)
  ctx.imageSmoothingEnabled = true
  ctx.imageSmoothingQuality = 'high'

  if (!props.imageUrl) {
    isCutoutLoading.value = false
    cutoutLoadFailed.value = true
    return
  }

  const requestId = ++cutoutRequestId
  isCutoutLoading.value = true
  cutoutLoadFailed.value = false

  const img = new Image()
  img.crossOrigin = 'anonymous'

  img.onload = () => {
    if (requestId !== cutoutRequestId) return

    const sourceX = Math.max(0, box.x)
    const sourceY = Math.max(0, box.y)
    const sourceXEnd = Math.min(img.naturalWidth, box.x + box.width)
    const sourceYEnd = Math.min(img.naturalHeight, box.y + box.height)
    const sourceWidth = Math.max(0, sourceXEnd - sourceX)
    const sourceHeight = Math.max(0, sourceYEnd - sourceY)

    if (sourceWidth <= 0 || sourceHeight <= 0) {
      isCutoutLoading.value = false
      cutoutLoadFailed.value = true
      return
    }

    const destX = sourceX - box.x
    const destY = sourceY - box.y

    ctx.clearRect(0, 0, displayWidth, displayHeight)
    ctx.drawImage(
      img,
      sourceX, sourceY, sourceWidth, sourceHeight,
      Math.round(destX * scale),
      Math.round(destY * scale),
      Math.round(sourceWidth * scale),
      Math.round(sourceHeight * scale)
    )

    isCutoutLoading.value = false
    cutoutLoadFailed.value = false
  }

  img.onerror = () => {
    if (requestId !== cutoutRequestId) return
    isCutoutLoading.value = false
    cutoutLoadFailed.value = true
  }

  img.src = props.imageUrl
}

const addTextContentVariant = () => {
  emit('addTextContentVariant', props.textline.id)
}

const removeTextContentVariant = (arrayPos: number) => {
  localTextContentVariants.value = localTextContentVariants.value.filter(variant => variant.pos !== arrayPos)
  emit('removeTextContentVariant', props.textline.id, arrayPos)
}

function getLocalTextContentVariant(arrayPos: number): TextContentVariantData | undefined {
  return localTextContentVariants.value.find(variant => variant.pos === arrayPos)
}

const updateText = (arrayPos: number, text: string) => {
  const textEquiv = getLocalTextContentVariant(arrayPos)
  if (!textEquiv) return
  textEquiv.text = text
  emit('updateTextContentVariant', props.textline.id, arrayPos, text)
}

const replaceTextRange = (arrayPos: number, start: number, end: number, replacement: string) => {
  const textEquiv = getLocalTextContentVariant(arrayPos)
  if (!textEquiv || !isEditableVariant(textEquiv.index)) return
  const nextText = `${textEquiv.text.slice(0, start)}${replacement}${textEquiv.text.slice(end)}`
  updateText(arrayPos, nextText)
}

interface DiffSegment {
  text: string
  type: 'equal' | 'insert' | 'delete'
}

function renderDiff(diffs: Diff[] | undefined): DiffSegment[] {
  if (!diffs) return []

  return diffs.map(diff => ({
    text: diff[1],
    type: diff[0] === 0 ? 'equal' : diff[0] === 1 ? 'insert' : 'delete'
  }))
}

function hasHighlight(text: string): boolean {
  return hasTextHighlight(text, normalizedTextHighlightQuery.value)
}

function highlightShellClass(index: number | undefined, text: string): string {
  return getTextHighlightShellClass(index, text, normalizedTextHighlightQuery.value, variantRole)
}

function highlightedSegments(text: string) {
  return getHighlightedSegments(text, normalizedTextHighlightQuery.value)
}

type UnknownSegment = {
  text: string
  unknown: boolean
}

type UnknownDictionarySegment = {
  text: string
  unknown: boolean
  start: number
  end: number
}

type UnknownCharacterMeta = {
  description: string | null
  source: 'unicode' | 'mufi' | null
  loading: boolean
}

type UnknownCharacterDetail = {
  char: string
  codepoint: string
  description: string | null
  source: 'unicode' | 'mufi' | null
  loading: boolean
}

type UnknownDictionaryTokenDetail = {
  token: string
  normalized: string
  suggestions: DictionarySuggestion[]
}

const canCheckDictionaryTokens = computed(() => {
  return Boolean(
    props.highlightUnknownDictionaryTokens
    && props.projectDictionaryId
    && workspaceStore.selectedWorkspaceId
  )
})

const gtDictionaryTokens = computed(() => {
  const tokens = localTextContentVariants.value
    .filter(variant => variantRole(variant.index) === 'gt')
    .flatMap(variant => tokenizeForDictionary(variant.text))
  return [...new Set(tokens)]
})

const unknownCharacterMeta = ref<Record<string, UnknownCharacterMeta>>({})

function splitCodepoints(text: string): string[] {
  return Array.from(text ?? '')
}

function isWhitespaceCharacter(char: string): boolean {
  return /\s/u.test(char)
}

function isUnknownCodecCharacter(char: string): boolean {
  if (!props.highlightUnknownCodecChars) return false
  if (!props.includeWhitespaceInCodecHighlight && isWhitespaceCharacter(char)) return false
  return !codecCharacterSet.value.has(char)
}

function toCodepoint(char: string): string {
  const codepoint = char.codePointAt(0)
  if (!codepoint) return 'N/A'
  const minWidth = codepoint > 0xFFFF ? 6 : 4
  return `U+${codepoint.toString(16).toUpperCase().padStart(minWidth, '0')}`
}

function uniqueCharacters(chars: string[]): string[] {
  const seen = new Set<string>()
  const result: string[] = []
  for (const ch of chars) {
    if (seen.has(ch)) continue
    seen.add(ch)
    result.push(ch)
  }
  return result
}

function getUnknownCharacterCount(text: string): number {
  return splitCodepoints(text).filter(ch => isUnknownCodecCharacter(ch)).length
}

function getUnknownCharactersFromText(text: string): string[] {
  const unknown = splitCodepoints(text).filter(ch => isUnknownCodecCharacter(ch))
  return uniqueCharacters(unknown)
}

function getUnknownSegments(text: string): UnknownSegment[] {
  if (!props.highlightUnknownCodecChars) {
    return [{ text, unknown: false }]
  }

  const chars = splitCodepoints(text)
  if (chars.length === 0) return [{ text: '', unknown: false }]

  const segments: UnknownSegment[] = []
  let currentUnknown = isUnknownCodecCharacter(chars[0] ?? '')
  let buffer = chars[0] ?? ''

  for (let i = 1; i < chars.length; i++) {
    const ch = chars[i] ?? ''
    const unknown = isUnknownCodecCharacter(ch)
    if (unknown === currentUnknown) {
      buffer += ch
      continue
    }
    segments.push({ text: buffer, unknown: currentUnknown })
    buffer = ch
    currentUnknown = unknown
  }

  segments.push({ text: buffer, unknown: currentUnknown })
  return segments
}

function getUnknownCharacterDetails(text: string): UnknownCharacterDetail[] {
  const chars = getUnknownCharactersFromText(text)
  return chars.map((char) => {
    const meta = unknownCharacterMeta.value[char]
    return {
      char,
      codepoint: toCodepoint(char),
      description: meta?.description ?? null,
      source: meta?.source ?? null,
      loading: meta?.loading ?? false
    }
  })
}

async function loadUnknownCharacterMeta(chars: string[]): Promise<void> {
  const pending = chars.filter((char) => {
    const existing = unknownCharacterMeta.value[char]
    return !existing
  })
  if (pending.length === 0) return

  await Promise.all(pending.map(async (char) => {
    unknownCharacterMeta.value[char] = {
      description: unknownCharacterMeta.value[char]?.description ?? null,
      source: unknownCharacterMeta.value[char]?.source ?? null,
      loading: true
    }

    try {
      const response = await GlyphService.search(char, { mufi: true, unicode: true }, 0, 20)
      const exactMatch = response.data.find(item => item.utf8 === char) ?? null
      unknownCharacterMeta.value[char] = {
        description: exactMatch?.description ?? null,
        source: exactMatch?.source ?? null,
        loading: false
      }
    } catch {
      unknownCharacterMeta.value[char] = {
        description: null,
        source: null,
        loading: false
      }
    }
  }))
}

function handleUnknownPopoverUpdate(open: boolean, text: string): void {
  if (!open) return
  const chars = getUnknownCharactersFromText(text)
  if (chars.length === 0) return
  void loadUnknownCharacterMeta(chars)
}

function getUnknownDictionaryTokenCount(text: string): number {
  const workspaceId = workspaceStore.selectedWorkspaceId
  const dictionaryId = props.projectDictionaryId
  if (!canCheckDictionaryTokens.value || !workspaceId || !dictionaryId) return 0
  return tokenizeForDictionary(text)
    .map(token => getTokenResult(workspaceId, dictionaryId, token))
    .filter(result => result && !result.known)
    .length
}

function isDictionaryCheckLoading(text: string): boolean {
  const workspaceId = workspaceStore.selectedWorkspaceId
  const dictionaryId = props.projectDictionaryId
  if (!canCheckDictionaryTokens.value || !workspaceId || !dictionaryId) return false

  const tokens = tokenizeForDictionary(text)
  if (tokens.length === 0) return false

  return tokens.some((token) => {
    const result = getTokenResult(workspaceId, dictionaryId, token)
    if (result) return false
    return isTokenPending(workspaceId, dictionaryId, token) || !result
  })
}

function getUnknownDictionaryTokenDetails(text: string): UnknownDictionaryTokenDetail[] {
  const workspaceId = workspaceStore.selectedWorkspaceId
  const dictionaryId = props.projectDictionaryId
  if (!canCheckDictionaryTokens.value || !workspaceId || !dictionaryId) return []
  const seen = new Set<string>()
  const tokens = tokenizeForDictionary(text)
  const details: UnknownDictionaryTokenDetail[] = []

  for (const token of tokens) {
    const result = getTokenResult(workspaceId, dictionaryId, token)
    if (!result || result.known || seen.has(result.normalizedToken)) continue
    seen.add(result.normalizedToken)

    details.push({
      token,
      normalized: result.normalizedToken,
      suggestions: result.suggestions ?? []
    })
  }

  return details
}

function getUnknownDictionaryTokenSegmentsFromLookup(text: string): UnknownDictionarySegment[] {
  const workspaceId = workspaceStore.selectedWorkspaceId
  const dictionaryId = props.projectDictionaryId
  if (!canCheckDictionaryTokens.value || !workspaceId || !dictionaryId) {
    return [{ text, unknown: false, start: 0, end: text.length }]
  }

  const segments: UnknownDictionarySegment[] = []
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

function handleUnknownDictionaryPopoverUpdate(open: boolean, text: string): void {
  if (!open) return
  const workspaceId = workspaceStore.selectedWorkspaceId
  const dictionaryId = props.projectDictionaryId
  if (!workspaceId || !dictionaryId) return
  void ensureTokenResults({
    workspaceId,
    dictionaryId,
    tokens: [text],
    includeSuggestions: true,
    limit: 5
  })
}

function isDictionarySuggestionLoading(token: string): boolean {
  const workspaceId = workspaceStore.selectedWorkspaceId
  const dictionaryId = props.projectDictionaryId
  if (!workspaceId || !dictionaryId) return false

  const result = getTokenResult(workspaceId, dictionaryId, token)
  if (!result) return true
  if (result.known) return false
  if (isTokenPending(workspaceId, dictionaryId, token)) return true
  return !hasSuggestionsLoaded(workspaceId, dictionaryId, token)
}

function applyDictionarySuggestion(arrayPos: number, segment: UnknownDictionarySegment, replacement: string) {
  replaceTextRange(arrayPos, segment.start, segment.end, replacement)
}

watch([canCheckDictionaryTokens, gtDictionaryTokens, () => props.projectDictionaryId, () => workspaceStore.selectedWorkspaceId], async ([enabled, tokens, dictionaryId, workspaceId]) => {
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
    // Ignore token-check failures in the editor; the text remains editable.
  }
}, { immediate: true })

watch(() => props.textline.textContentVariants, (newEquivs) => {
  localTextContentVariants.value = [...newEquivs]
}, { immediate: true })

const pointsKey = computed(() => props.textline.points.map(p => `${p.x},${p.y}`).join(';'))

watch([() => props.imageUrl, () => props.padding, () => props.textline.id, pointsKey, isVisible], () => {
  if (isVisible.value) {
    drawCutout()
  }
})

let observer: IntersectionObserver | null = null
let resizeObserver: ResizeObserver | null = null
let onViewportResize: (() => void) | null = null

onMounted(() => {
  if (typeof IntersectionObserver !== 'undefined' && rootRef.value) {
    observer = new IntersectionObserver(
      (entries) => {
        const entry = entries[0]
        if (entry?.isIntersecting) {
          isVisible.value = true
          nextTick(() => drawCutout())
        }
      },
      {
        rootMargin: '100px', // Start loading slightly before visible
        threshold: 0
      }
    )
    observer.observe(rootRef.value)
  } else {
    isVisible.value = true
    drawCutout()
  }

  if (typeof ResizeObserver !== 'undefined' && cutoutContainerRef.value) {
    resizeObserver = new ResizeObserver(() => {
      if (!isVisible.value) return
      drawCutout()
    })
    resizeObserver.observe(cutoutContainerRef.value)
  }

  if (typeof window !== 'undefined') {
    onViewportResize = () => {
      if (!isVisible.value) return
      drawCutout()
    }
    window.addEventListener('resize', onViewportResize)
  }
})

onBeforeUnmount(() => {
  if (observer) {
    observer.disconnect()
    observer = null
  }
  if (resizeObserver) {
    resizeObserver.disconnect()
    resizeObserver = null
  }
  if (typeof window !== 'undefined' && onViewportResize) {
    window.removeEventListener('resize', onViewportResize)
    onViewportResize = null
  }
})
</script>

<template>
  <TooltipProvider :delay-duration="200">
    <div
      ref="rootRef"
      :style="textViewFontVars"
      class="@container group relative rounded-md border transition-all duration-150"
      :class="[
        props.isSelected ? 'border-burnt-sienna-500 ring-1 ring-primary/12' : 'border-neutral-200 dark:border-neutral-800 hover:border-border/22'
      ]"
      @click="emit('selectTextline', props.textline.id)"
    >
      <template v-if="!isVisible">
        <div class="flex-1 p-3 space-y-2">
          <USkeleton class="h-4 w-24" />
          <USkeleton class="h-16 w-full" />
          <USkeleton class="h-8 w-full" />
        </div>
      </template>

      <template v-else>
        <div
          class="flex min-w-0"
          :class="isVertical ? 'flex-col' : '@max-sm:flex-col'"
        >
          <div class="min-w-0 p-3 pb-2 flex flex-col gap-2" :class="isVertical ? 'w-full' : '@max-sm:w-full flex-1'">
            <div class="flex items-center justify-between gap-2">
              <UBadge variant="subtle" color="neutral" class="font-mono">
                <Icon name="i-lucide-hash" class="h-3 w-3 mr-1" />
                {{ props.textline.label ?? props.textline.id }}
              </UBadge>
              <div class="flex items-center gap-1">
                <template v-if="props.showReorderButtons">
                  <UTooltip text="Move up within region" :content="{ side: 'top', align: 'center', sideOffset: 6 }">
                    <UButton
                      color="neutral"
                      variant="ghost"
                      size="xs"
                      icon="i-lucide-arrow-up"
                      :disabled="!canMutateAnnotation || !props.canMoveUp"
                      @click.stop="emit('moveUpTextline', props.textline.id)"
                    />
                  </UTooltip>
                  <UTooltip text="Move down within region" :content="{ side: 'top', align: 'center', sideOffset: 6 }">
                    <UButton
                      color="neutral"
                      variant="ghost"
                      icon="i-lucide-arrow-down"
                      size="xs"
                      :disabled="!canMutateAnnotation || !props.canMoveDown"
                      @click.stop="emit('moveDownTextline', props.textline.id)"
                    />
                  </UTooltip>
                </template>
                <UTooltip v-if="props.showDeleteButton" text="Delete textline" :content="{ side: 'top', align: 'center', sideOffset: 6 }">
                  <UButton
                    color="error"
                    variant="ghost"
                    icon="i-lucide-trash-2"
                    size="xs"
                    :disabled="!canMutateAnnotation"
                    @click.stop="emit('deleteTextline', props.textline.id)"
                  />
                </UTooltip>
              </div>
            </div>

            <div
              ref="cutoutContainerRef"
              class="rounded-md overflow-hidden bg-linear-to-b from-muted/30 to-muted/10 flex items-center relative"
              :class="isVertical ? 'justify-start' : 'justify-center'"
              :style="cutoutWrapperStyle"
            >
              <USkeleton
                v-if="isCutoutLoading"
                class="absolute inset-0 h-full w-full"
              />
              <div
                class="relative max-w-full shrink-0"
                :style="cutoutFrameStyle"
              >
                <canvas
                  ref="canvasRef"
                  class="cutout-canvas block h-auto max-w-full"
                  :class="isCutoutLoading ? 'opacity-0' : 'opacity-100'"
                />
                <svg
                  v-if="boundingBox && polygonPath"
                  class="pointer-events-none absolute inset-0 h-full w-full"
                  :viewBox="`0 0 ${boundingBox.width} ${boundingBox.height}`"
                  preserveAspectRatio="xMinYMin meet"
                  aria-hidden="true"
                >
                  <path
                    v-if="cutoutMaskPath"
                    :d="cutoutMaskPath"
                    fill="rgba(2, 6, 23, 0.14)"
                    fill-rule="evenodd"
                  />
                  <path
                    :d="polygonPath"
                    fill="none"
                    stroke="rgba(125, 211, 252, 0.38)"
                    stroke-width="4"
                    vector-effect="non-scaling-stroke"
                    stroke-linejoin="round"
                  />
                  <path
                    :d="polygonPath"
                    fill="rgba(255, 255, 255, 0.08)"
                    stroke="rgba(14, 165, 233, 0.96)"
                    stroke-width="1.5"
                    vector-effect="non-scaling-stroke"
                    stroke-linejoin="round"
                  />
                </svg>
              </div>
              <div
                v-if="cutoutLoadFailed"
                class="absolute inset-0 flex items-center justify-center textline-ui-xs text-muted bg-muted/20"
              >
                Cutout unavailable
              </div>
            </div>
          </div>

          <div class="min-w-0 p-3 pt-2 flex flex-col gap-2" :class="isVertical ? 'w-full' : '@max-sm:w-full flex-1'">
            <div class="flex items-center gap-1 justify-end w-full">
              <UTooltip v-if="canCreateGtFromRecognition" text="Create GT from first recognition variant" :content="{ side: 'top', align: 'center', sideOffset: 6 }">
                <UButton
                  color="success"
                  variant="soft"
                  size="xs"
                  icon="i-lucide-copy-plus"
                  :disabled="!canMutateAnnotation"
                  @click.stop="createGtFromRecognition"
                >
                  Create GT
                </UButton>
              </UTooltip>
              <UTooltip v-if="canAddTextContentVariant" text="Add another transcription variant" :content="{ side: 'top', align: 'center', sideOffset: 6 }">
                <UButton
                  color="neutral"
                  variant="ghost"
                  size="sm"
                  icon="i-lucide-plus"
                  :disabled="!canMutateAnnotation"
                  @click.stop="addTextContentVariant"
                >
                  Add
                </UButton>
              </UTooltip>
              <UPopover
                v-model:open="commentEditorOpen"
                :content="{ side: 'top', align: 'end', sideOffset: 6 }"
              >
                <UButton
                  color="neutral"
                  variant="ghost"
                  size="xs"
                  class="h-6 px-2 textline-ui-xs text-muted hover:text-foreground"
                  :disabled="!canMutateAnnotation"
                  @click.stop="openCommentEditor"
                >
                  <Icon name="i-lucide-message-square" class="h-3 w-3 mr-1" />
                  {{ hasElementComment ? 'Edit Comment' : 'Add Comment' }}
                </UButton>
                <template #content>
                  <div class="w-80 p-3 flex flex-col gap-2" @click.stop>
                    <div class="textline-ui-xs font-medium text-muted">
                      {{ hasElementComment ? 'Edit metadata comment' : 'Add metadata comment' }}
                    </div>
                    <UTextarea
                      v-model="commentDraft"
                      :rows="4"
                      autoresize
                      placeholder="Enter comment..."
                      :disabled="!canMutateAnnotation"
                    />
                    <div class="flex items-center justify-end gap-2">
                      <UButton
                        color="neutral"
                        variant="ghost"
                        size="xs"
                        @click.stop="cancelCommentEdit"
                      >
                        Cancel
                      </UButton>
                      <UButton
                        color="primary"
                        size="xs"
                        :disabled="!canMutateAnnotation"
                        @click.stop="saveCommentEdit"
                      >
                        Save
                      </UButton>
                    </div>
                  </div>
                </template>
              </UPopover>
            </div>

            <UAlert
              v-if="props.showComments && normalizedComment.length > 0"
              color="info"
              variant="subtle"
              title="Comment"
              :description="normalizedComment"
              icon="i-lucide-message-square-quote"
            />

            <div class="flex flex-col gap-2">
              <div
                v-for="textEquiv in textContentVariantsWithDiff"
                :key="getTextContentVariantRenderKey(textEquiv)"
                class="flex items-center gap-1.5 group/input"
              >
                <UPopover :content="{ side: 'left', align: 'center' }" @update:open="(open: boolean) => { if (!open) closeIndexEditor() }">
                  <UButton
                    variant="ghost"
                    color="neutral"
                    size="xs"
                    class="relative shrink-0 min-w-5 h-5 p-0 textline-ui-2xs inline-flex items-center justify-center"
                    :class="typeof textEquiv.index === 'number' ? 'text-muted/60 hover:text-primary' : 'text-muted/40 italic hover:text-primary'"
                    :disabled="!canMutateAnnotation"
                    title="Change index"
                    @click.stop="openIndexEditor(textEquiv.pos, textEquiv.index)"
                  >
                    {{ typeof textEquiv.index === 'number' ? textEquiv.index : '–' }}
                  </UButton>
                  <template #content>
                    <div v-if="editingIndexPos === textEquiv.pos" class="p-3 flex flex-col gap-2 w-52" @click.stop>
                      <span class="textline-ui-xs font-medium text-muted">Change Index</span>
                      <UCheckbox v-model="editingUnindexed" label="Unindexed" :disabled="!canMutateAnnotation" />
                      <UInput
                        v-if="!editingUnindexed"
                        v-model.number="editingIndexValue"
                        type="number"
                        :min="0"
                        size="sm"
                        placeholder="Index"
                        :disabled="!canMutateAnnotation"
                        :color="hasIndexConflict(textEquiv.pos, editingEffectiveIndex) ? 'error' : undefined"
                        @keydown.enter.stop="saveIndex(textEquiv.pos)"
                        @keydown.escape.stop="closeIndexEditor"
                      />
                      <p v-if="hasIndexConflict(textEquiv.pos, editingEffectiveIndex)" class="textline-ui-xs text-error">
                        {{ editingEffectiveIndex === undefined ? 'Another variant already has no index' : `Index ${editingEffectiveIndex} is already in use` }} (will swap)
                      </p>
                      <div class="flex justify-end gap-1">
                        <UButton
                          size="xs"
                          color="neutral"
                          variant="ghost"
                          @click.stop="closeIndexEditor"
                        >
                          Cancel
                        </UButton>
                        <UButton
                          size="xs"
                          color="primary"
                          :disabled="!canMutateAnnotation"
                          @click.stop="saveIndex(textEquiv.pos)"
                        >
                          Save
                        </UButton>
                      </div>
                    </div>
                  </template>
                </UPopover>

                <div class="shrink-0 w-14 flex justify-center">
                  <UBadge
                    v-if="getConfidencePercent(textEquiv.confidence) !== undefined"
                    variant="outline"
                    class="textline-ui-xs px-1.5 py-0"
                    :class="getConfidenceClass(textEquiv.confidence)"
                  >
                    {{ getConfidencePercent(textEquiv.confidence) }}%
                  </UBadge>
                </div>

                <div class="flex-1 min-w-0 flex flex-col gap-1">
                  <div class="flex items-center gap-1">
                    <UTextarea
                      :id="`textequiv_${props.textline.id}_${String(textEquiv.index ?? textEquiv.pos)}`"
                      :model-value="textEquiv.text"
                      :rows="props.allowMultiline ? 3 : 1"
                      autoresize
                      placeholder="Enter transcription..."
                      :dir="textDirectionDir"
                      :style="textDirectionStyle"
                      :ui="hasHighlight(textEquiv.text) ? { base: 'relative z-10 bg-transparent' } : { base: 'relative z-10' }"
                      :readonly="props.readOnly || variantRole(textEquiv.index) === 'recognition'"
                      :disabled="props.readOnly || variantRole(textEquiv.index) === 'nonAssigned'"
                      :tabindex="isEditableVariant(textEquiv.index) ? 0 : -1"
                      :data-textline-id="props.textline.id"
                      :data-textequiv-index="typeof textEquiv.index === 'number' ? String(textEquiv.index) : ''"
                      :data-textequiv-pos="String(textEquiv.pos)"
                      class="textline-textarea flex-1 min-w-0 min-h-9 h-auto resize-none transition-colors focus:border-primary/50 focus:ring-1 focus:ring-primary/20 font-junicode"
                      :class="[
                        variantRole(textEquiv.index) === 'gt' && (hasHighlight(textEquiv.text)
                          ? 'textline-textarea--gt border-emerald-200'
                          : 'textline-textarea--gt border-emerald-200 bg-emerald-100/95 dark:bg-emerald-900/90'),
                        variantRole(textEquiv.index) === 'recognition' && 'textline-textarea--recognition',
                        variantRole(textEquiv.index) === 'nonAssigned' && 'textline-textarea--non-assigned border-rose-200/70 text-muted',
                        hasHighlight(textEquiv.text) && ['textline-textarea--has-highlight', highlightShellClass(textEquiv.index, textEquiv.text)]
                      ]"
                      @click.stop
                      @focus="emit('selectTextline', props.textline.id)"
                      @keydown.enter="(event: KeyboardEvent) => handleTextareaKeydownEnter(event, textEquiv.index)"
                      @beforeinput="(event: InputEvent) => handleTextareaBeforeInput(event, textEquiv.index)"
                      @paste="(event: ClipboardEvent) => handleTextareaPaste(event, textEquiv.index)"
                      @drop="(event: DragEvent) => handleTextareaDrop(event, textEquiv.index)"
                      @update:model-value="(value) => { if (isEditableVariant(textEquiv.index)) updateText(textEquiv.pos, String(value ?? '')) }"
                    >
                      <div
                        v-if="hasHighlight(textEquiv.text)"
                        class="textline-textarea-highlight-layer pointer-events-none absolute inset-px z-0 overflow-hidden rounded-md px-2.5 py-1.5 font-junicode text-transparent whitespace-pre-wrap break-words"
                        :dir="textDirectionDir"
                        :style="textDirectionStyle"
                        aria-hidden="true"
                      >
                        <template
                          v-for="(segment, segmentIndex) in highlightedSegments(textEquiv.text)"
                          :key="`${textEquiv.pos}_${segmentIndex}`"
                        >
                          <mark v-if="segment.matched" class="textline-highlight-mark">{{ segment.text }}</mark>
                          <span v-else>{{ segment.text }}</span>
                        </template>
                      </div>
                    </UTextarea>
                    <UTooltip text="Remove variant" :content="{ side: 'top', align: 'center', sideOffset: 6 }">
                      <UButton
                        color="neutral"
                        variant="ghost"
                        size="xs"
                        icon="i-lucide-x"
                        :disabled="!canMutateAnnotation"
                        @click.stop="removeTextContentVariant(textEquiv.pos)"
                      />
                    </UTooltip>
                  </div>

                  <div
                    v-if="highlightUnknownCodecChars && codecCharacterSet.size > 0"
                    class="textline-ui-xs rounded-sm bg-muted/25 p-2 space-y-1"
                  >
                    <div class="flex items-center justify-between gap-2">
                      <span class="text-muted">Codec check</span>
                      <UBadge
                        :color="getUnknownCharacterCount(textEquiv.text) > 0 ? 'warning' : 'success'"
                        variant="soft"
                        size="xs"
                      >
                        {{ getUnknownCharacterCount(textEquiv.text) }} unknown
                      </UBadge>
                    </div>
                    <div class="font-junicode break-all" :dir="textDirectionDir" :style="[codecPreviewStyle, textDirectionStyle]">
                      <template v-for="(segment, segmentIndex) in getUnknownSegments(textEquiv.text)" :key="`seg_${textEquiv.pos}_${segmentIndex}`">
                        <UPopover
                          v-if="segment.unknown"
                          mode="hover"
                          :content="{ side: 'top', align: 'start', sideOffset: 8 }"
                          @update:open="(open: boolean) => handleUnknownPopoverUpdate(open, segment.text)"
                        >
                          <span class="bg-warning/20 text-warning-700 dark:text-warning-300 rounded-sm px-0.5 cursor-help">
                            {{ segment.text }}
                          </span>
                          <template #content>
                            <div class="p-2 min-w-48 max-w-80 space-y-1">
                              <div class="textline-ui-xs font-medium text-muted">
                                Unknown characters
                              </div>
                              <div
                                v-for="detail in getUnknownCharacterDetails(segment.text)"
                                :key="`unknown_${textEquiv.pos}_${segmentIndex}_${detail.char}_${detail.codepoint}`"
                                class="space-y-1"
                              >
                                <div class="flex items-center gap-2 textline-ui-xs">
                                  <span class="font-junicode textline-ui-char leading-none">{{ detail.char }}</span>
                                  <span class="font-mono">{{ detail.codepoint }}</span>
                                  <span v-if="detail.loading" class="text-muted">Loading...</span>
                                  <span v-else-if="detail.description" class="text-muted truncate">{{ detail.description }}</span>
                                  <UBadge
                                    v-if="detail.source"
                                    color="neutral"
                                    variant="soft"
                                    size="xs"
                                    class="uppercase"
                                  >
                                    {{ detail.source }}
                                  </UBadge>
                                </div>
                                <div class="flex flex-wrap gap-1">
                                  <UButton
                                    size="xs"
                                    color="neutral"
                                    variant="soft"
                                    :disabled="!projectCodecId"
                                    @click.stop="emit('quickAddCodecChar', detail.char)"
                                  >
                                    Add to Codec
                                  </UButton>
                                  <UButton
                                    size="xs"
                                    color="neutral"
                                    variant="soft"
                                    :disabled="!hasVirtualKeyboard"
                                    @click.stop="emit('quickAddKeyboardChar', detail.char)"
                                  >
                                    Add to Keyboard
                                  </UButton>
                                  <UButton
                                    size="xs"
                                    color="neutral"
                                    variant="ghost"
                                    :disabled="!projectCodecId"
                                    @click.stop="emit('openCodecEditor')"
                                  >
                                    Open Codec
                                  </UButton>
                                  <UButton
                                    size="xs"
                                    color="neutral"
                                    variant="ghost"
                                    :disabled="!hasVirtualKeyboard"
                                    @click.stop="emit('openKeyboardEditor')"
                                  >
                                    Open Keyboard
                                  </UButton>
                                </div>
                              </div>
                            </div>
                          </template>
                        </UPopover>
                        <span
                          v-else
                        >{{ segment.text }}</span>
                      </template>
                    </div>
                  </div>

                  <div
                    v-if="canCheckDictionaryTokens && variantRole(textEquiv.index) === 'gt'"
                    class="textline-ui-xs rounded-sm bg-muted/25 p-2 space-y-1"
                  >
                    <div class="flex items-center justify-between gap-2">
                      <span class="text-muted">Dictionary check</span>
                      <USkeleton
                        v-if="isDictionaryCheckLoading(textEquiv.text)"
                        class="h-5 w-20"
                      />
                      <UBadge
                        v-else
                        :color="getUnknownDictionaryTokenCount(textEquiv.text) > 0 ? 'warning' : 'success'"
                        variant="soft"
                        size="xs"
                      >
                        {{ getUnknownDictionaryTokenCount(textEquiv.text) }} unknown
                      </UBadge>
                    </div>
                    <div
                      v-if="isDictionaryCheckLoading(textEquiv.text)"
                      class="space-y-2"
                    >
                      <USkeleton class="h-5 w-full" />
                      <USkeleton class="h-5 w-3/4" />
                    </div>
                    <div
                      v-else
                      class="font-junicode break-words"
                      :dir="textDirectionDir"
                      :style="[codecPreviewStyle, textDirectionStyle]"
                    >
                      <template v-for="(segment, segmentIndex) in getUnknownDictionaryTokenSegmentsFromLookup(textEquiv.text)" :key="`dict_seg_${textEquiv.pos}_${segmentIndex}`">
                        <UPopover
                          v-if="segment.unknown"
                          mode="hover"
                          :content="{ side: 'top', align: 'start', sideOffset: 8 }"
                          @update:open="(open: boolean) => handleUnknownDictionaryPopoverUpdate(open, segment.text)"
                        >
                          <span class="underline decoration-warning decoration-2 underline-offset-2 text-warning-700 dark:text-warning-300">
                            {{ segment.text }}
                          </span>
                          <template #content>
                            <div class="p-2 min-w-56 max-w-96 space-y-2">
                              <div class="textline-ui-xs font-medium text-muted">
                                Dictionary suggestions
                              </div>
                              <div
                                v-for="detail in getUnknownDictionaryTokenDetails(segment.text)"
                                :key="`dictionary_${textEquiv.pos}_${segmentIndex}_${detail.normalized}`"
                                class="space-y-2"
                              >
                                <div class="textline-ui-xs">
                                  <span class="font-medium">{{ detail.token }}</span>
                                  <span class="text-muted"> is not in the dictionary.</span>
                                </div>
                                <div v-if="detail.suggestions.length > 0" class="flex flex-wrap gap-1">
                                  <UButton
                                    v-for="suggestion in detail.suggestions"
                                    :key="`${detail.normalized}_${suggestion.display}`"
                                    color="neutral"
                                    variant="soft"
                                    size="xs"
                                    @click.stop="applyDictionarySuggestion(textEquiv.pos, segment, suggestion.display)"
                                  >
                                    {{ suggestion.display }}
                                  </UButton>
                                </div>
                                <div v-else-if="isDictionarySuggestionLoading(detail.token)" class="space-y-2">
                                  <USkeleton class="h-7 w-full" />
                                  <USkeleton class="h-7 w-2/3" />
                                </div>
                                <div class="flex flex-wrap gap-1">
                                  <UButton
                                    v-if="!projectDictionaryLocked"
                                    size="xs"
                                    color="neutral"
                                    variant="soft"
                                    :disabled="!projectDictionaryId || !canQuickAddToDictionary"
                                    @click.stop="emit('quickAddDictionaryToken', detail.token)"
                                  >
                                    Add to Dictionary
                                  </UButton>
                                  <UButton
                                    size="xs"
                                    color="neutral"
                                    variant="ghost"
                                    :disabled="!projectDictionaryId"
                                    @click.stop="emit('openDictionaryEditor')"
                                  >
                                    Open Dictionary
                                  </UButton>
                                </div>
                              </div>
                            </div>
                          </template>
                        </UPopover>
                        <span v-else>{{ segment.text }}</span>
                      </template>
                    </div>
                  </div>

                  <div
                    v-if="showDiff && textEquiv.diffs"
                    class="textline-ui-sm font-mono p-2 bg-muted/25 rounded-sm"
                    :dir="textDirectionDir"
                    :style="textDirectionStyle"
                  >
                    <template v-for="segment in renderDiff(textEquiv.diffs)" :key="segment.text">
                      <span v-if="segment.type === 'equal'" class="text-foreground">{{ segment.text }}</span>
                      <span v-else-if="segment.type === 'delete'" class="text-red-500 line-through bg-red-500/10 px-0.5 rounded">
                        {{ segment.text }}
                      </span>
                      <span v-else-if="segment.type === 'insert'" class="text-green-500 bg-green-500/10 px-0.5 rounded-sm font-semibold">
                        +{{ segment.text }}
                      </span>
                    </template>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </div>
      </template>
    </div>
  </TooltipProvider>
</template>

<style scoped>
.textline-ui-sm {
  font-size: var(--text-font-size-sm);
  line-height: 1.35;
}

.textline-ui-xs {
  font-size: var(--text-font-size-xs);
  line-height: 1.3;
}

.textline-ui-2xs {
  font-size: var(--text-font-size-2xs);
  line-height: 1.25;
}

.textline-ui-char {
  font-size: var(--text-font-size-char);
}

.textline-textarea :deep(textarea) {
  font-size: var(--text-font-size, 18px);
}

.textline-textarea.textline-textarea--has-highlight :deep(textarea) {
  background-color: transparent;
}

.textline-textarea.textline-textarea--gt.textline-textarea--has-highlight :deep(textarea) {
  background-color: transparent;
}

.textline-textarea.textline-textarea--gt :deep(textarea) {
  background-color: rgb(220 252 231 / 0.95);
}

.dark .textline-textarea.textline-textarea--gt :deep(textarea) {
  background-color: rgb(6 78 59 / 0.9);
}

.dark .textline-textarea.textline-textarea--gt.textline-textarea--has-highlight :deep(textarea) {
  background-color: transparent;
}

.textline-textarea.textline-textarea--recognition :deep(textarea) {
  background-color: rgb(148 163 184 / 0.12);
}

.textline-textarea.textline-textarea--non-assigned :deep(textarea) {
  background-color: rgb(254 242 242 / 0.9);
}

.textline-textarea-highlight-layer {
  font-size: var(--text-font-size, 18px);
  line-height: 1.25rem;
}

.textline-highlight-mark {
  color: transparent;
  background-color: rgb(253 224 71 / 0.45);
  line-height: inherit;
  padding: 0;
  box-decoration-break: clone;
  -webkit-box-decoration-break: clone;
}

:deep(.dark) .textline-highlight-mark {
  background-color: rgb(250 204 21 / 0.3);
}

.cutout-canvas {
  border-radius: 0.375rem;
  box-shadow:
    inset 0 0 0 1px rgb(15 23 42 / 0.08),
    0 2px 8px rgb(2 6 23 / 0.06);
}
</style>
