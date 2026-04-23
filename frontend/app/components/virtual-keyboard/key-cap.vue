<script setup lang="ts">
import type { KeyboardItem } from '@/types/virtual-keyboard'
import type { VirtualKeyboardPalette } from '@/composables/use-virtual-keyboard-palette'
import { GlyphService } from '@/utils/glyph-service'
import { toUnicodeCodepoint } from '@/utils/unicode'

type GlyphMeta = {
  description: string | null
  source: 'unicode' | 'mufi' | null
}

const glyphMetaCache = new Map<string, GlyphMeta>()
const glyphMetaPending = new Map<string, Promise<GlyphMeta>>()

const props = defineProps<{
  item: KeyboardItem
  isShiftPressed: boolean
  isPressed: boolean
  isEchoing: boolean
  palette: VirtualKeyboardPalette
  cellSize: number
}>()

const isShiftKey = computed(() => props.item.char === 'Shift' || props.item.description === 'Shift Modifier')
const displayChar = computed(() => (props.isShiftPressed && props.item.shiftChar) ? props.item.shiftChar : props.item.char)
const displayDescription = computed(() => (props.isShiftPressed && props.item.shiftDescription)
  ? props.item.shiftDescription
  : props.item.description)
const keyLabelLength = computed(() => (displayChar.value || '').length)
const keyFontSize = computed(() => {
  if (keyLabelLength.value > 1) {
    return Math.max(11, Math.min(props.cellSize * 0.3, props.cellSize - 8))
  }
  return Math.max(14, Math.min(props.cellSize * 0.46, props.cellSize - 8))
})
const shiftHintFontSize = computed(() =>
  Math.max(8, Math.min(props.cellSize * 0.18, props.cellSize * 0.24))
)

const hasSingleCodepointChar = computed(() => Array.from(displayChar.value || '').length === 1)
const displayCodepoint = computed(() => hasSingleCodepointChar.value ? toUnicodeCodepoint(displayChar.value) : null)
const isMetadataOpen = ref(false)
const glyphMetaLoading = ref(false)
const glyphMeta = ref<GlyphMeta | null>(null)

async function fetchGlyphMeta(char: string): Promise<GlyphMeta> {
  const cached = glyphMetaCache.get(char)
  if (cached) return cached

  const pending = glyphMetaPending.get(char)
  if (pending) return pending

  const request = GlyphService
    .search(char, { mufi: true, unicode: true }, 0, 20)
    .then((response) => {
      const exactMatch = response.data.find(item => item.utf8 === char) ?? null
      return {
        description: exactMatch?.description ?? null,
        source: exactMatch?.source ?? null
      } as GlyphMeta
    })
    .catch(() => ({
      description: null,
      source: null
    }) satisfies GlyphMeta)
    .finally(() => {
      glyphMetaPending.delete(char)
    })

  glyphMetaPending.set(char, request)
  const result = await request
  glyphMetaCache.set(char, result)
  return result
}

async function ensureGlyphMeta(): Promise<void> {
  if (!hasSingleCodepointChar.value) {
    glyphMeta.value = null
    glyphMetaLoading.value = false
    return
  }

  const char = displayChar.value
  if (!char) return

  glyphMetaLoading.value = true
  glyphMeta.value = await fetchGlyphMeta(char)
  glyphMetaLoading.value = false
}

const tooltipDescription = computed(() =>
  glyphMeta.value?.description
  ?? displayDescription.value
  ?? 'No character description available.'
)
const tooltipSource = computed(() => glyphMeta.value?.source ? glyphMeta.value.source.toUpperCase() : null)

watch(
  [isMetadataOpen, displayChar],
  ([open]) => {
    if (!open) return
    void ensureGlyphMeta()
  }
)
</script>

<template>
  <UPopover
    v-model:open="isMetadataOpen"
    mode="hover"
    :open-delay="1000"
    :close-delay="100"
    :content="{ side: 'top', align: 'center', sideOffset: 6 }"
    class="block w-full h-full"
  >
    <div
      class="keycap w-full h-full flex items-center justify-center relative overflow-hidden cursor-pointer select-none"
      :class="[
        item.colorClass || palette.keyBgClass,
        item.textClass || palette.keyTextClass,
        (isPressed || isEchoing) ? 'is-pressed' : '',
        isEchoing ? 'is-echoing ring-2 ring-primary-400' : '',
        (isShiftKey && isShiftPressed) ? 'shift-active' : ''
      ]"
      :style="{
        background: palette.keyBgStyle,
        color: palette.keyTextStyle
      }"
    >
      <div class="absolute inset-0.5 keycap-surface" />
      <div
        class="relative z-10 font-bold break-all text-center leading-none font-junicode"
        :style="{ fontSize: `${keyFontSize}px` }"
      >
        {{ displayChar }}
      </div>
      <div
        v-if="item.shiftChar"
        class="absolute top-1 right-2 font-bold opacity-60 z-10 font-junicode"
        :style="{ fontSize: `${shiftHintFontSize}px` }"
      >
        {{ isShiftPressed ? item.char : item.shiftChar }}
      </div>
    </div>

    <template #content>
      <div class="max-w-60 p-2 space-y-1.5 text-xs">
        <div class="flex items-center gap-2">
          <span class="font-junicode text-base leading-none">{{ displayChar }}</span>
          <span class="font-mono text-[11px] text-muted">{{ displayCodepoint ?? 'N/A' }}</span>
        </div>
        <p class="text-toned leading-4">
          {{ tooltipDescription }}
        </p>
        <p v-if="glyphMetaLoading" class="text-muted text-[11px]">
          Looking up Unicode metadata...
        </p>
        <p v-else-if="tooltipSource" class="text-muted text-[11px]">
          Source: {{ tooltipSource }}
        </p>
      </div>
    </template>
  </UPopover>
</template>

<style scoped>
.keycap {
  border-radius: 0.4rem;
  background: linear-gradient(to bottom right, rgba(255,255,255,0.1), rgba(0,0,0,0.05));
  border: 1px solid rgba(255,255,255,0.1);
  box-shadow: inset 0 1px 1px rgba(255,255,255,0.2), 0 2px 4px rgba(0,0,0,0.2);
  transition: all 0.05s ease-out;
  transform: translateY(-1px);
}
.keycap:active, .keycap.is-pressed, .keycap.is-echoing {
  transform: scale(0.96);
  box-shadow: inset 0 2px 4px rgba(0,0,0,0.15), 0 0 1px rgba(0,0,0,0.1);
  background: linear-gradient(to bottom right, rgba(0,0,0,0.05), rgba(255,255,255,0.05));
}
.keycap.shift-active {
  border-color: #818cf8;
  box-shadow: 0 0 8px rgba(99, 102, 241, 0.5);
  transform: translateY(1px);
}
.keycap-surface {
  background: linear-gradient(to bottom right, rgba(0,0,0,0.02), rgba(0,0,0,0.08));
  border-radius: 0.3rem;
  box-shadow: inset 0 1px 3px rgba(0,0,0,0.1);
}
</style>
