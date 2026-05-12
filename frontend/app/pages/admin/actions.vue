<script setup lang="ts">
import { Compartment, EditorState } from '@codemirror/state'
import { EditorView, keymap } from '@codemirror/view'
import { yaml } from '@codemirror/lang-yaml'
import { basicSetup } from 'codemirror'
import { lintGutter, setDiagnostics, type Diagnostic } from '@codemirror/lint'
import { closeSearchPanel, highlightSelectionMatches, openSearchPanel, search, searchKeymap, searchPanelOpen } from '@codemirror/search'
import { LazyUiConfirmSlideover } from '#components'
import {
  DEFAULT_ACTION_YAML,
  type ActionDefinitionResponse,
  type ActionValidationDiagnostic,
  type ActionValidationResponse,
  type ActionWorkspaceAvailability,
  type AdminActionRun,
  type ClearActionRunsResponse
} from '@/types/action'
import { extractApiErrorMessage } from '@/utils/api-error'
import { generateRandomActionSlug } from '@/utils/random-action-slug'

definePageMeta({ layout: 'admin', middleware: 'admin' })

type AdminWorkspace = {
  id: string
  name: string
}

const toast = useToast()
const colorMode = useColorMode()
const overlay = useOverlay()
const confirmSlideover = overlay.create(LazyUiConfirmSlideover)

const { data: definitions, pending, refresh } = await useFetch<ActionDefinitionResponse[]>('/api/admin/actions/processors', {
  key: globalKey('admin', 'actions', 'processors'),
  default: () => []
})

const { data: workspaces } = await useFetch<AdminWorkspace[]>('/api/admin/workspaces', {
  key: globalKey('admin', 'actions', 'workspaces'),
  default: () => []
})

const selectedId = ref<string | null>(null)
const draftDefinition = ref<ActionDefinitionResponse | null>(null)
const editorHost = ref<HTMLElement | null>(null)
const fileInput = ref<HTMLInputElement | null>(null)
const validating = ref(false)
const saving = ref(false)
const deleting = ref(false)
const loadingAvailability = ref(false)
const assigningAvailability = ref(false)
const loadingRuns = ref(false)
const clearingRuns = ref(false)
const diagnostics = ref<ActionValidationDiagnostic[]>([])
const validation = ref<ActionValidationResponse | null>(null)
const initialYaml = ref(DEFAULT_ACTION_YAML)
const editorContent = ref(DEFAULT_ACTION_YAML)
const workflowFilter = ref('')
const selectedAvailabilityWorkspaceIds = ref<string[]>([])
const workspaceAvailability = ref<ActionWorkspaceAvailability[]>([])
const isRunsPanelVisible = ref(false)
const runs = ref<AdminActionRun[]>([])
const expandedRunIds = ref<string[]>([])

let editorView: EditorView | null = null
const themeCompartment = new Compartment()
const yamlSearchKeymap = keymap.of([
  { key: 'Mod-h', run: openSearchPanel },
  ...searchKeymap
])

const selectedPersistedDefinition = computed(() => definitions.value.find(definition => definition.id === selectedId.value) ?? null)
const isDraftSelected = computed(() => Boolean(draftDefinition.value && selectedId.value === draftDefinition.value.id))
const selectedDefinition = computed(() => selectedPersistedDefinition.value ?? (isDraftSelected.value ? draftDefinition.value : null))
const currentYaml = computed(() => editorContent.value)
const isDirty = computed(() => currentYaml.value !== initialYaml.value)
const canSave = computed(() => validation.value?.valid === true && !saving.value && !validating.value)
const workspaceById = computed(() => new Map(workspaces.value.map(workspace => [workspace.id, workspace.name])))
const assignedWorkspaceIds = computed(() => new Set(workspaceAvailability.value.map(availability => availability.workspaceId)))
const assignableWorkspaceOptions = computed(() => workspaces.value
  .filter(workspace => !assignedWorkspaceIds.value.has(workspace.id))
  .map(workspace => ({ label: workspace.name, value: workspace.id })))
const filteredDefinitions = computed(() => {
  const items = draftDefinition.value ? [draftDefinition.value, ...definitions.value] : definitions.value
  const needle = workflowFilter.value.trim().toLowerCase()
  if (!needle) return items
  return items.filter(definition =>
    definition.name.toLowerCase().includes(needle)
    || definition.processorKey.toLowerCase().includes(needle)
    || (definition.description ?? '').toLowerCase().includes(needle)
  )
})
const terminalRuns = computed(() => runs.value.filter(run => isTerminalRun(run.status)))
const activeRuns = computed(() => runs.value.filter(run => !isTerminalRun(run.status)))
const runPanelSummary = computed(() => {
  if (runs.value.length === 0) return 'No runs recorded for this Action.'
  return `${activeRuns.value.length} active, ${terminalRuns.value.length} completed`
})

onMounted(() => {
  const firstDefinition = definitions.value[0]
  if (firstDefinition) {
    selectDefinition(firstDefinition)
  } else {
    createNewDefinition()
  }
})

watch(() => colorMode.value, () => {
  editorView?.dispatch({
    effects: themeCompartment.reconfigure(buildThemeExtension())
  })
})

watch(definitions, (items) => {
  if (isDraftSelected.value || selectedPersistedDefinition.value) {
    return
  }
  const firstDefinition = items[0]
  if (firstDefinition) {
    selectDefinition(firstDefinition)
  }
})

watch(selectedId, async () => {
  selectedAvailabilityWorkspaceIds.value = []
  await loadWorkspaceAvailability()
  if (isRunsPanelVisible.value) {
    await loadRuns()
  }
})

onBeforeUnmount(() => {
  editorView?.destroy()
  editorView = null
})

function buildThemeExtension() {
  return buildXmlEditorTheme(colorMode.value === 'dark')
}

function createEditor(content: string) {
  if (!editorHost.value) return
  editorView?.destroy()
  editorContent.value = content
  editorView = new EditorView({
    state: EditorState.create({
      doc: content,
      extensions: [
        themeCompartment.of(buildThemeExtension()),
        basicSetup,
        search({ top: true }),
        highlightSelectionMatches(),
        yamlSearchKeymap,
        yaml(),
        lintGutter(),
        EditorView.lineWrapping,
        EditorView.updateListener.of((update) => {
          if (!update.docChanged) return
          editorContent.value = update.state.doc.toString()
          validation.value = null
          diagnostics.value = []
          applyDiagnostics([])
        })
      ]
    }),
    parent: editorHost.value
  })
}

function replaceEditorContent(content: string) {
  editorContent.value = content
  if (!editorView) {
    createEditor(content)
    return
  }
  editorView.dispatch({
    changes: {
      from: 0,
      to: editorView.state.doc.length,
      insert: content
    }
  })
  applyDiagnostics([])
}

function selectDefinition(definition: ActionDefinitionResponse) {
  selectedId.value = definition.id
  initialYaml.value = definition.yaml
  validation.value = null
  diagnostics.value = []
  expandedRunIds.value = []
  void nextTick(() => replaceEditorContent(definition.yaml))
}

function createNewDefinition() {
  const processorKey = generateRandomActionSlug(definitions.value.map(definition => definition.processorKey))
  const yaml = buildDraftActionYaml(processorKey)
  draftDefinition.value = {
    id: `draft:${processorKey}`,
    processorKey,
    name: processorKey,
    description: `Describe what ${processorKey} does.`,
    yaml,
    endpointUrl: 'http://processor:9000/dispatch',
    endpointTimeoutSeconds: 30,
    executeRole: 'CURATOR',
    lockMode: 'PAGES',
    acceptsImages: true,
    acceptsXml: true,
    outputsImages: false,
    outputsXml: true,
    enabled: true,
    global: false,
    created: new Date().toISOString(),
    updated: new Date().toISOString()
  }
  selectedId.value = draftDefinition.value.id
  initialYaml.value = yaml
  validation.value = null
  diagnostics.value = []
  workspaceAvailability.value = []
  runs.value = []
  expandedRunIds.value = []
  void nextTick(() => replaceEditorContent(yaml))
}

function buildDraftActionYaml(processorKey: string) {
  return `version: 1
id: ${processorKey}
name: ${processorKey}
description: Describe what ${processorKey} does.

endpoint:
  url: http://processor:9000/dispatch
  timeoutSeconds: 30
  auth:
    type: none

access:
  execute: CURATOR

locking:
  mode: PAGES

inputs:
  images: true
  xml: true

outputs:
  xml:
    enabled: true
    mode: upsert
  images:
    enabled: false
    variant: ${processorKey}
    mode: upsert

runtime:
  model:
    name: default
    optional: true

parameters:
`
}

async function validateYaml() {
  validating.value = true
  try {
    const result = await $fetch<ActionValidationResponse>('/api/admin/actions/processors/validate', {
      method: 'POST',
      body: { yaml: currentYaml.value },
      query: selectedPersistedDefinition.value ? { existingDefinitionId: selectedPersistedDefinition.value.id } : undefined
    })
    validation.value = result
    diagnostics.value = result.diagnostics ?? []
    applyDiagnostics(diagnostics.value)
    toast.add({
      title: result.valid ? 'Action YAML is valid' : 'Action YAML has issues',
      color: result.valid ? 'success' : 'warning',
      icon: result.valid ? 'i-lucide-check-circle' : 'i-lucide-triangle-alert'
    })
  } catch (error: unknown) {
    const message = error instanceof Error ? error.message : 'Validation failed.'
    diagnostics.value = [{
      path: '',
      line: 1,
      column: 1,
      severity: 'ERROR',
      code: 'VALIDATION_REQUEST_FAILED',
      message
    }]
    validation.value = { valid: false, diagnostics: diagnostics.value, preview: null }
    applyDiagnostics(diagnostics.value)
  } finally {
    validating.value = false
  }
}

async function saveDefinition() {
  await validateYaml()
  if (!canSave.value) return
  saving.value = true
  try {
    const saved = selectedPersistedDefinition.value
      ? await $fetch<ActionDefinitionResponse>(`/api/admin/actions/processors/${selectedPersistedDefinition.value.id}`, {
          method: 'PUT',
          body: { yaml: currentYaml.value }
        })
      : await $fetch<ActionDefinitionResponse>('/api/admin/actions/processors', {
          method: 'POST',
          body: { yaml: currentYaml.value }
        })
    await refresh()
    draftDefinition.value = null
    selectedId.value = saved.id
    initialYaml.value = saved.yaml
    validation.value = null
    diagnostics.value = []
    replaceEditorContent(saved.yaml)
    await loadWorkspaceAvailability()
    toast.add({ title: 'Action saved', color: 'success', icon: 'i-lucide-save' })
  } catch (error: unknown) {
    const message = error instanceof Error ? error.message : 'Could not save Action.'
    toast.add({ title: 'Save failed', description: message, color: 'error', icon: 'i-lucide-alert-circle' })
  } finally {
    saving.value = false
  }
}

async function loadWorkspaceAvailability() {
  if (!selectedPersistedDefinition.value) {
    workspaceAvailability.value = []
    return
  }
  loadingAvailability.value = true
  try {
    workspaceAvailability.value = await $fetch<ActionWorkspaceAvailability[]>(`/api/admin/actions/processors/${selectedPersistedDefinition.value.id}/workspace-availability`)
    selectedAvailabilityWorkspaceIds.value = selectedAvailabilityWorkspaceIds.value.filter(id =>
      assignableWorkspaceOptions.value.some(option => option.value === id)
    )
  } catch (error: unknown) {
    const message = error instanceof Error ? error.message : 'Could not load Action availability.'
    toast.add({ title: 'Availability load failed', description: message, color: 'error' })
  } finally {
    loadingAvailability.value = false
  }
}

async function assignSelectedWorkspaces() {
  if (!selectedPersistedDefinition.value || selectedAvailabilityWorkspaceIds.value.length === 0) return
  assigningAvailability.value = true
  try {
    await Promise.all(selectedAvailabilityWorkspaceIds.value.map(workspaceId =>
      $fetch(`/api/admin/actions/processors/${selectedPersistedDefinition.value!.id}/workspace-availability`, {
        method: 'POST',
        body: { workspaceId, enabled: true }
      })
    ))
    selectedAvailabilityWorkspaceIds.value = []
    await loadWorkspaceAvailability()
    toast.add({ title: 'Workspace availability updated', color: 'success', icon: 'i-lucide-check-circle' })
  } catch (error: unknown) {
    const message = error instanceof Error ? error.message : 'Could not update workspace availability.'
    toast.add({ title: 'Availability update failed', description: message, color: 'error' })
  } finally {
    assigningAvailability.value = false
  }
}

async function removeWorkspaceAvailability(availabilityId: string) {
  if (!selectedPersistedDefinition.value) return
  try {
    await $fetch(`/api/admin/actions/processors/${selectedPersistedDefinition.value.id}/workspace-availability/${availabilityId}`, {
      method: 'DELETE'
    })
    await loadWorkspaceAvailability()
    toast.add({ title: 'Workspace removed', color: 'success' })
  } catch (error: unknown) {
    const message = error instanceof Error ? error.message : 'Could not remove workspace availability.'
    toast.add({ title: 'Remove failed', description: message, color: 'error' })
  }
}

async function toggleDefinition(definition: ActionDefinitionResponse) {
  try {
    await $fetch(`/api/admin/actions/processors/${definition.id}/enabled`, {
      method: 'PUT',
      query: { enabled: !definition.enabled }
    })
    await refresh()
  } catch (error: unknown) {
    const message = error instanceof Error ? error.message : 'Could not update Action.'
    toast.add({ title: 'Update failed', description: message, color: 'error' })
  }
}

async function toggleGlobalDefinition(definition: ActionDefinitionResponse) {
  try {
    await $fetch(`/api/admin/actions/processors/${definition.id}/global`, {
      method: 'PUT',
      query: { global: !definition.global }
    })
    await refresh()
    toast.add({
      title: !definition.global ? 'Action is now global' : 'Action is workspace-scoped',
      color: 'success',
      icon: 'i-lucide-globe'
    })
  } catch (error: unknown) {
    const message = error instanceof Error ? error.message : 'Could not update global availability.'
    toast.add({ title: 'Global toggle failed', description: message, color: 'error' })
  }
}

async function deleteSelectedAction() {
  const definition = selectedDefinition.value
  if (!definition) return
  if (isDraftSelected.value) {
    const instance = confirmSlideover.open({
      title: 'Discard Draft Action?',
      message: `Discard unsaved Action "${definition.name}"?`,
      confirmLabel: 'Discard Draft',
      confirmColor: 'warning',
      confirmIcon: 'i-lucide-trash-2'
    })
    const confirmed = await instance.result
    if (!confirmed) return
    discardDraft()
    return
  }
  if (!selectedPersistedDefinition.value) return
  const instance = confirmSlideover.open({
    title: 'Delete Action?',
    message: `Delete Action "${selectedPersistedDefinition.value.name}"? Completed run history and assignments for this Action will also be removed. Active runs must be cancelled first.`,
    confirmLabel: 'Delete Action',
    confirmColor: 'error',
    confirmIcon: 'i-lucide-trash-2'
  })
  const confirmed = await instance.result
  if (!confirmed) return

  deleting.value = true
  try {
    const deletedId = selectedPersistedDefinition.value.id
    await $fetch(`/api/admin/actions/processors/${deletedId}`, { method: 'DELETE' })
    if (draftDefinition.value?.id === deletedId) {
      draftDefinition.value = null
    }
    await refresh()
    const nextDefinition = definitions.value.find(definition => definition.id !== deletedId) ?? null
    if (nextDefinition) {
      selectDefinition(nextDefinition)
    } else {
      createNewDefinition()
    }
    toast.add({ title: 'Action deleted', color: 'success', icon: 'i-lucide-trash-2' })
  } catch (error: unknown) {
    const message = extractApiErrorMessage(error, 'Could not delete Action.')
    if (message.toLowerCase().includes('active run')) {
      toast.add({
        title: 'Action has active runs',
        description: 'Cancel or wait for active runs before deleting this Action.',
        color: 'warning',
        icon: 'i-lucide-loader-circle'
      })
    } else {
      toast.add({ title: 'Delete failed', description: message, color: 'error', icon: 'i-lucide-alert-circle' })
    }
  } finally {
    deleting.value = false
  }
}

function discardDraft() {
  draftDefinition.value = null
  const firstDefinition = definitions.value[0]
  if (firstDefinition) {
    selectDefinition(firstDefinition)
    return
  }
  selectedId.value = null
  initialYaml.value = DEFAULT_ACTION_YAML
  validation.value = null
  diagnostics.value = []
  workspaceAvailability.value = []
  runs.value = []
  expandedRunIds.value = []
  void nextTick(() => replaceEditorContent(DEFAULT_ACTION_YAML))
}

async function toggleRunsPanel() {
  if (!selectedPersistedDefinition.value) return
  isRunsPanelVisible.value = !isRunsPanelVisible.value
  if (isRunsPanelVisible.value) {
    await loadRuns()
  }
}

async function loadRuns() {
  if (!selectedPersistedDefinition.value) {
    runs.value = []
    return
  }
  loadingRuns.value = true
  try {
    runs.value = await $fetch<AdminActionRun[]>(`/api/admin/actions/processors/${selectedPersistedDefinition.value.id}/runs`)
  } catch (error: unknown) {
    const message = error instanceof Error ? error.message : 'Could not load Action runs.'
    toast.add({ title: 'Run load failed', description: message, color: 'error' })
  } finally {
    loadingRuns.value = false
  }
}

async function clearTerminalRuns() {
  if (!selectedPersistedDefinition.value || terminalRuns.value.length === 0) return
  clearingRuns.value = true
  try {
    const result = await $fetch<ClearActionRunsResponse>(`/api/admin/actions/processors/${selectedPersistedDefinition.value.id}/runs/terminal`, {
      method: 'DELETE'
    })
    await loadRuns()
    toast.add({ title: `Cleared ${result.deletedCount} completed run${result.deletedCount === 1 ? '' : 's'}`, color: 'success' })
  } catch (error: unknown) {
    const message = error instanceof Error ? error.message : 'Could not clear completed runs.'
    toast.add({ title: 'Clear failed', description: message, color: 'error' })
  } finally {
    clearingRuns.value = false
  }
}

function openUpload() {
  fileInput.value?.click()
}

async function uploadYaml(event: Event) {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  if (!file) return
  const content = await file.text()
  createNewDefinition()
  replaceEditorContent(content)
  input.value = ''
}

function toggleFindReplacePanel() {
  if (!editorView) return
  if (searchPanelOpen(editorView.state)) {
    closeSearchPanel(editorView)
  } else {
    openSearchPanel(editorView)
    editorView.focus()
  }
}

function toggleRunExpanded(runId: string) {
  expandedRunIds.value = expandedRunIds.value.includes(runId)
    ? expandedRunIds.value.filter(id => id !== runId)
    : [...expandedRunIds.value, runId]
}

function isRunExpanded(runId: string) {
  return expandedRunIds.value.includes(runId)
}

function isTerminalRun(status: AdminActionRun['status']) {
  return status === 'COMPLETED' || status === 'FAILED' || status === 'CANCELLED'
}

function statusColor(status: AdminActionRun['status']): 'success' | 'error' | 'neutral' | 'warning' | 'primary' {
  if (status === 'COMPLETED') return 'success'
  if (status === 'FAILED') return 'error'
  if (status === 'CANCELLED') return 'neutral'
  if (status === 'CANCEL_REQUESTED') return 'warning'
  return 'primary'
}

function statusIcon(status: AdminActionRun['status']) {
  if (status === 'COMPLETED') return 'i-lucide-check-circle'
  if (status === 'FAILED') return 'i-lucide-x-circle'
  if (status === 'CANCELLED') return 'i-lucide-circle-slash'
  if (status === 'CANCEL_REQUESTED') return 'i-lucide-ban'
  return 'i-lucide-loader-circle'
}

function runStatusTextClass(status: AdminActionRun['status']) {
  if (status === 'COMPLETED') return 'text-success'
  if (status === 'FAILED') return 'text-error'
  if (status === 'CANCELLED') return 'text-muted'
  if (status === 'CANCEL_REQUESTED') return 'text-warning'
  return 'text-primary'
}

function formatDate(value: string | null) {
  if (!value) return 'Never'
  return new Intl.DateTimeFormat(undefined, {
    month: 'short',
    day: 'numeric',
    hour: '2-digit',
    minute: '2-digit'
  }).format(new Date(value))
}

function formatDuration(seconds: number | null) {
  if (seconds === null) return 'Running'
  if (seconds < 60) return `${seconds}s`
  const minutes = Math.floor(seconds / 60)
  const remainder = seconds % 60
  return `${minutes}m ${remainder}s`
}

function formatResultSummary(value: unknown) {
  if (value === null || value === undefined) return 'No result summary.'
  if (typeof value === 'string') return value
  return JSON.stringify(value, null, 2)
}

function applyDiagnostics(items: ActionValidationDiagnostic[]) {
  if (!editorView) return
  const mapped: Diagnostic[] = items.map((item) => {
    const from = lineColumnToOffset(editorView!.state.doc.toString(), item.line ?? 1, item.column ?? 1)
    return {
      from,
      to: Math.min(from + 1, editorView!.state.doc.length),
      severity: item.severity?.toLowerCase() === 'warning' ? 'warning' : 'error',
      message: `${item.code ?? item.path ?? 'YAML'}: ${item.message}`
    }
  })
  editorView.dispatch(setDiagnostics(editorView.state, mapped))
}

function lineColumnToOffset(source: string, line: number, column: number) {
  const safeLine = Math.max(1, line)
  const safeColumn = Math.max(1, column)
  let offset = 0
  const lines = source.split('\n')
  for (let index = 0; index < safeLine - 1 && index < lines.length; index += 1) {
    offset += (lines[index]?.length ?? 0) + 1
  }
  return Math.min(offset + safeColumn - 1, source.length)
}
</script>

<template>
  <UDashboardPanel id="admin-actions" :ui="{ body: 'p-0 sm:p-0' }">
    <template #header>
      <UDashboardNavbar title="Actions" :ui="{ right: 'gap-2' }">
        <template #leading>
          <LazyUDashboardSidebarCollapse />
        </template>
        <template #right>
          <UButton icon="i-lucide-plus" @click="createNewDefinition">
            New Action
          </UButton>
          <UButton color="neutral" variant="outline" icon="i-lucide-upload" :disabled="saving" @click="openUpload">
            Upload YAML
          </UButton>
          <input ref="fileInput" type="file" accept=".yaml,.yml,text/yaml" class="hidden" @change="uploadYaml">
        </template>
      </UDashboardNavbar>

      <UDashboardToolbar>
        <template #left>
          <div v-if="selectedDefinition" class="flex min-w-0 items-center gap-2">
            <UBadge variant="soft" :color="selectedDefinition.enabled ? 'success' : 'neutral'">
              {{ selectedDefinition.enabled ? 'Enabled' : 'Disabled' }}
            </UBadge>
            <UBadge v-if="selectedDefinition.global" variant="soft" color="primary">
              Global
            </UBadge>
            <span class="truncate text-sm text-muted">{{ selectedDefinition.processorKey }}</span>
          </div>
          <span v-else class="text-sm text-muted">Unsaved Action</span>
        </template>
        <template #right>
          <UButton
            v-if="selectedPersistedDefinition"
            color="neutral"
            variant="outline"
            :icon="selectedPersistedDefinition.enabled ? 'i-lucide-circle-pause' : 'i-lucide-circle-play'"
            @click="toggleDefinition(selectedPersistedDefinition)"
          >
            {{ selectedPersistedDefinition.enabled ? 'Disable' : 'Enable' }}
          </UButton>
          <UPopover
            v-if="selectedDefinition"
            :content="{ align: 'end', side: 'bottom', sideOffset: 8 }"
          >
            <UButton
              color="neutral"
              variant="outline"
              :icon="selectedDefinition.global ? 'i-lucide-globe' : 'i-lucide-building-2'"
            >
              Availability
            </UButton>

            <template #content>
              <div class="w-96 max-w-[calc(100vw-2rem)] space-y-4 p-4">
                <div class="flex items-start justify-between gap-3">
                  <div class="min-w-0">
                    <p class="truncate text-sm font-semibold">
                      Availability
                    </p>
                    <p class="text-xs leading-5 text-muted">
                      Make this Action global or available to selected workspaces.
                    </p>
                  </div>
                  <UBadge variant="soft" :color="selectedDefinition.global ? 'primary' : 'neutral'">
                    {{ selectedDefinition.global ? 'Global' : 'Workspace' }}
                  </UBadge>
                </div>

                <div v-if="isDraftSelected" class="rounded-sm border border-default p-3 text-sm text-muted">
                  Save this Action before configuring global or workspace availability.
                </div>

                <template v-else-if="selectedPersistedDefinition">
                  <div class="flex items-center justify-between gap-3 rounded-sm border border-default p-3">
                    <div class="min-w-0">
                      <p class="text-sm font-medium">
                        Global availability
                      </p>
                      <p class="text-xs leading-5 text-muted">
                        Global Actions can be executed in every workspace.
                      </p>
                    </div>
                    <UButton
                      color="neutral"
                      variant="outline"
                      size="sm"
                      :icon="selectedPersistedDefinition.global ? 'i-lucide-globe-lock' : 'i-lucide-globe'"
                      @click="toggleGlobalDefinition(selectedPersistedDefinition)"
                    >
                      {{ selectedPersistedDefinition.global ? 'Make scoped' : 'Make global' }}
                    </UButton>
                  </div>

                  <div v-if="selectedPersistedDefinition.global" class="rounded-sm border border-default p-3 text-sm text-muted">
                    This Action is global, so workspace-specific availability is not needed.
                  </div>

                  <div v-else class="space-y-3">
                    <div class="grid gap-3 sm:grid-cols-[minmax(0,1fr)_auto] sm:items-end">
                      <UFormField label="Workspaces">
                        <USelectMenu
                          v-model="selectedAvailabilityWorkspaceIds"
                          :items="assignableWorkspaceOptions"
                          value-key="value"
                          multiple
                          searchable
                          placeholder="Select workspaces"
                          :disabled="assignableWorkspaceOptions.length === 0 || loadingAvailability"
                        />
                      </UFormField>
                      <UButton
                        icon="i-lucide-plus"
                        :loading="assigningAvailability"
                        :disabled="selectedAvailabilityWorkspaceIds.length === 0"
                        @click="assignSelectedWorkspaces"
                      >
                        Add
                      </UButton>
                    </div>

                    <div v-if="loadingAvailability" class="space-y-2">
                      <USkeleton class="h-10 w-full" />
                      <USkeleton class="h-10 w-full" />
                    </div>
                    <div v-else-if="workspaceAvailability.length === 0" class="rounded-sm border border-default p-3 text-sm text-muted">
                      This Action is not available in any workspace yet.
                    </div>
                    <div v-else class="max-h-64 divide-y divide-default overflow-auto rounded-sm border border-default">
                      <div
                        v-for="availability in workspaceAvailability"
                        :key="availability.id"
                        class="flex items-center justify-between gap-3 p-3"
                      >
                        <div class="min-w-0">
                          <p class="truncate text-sm font-medium">
                            {{ workspaceById.get(availability.workspaceId) || availability.workspaceId }}
                          </p>
                          <p class="truncate text-xs text-muted">
                            Workspace availability
                          </p>
                        </div>
                        <UButton
                          color="error"
                          variant="ghost"
                          icon="i-lucide-x"
                          size="sm"
                          @click="removeWorkspaceAvailability(availability.id)"
                        />
                      </div>
                    </div>
                  </div>
                </template>
              </div>
            </template>
          </UPopover>
          <UButton color="primary" icon="i-lucide-save" :loading="saving" :disabled="!isDraftSelected && !isDirty && !!selectedDefinition" @click="saveDefinition">
            Save
          </UButton>
          <UButton
            v-if="selectedDefinition"
            color="error"
            variant="outline"
            icon="i-lucide-trash-2"
            :loading="deleting"
            :disabled="saving || validating"
            @click="deleteSelectedAction"
          >
            {{ isDraftSelected ? 'Discard' : 'Delete' }}
          </UButton>
          <USeparator v-if="selectedPersistedDefinition" orientation="vertical" class="h-4" />
          <UButton
            v-if="selectedPersistedDefinition"
            color="neutral"
            variant="ghost"
            size="sm"
            :icon="isRunsPanelVisible ? 'i-lucide-panel-right-close' : 'i-lucide-panel-right-open'"
            :aria-label="isRunsPanelVisible ? 'Hide runs sidebar' : 'Show runs sidebar'"
            @click="toggleRunsPanel"
          >
            Runs
          </UButton>
        </template>
      </UDashboardToolbar>
    </template>

    <template #body>
      <div class="h-full flex overflow-hidden">
        <aside class="w-80 shrink-0 border-r border-neutral-200 bg-neutral-50/30 dark:border-neutral-700 dark:bg-neutral-800/50 overflow-y-auto">
          <div class="border-b border-neutral-200 p-4 dark:border-neutral-700 lg:p-5">
            <div class="mb-3 flex items-center justify-between gap-2">
              <div>
                <h2 class="text-sm font-semibold">
                  Actions
                </h2>
                <p class="text-xs text-muted">
                  {{ definitions.length }} registered
                </p>
              </div>
              <UBadge size="sm" variant="soft" color="neutral">
                YAML v1
              </UBadge>
            </div>
            <UInput v-model="workflowFilter" icon="i-lucide-search" size="sm" placeholder="Filter Actions" />
          </div>

          <div class="p-2">
            <USkeleton v-if="pending" class="h-24 w-full" />
            <div v-else-if="filteredDefinitions.length === 0" class="p-3 text-sm text-muted">
              No Actions found.
            </div>
            <button
              v-for="definition in filteredDefinitions"
              :key="definition.id"
              type="button"
              class="flex w-full items-center gap-3 rounded-sm px-3 py-2 text-left text-sm hover:bg-elevated"
              :class="definition.id === selectedId ? 'bg-elevated text-highlighted' : 'text-default'"
              @click="selectDefinition(definition)"
            >
              <UIcon :name="definition.enabled ? 'i-lucide-circle-play' : 'i-lucide-circle-pause'" class="size-4 shrink-0 text-muted" />
              <span class="min-w-0 flex-1">
                <span class="block truncate font-medium">{{ definition.name }}</span>
                <span class="block truncate text-xs text-muted">{{ definition.processorKey }}</span>
              </span>
              <UBadge v-if="definition.global" size="sm" variant="soft" color="primary">
                Global
              </UBadge>
              <UBadge v-if="draftDefinition?.id === definition.id" size="sm" variant="soft" color="warning">
                Draft
              </UBadge>
            </button>
          </div>
        </aside>

        <section class="flex-1 bg-neutral-50/70 dark:bg-neutral-900 flex flex-col min-w-0 overflow-hidden">
          <div class="flex items-center justify-between gap-3 border-b border-default px-4 py-3 lg:px-5">
            <div class="min-w-0">
              <h2 class="truncate text-base font-semibold">
                {{ selectedDefinition?.name || 'New Action' }}
              </h2>
              <p class="text-sm text-muted">
                {{ selectedDefinition?.description || 'Generic YAML v1 Action definition' }}
              </p>
            </div>
            <UAlert
              v-if="validation"
              class="max-w-md"
              :color="validation.valid ? 'success' : 'warning'"
              variant="subtle"
              :icon="validation.valid ? 'i-lucide-check-circle' : 'i-lucide-triangle-alert'"
              :title="validation.valid ? 'Definition is valid' : 'Definition needs changes'"
            />
          </div>

          <div class="flex-1 min-h-0 overflow-y-auto p-4 lg:p-5">
            <div class="flex flex-col gap-4">
              <section class="overflow-hidden rounded-sm border border-default bg-default">
                <div class="flex flex-wrap items-center justify-between gap-2 border-b border-default px-3 py-2">
                  <div class="min-w-0">
                    <p class="truncate text-sm font-medium">
                      YAML
                    </p>
                    <p class="truncate text-xs text-muted">
                      Definition source
                    </p>
                  </div>
                  <div class="flex items-center gap-2">
                    <UButton color="neutral" variant="ghost" size="sm" icon="i-lucide-search" @click="toggleFindReplacePanel">
                      Find
                    </UButton>
                    <UButton color="neutral" variant="outline" size="sm" icon="i-lucide-badge-check" :loading="validating" @click="validateYaml">
                      Validate
                    </UButton>
                  </div>
                </div>
                <ClientOnly>
                  <div ref="editorHost" class="action-yaml-editor" />
                </ClientOnly>
              </section>

              <section class="rounded-sm border border-default bg-default p-3">
                <div class="mb-2 flex items-center justify-between gap-2">
                  <p class="text-sm font-medium">
                    Validation Diagnostics
                  </p>
                  <UBadge size="sm" variant="soft" :color="diagnostics.length === 0 ? 'success' : 'warning'">
                    {{ diagnostics.length }}
                  </UBadge>
                </div>
                <p v-if="diagnostics.length === 0" class="text-sm text-muted">
                  No diagnostics.
                </p>
                <ul v-else class="max-h-36 space-y-1 overflow-auto">
                  <li
                    v-for="(diagnostic, index) in diagnostics"
                    :key="`${diagnostic.message}-${index}`"
                    class="text-sm"
                  >
                    <span class="font-medium">{{ diagnostic.path || 'YAML' }}</span>
                    <span class="text-muted"> at {{ diagnostic.line }}:{{ diagnostic.column }}</span>
                    <span> - {{ diagnostic.message }}</span>
                  </li>
                </ul>
              </section>

            </div>
          </div>
        </section>

        <aside
          v-if="isRunsPanelVisible"
          class="w-96 shrink-0 border-l border-neutral-200 bg-neutral-50/30 dark:border-neutral-700 dark:bg-neutral-800/50 overflow-y-auto"
        >
          <div class="border-b border-neutral-200 p-4 dark:border-neutral-700 lg:p-5">
            <div class="mb-3 flex items-center justify-between gap-2">
              <div>
                <h2 class="text-sm font-semibold">
                  Action Runs
                </h2>
                <p class="text-xs text-muted">
                  {{ runPanelSummary }}
                </p>
              </div>
              <UButton color="neutral" variant="ghost" icon="i-lucide-x" size="sm" @click="isRunsPanelVisible = false" />
            </div>
            <div class="flex gap-2">
              <UButton color="neutral" variant="outline" icon="i-lucide-refresh-cw" size="sm" :loading="loadingRuns" @click="loadRuns">
                Refresh
              </UButton>
              <UButton
                color="neutral"
                variant="outline"
                icon="i-lucide-trash-2"
                size="sm"
                :loading="clearingRuns"
                :disabled="terminalRuns.length === 0"
                @click="clearTerminalRuns"
              >
                Clear completed
              </UButton>
            </div>
          </div>

          <div class="p-3 lg:p-4">
            <div v-if="loadingRuns" class="space-y-2">
              <USkeleton class="h-20 w-full" />
              <USkeleton class="h-20 w-full" />
            </div>
            <div v-else-if="runs.length === 0" class="rounded-sm border border-default p-3 text-sm text-muted">
              No runs recorded for this Action.
            </div>
            <div v-else class="divide-y divide-default rounded-sm border border-default bg-default">
              <div v-for="run in runs" :key="run.id" class="p-3">
                <button type="button" class="flex w-full items-start gap-3 text-left" @click="toggleRunExpanded(run.id)">
                  <UIcon :name="statusIcon(run.status)" class="mt-0.5 size-4 shrink-0" :class="runStatusTextClass(run.status)" />
                  <span class="min-w-0 flex-1">
                    <span class="flex items-center justify-between gap-2">
                      <span class="truncate text-sm font-medium">{{ run.statusMessage || run.status }}</span>
                      <UBadge size="sm" variant="soft" :color="statusColor(run.status)">
                        {{ run.progressPercent }}%
                      </UBadge>
                    </span>
                    <span class="mt-1 block truncate text-xs text-muted">
                      {{ run.projectLabel }} · {{ run.pageCount }} page{{ run.pageCount === 1 ? '' : 's' }}
                    </span>
                    <span class="mt-1 block text-xs text-muted">
                      {{ formatDate(run.created) }} · {{ formatDuration(run.durationSeconds) }}
                    </span>
                  </span>
                  <UIcon :name="isRunExpanded(run.id) ? 'i-lucide-chevron-up' : 'i-lucide-chevron-down'" class="mt-0.5 size-4 shrink-0 text-muted" />
                </button>

                <div v-if="isRunExpanded(run.id)" class="mt-3 space-y-3 border-t border-default pt-3">
                  <UProgress :model-value="run.progressPercent" :color="statusColor(run.status)" />
                  <p v-if="run.errorMessage" class="text-sm text-error">
                    {{ run.errorMessage }}
                  </p>
                  <div>
                    <p class="mb-1 text-xs font-medium text-muted">
                      Result Summary
                    </p>
                    <pre class="max-h-40 overflow-auto rounded-sm bg-elevated p-2 text-xs">{{ formatResultSummary(run.resultSummary) }}</pre>
                  </div>
                  <div>
                    <p class="mb-1 text-xs font-medium text-muted">
                      Logs
                    </p>
                    <pre class="max-h-56 overflow-auto rounded-sm bg-elevated p-2 text-xs">{{ run.logText || 'No logs recorded.' }}</pre>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </aside>
      </div>
    </template>
  </UDashboardPanel>
</template>

<style scoped>
.action-yaml-editor {
  height: 58vh;
  overflow: hidden;
}

.action-yaml-editor :deep(.cm-editor) {
  height: 100%;
  font-size: 13px;
}

.action-yaml-editor :deep(.cm-scroller) {
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, "Liberation Mono", "Courier New", monospace;
}
</style>
