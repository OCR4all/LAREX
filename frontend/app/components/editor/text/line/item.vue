<script setup lang="ts">
import { LazyUiConfirmModal } from '#components'
import type { Point } from '@/models/editor/types'
import type { TextItemLayout } from '@/stores/editor/types'
import DiffMatchPatch from 'diff-match-patch'
import type { Diff } from 'diff-match-patch'
import { GlyphService } from '@/utils/glyph-service'
import type { DictionarySuggestion } from '@/types/dictionary'
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
  cutoutMaxHeightClass: null,
  readOnly: false,
  showComments: false
})
const emit = defineEmits<{
  updateTextContentVariant: [id: string, arrayPos: number, text: string]
  updateTextContentVariantIndex: [id: string, arrayPos: number, index: number | undefined]
  addTextContentVariant: [id: string]
  removeTextContentVariant: [id: string, arrayPos: number]
  selectTextline: [id: string]
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
const lensCanvasRef = ref<HTMLCanvasElement | null>(null)
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
const isCutoutLoading = ref(false)
const cutoutLoadFailed = ref(false)
let cutoutRequestId = 0

const isVertical = computed(() => props.layout === 'vertical')
const canMutateAnnotation = computed(() => !props.readOnly)
// Keep cutout start aligned with the transcription textarea start rail in vertical/isolated layout.
// 5.5rem = index button (1.25rem) + two gaps (0.375rem each) + confidence column (3.5rem).
const cutoutStartOffsetRem = 5.5
const cutoutWrapperStyle = computed(() => {
  if (!isVertical.value) return undefined
  return {
    paddingInlineStart: `${cutoutStartOffsetRem}rem`
  }
})
const cutoutCanvasStyle = computed(() => ({
  objectFit: 'contain',
  objectPosition: isVertical.value ? 'left center' : 'center center'
}))
const effectiveCutoutMaxHeightClass = computed(() => {
  if (props.cutoutMaxHeightClass) return props.cutoutMaxHeightClass
  return isVertical.value ? 'max-h-56' : 'max-h-40'
})
const codecCharacterSet = computed(() => new Set(props.codecCharacters ?? []))
const codecPreviewStyle = computed(() => {
  const nextFontSize = Math.max(10, Number(props.fontSize ?? 18))
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
const editableTextContentVariants = computed(() => props.textline.allTextContentVariants ?? props.textline.textContentVariants)

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

function createGtFromRecognition() {
  if (!canCreateGtFromRecognition.value) return
  const gtIndex = typeof props.gtIndex === 'number' && props.gtIndex >= 0 ? props.gtIndex : 0
  const source = recognitionCandidates.value[0]
  if (!source) return
  emit('createGtFromRecognition', props.textline.id, { gtIndex, sourceRecognitionIndex: source.index })
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

  const minX = Math.min(...xs) - props.padding
  const minY = Math.min(...ys) - props.padding
  const maxX = Math.max(...xs) + props.padding
  const maxY = Math.max(...ys) + props.padding

  return {
    x: minX,
    y: minY,
    width: maxX - minX,
    height: maxY - minY
  }
})

const drawCutout = () => {
  if (!isVisible.value) return

  const canvas = canvasRef.value
  const box = boundingBox.value

  if (!canvas || !box) return

  const ctx = canvas.getContext('2d')
  if (!ctx) return

  canvas.width = box.width
  canvas.height = box.height

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
    ctx.drawImage(
      img,
      box.x, box.y, box.width, box.height, // Source rectangle
      0, 0, box.width, box.height // Destination rectangle
    )

    const adjustedPoints = props.textline.points.map(p => ({
      x: p.x - box.x,
      y: p.y - box.y
    }))

    ctx.save()
    ctx.fillStyle = 'rgba(0, 0, 0, 0.06)'
    ctx.beginPath()
    ctx.rect(0, 0, box.width, box.height)
    if (adjustedPoints.length > 0) {
      const firstPoint = adjustedPoints[0]
      if (firstPoint) {
        ctx.moveTo(firstPoint.x, firstPoint.y)
        for (let i = 1; i < adjustedPoints.length; i++) {
          const point = adjustedPoints[i]
          if (point) {
            ctx.lineTo(point.x, point.y)
          }
        }
        ctx.closePath()
      }
    }
    ctx.fill('evenodd')
    ctx.restore()

    ctx.strokeStyle = '#007acc'
    ctx.lineWidth = 2
    ctx.beginPath()

    if (adjustedPoints.length > 0) {
      const firstPoint = adjustedPoints[0]
      if (firstPoint) {
        ctx.moveTo(firstPoint.x, firstPoint.y)
        for (let i = 1; i < adjustedPoints.length; i++) {
          const point = adjustedPoints[i]
          if (point) {
            ctx.lineTo(point.x, point.y)
          }
        }
      }
      ctx.closePath()
      ctx.stroke()
    }

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

const LENS_SIZE = 160
const DISPLAY_ZOOM = 2 // Always 2x relative to what's currently displayed on screen
const showLens = ref(false)
const lensX = ref(0)
const lensY = ref(0)

/**
 * Compute the rendered content area within the canvas CSS box,
 * accounting for object-fit: contain (which may add dead space).
 */
function getCanvasContentRect(canvas: HTMLCanvasElement) {
  const rect = canvas.getBoundingClientRect()
  if (rect.width === 0 || rect.height === 0) return null

  const bufferAspect = canvas.width / canvas.height
  const cssAspect = rect.width / rect.height

  if (bufferAspect > cssAspect) {
    const contentHeight = rect.width / bufferAspect
    return { x: 0, y: (rect.height - contentHeight) / 2, width: rect.width, height: contentHeight }
  }
  const contentWidth = rect.height * bufferAspect
  return { x: (rect.width - contentWidth) / 2, y: 0, width: contentWidth, height: rect.height }
}

function onCutoutMouseMove(e: MouseEvent) {
  const canvas = canvasRef.value
  const lensCanvas = lensCanvasRef.value
  if (!canvas || !lensCanvas) return

  const rect = canvas.getBoundingClientRect()
  const content = getCanvasContentRect(canvas)
  if (!content) return

  const mouseX = e.clientX - rect.left - content.x
  const mouseY = e.clientY - rect.top - content.y

  if (mouseX < 0 || mouseY < 0 || mouseX > content.width || mouseY > content.height) {
    showLens.value = false
    return
  }

  const scaleX = canvas.width / content.width
  const scaleY = canvas.height / content.height

  const srcX = mouseX * scaleX
  const srcY = mouseY * scaleY

  lensX.value = e.clientX
  lensY.value = e.clientY
  showLens.value = true

  const lensCtx = lensCanvas.getContext('2d')
  if (!lensCtx) return

  lensCanvas.width = LENS_SIZE
  lensCanvas.height = LENS_SIZE

  lensCtx.clearRect(0, 0, LENS_SIZE, LENS_SIZE)

  lensCtx.save()
  lensCtx.beginPath()
  lensCtx.arc(LENS_SIZE / 2, LENS_SIZE / 2, LENS_SIZE / 2, 0, Math.PI * 2)
  lensCtx.clip()

  const srcRegionW = LENS_SIZE * scaleX / DISPLAY_ZOOM
  const srcRegionH = LENS_SIZE * scaleY / DISPLAY_ZOOM
  lensCtx.drawImage(
    canvas,
    srcX - srcRegionW / 2, srcY - srcRegionH / 2, srcRegionW, srcRegionH,
    0, 0, LENS_SIZE, LENS_SIZE
  )

  const vignette = lensCtx.createRadialGradient(
    LENS_SIZE / 2, LENS_SIZE / 2, LENS_SIZE * 0.3,
    LENS_SIZE / 2, LENS_SIZE / 2, LENS_SIZE / 2
  )
  vignette.addColorStop(0, 'rgba(255, 255, 255, 0)')
  vignette.addColorStop(0.7, 'rgba(255, 255, 255, 0)')
  vignette.addColorStop(1, 'rgba(0, 0, 0, 0.15)')
  lensCtx.fillStyle = vignette
  lensCtx.fillRect(0, 0, LENS_SIZE, LENS_SIZE)

  const shine = lensCtx.createRadialGradient(
    LENS_SIZE * 0.35, LENS_SIZE * 0.3, 0,
    LENS_SIZE * 0.35, LENS_SIZE * 0.3, LENS_SIZE * 0.35
  )
  shine.addColorStop(0, 'rgba(255, 255, 255, 0.2)')
  shine.addColorStop(1, 'rgba(255, 255, 255, 0)')
  lensCtx.fillStyle = shine
  lensCtx.fillRect(0, 0, LENS_SIZE, LENS_SIZE)

  lensCtx.restore()

  lensCtx.fillStyle = 'rgba(0, 122, 204, 0.35)'
  lensCtx.beginPath()
  lensCtx.arc(LENS_SIZE / 2, LENS_SIZE / 2, 2.5, 0, Math.PI * 2)
  lensCtx.fill()
}

function onCutoutMouseLeave() {
  showLens.value = false
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
})

onBeforeUnmount(() => {
  if (observer) {
    observer.disconnect()
    observer = null
  }
})
</script>

<template>
  <div
    ref="rootRef"
    :style="{ '--text-font-size': fontSize + 'px' }"
    class="@container group relative flex rounded-sm border bg-card transition-all duration-200"
    :class="[
      props.isSelected ? 'border-primary border-rounded-sm ring-2 ring-primary shadow-md' : 'border-border/60 hover:border-border hover:shadow-sm'
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
      <div v-if="props.showDragHandle" class="textline-drag-handle flex items-center px-1.5 border-r border-border/40 bg-muted/30 cursor-grab active:cursor-grabbing opacity-0 group-hover:opacity-100 transition-opacity rounded-l-lg">
        <Icon name="i-lucide-grip-vertical" class="h-4 w-4 text-muted/70" />
      </div>

      <div
        class="flex-1 min-w-0 flex pl-2"
        :class="isVertical ? 'flex-col' : '@max-sm:flex-col'"
      >
        <div class="min-w-0 p-3 flex flex-col gap-2" :class="isVertical ? 'w-full' : '@max-sm:w-full flex-1'">
          <div class="flex items-center gap-2">
            <UBadge variant="subtle" color="neutral" class="font-mono">
              <Icon name="i-lucide-hash" class="h-3 w-3 mr-1" />
              {{ props.textline.label ?? props.textline.id }}
            </UBadge>
          </div>

          <div
            class="rounded-sm overflow-hidden bg-muted/20 border border-border/30 flex items-center relative"
            :class="isVertical ? 'justify-start' : 'justify-center'"
            :style="cutoutWrapperStyle"
            @mousemove="onCutoutMouseMove"
            @mouseleave="onCutoutMouseLeave"
          >
            <USkeleton
              v-if="isCutoutLoading"
              class="absolute inset-0 h-full w-full"
            />
            <canvas
              ref="canvasRef"
              class="w-full h-auto block"
              :class="[isCutoutLoading ? 'opacity-0' : 'opacity-100', effectiveCutoutMaxHeightClass]"
              :style="cutoutCanvasStyle"
            />
            <div
              v-if="cutoutLoadFailed"
              class="absolute inset-0 flex items-center justify-center text-xs text-muted bg-muted/20"
            >
              Cutout unavailable
            </div>
          </div>
        </div>

        <div v-if="!isVertical" class="w-px bg-border/40 my-3 @max-sm:hidden" />

        <div class="min-w-0 p-3 flex flex-col gap-2" :class="isVertical ? 'w-full' : '@max-sm:w-full flex-1'">
          <div class="flex items-center justify-between">
            <span class="text-xs font-medium text-muted flex items-center gap-1.5">
              <Icon name="i-lucide-type" class="h-3 w-3" />
              Transcription
            </span>
            <div class="flex items-center gap-1">
              <UButton
                v-if="canCreateGtFromRecognition"
                color="success"
                variant="soft"
                size="sm"
                class="h-6 px-2 text-xs"
                :disabled="!canMutateAnnotation"
                @click.stop="createGtFromRecognition"
              >
                <Icon name="i-lucide-copy-plus" class="h-3 w-3 mr-1" />
                Create GT
              </UButton>
              <UButton
                color="neutral"
                variant="ghost"
                size="sm"
                class="h-6 px-2 text-xs text-muted hover:text-foreground"
                :disabled="!canMutateAnnotation"
                @click.stop="addTextContentVariant"
              >
                <Icon name="i-lucide-plus" class="h-3 w-3 mr-1" />
                Add
              </UButton>
            </div>
          </div>

          <div
            v-if="props.showComments && normalizedComment.length > 0"
            class="rounded-sm border border-amber-200/70 bg-amber-50/70 px-2 py-1.5"
          >
            <p class="text-[11px] font-medium uppercase tracking-wide text-amber-800/90">
              Comment
            </p>
            <p class="mt-0.5 text-xs text-amber-900/90 whitespace-pre-wrap break-words">
              {{ normalizedComment }}
            </p>
          </div>

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
                  class="relative shrink-0 min-w-5 h-5 p-0 text-[10px] inline-flex items-center justify-center"
                  :class="typeof textEquiv.index === 'number' ? 'text-muted/60 hover:text-primary' : 'text-muted/40 italic hover:text-primary'"
                  title="Change index"
                  :disabled="!canMutateAnnotation"
                  @click.stop="openIndexEditor(textEquiv.pos, textEquiv.index)"
                >
                  {{ typeof textEquiv.index === 'number' ? textEquiv.index : '–' }}
                </UButton>
                <template #content>
                  <div v-if="editingIndexPos === textEquiv.pos" class="p-3 flex flex-col gap-2 w-52" @click.stop>
                    <span class="text-xs font-medium text-muted">Change Index</span>
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
                    <p v-if="hasIndexConflict(textEquiv.pos, editingEffectiveIndex)" class="text-xs text-error">
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
                  class="text-xs px-1.5 py-0"
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
                    :data-textline-id="props.textline.id"
                    :data-textequiv-index="typeof textEquiv.index === 'number' ? String(textEquiv.index) : ''"
                    :data-textequiv-pos="String(textEquiv.pos)"
                    class="textline-textarea flex-1 min-w-0 min-h-9 h-auto resize-none transition-colors focus:border-primary/50 focus:ring-1 focus:ring-primary/20 font-junicode"
                    :class="[
                      variantRole(textEquiv.index) === 'gt' && (hasHighlight(textEquiv.text)
                        ? 'textline-textarea--gt border-emerald-300'
                        : 'textline-textarea--gt border-emerald-300 bg-emerald-100/95 dark:bg-emerald-900/90'),
                      variantRole(textEquiv.index) === 'recognition' && 'textline-textarea--recognition',
                      variantRole(textEquiv.index) === 'nonAssigned' && 'textline-textarea--non-assigned border-rose-200 text-muted',
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
                  <UButton
                    color="neutral"
                    variant="ghost"
                    size="xs"
                    class="shrink-0 h-6 w-6 p-0 opacity-0 group-hover/input:opacity-100 transition-opacity text-muted hover:text-destructive"
                    title="Remove this TextContentVariant"
                    :disabled="!canMutateAnnotation"
                    @click.stop="removeTextContentVariant(textEquiv.pos)"
                  >
                    <Icon name="i-lucide-x" class="h-3 w-3" />
                  </UButton>
                </div>

                <div
                  v-if="highlightUnknownCodecChars && codecCharacterSet.size > 0"
                  class="text-xs rounded-sm border border-default p-2 space-y-1"
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
                            <div class="text-xs font-medium text-muted">
                              Unknown characters
                            </div>
                            <div
                              v-for="detail in getUnknownCharacterDetails(segment.text)"
                              :key="`unknown_${textEquiv.pos}_${segmentIndex}_${detail.char}_${detail.codepoint}`"
                              class="space-y-1"
                            >
                              <div class="flex items-center gap-2 text-xs">
                                <span class="font-junicode text-base leading-none">{{ detail.char }}</span>
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
                  class="text-xs rounded-sm border border-default p-2 space-y-1"
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
                            <div class="text-xs font-medium text-muted">
                              Dictionary suggestions
                            </div>
                            <div
                              v-for="detail in getUnknownDictionaryTokenDetails(segment.text)"
                              :key="`dictionary_${textEquiv.pos}_${segmentIndex}_${detail.normalized}`"
                              class="space-y-2"
                            >
                              <div class="text-xs">
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
                  class="text-sm font-mono p-2 bg-muted/30 rounded-sm border"
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

      <div v-if="props.showDeleteButton" class="flex flex-col items-center justify-between p-2 border-l border-border/40">
        <UButton
          color="neutral"
          variant="ghost"
          size="sm"
          class="h-7 w-7 p-0 text-muted hover:text-destructive hover:bg-destructive/10"
          :disabled="!canMutateAnnotation"
          @click.stop="emit('deleteTextline', props.textline.id)"
        >
          <Icon name="i-lucide-x" class="h-3.5 w-3.5" />
        </UButton>
      </div>
    </template>

    <Teleport to="body">
      <canvas
        v-show="showLens"
        ref="lensCanvasRef"
        class="fixed pointer-events-none rounded-full border-2 border-primary/60 shadow-xl z-50"
        :width="LENS_SIZE"
        :height="LENS_SIZE"
        :style="{
          width: LENS_SIZE + 'px',
          height: LENS_SIZE + 'px',
          left: (lensX - LENS_SIZE / 2) + 'px',
          top: (lensY - LENS_SIZE / 2) + 'px'
        }"
      />
    </Teleport>
  </div>
</template>

<style scoped>
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
</style>
