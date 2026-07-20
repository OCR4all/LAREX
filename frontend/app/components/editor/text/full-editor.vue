<script setup lang="ts">
import type { ComponentPublicInstance } from 'vue'
import { TooltipProvider } from 'reka-ui'
import type { TextContentVariantData } from '@/models/editor'
import type { TextLine } from '@/models/editor/text'
import type { Region } from '@/models/editor/region'
import { getEditorSession } from '@/session/editor/editor-session'
import { useEditorCollaboration } from '@/composables/editor/use-editor-collaboration'
import { collectTextlineIdsInReadingOrder, getAdjacentTextlineId } from '@/utils/editor/textline-navigation'
import { findTextLineRecursive } from '@/utils/editor/pcgts-editor-primitives'
import { setGtVariantUnicode } from '@/utils/editor/text-variants'
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
  getTextViewRuntimeControls
} from './shared/text-view-runtime'
import { createTextlineVariantsUpdateCommand } from './shared/textline-variant-update'

type FullTextLine = {
  id: string
  text: string
  source: FullTextLineSource
  sourceIndex?: number
  readingDirection: ReturnType<typeof computeTextLineReadingDirectionMap>[string]
  textContentVariants?: TextContentVariantData[]
}

const props = defineProps<{ canvasId?: string | null }>()

const editorStore = useEditorStore()
const uiStore = useEditorUiStore()
const collaboration = useEditorCollaboration()

const effectiveCanvasId = computed(() => props.canvasId ?? editorStore.activeCanvasId)
const page = computed(() => {
  const canvasId = effectiveCanvasId.value
  return canvasId ? getEditorSession(canvasId)?.document.value?.page ?? null : null
})
const activeGtIndex = computed(() => editorStore.projectTextDefaultGtIndex ?? 0)
const recognitionIndices = computed(() => editorStore.projectTextDefaultRecognitionIndices ?? [1])
const fontSize = computed(() => Math.max(12, Number(uiStore.textViewFontSize) || 30))
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
  const currentPage = page.value
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
        readingDirection: directionById[id],
        textContentVariants: textline.textContentVariants as TextContentVariantData[] | undefined
      }]
    })
})
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
  if (!nextId) return
  nextTick(() => {
    textareaById.get(nextId)?.scrollIntoView({
      block: 'center',
      inline: 'nearest',
      behavior: 'smooth'
    })
  })
}, { immediate: true })

onBeforeUnmount(() => {
  flushPendingCommit()
  textareaById.clear()
})
</script>

<template>
  <TooltipProvider :delay-duration="200">
    <section class="flex h-full min-h-0 w-full flex-col bg-default" aria-label="Full transcription">
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
          <div class="flex w-6 shrink-0 justify-center pt-2">
            <UTooltip
              v-if="line.source === 'prediction'"
              :text="line.sourceIndex === undefined
                ? 'Prediction (unindexed); edit to create GT'
                : `Prediction #${line.sourceIndex}; edit to create GT`"
            >
              <span
                class="flex size-4 items-center justify-center rounded-sm bg-amber-500/12 text-[9px] font-semibold text-amber-700 dark:text-amber-300"
                aria-label="Prediction; edit to create ground truth"
              >
                P
              </span>
            </UTooltip>
          </div>

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
            class="min-h-10 min-w-0 flex-1 resize-none overflow-hidden border-0 bg-transparent px-1 py-1 font-junicode text-highlighted outline-none placeholder:text-muted/50"
            placeholder="…"
            @click.stop
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
    </section>
  </TooltipProvider>
</template>
