<script setup lang="ts">
import { serializePageXmlRegionStartTag } from '@/utils/editor/page-label-mapping'

const { activeLabel } = useLabelBuilder()
const currentLabel = computed(() => activeLabel.value)
const pageXmlPreview = computed(() => serializePageXmlRegionStartTag(currentLabel.value?.mapping.pageXml))
</script>

<template>
  <div data-tour="label-builder-preview" class="flex w-full flex-col border-t border-default bg-muted/20 p-6 lg:w-96 lg:shrink-0 lg:border-l lg:border-t-0">
    <div>
      <h2 class="text-sm font-semibold text-highlighted">
        Preview
      </h2>
      <p class="mt-1 text-xs text-muted">
        Editor appearance and PAGE XML output.
      </p>
    </div>

    <div class="flex flex-1 flex-col items-center justify-center py-6">
      <div class="relative aspect-3/4 w-full overflow-hidden rounded-lg border border-default bg-default shadow-sm">
        <div class="absolute inset-0 opacity-15" style="background-image: radial-gradient(#64748b 1px, transparent 1px); background-size: 12px 12px;" />

        <template v-if="currentLabel">
          <div class="absolute inset-x-6 bottom-16 top-16 rounded-md border-2" :style="{ backgroundColor: currentLabel.color + '20', borderColor: currentLabel.color }">
            <div class="absolute -top-3 left-2 flex items-center gap-1 rounded-md px-2 py-1 text-[10px] font-semibold text-white shadow-sm" :style="{ backgroundColor: currentLabel.color }">
              {{ currentLabel.name || 'Untitled' }}
            </div>
            <div v-if="currentLabel.mapping.pageXml.regionType === 'TextRegion'" class="space-y-2 p-3 opacity-50">
              <div class="h-1.5 w-3/4 rounded-full bg-current" :style="{ color: currentLabel.color }" />
              <div class="h-1.5 w-full rounded-full bg-current" :style="{ color: currentLabel.color }" />
              <div class="h-1.5 w-2/3 rounded-full bg-current" :style="{ color: currentLabel.color }" />
            </div>
          </div>
        </template>
        <div v-else class="absolute inset-0 flex flex-col items-center justify-center gap-2 text-muted">
          <UIcon name="i-lucide-mouse-pointer-2" class="size-5" />
          <span class="text-xs">Select a label</span>
        </div>
      </div>

      <div class="mt-6 w-full">
        <div class="mb-2 flex items-center gap-2">
          <UIcon name="i-lucide-code-xml" class="size-4 text-muted" />
          <h3 class="text-xs font-semibold text-highlighted">
            PAGE XML
          </h3>
        </div>

        <div v-if="currentLabel" class="min-w-0 rounded-md border border-default bg-default p-3 text-left font-mono text-xs leading-5 text-default">
          <div class="whitespace-normal break-words [overflow-wrap:anywhere]">
            {{ pageXmlPreview }}
          </div>
        </div>
        <div v-else class="rounded-md border border-dashed border-default px-3 py-5 text-center text-xs text-muted">
          No label selected
        </div>
      </div>
    </div>
  </div>
</template>
