<script setup lang="ts">
import { useBlockEditorCanvasInteractions } from '@/composables/editor/use-canvas-interaction-blocker'
import type { ActionRunImpact } from '@/types/action'

const props = defineProps<{
  pages: ActionRunImpact['skippedPages']
}>()

const emit = defineEmits<{
  close: []
}>()

useBlockEditorCanvasInteractions()

const title = computed(() => `${props.pages.length} page${props.pages.length === 1 ? '' : 's'} skipped`)
</script>

<template>
  <UModal
    :title="title"
    description="These pages were excluded from the action for the reasons listed below."
    :ui="{ content: 'max-w-2xl max-h-[min(80dvh,40rem)] sm:max-h-[min(80dvh,40rem)]', wrapper: 'min-w-0 pe-8', body: 'overscroll-contain', footer: 'justify-end' }"
    @update:open="open => { if (!open) emit('close') }"
  >
    <template #body>
      <ul class="divide-y divide-default">
        <li v-for="page in pages" :key="page.pageId" class="space-y-1 py-3 first:pt-0 last:pb-0">
          <p class="text-sm font-medium text-highlighted wrap-anywhere">
            {{ page.name || page.pageId }}
          </p>
          <p class="text-sm text-muted whitespace-pre-wrap wrap-anywhere">
            {{ page.reason }}
          </p>
        </li>
      </ul>
    </template>

    <template #footer>
      <UButton
        label="Close"
        color="neutral"
        variant="outline"
        @click="emit('close')"
      />
    </template>
  </UModal>
</template>
