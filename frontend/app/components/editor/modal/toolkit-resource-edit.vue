<script setup lang="ts">
import { useBlockEditorCanvasInteractions } from '@/composables/editor/use-canvas-interaction-blocker'

const props = defineProps<{
  title: string
  src: string
  onSaved?: () => void | Promise<void>
}>()

const emit = defineEmits<{
  close: [result: boolean]
}>()

const isOpen = ref(true)

useBlockEditorCanvasInteractions(isOpen)

function isToolkitResourceSavedMessage(data: unknown): data is { type: 'larex:toolkit-resource-saved' } {
  return typeof data === 'object'
    && data !== null
    && 'type' in data
    && data.type === 'larex:toolkit-resource-saved'
}

function handleMessage(event: MessageEvent) {
  if (event.origin !== window.location.origin) return
  if (isToolkitResourceSavedMessage(event.data)) {
    void props.onSaved?.()
  }
}

onMounted(() => {
  window.addEventListener('message', handleMessage)
})

onBeforeUnmount(() => {
  window.removeEventListener('message', handleMessage)
})

function close() {
  emit('close', true)
  isOpen.value = false
}
</script>

<template>
  <UModal
    v-model:open="isOpen"
    :title="props.title"
    :ui="{
      content: 'sm:max-w-[min(98vw,104rem)]',
      body: 'p-0 sm:p-0'
    }"
    @close="close"
  >
    <template #body>
      <iframe
        :src="props.src"
        :title="props.title"
        class="h-[min(88vh,72rem)] w-full rounded-sm border border-default bg-default"
      />
    </template>
  </UModal>
</template>
