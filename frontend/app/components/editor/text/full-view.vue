<script setup lang="ts">
import { clampFullTextSplitRatio } from './shared/full-text-split'

const props = defineProps<{
  canvasId: string
  src: string
}>()

const rootEl = ref<HTMLElement | null>(null)
const canvasRatio = ref(0.5)
const isResizing = ref(false)

const KEYBOARD_STEP = 0.025

function clampRatio(ratio: number): number {
  const width = rootEl.value?.getBoundingClientRect().width ?? 0
  return clampFullTextSplitRatio(ratio, width)
}

function setRatioFromClientX(clientX: number): void {
  const rect = rootEl.value?.getBoundingClientRect()
  if (!rect || rect.width <= 0) return
  canvasRatio.value = clampRatio((clientX - rect.left) / rect.width)
}

function handlePointerMove(event: PointerEvent): void {
  if (!isResizing.value) return
  setRatioFromClientX(event.clientX)
}

function stopResize(): void {
  if (!isResizing.value) return
  isResizing.value = false
  window.removeEventListener('pointermove', handlePointerMove)
  window.removeEventListener('pointerup', stopResize)
  window.removeEventListener('pointercancel', stopResize)
}

function startResize(event: PointerEvent): void {
  event.preventDefault()
  isResizing.value = true
  setRatioFromClientX(event.clientX)
  window.addEventListener('pointermove', handlePointerMove)
  window.addEventListener('pointerup', stopResize)
  window.addEventListener('pointercancel', stopResize)
}

function handleDividerKeydown(event: KeyboardEvent): void {
  if (event.key === 'ArrowLeft') {
    event.preventDefault()
    canvasRatio.value = clampRatio(canvasRatio.value - KEYBOARD_STEP)
  } else if (event.key === 'ArrowRight') {
    event.preventDefault()
    canvasRatio.value = clampRatio(canvasRatio.value + KEYBOARD_STEP)
  } else if (event.key === 'Home') {
    event.preventDefault()
    canvasRatio.value = clampRatio(0.25)
  } else if (event.key === 'End') {
    event.preventDefault()
    canvasRatio.value = clampRatio(0.75)
  }
}

onBeforeUnmount(stopResize)
</script>

<template>
  <div ref="rootEl" class="flex h-full w-full min-w-0 overflow-hidden">
    <div
      class="h-full min-w-0"
      :style="{ flexBasis: `${canvasRatio * 100}%` }"
    >
      <Editor class="h-full w-full" :src="props.src" :canvas-id="props.canvasId" />
    </div>

    <div
      role="separator"
      tabindex="0"
      aria-label="Resize canvas and full transcription panes"
      aria-orientation="vertical"
      :aria-valuenow="Math.round(canvasRatio * 100)"
      aria-valuemin="20"
      aria-valuemax="80"
      class="group relative z-20 h-full w-2 shrink-0 cursor-col-resize touch-none outline-none"
      :class="isResizing ? 'bg-primary/10' : 'bg-default'"
      @pointerdown="startResize"
      @keydown="handleDividerKeydown"
    >
      <span
        class="absolute inset-y-0 left-1/2 w-px -translate-x-1/2 transition-colors"
        :class="isResizing ? 'bg-primary' : 'bg-default group-hover:bg-accented group-focus-visible:bg-primary'"
      />
    </div>

    <div
      class="h-full min-w-0 border-l border-accented"
      :style="{ flexBasis: `${(1 - canvasRatio) * 100}%` }"
    >
      <EditorTextFullEditor class="h-full w-full" :canvas-id="props.canvasId" />
    </div>
  </div>
</template>
