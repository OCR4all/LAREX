<script setup lang="ts">
import type { TabPartInitParameters } from 'dockview-core'
import { parseProjectPanelId } from '@/stores/editor/editor.keys'

const props = defineProps<{ params: TabPartInitParameters }>()

const closeRequests = useEditorCloseRequests()

const title = ref(props.params.api.title ?? '')
const projectId = computed(() => parseProjectPanelId(props.params.api?.id ?? ''))

let titleDisposable: { dispose: () => void } | null = null

onMounted(() => {
  title.value = props.params.api.title ?? ''
  titleDisposable = props.params.api.onDidTitleChange((event) => {
    title.value = event.title ?? ''
  })
})

onUnmounted(() => {
  titleDisposable?.dispose()
  titleDisposable = null
})

function requestClose(ev: MouseEvent) {
  ev.preventDefault()
  ev.stopPropagation()
  closeRequests.emit({
    panelApi: props.params.api,
    projectId: projectId.value
  })
}
</script>

<template>
  <div class="dv-default-tab">
    <div class="dv-default-tab-content">
      <Icon name="i-lucide-folder" class="size-3.5 shrink-0 opacity-70" />
      <span class="truncate" :title="title">{{ title }}</span>
    </div>
    <button
      type="button"
      class="dv-default-tab-action"
      :aria-label="`Close project ${title}`"
      :title="`Close project ${title}`"
      @pointerdown.prevent.stop
      @keydown.stop
      @click="requestClose"
    >
      <Icon name="i-lucide-x" class="h-3.5 w-3.5" />
    </button>
  </div>
</template>
