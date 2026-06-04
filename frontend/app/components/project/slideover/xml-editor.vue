<script setup lang="ts">
import { Compartment, EditorState } from '@codemirror/state'
import { EditorView, keymap } from '@codemirror/view'
import { xml } from '@codemirror/lang-xml'
import { basicSetup } from 'codemirror'
import { lintGutter, setDiagnostics } from '@codemirror/lint'
import { closeSearchPanel, highlightSelectionMatches, openSearchPanel, search, searchKeymap, searchPanelOpen } from '@codemirror/search'
import { useOverlayDialogs } from '@/composables/editor/use-overlay-dialogs'

type XmlValidationResult = {
  valid: boolean
  errors: XmlValidationError[]
  pageVersion: string | null
  namespace: string | null
}

type XmlTextResponse = {
  xmlId: string
  schema: string
  xml: string
  validation: XmlValidationResult
}

type FetchErrorShape = {
  statusCode?: number
  message?: string
  data?: {
    message?: string
    valid?: boolean
    errors?: XmlValidationError[]
    pageVersion?: string | null
    namespace?: string | null
  }
}

const props = defineProps<{
  projectId: string
  pageId: string
  xmlId: string
  xmlBasePath?: string
  pageName?: string
  readOnly?: boolean
  readOnlyMessage?: string
}>()

const emit = defineEmits<{
  close: [result: 'saved' | 'closed']
}>()

const toast = useToast()
const { confirm } = useOverlayDialogs()
const colorMode = useColorMode()

const loading = ref(true)
const saving = ref(false)
const validating = ref(false)
const validation = ref<XmlValidationResult | null>(null)
const initialXml = ref('')
const currentXml = ref('')

const editorHost = ref<HTMLElement | null>(null)
const searchAreaHost = ref<HTMLElement | null>(null)
let editorView: EditorView | null = null
let latestValidationRequestId = 0
let skipChangeValidation = false
const themeCompartment = new Compartment()
const xmlSearchKeymap = keymap.of([
  { key: 'Mod-h', run: openSearchPanel },
  ...searchKeymap
])

const isDirty = computed(() => currentXml.value !== initialXml.value)
const isReadOnly = computed(() => Boolean(props.readOnly))
const title = computed(() => isReadOnly.value ? 'View PAGE XML' : 'View/Edit PAGE XML')
const canSave = computed(() => !isReadOnly.value && isDirty.value && validation.value?.valid === true && !saving.value)
const errorCount = computed(() => validation.value?.errors?.length ?? 0)
const resolvedXmlBasePath = computed(() => {
  if (props.xmlBasePath?.trim()) return props.xmlBasePath
  return `/api/projects/${props.projectId}/pages/${props.pageId}/xml`
})

const validateDebounced = useDebounceFn(async (xmlText: string) => {
  await validateXml(xmlText)
}, 350)

onClickOutside(searchAreaHost, () => {
  closeFindReplacePanel()
})

onMounted(async () => {
  await loadXml()
})

watch(() => colorMode.value, () => {
  if (!editorView) return
  editorView.dispatch({
    effects: themeCompartment.reconfigure(buildThemeExtension())
  })
})

onBeforeUnmount(() => {
  editorView?.destroy()
  editorView = null
})

async function loadXml() {
  loading.value = true
  try {
    const response = await $fetch<XmlTextResponse>(
      `${resolvedXmlBasePath.value}/${props.xmlId}/text`
    )
    initialXml.value = response.xml
    currentXml.value = response.xml
    validation.value = response.validation
  } catch (error: unknown) {
    const err = error as FetchErrorShape
    toast.add({
      title: 'Failed to load XML',
      description: err.data?.message || err.message || 'Could not load XML content.',
      color: 'error'
    })
    emit('close', 'closed')
  } finally {
    loading.value = false
    await nextTick()
    createEditor(currentXml.value)
    if (validation.value) {
      applyDiagnostics(validation.value)
    }
  }
}

function createEditor(content: string) {
  if (!editorHost.value) return

  editorView?.destroy()
  currentXml.value = content
  editorView = new EditorView({
    state: EditorState.create({
      doc: content,
      extensions: [
        themeCompartment.of(buildThemeExtension()),
        basicSetup,
        search({ top: true }),
        highlightSelectionMatches(),
        xmlSearchKeymap,
        xml(),
        lintGutter(),
        EditorState.readOnly.of(isReadOnly.value),
        EditorView.editable.of(!isReadOnly.value),
        EditorView.lineWrapping,
        EditorView.updateListener.of((update) => {
          if (!update.docChanged) return
          currentXml.value = update.state.doc.toString()
          if (skipChangeValidation) return
          void validateDebounced(currentXml.value)
        })
      ]
    }),
    parent: editorHost.value
  })
}

function getEditorXml() {
  return editorView?.state.doc.toString() ?? currentXml.value
}

function buildThemeExtension() {
  return buildXmlEditorTheme(colorMode.value === 'dark')
}

function toggleFindReplacePanel() {
  if (!editorView) return
  if (searchPanelOpen(editorView.state)) {
    closeSearchPanel(editorView)
    return
  }
  openSearchPanel(editorView)
  editorView.focus()
}

function closeFindReplacePanel() {
  if (!editorView || !searchPanelOpen(editorView.state)) return
  closeSearchPanel(editorView)
}

async function validateXml(xmlText: string) {
  const requestId = ++latestValidationRequestId
  validating.value = true
  try {
    const result = await $fetch<XmlValidationResult>(
      `${resolvedXmlBasePath.value}/${props.xmlId}/validate`,
      {
        method: 'POST',
        body: { xml: xmlText }
      }
    )
    if (requestId !== latestValidationRequestId) return
    validation.value = result
    applyDiagnostics(result)
  } catch (error: unknown) {
    const err = error as FetchErrorShape
    if (requestId !== latestValidationRequestId) return
    const fallback: XmlValidationResult = {
      valid: false,
      errors: [{
        line: 1,
        column: 1,
        severity: 'error',
        code: 'VALIDATION_REQUEST_FAILED',
        message: err.data?.message || err.message || 'Validation request failed.'
      }],
      pageVersion: validation.value?.pageVersion ?? null,
      namespace: validation.value?.namespace ?? null
    }
    validation.value = fallback
    applyDiagnostics(fallback)
  } finally {
    if (requestId === latestValidationRequestId) {
      validating.value = false
    }
  }
}

function applyDiagnostics(result: XmlValidationResult) {
  if (!editorView) return
  const diagnostics = toCodeMirrorDiagnostics(editorView.state.doc.toString(), result.errors ?? [])
  editorView.dispatch(setDiagnostics(editorView.state, diagnostics))
}

async function saveXml() {
  if (!editorView || !canSave.value) return

  saving.value = true
  try {
    const xmlText = getEditorXml()
    await $fetch(`${resolvedXmlBasePath.value}/${props.xmlId}/text`, {
      method: 'PUT',
      body: { xml: xmlText }
    })

    skipChangeValidation = true
    initialXml.value = xmlText
    currentXml.value = xmlText
    skipChangeValidation = false

    toast.add({
      title: 'XML saved',
      description: 'PAGE XML was validated and saved successfully.',
      color: 'success'
    })
    emit('close', 'saved')
  } catch (error: unknown) {
    const err = error as FetchErrorShape
    if (err.statusCode === 422 && err.data?.valid === false && Array.isArray(err.data.errors)) {
      const result: XmlValidationResult = {
        valid: false,
        errors: err.data.errors,
        pageVersion: err.data.pageVersion ?? null,
        namespace: err.data.namespace ?? null
      }
      validation.value = result
      applyDiagnostics(result)
      toast.add({
        title: 'Save blocked',
        description: 'XML is invalid against PAGE XSD. Fix validation errors and try again.',
        color: 'warning'
      })
      return
    }

    toast.add({
      title: 'Save failed',
      description: err.statusCode === 403
        ? 'You do not have permission to edit this XML.'
        : (err.data?.message || err.message || 'Could not save XML.'),
      color: 'error'
    })
  } finally {
    saving.value = false
  }
}

async function closeWithGuard() {
  currentXml.value = getEditorXml()
  if (isDirty.value) {
    const confirmed = await confirm({
      title: 'Discard unsaved XML changes?',
      message: 'Your raw XML edits are not saved yet.',
      confirmLabel: 'Discard',
      confirmColor: 'warning',
      confirmIcon: 'i-lucide-triangle-alert'
    })
    if (!confirmed) return
  }
  emit('close', 'closed')
}
</script>

<template>
  <UiResponsiveSlideover
    side="right"
    :ui="{ content: 'max-w-7/8' }"
    :close="{ onClick: closeWithGuard }"
  >
    <template #header>
      <UiSlideoverHeader :title="title" icon="i-lucide-file-pen-line" />
    </template>

    <template #body>
      <div v-if="loading" class="flex flex-col gap-3">
        <USkeleton class="h-10 w-full" />
        <USkeleton class="h-[60vh] w-full" />
      </div>

      <div v-else class="flex flex-col gap-3">
        <div class="flex items-center justify-between gap-2 rounded-sm border border-default p-2">
          <div class="flex items-center gap-2 text-xs text-muted">
            <span class="truncate">{{ pageName || pageId }}</span>
            <span>•</span>
            <span>{{ xmlId }}</span>
            <span v-if="validation?.pageVersion">• PAGE {{ validation.pageVersion }}</span>
          </div>
          <div class="flex items-center gap-2">
            <UBadge
              :color="validation?.valid ? 'success' : 'error'"
              variant="subtle"
              size="sm"
            >
              {{ validation?.valid ? 'Valid' : 'Invalid' }}
            </UBadge>
            <UBadge
              v-if="validating"
              color="neutral"
              variant="subtle"
              size="sm"
            >
              Validating...
            </UBadge>
          </div>
        </div>

        <UAlert
          v-if="isReadOnly"
          color="neutral"
          variant="subtle"
          icon="i-lucide-eye"
          :title="props.readOnlyMessage || 'This XML is currently view-only.'"
        />

        <div ref="searchAreaHost" class="flex flex-col gap-3">
          <div class="flex items-center justify-between gap-2 rounded-sm border border-default p-2">
            <div class="flex items-center gap-2">
              <UButton
                color="neutral"
                variant="subtle"
                icon="i-lucide-search"
                size="sm"
                @click="toggleFindReplacePanel"
              >
                Find / Replace
              </UButton>
            </div>
            <p class="text-xs text-muted">
              Shortcuts: <span class="font-mono">Ctrl/Cmd+F</span>, <span class="font-mono">Ctrl/Cmd+H</span>
            </p>
          </div>

          <ClientOnly>
            <div ref="editorHost" class="xml-editor-host rounded-sm border border-default" />
          </ClientOnly>
        </div>

        <div class="rounded-sm border border-default p-2">
          <p class="text-xs font-medium mb-1">
            Validation Diagnostics ({{ errorCount }})
          </p>
          <p v-if="!errorCount" class="text-xs text-muted">
            No validation errors.
          </p>
          <ul v-else class="space-y-1 max-h-36 overflow-auto">
            <li
              v-for="(err, index) in validation?.errors ?? []"
              :key="`${err.code}-${err.line}-${err.column}-${index}`"
              class="text-xs"
            >
              <span class="font-medium">{{ err.code }}</span>
              <span class="text-muted"> at {{ err.line }}:{{ err.column }}</span>
              <span> - {{ err.message }}</span>
            </li>
          </ul>
        </div>
      </div>
    </template>

    <template #footer>
      <div class="flex items-center justify-end gap-2">
        <UButton
          color="neutral"
          variant="ghost"
          @click="closeWithGuard"
        >
          Close
        </UButton>
        <UButton
          v-if="!isReadOnly"
          color="primary"
          icon="i-lucide-save"
          :loading="saving"
          :disabled="!canSave"
          @click="saveXml"
        >
          Save XML
        </UButton>
      </div>
    </template>
  </UiResponsiveSlideover>
</template>

<style scoped>
.xml-editor-host {
  height: 60vh;
  overflow: hidden;
}

.xml-editor-host :deep(.cm-editor) {
  height: 100%;
  font-size: 13px;
}

.xml-editor-host :deep(.cm-scroller) {
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, "Liberation Mono", "Courier New", monospace;
}
</style>
