<script setup lang="ts">
import { LazyProjectSlideoverXmlEditor } from '#components'
import { useOverlayDialogs } from '@/composables/editor/use-overlay-dialogs'
import { extractApiErrorMessage } from '@/utils/api-error'
import { useEditorSessionStore } from '@/stores/editor/editor.session.store'

const props = defineProps<{ canvasId: string }>()
const editorStore = useEditorStore()
const sessionStore = useEditorSessionStore()
const { capabilities } = useWorkspaceCapabilities(computed(() => sessionStore.workspaceId))
const { confirm } = useOverlayDialogs()
const toast = useToast()
const overlay = useOverlay()
const xmlEditor = overlay.create(LazyProjectSlideoverXmlEditor)
const busy = ref(false)
const canvas = computed(() => editorStore.canvases[props.canvasId])
const failure = computed(() => canvas.value?.annotationLoadError)
const page = computed(() => editorStore.getPage(canvas.value?.pageId ?? '', canvas.value?.projectId ?? undefined))
const readOnly = computed(() => Boolean(page.value?.locked || canvas.value?.comparison?.readOnly))
const canDiscard = computed(() => failure.value?.invalidXml
  && !readOnly.value
  && canvas.value?.annotationContext?.mode === 'PROJECT'
  && capabilities.value.canManageProjects)

async function retry() {
  const current = canvas.value
  if (!current?.projectId || !current.pageId) return
  busy.value = true
  try {
    editorStore.invalidateAnnotationCache(current.pageId, current.projectId)
    await editorStore.loadPageIntoCanvas(props.canvasId, current.projectId, current.pageId, current.imageVariantId ?? undefined, {
      awaitAnnotations: true, refreshPageData: true
    })
  } finally {
    busy.value = false
  }
}

async function repair() {
  const current = canvas.value
  const error = failure.value
  if (!current?.projectId || !current.pageId || !error?.xmlId) return
  const basePath = current.annotationContext?.basePath ?? `/api/projects/${current.projectId}/pages/${current.pageId}/annotations`
  const instance = xmlEditor.open({
    projectId: current.projectId,
    pageId: current.pageId,
    xmlId: error.xmlId,
    xmlBasePath: basePath.replace(/\/annotations$/, '/xml'),
    pageName: page.value?.label,
    readOnly: readOnly.value
  })
  if (await instance.result === 'saved') await retry()
}

async function discard() {
  const current = canvas.value
  if (!canDiscard.value || !current?.projectId || !current.pageId) return
  const accepted = await confirm({
    title: 'Start a fresh annotation?',
    message: `Discard the saved XML and its version history for "${page.value?.label ?? current.pageId}"? The page images will be kept. This cannot be undone.`,
    confirmLabel: 'Discard and start fresh',
    confirmColor: 'error'
  })
  if (!accepted) return
  busy.value = true
  try {
    const result = await $fetch<{ deletedAnnotationCount: number }>(`/api/projects/${current.projectId}/pages/annotations/batch`, {
      method: 'DELETE', body: [current.pageId]
    })
    if (result.deletedAnnotationCount !== 1) throw new Error('The annotations could not be discarded. The page may be locked or your permissions may have changed.')
    editorStore.invalidateAnnotationCache(current.pageId, current.projectId)
    await retry()
  } catch (error) {
    toast.add({ title: 'Could not start a fresh annotation', description: extractApiErrorMessage(error, 'Please try again.'), color: 'error' })
  } finally {
    busy.value = false
  }
}
</script>

<template>
  <div class="relative flex h-full w-full items-center justify-center overflow-auto bg-default p-6">
    <img
      v-if="canvas?.imageSrc"
      :src="canvas.imageSrc"
      alt="Page preview"
      class="pointer-events-none absolute inset-0 h-full w-full object-contain opacity-15"
    >
    <div class="relative w-full max-w-xl rounded-lg border border-default bg-default p-6 shadow-lg" role="alert">
      <div class="mb-3 flex items-center gap-2 text-highlighted">
        <UIcon name="i-lucide-file-warning" class="size-5 text-warning" />
        <h2 class="text-base font-semibold">
          {{ failure?.invalidXml ? 'Invalid PAGE XML' : 'Annotations could not be opened' }}
        </h2>
      </div>
      <p class="text-sm text-muted">
        {{ failure?.message }}
      </p>
      <p class="mt-2 text-sm text-muted">
        Editing is paused until the saved annotations can be opened.
      </p>
      <ul v-if="failure?.errors.length" class="mt-4 max-h-52 space-y-2 overflow-auto rounded-md bg-elevated p-3 text-sm">
        <li v-for="(error, index) in failure.errors" :key="index">
          <span class="font-medium">Line {{ error.line }}, column {{ error.column }}:</span> {{ error.message }}
        </li>
      </ul>
      <div class="mt-5 flex flex-wrap gap-2">
        <UButton
          v-if="failure?.xmlId"
          icon="i-lucide-code-xml"
          :disabled="busy"
          @click="repair"
        >
          {{ readOnly ? 'View XML' : 'Fix in XML editor' }}
        </UButton>
        <UButton
          color="neutral"
          variant="outline"
          icon="i-lucide-refresh-cw"
          :loading="busy"
          @click="retry"
        >
          Try again
        </UButton>
        <UButton
          v-if="canDiscard"
          color="error"
          variant="soft"
          :disabled="busy"
          @click="discard"
        >
          Discard and start fresh
        </UButton>
      </div>
    </div>
  </div>
</template>
