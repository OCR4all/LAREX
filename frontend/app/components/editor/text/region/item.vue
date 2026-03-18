<script setup lang="ts">
import type { Point } from '@/models/editor/types'
import type { TextItemLayout } from '@/stores/editor/types'
import type { ReadingDirection } from '../line/reading-direction'

interface TextContentVariantData {
  index?: number
  text: string
  confidence?: number
}

interface Props {
  region: {
    id: string
    label?: string
    points: Point[]
    readingDirection?: ReadingDirection
    textContentVariants: TextContentVariantData[]
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
  hasVirtualKeyboard: false
})

const emit = defineEmits<{
  updateTextContentVariant: [id: string, arrayPos: number, text: string]
  updateTextContentVariantIndex: [id: string, arrayPos: number, index: number | undefined]
  addTextContentVariant: [id: string]
  removeTextContentVariant: [id: string, arrayPos: number]
  selectRegion: [id: string]
  createGtFromRecognition: [id: string, payload: { gtIndex: number, sourceRecognitionIndex?: number }]
  quickAddCodecChar: [char: string]
  quickAddDictionaryToken: [token: string]
  quickAddKeyboardChar: [char: string]
  openCodecEditor: []
  openDictionaryEditor: []
  openKeyboardEditor: []
}>()

const lineItemModel = computed(() => ({
  ...props.region,
  label: props.region.label ?? props.region.id
}))
</script>

<template>
  <EditorTextLineItem
    :textline="lineItemModel"
    :image-url="props.imageUrl"
    :padding="props.padding"
    :font-size="props.fontSize"
    :layout="props.layout"
    :codec-characters="props.codecCharacters"
    :highlight-unknown-codec-chars="props.highlightUnknownCodecChars"
    :include-whitespace-in-codec-highlight="props.includeWhitespaceInCodecHighlight"
    :highlight-unknown-dictionary-tokens="props.highlightUnknownDictionaryTokens"
    :gt-index="props.gtIndex"
    :recognition-indices="props.recognitionIndices"
    :show-diff="props.showDiff"
    :is-selected="props.isSelected"
    :text-highlight-query="props.textHighlightQuery"
    :project-codec-id="props.projectCodecId"
    :project-dictionary-id="props.projectDictionaryId"
    :can-quick-add-to-dictionary="props.canQuickAddToDictionary"
    :project-dictionary-locked="props.projectDictionaryLocked"
    :project-dictionary-case-sensitive="props.projectDictionaryCaseSensitive"
    :project-dictionary-unicode-normalization="props.projectDictionaryUnicodeNormalization"
    :selected-keyboard-id="props.selectedKeyboardId"
    :has-virtual-keyboard="props.hasVirtualKeyboard"
    :allow-multiline="true"
    :show-drag-handle="false"
    :show-delete-button="false"
    cutout-max-height-class="max-h-[100vh]"
    @select-textline="emit('selectRegion', $event)"
    @add-text-content-variant="emit('addTextContentVariant', $event)"
    @remove-text-content-variant="(id, arrayPos) => emit('removeTextContentVariant', id, arrayPos)"
    @update-text-content-variant="(id, arrayPos, text) => emit('updateTextContentVariant', id, arrayPos, text)"
    @update-text-content-variant-index="(id, arrayPos, index) => emit('updateTextContentVariantIndex', id, arrayPos, index)"
    @create-gt-from-recognition="(id, payload) => emit('createGtFromRecognition', id, payload)"
    @quick-add-codec-char="emit('quickAddCodecChar', $event)"
    @quick-add-dictionary-token="emit('quickAddDictionaryToken', $event)"
    @quick-add-keyboard-char="emit('quickAddKeyboardChar', $event)"
    @open-codec-editor="emit('openCodecEditor')"
    @open-dictionary-editor="emit('openDictionaryEditor')"
    @open-keyboard-editor="emit('openKeyboardEditor')"
  />
</template>
