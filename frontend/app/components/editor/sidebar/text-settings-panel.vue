<script setup lang="ts">
import type { TextItemLayout } from '@/stores/editor/types'

const padding = defineModel<number>('padding', { default: 10 })
const fontSize = defineModel<number>('fontSize', { default: 18 })
const textItemLayout = defineModel<TextItemLayout>('textItemLayout', { default: 'side-by-side' })
const showComments = defineModel<boolean>('showComments', { default: false })

const layoutTabItems = [
  { label: 'Side by side', value: 'side-by-side' as const, icon: 'i-lucide-columns-2' },
  { label: 'Vertical', value: 'vertical' as const, icon: 'i-lucide-rows-2' }
]
</script>

<template>
  <div class="p-4 flex flex-col gap-4">
    <div>
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

    <div>
      <span class="text-sm font-medium mb-2 block">Item Layout</span>
      <UTabs v-model="textItemLayout" :items="layoutTabItems" />
    </div>

    <div class="flex items-center justify-between gap-3">
      <div class="min-w-0">
        <span class="text-sm font-medium block">Show Comments</span>
        <span class="text-xs text-muted">
          Show element comments above the transcription when metadata comments are available.
        </span>
      </div>
      <USwitch v-model="showComments" />
    </div>

  </div>
</template>
