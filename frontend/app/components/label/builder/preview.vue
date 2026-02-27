<script setup lang="ts">
import { buildTextRegionCustomPreview } from '@/utils/editor/page-label-mapping'

const { activeLabel } = useLabelBuilder()
</script>

<template>
  <div data-tour="label-builder-preview" class="w-full lg:w-96 bg-neutral-50 dark:bg-neutral-950 border-l border-neutral-200 dark:border-neutral-800 p-8 flex flex-col items-center justify-center relative">
    <div class="absolute top-4 left-4 text-xs font-bold text-neutral-500 tracking-widest">
      Live Preview
    </div>

    <div class="relative w-64 h-80 bg-neutral-200/20 dark:bg-neutral-800 rounded-sm shadow-2xl border border-neutral-300 dark:border-neutral-700 overflow-hidden flex items-center justify-center">
      <div class="absolute inset-0 opacity-20" style="background-image: radial-gradient(#4b5563 1px, transparent 1px); background-size: 10px 10px;" />

      <div v-if="activeLabel.scope === 'region'" class="absolute left-6 right-6 top-20 bottom-20 rounded-sm border-2 flex items-start justify-start" :style="{ backgroundColor: activeLabel.color + '33', borderColor: activeLabel.color }">
        <div class="px-2 py-0.5 text-[10px] font-bold text-white shadow-sm flex items-center gap-1 absolute -top-3 left-2 rounded" :style="{ backgroundColor: activeLabel.color }">
          {{ activeLabel.name || 'Untitled' }}
        </div>
        <div v-if="activeLabel.scope === 'region' && activeLabel.mapping.pageXml.regionType === 'TextRegion'" class="w-full p-2 space-y-2 opacity-50">
          <div class="h-2 bg-current rounded-sm w-3/4" :style="{ color: activeLabel.color }" />
        </div>
      </div>

      <div v-else class="absolute left-4 right-4 top-1/2 -translate-y-1/2 h-8 rounded-sm bg-blue-400/10 border-b-2 flex items-end pb-1 px-2" :style="{ borderColor: activeLabel.color }">
        <div class="text-[10px] font-mono opacity-80" :style="{ color: activeLabel.color }">
          Mock Text Line...
        </div>
        <div class="absolute -top-3 left-0 px-1.5 py-0.5 text-[8px] font-bold text-white rounded" :style="{ backgroundColor: activeLabel.color }">
          {{ activeLabel.name }}
        </div>
      </div>
    </div>

    <div class="mt-8 text-center space-y-2 w-full px-6">
      <h4 class="text-black dark:text-white font-bold mb-1">
        Visual Representation
      </h4>

      <div class="text-[10px] font-mono text-left bg-neutral-100 dark:bg-neutral-900 p-3 rounded-sm border border-neutral-700 text-neutral-400 overflow-x-auto">
        <div v-if="activeLabel.scope === 'region'">
          <span class="text-emerald-700 dark:text-emerald-400">&lt;{{ activeLabel.mapping.pageXml.regionType }}</span>
          <span v-if="activeLabel.mapping.pageXml.textType && activeLabel.mapping.pageXml.textType !== 'custom'"> type="{{ activeLabel.mapping.pageXml.textType }}"</span>
          <span v-if="activeLabel.mapping.pageXml.textType === 'custom'"> type="other" custom="{{ buildTextRegionCustomPreview(activeLabel.mapping.pageXml) || '' }}"</span>
          <br>
          <span class="text-primary-700 dark:text-primary-400">&lt;{{ activeLabel.mapping.altoXml.blockType }}</span>
          <span> {{ activeLabel.mapping.altoXml.role }}="{{ activeLabel.mapping.altoXml.tag }}"</span>&gt;
        </div>
        <div v-else>
          <span class="text-emerald-700 dark:text-emerald-400">&lt;TextLine</span> custom="{{ activeLabel.mapping.pageXml.customKey }} { {{ activeLabel.mapping.pageXml.customData }}; }"<br>
          <span class="text-primary-600 dark:text-primary-400">&lt;TextLine</span> {{ activeLabel.mapping.altoXml.role }}="{{ activeLabel.mapping.altoXml.tag }}"&gt;
        </div>
      </div>
    </div>
  </div>
</template>
