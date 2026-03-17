<script setup lang="ts">
import { useEditorSessionStore } from '@/stores/editor/editor.session.store'

const props = defineProps<{ canvasId?: string | null }>()

const sessionStore = useEditorSessionStore()

const textViewMode = computed(() => sessionStore.textViewSettings.mode)
</script>

<template>
  <div class="h-full flex flex-col bg-background">
    <EditorTextLineList v-if="textViewMode === 'textline'" :canvas-id="props.canvasId" class="h-full w-full" />
    <EditorTextRegionList v-else :canvas-id="props.canvasId" class="h-full w-full" />
  </div>
</template>
