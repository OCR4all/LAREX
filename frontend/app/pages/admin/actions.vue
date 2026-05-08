<script setup lang="ts">
import { Compartment, EditorState } from '@codemirror/state'
import { EditorView, keymap } from '@codemirror/view'
import { yaml } from '@codemirror/lang-yaml'
import { basicSetup } from 'codemirror'
import { lintGutter, setDiagnostics, type Diagnostic } from '@codemirror/lint'
import { closeSearchPanel, highlightSelectionMatches, openSearchPanel, search, searchKeymap, searchPanelOpen } from '@codemirror/search'
import {
  DEFAULT_ACTION_YAML,
  type ActionAssignmentResponse,
  type ActionDefinitionResponse,
  type ActionValidationDiagnostic,
  type ActionValidationResponse
} from '@/types/action'

definePageMeta({ layout: 'admin', middleware: 'admin' })

type AdminWorkspace = {
  id: string
  name: string
}

type ProjectOption = {
  id: string
  name: string
}

const toast = useToast()
const colorMode = useColorMode()

const { data: definitions, pending, refresh } = await useFetch<ActionDefinitionResponse[]>('/api/admin/actions/processors', {
  key: globalKey('admin', 'actions', 'processors'),
  default: () => []
})

const selectedId = ref<string | null>(null)
const editorHost = ref<HTMLElement | null>(null)
const fileInput = ref<HTMLInputElement | null>(null)
const validating = ref(false)
const saving = ref(false)
const loadingAssignments = ref(false)
const assigning = ref(false)
const diagnostics = ref<ActionValidationDiagnostic[]>([])
const validation = ref<ActionValidationResponse | null>(null)
const initialYaml = ref(DEFAULT_ACTION_YAML)
const editorContent = ref(DEFAULT_ACTION_YAML)
const selectedWorkspaceId = ref('')
const selectedProjectId = ref('')
const selectedAssignmentProcessorIds = ref<string[]>([])
const projects = ref<ProjectOption[]>([])
const assignments = ref<ActionAssignmentResponse[]>([])

const { data: workspaces } = await useFetch<AdminWorkspace[]>('/api/admin/workspaces', {
  key: globalKey('admin', 'actions', 'workspaces'),
  default: () => []
})

let editorView: EditorView | null = null
const themeCompartment = new Compartment()
const yamlSearchKeymap = keymap.of([
  { key: 'Mod-h', run: openSearchPanel },
  ...searchKeymap
])

const selectedDefinition = computed(() => definitions.value.find(definition => definition.id === selectedId.value) ?? null)
const currentYaml = computed(() => editorContent.value)
const isDirty = computed(() => currentYaml.value !== initialYaml.value)
const canSave = computed(() => validation.value?.valid === true && !saving.value && !validating.value)
const workspaceOptions = computed(() => workspaces.value.map(workspace => ({ label: workspace.name, value: workspace.id })))
const projectOptions = computed(() => [
  { label: 'Workspace default', value: '' },
  ...projects.value.map(project => ({ label: project.name, value: project.id }))
])
const assignableDefinitionOptions = computed(() => definitions.value
  .filter(definition => definition.enabled)
  .filter(definition => !assignments.value.some(assignment => assignment.processor.id === definition.id))
  .map(definition => ({ label: definition.name, value: definition.id })))

const definitionItems = computed(() => definitions.value.map(definition => ({
  label: definition.name,
  suffix: definition.processorKey,
  icon: definition.enabled ? 'i-lucide-bolt' : 'i-lucide-bolt-off',
  active: definition.id === selectedId.value,
  onSelect: () => selectDefinition(definition)
})))

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
  const firstDefinition = items[0]
  if (!selectedId.value && firstDefinition) {
    selectDefinition(firstDefinition)
  }
})

watch(workspaces, (items) => {
  const firstWorkspace = items[0]
  if (!selectedWorkspaceId.value && firstWorkspace) {
    selectedWorkspaceId.value = firstWorkspace.id
  }
}, { immediate: true })

watch(selectedWorkspaceId, async () => {
  selectedProjectId.value = ''
  selectedAssignmentProcessorIds.value = []
  await loadProjects()
  await loadAssignments()
})

watch(selectedProjectId, async () => {
  selectedAssignmentProcessorIds.value = []
  await loadAssignments()
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
  void nextTick(() => replaceEditorContent(definition.yaml))
}

function createNewDefinition() {
  selectedId.value = null
  initialYaml.value = DEFAULT_ACTION_YAML
  validation.value = null
  diagnostics.value = []
  void nextTick(() => replaceEditorContent(DEFAULT_ACTION_YAML))
}

async function validateYaml() {
  validating.value = true
  try {
    const result = await $fetch<ActionValidationResponse>('/api/admin/actions/processors/validate', {
      method: 'POST',
      body: { yaml: currentYaml.value },
      query: selectedId.value ? { existingDefinitionId: selectedId.value } : undefined
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
    const saved = selectedId.value
      ? await $fetch<ActionDefinitionResponse>(`/api/admin/actions/processors/${selectedId.value}`, {
          method: 'PUT',
          body: { yaml: currentYaml.value }
        })
      : await $fetch<ActionDefinitionResponse>('/api/admin/actions/processors', {
          method: 'POST',
          body: { yaml: currentYaml.value }
        })
    await refresh()
    selectedId.value = saved.id
    initialYaml.value = saved.yaml
    validation.value = null
    diagnostics.value = []
    if (saved.enabled && selectedWorkspaceId.value) {
      selectedAssignmentProcessorIds.value = [saved.id]
    }
    replaceEditorContent(saved.yaml)
    toast.add({ title: 'Action processor saved', color: 'success', icon: 'i-lucide-save' })
  } catch (error: unknown) {
    const message = error instanceof Error ? error.message : 'Could not save Action processor.'
    toast.add({ title: 'Save failed', description: message, color: 'error', icon: 'i-lucide-alert-circle' })
  } finally {
    saving.value = false
  }
}

async function loadProjects() {
  if (!selectedWorkspaceId.value) {
    projects.value = []
    return
  }
  try {
    projects.value = await $fetch<ProjectOption[]>(`/api/workspaces/${selectedWorkspaceId.value}/projects`)
  } catch {
    projects.value = []
  }
}

async function loadAssignments() {
  if (!selectedWorkspaceId.value) {
    assignments.value = []
    return
  }
  loadingAssignments.value = true
  try {
    assignments.value = await $fetch<ActionAssignmentResponse[]>(`/api/workspaces/${selectedWorkspaceId.value}/actions/assignments`, {
      query: selectedProjectId.value ? { projectId: selectedProjectId.value } : undefined
    })
    selectedAssignmentProcessorIds.value = selectedAssignmentProcessorIds.value.filter(id =>
      assignableDefinitionOptions.value.some(option => option.value === id)
    )
  } catch (error: unknown) {
    const message = error instanceof Error ? error.message : 'Could not load Action assignments.'
    toast.add({ title: 'Assignment load failed', description: message, color: 'error' })
  } finally {
    loadingAssignments.value = false
  }
}

async function assignSelectedProcessors() {
  if (!selectedWorkspaceId.value || selectedAssignmentProcessorIds.value.length === 0) return
  assigning.value = true
  try {
    await Promise.all(selectedAssignmentProcessorIds.value.map(processorDefinitionId =>
      $fetch(`/api/workspaces/${selectedWorkspaceId.value}/actions/assignments`, {
        method: 'POST',
        body: {
          processorDefinitionId,
          projectId: selectedProjectId.value || null,
          enabled: true
        }
      })
    ))
    selectedAssignmentProcessorIds.value = []
    await loadAssignments()
    toast.add({ title: 'Actions assigned', color: 'success', icon: 'i-lucide-bolt' })
  } catch (error: unknown) {
    const message = error instanceof Error ? error.message : 'Could not assign Actions.'
    toast.add({ title: 'Assignment failed', description: message, color: 'error' })
  } finally {
    assigning.value = false
  }
}

async function unassignProcessor(assignmentId: string) {
  if (!selectedWorkspaceId.value) return
  try {
    await $fetch(`/api/workspaces/${selectedWorkspaceId.value}/actions/assignments/${assignmentId}`, {
      method: 'DELETE'
    })
    await loadAssignments()
    toast.add({ title: 'Action unassigned', color: 'success' })
  } catch (error: unknown) {
    const message = error instanceof Error ? error.message : 'Could not unassign Action.'
    toast.add({ title: 'Unassign failed', description: message, color: 'error' })
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
    const message = error instanceof Error ? error.message : 'Could not update Action processor.'
    toast.add({ title: 'Update failed', description: message, color: 'error' })
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
  <UDashboardPanel id="admin-actions">
    <template #header>
      <UDashboardNavbar title="LAREX Actions">
        <template #leading>
          <UDashboardSidebarCollapse />
        </template>
      </UDashboardNavbar>

      <UDashboardToolbar>
        <template #left>
          <UButton icon="i-lucide-plus" @click="createNewDefinition">
            New Processor
          </UButton>
          <UButton color="neutral" variant="outline" icon="i-lucide-upload" :disabled="saving" @click="openUpload">
            Upload YAML
          </UButton>
          <input ref="fileInput" type="file" accept=".yaml,.yml,text/yaml" class="hidden" @change="uploadYaml">
        </template>
        <template #right>
          <UButton color="neutral" variant="outline" icon="i-lucide-search" @click="toggleFindReplacePanel">
            Find
          </UButton>
          <UButton color="neutral" variant="outline" icon="i-lucide-badge-check" :loading="validating" @click="validateYaml">
            Validate
          </UButton>
          <UButton
            v-if="selectedDefinition"
            color="neutral"
            variant="outline"
            :icon="selectedDefinition.enabled ? 'i-lucide-bolt-off' : 'i-lucide-bolt'"
            @click="toggleDefinition(selectedDefinition)"
          >
            {{ selectedDefinition.enabled ? 'Disable' : 'Enable' }}
          </UButton>
          <UButton color="primary" icon="i-lucide-save" :loading="saving" :disabled="!isDirty && !!selectedDefinition" @click="saveDefinition">
            Save
          </UButton>
        </template>
      </UDashboardToolbar>
    </template>

    <template #body>
      <div class="grid min-h-0 gap-0 lg:grid-cols-[280px_minmax(0,1fr)_340px]">
        <aside class="border-r border-default p-4">
          <div class="mb-3 flex items-center justify-between gap-2">
            <div>
              <h2 class="text-sm font-semibold">
                Processor Registry
              </h2>
              <p class="text-xs text-muted">
                {{ definitions.length }} definitions
              </p>
            </div>
            <UBadge size="sm" variant="soft" color="neutral">
              YAML v1
            </UBadge>
          </div>

          <div class="flex flex-col gap-2">
              <USkeleton v-if="pending" class="h-24 w-full" />
              <div v-else-if="definitionItems.length === 0" class="rounded-sm border border-default p-3 text-sm text-muted">
                No Action processors are registered.
              </div>
              <UButton
                v-for="definition in definitions"
                :key="definition.id"
                color="neutral"
                :variant="definition.id === selectedId ? 'subtle' : 'ghost'"
                class="justify-start"
                :icon="definition.enabled ? 'i-lucide-bolt' : 'i-lucide-bolt-off'"
                @click="selectDefinition(definition)"
              >
                <span class="min-w-0 flex-1 truncate text-left">{{ definition.name }}</span>
                <UBadge size="sm" variant="soft" :color="definition.enabled ? 'success' : 'neutral'">
                  {{ definition.processorKey }}
                </UBadge>
              </UButton>
          </div>
        </aside>

        <main class="min-w-0 p-4">
          <div class="mb-3 flex flex-wrap items-center justify-between gap-3">
            <div>
              <h1 class="text-xl font-semibold">
                {{ selectedDefinition?.name || 'New Action Processor' }}
              </h1>
              <p class="text-sm text-muted">
                Generic YAML v1 processor definition
              </p>
            </div>
            <UAlert
              v-if="validation"
              class="max-w-lg"
              :color="validation.valid ? 'success' : 'warning'"
              variant="subtle"
              :icon="validation.valid ? 'i-lucide-check-circle' : 'i-lucide-triangle-alert'"
              :title="validation.valid ? 'Definition is valid' : 'Definition needs changes'"
            />
          </div>

          <ClientOnly>
            <div ref="editorHost" class="action-yaml-editor rounded-sm border border-default" />
          </ClientOnly>

          <section class="mt-3 rounded-sm border border-default p-3">
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
                :key="`${diagnostic.code}-${index}`"
                class="text-sm"
              >
                <span class="font-medium">{{ diagnostic.code || diagnostic.path || 'YAML' }}</span>
                <span class="text-muted"> at {{ diagnostic.path || 'document' }} {{ diagnostic.line }}:{{ diagnostic.column }}</span>
                <span> - {{ diagnostic.message }}</span>
              </li>
            </ul>
          </section>
        </main>

        <aside class="border-l border-default p-4">
          <div class="mb-3">
            <h2 class="text-sm font-semibold">
              Assignments
            </h2>
            <p class="text-xs text-muted">
              Assign enabled processors to a workspace or one project.
            </p>
          </div>

          <div class="flex flex-col gap-3">
            <UFormField label="Workspace">
              <USelectMenu
                v-model="selectedWorkspaceId"
                :items="workspaceOptions"
                value-key="value"
                searchable
                placeholder="Select workspace"
              />
            </UFormField>
            <UFormField label="Scope">
              <USelectMenu
                v-model="selectedProjectId"
                :items="projectOptions"
                value-key="value"
                searchable
                :disabled="!selectedWorkspaceId"
              />
            </UFormField>
            <UFormField label="Processors">
              <USelectMenu
                v-model="selectedAssignmentProcessorIds"
                :items="assignableDefinitionOptions"
                value-key="value"
                multiple
                searchable
                placeholder="Select processors"
                :disabled="!selectedWorkspaceId || assignableDefinitionOptions.length === 0"
              />
            </UFormField>
            <UButton
              icon="i-lucide-plus"
              :loading="assigning"
              :disabled="selectedAssignmentProcessorIds.length === 0"
              @click="assignSelectedProcessors"
            >
              Assign Selected
            </UButton>

            <USeparator />

            <div v-if="loadingAssignments" class="space-y-2">
              <USkeleton class="h-10 w-full" />
              <USkeleton class="h-10 w-full" />
            </div>
            <div v-else-if="assignments.length === 0" class="rounded-sm border border-default p-3 text-sm text-muted">
              No processors are assigned for this scope.
            </div>
            <div v-else class="divide-y divide-default rounded-sm border border-default">
              <div
                v-for="assignment in assignments"
                :key="assignment.id"
                class="flex items-center justify-between gap-2 p-3"
              >
                <div class="min-w-0">
                  <p class="truncate text-sm font-medium">
                    {{ assignment.processor.name }}
                  </p>
                  <p class="truncate text-xs text-muted">
                    {{ assignment.processor.processorKey }} · {{ assignment.processor.executeRole }}
                  </p>
                </div>
                <UButton
                  color="error"
                  variant="ghost"
                  icon="i-lucide-x"
                  size="sm"
                  @click="unassignProcessor(assignment.id)"
                />
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
  height: 64vh;
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
