<script setup lang="ts">
import type { TabPartInitParameters } from 'dockview-core'
import { useCollaborationPageSummary } from '@/composables/use-collaboration-page-summary'
import { useEditorCloseRequests } from '@/composables/use-editor-close-requests'
import { useEditorStore } from '@/stores/editor/editor.store'
import { parsePagePanelId } from '@/stores/editor/editor.keys'

const props = defineProps<{ params: TabPartInitParameters }>()

const editorStore = useEditorStore()
const pageSummaries = useCollaborationPageSummary()
const closeRequests = useEditorCloseRequests()

const title = ref(props.params.api.title ?? '')
const parsedPanelId = computed(() => parsePagePanelId(props.params.api?.id ?? ''))
const projectId = computed(() => {
  const fromParams = (props.params.params as { projectId?: string } | undefined)?.projectId ?? null
  return fromParams ?? parsedPanelId.value?.projectId ?? null
})
const pageId = computed(() => {
  const fromParams = (props.params.params as { pageId?: string } | undefined)?.pageId ?? null
  if (fromParams) return fromParams
  return parsedPanelId.value?.pageId ?? null
})
const hasUnsavedChanges = computed(() => {
  const id = pageId.value
  if (!id) return false
  return editorStore.hasUnsavedChangesForPage(id, projectId.value ?? undefined)
})
const collaborationSummary = computed(() => {
  const id = pageId.value
  if (!id) return null
  return pageSummaries.getPageSummary(id, projectId.value)
})

const collaborationDotTitle = computed(() => {
  const summary = collaborationSummary.value
  if (!summary) return ''

  if (summary.editor) {
    const activity = summary.isLive ? 'live' : 'idle'
    return `${summary.editor.user.displayName} is editing (${activity})`
  }

  if (summary.viewerCount > 0) {
    return `${summary.viewerCount} viewer${summary.viewerCount === 1 ? '' : 's'} on this page`
  }

  return 'Collaboration active on this page'
})

const collaborationDotClass = computed(() => {
  const summary = collaborationSummary.value
  if (!summary) return ''
  if (summary.editor?.user.id) {
    return summary.isLive ? 'bg-sky-400' : 'bg-neutral-400'
  }
  return 'bg-sky-400'
})

let titleDisposable: { dispose: () => void } | null = null

onMounted(() => {
  title.value = props.params.api.title ?? ''
  if (projectId.value) {
    void pageSummaries.ensureProjectSummary(projectId.value)
  }
  titleDisposable = props.params.api.onDidTitleChange((event) => {
    title.value = event.title ?? ''
  })
})

watch(projectId, (value) => {
  if (value) {
    void pageSummaries.ensureProjectSummary(value)
  }
}, { immediate: true })

onUnmounted(() => {
  titleDisposable?.dispose()
  titleDisposable = null
})

function requestClose(ev: MouseEvent) {
  ev.preventDefault()
  ev.stopPropagation()
  closeRequests.emit({
    panelApi: props.params.api,
    projectId: projectId.value,
    pageId: pageId.value
  })
}
</script>

<template>
  <div class="dv-default-tab">
    <div class="dv-default-tab-content">
      <span class="truncate">{{ title }}</span>
      <span v-if="hasUnsavedChanges" class="ml-2 inline-flex h-1.5 w-1.5 rounded-full bg-amber-400" />
      <span
        v-if="collaborationSummary"
        :class="['ml-2 inline-flex h-1.5 w-1.5 rounded-full', collaborationDotClass]"
        :title="collaborationDotTitle"
      />
    </div>
    <div class="dv-default-tab-action" @pointerdown.prevent @click="requestClose">
      <Icon name="i-lucide-x" class="h-3.5 w-3.5" />
    </div>
  </div>
</template>
