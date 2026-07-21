<script setup lang="ts">
import type { TextItemLayout } from '@/stores/editor/types'

const padding = defineModel<number>('padding', { default: 10 })
const fontSize = defineModel<number>('fontSize', { default: 18 })
const cutoutHeight = defineModel<number>('cutoutHeight', { default: 72 })
const textItemLayout = defineModel<TextItemLayout>('textItemLayout', { default: 'side-by-side' })
const autoSelectFirstLine = defineModel<boolean>('autoSelectFirstLine', { default: true })
const focusMode = defineModel<boolean>('focusMode', { default: false })

const props = withDefaults(defineProps<{
  fullTextMode?: boolean
}>(), {
  fullTextMode: false
})

const layoutTabItems = [
  { label: 'Side by side', value: 'side-by-side' as const, icon: 'i-lucide-columns-2' },
  { label: 'Vertical', value: 'vertical' as const, icon: 'i-lucide-rows-2' }
]
</script>

<template>
  <div class="p-4 flex flex-col gap-4">
    <div v-if="!props.fullTextMode">
      <div class="flex justify-between items-center mb-2">
        <span class="text-sm font-medium">Cutout Padding</span>
        <span class="text-sm font-semibold text-primary">{{ padding }}px</span>
      </div>
      <USlider
        v-model="padding"
        :min="0"
        :max="100"
        :step="5"
      />
    </div>

    <div>
      <div class="flex justify-between items-center mb-2">
        <span class="text-sm font-medium">Font Size</span>
        <span class="text-sm font-semibold text-primary">{{ fontSize }}px</span>
      </div>
      <USlider
        v-model="fontSize"
        :min="8"
        :max="32"
        :step="1"
      />
    </div>

    <div v-if="!props.fullTextMode">
      <div class="flex justify-between items-center mb-2">
        <span class="text-sm font-medium">Cutout Height</span>
        <span class="text-sm font-semibold text-primary">{{ cutoutHeight }}px</span>
      </div>
      <USlider
        v-model="cutoutHeight"
        :min="24"
        :max="220"
        :step="4"
      />
    </div>

    <div v-if="!props.fullTextMode">
      <span class="text-sm font-medium mb-2 block">Item Layout</span>
      <UTabs v-model="textItemLayout" :items="layoutTabItems" />
    </div>

    <div v-if="!props.fullTextMode" class="flex items-center justify-between gap-3">
      <div class="min-w-0">
        <span class="text-sm font-medium block">Focus Mode</span>
        <span class="text-xs text-muted">
          Show only cutouts, GT/recognition text, dictionary checks, and diffs.
        </span>
      </div>
      <USwitch v-model="focusMode" />
    </div>

    <div v-if="!props.fullTextMode" class="flex items-center justify-between gap-3">
      <div class="min-w-0">
        <span class="text-sm font-medium block">Auto-select First Line</span>
        <span class="text-xs text-muted">
          Select the first textline when Visual Text opens without an active line.
        </span>
      </div>
      <USwitch v-model="autoSelectFirstLine" />
    </div>
  </div>
</template>
