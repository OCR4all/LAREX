<script setup lang="ts">
import type { ActionRunImpact, ExecutableActionProcessor } from '~/types/action'
import type { ActionWorkflow, ActionWorkflowRun, ActionWorkflowStep } from '~/types/action-workflow'
import { extractApiErrorDetails } from '~/utils/api-error'
import { annotationLevelLabels } from '~/utils/action-impact'

const props = defineProps<{
  workspaceId: string
  projectId: string
  projectName?: string | null
  pageIds: string[]
  initialWorkflowId?: string
  processors: ExecutableActionProcessor[]
}>()
const emit = defineEmits<{ started: [], busy: [value: boolean] }>()
const store = useActionRunsStore()
const workflows = ref<ActionWorkflow[]>([])
const selectedId = ref('')
const contextProcessors = ref<ExecutableActionProcessor[]>([])
const loadingProcessors = ref(false)
const processorsReady = ref(false)
let processorGeneration = 0
const steps = ref<ActionWorkflowStep[]>([])
const selectedStepIndex = ref(0)
const scope = ref<'selection' | 'all'>(props.pageIds.length ? 'selection' : 'all')
const loading = ref(true)
const busy = ref(false)
const error = ref('')
const impact = ref<ActionRunImpact | null>(null)
const excluded = ref<string[]>([])
let generation = 0
const selected = computed(() => workflows.value.find(workflow => workflow.id === selectedId.value))
const processorResponses = computed(() => [...new Map([...props.processors, ...contextProcessors.value].map(item => [item.processor.id, item])).values()])
const processorDefinitions = computed(() => processorResponses.value.map(item => item.processor))
const selectedStep = computed(() => steps.value[selectedStepIndex.value])
const selectedProcessor = computed(() => processorDefinitions.value.find(item => item.id === selectedStep.value?.processorDefinitionId))
const chosenPages = computed(() => scope.value === 'selection' ? props.pageIds : [])
const included = computed(() => impact.value?.pages.filter(page => !excluded.value.includes(page.pageId)) ?? [])
const base = computed(() => `/api/workspaces/${props.workspaceId}/actions/projects/${props.projectId}/workflow-runs`)
const blocked = computed(() => steps.value.map((step, index) => {
  const processor = processorResponses.value.find(item => item.processor.id === step.processorDefinitionId)
  return !processor
    ? `Step ${index + 1}: processor is unavailable to this workflow.`
    : !processor.executable ? `Step ${index + 1}: ${processor.blockedReason}` : ''
}).filter(Boolean))
function resetReview() {
  impact.value = null
  excluded.value = []
}
watch([steps, scope], resetReview, { deep: true })
watch(busy, value => emit('busy', value))
watch(selectedId, async () => {
  steps.value = selected.value ? structuredClone(toRaw(selected.value.steps)) : []
  selectedStepIndex.value = 0
  error.value = ''
  resetReview()
  contextProcessors.value = []
  processorsReady.value = false
  loadingProcessors.value = false
  const request = ++processorGeneration
  if (!selectedId.value) return
  loadingProcessors.value = true
  try {
    const result = await $fetch<ExecutableActionProcessor[]>(`/api/workspaces/${props.workspaceId}/actions/projects/${props.projectId}/workflows/${selectedId.value}/processors`)
    if (request === processorGeneration) {
      contextProcessors.value = result ?? []
      processorsReady.value = true
    }
  } catch (cause) {
    if (request === processorGeneration) error.value = extractApiErrorDetails(cause, 'Could not load workflow processors.').message
  } finally {
    if (request === processorGeneration) loadingProcessors.value = false
  }
})
function body() {
  return { workflowId: selected.value!.id, version: selected.value!.version, steps: steps.value, pageIds: chosenPages.value }
}
async function load() {
  const request = ++generation
  loading.value = true
  error.value = ''
  try {
    const result = await $fetch<ActionWorkflow[]>(`/api/workspaces/${props.workspaceId}/actions/projects/${props.projectId}/workflows`)
    if (request === generation) {
      workflows.value = result.filter(workflow => workflow.enabled)
      if (props.initialWorkflowId && workflows.value.some(workflow => workflow.id === props.initialWorkflowId)) selectedId.value = props.initialWorkflowId
    }
  } catch (cause) {
    if (request === generation) error.value = extractApiErrorDetails(cause, 'Could not load workflows.').message
  } finally { if (request === generation) loading.value = false }
}
async function review() {
  if (!selected.value || busy.value || !processorsReady.value || loadingProcessors.value || blocked.value.length) return
  busy.value = true
  error.value = ''
  try {
    impact.value = await $fetch<ActionRunImpact>(`${base.value}/impact`, { method: 'POST', body: body() })
    excluded.value = []
    if (!impact.value.pages.length) error.value = 'No pages satisfy the first step’s input requirements.'
  } catch (cause) {
    error.value = extractApiErrorDetails(cause, 'Could not review workflow.').message
  } finally {
    busy.value = false
  }
}
async function start() {
  if (!selected.value || !included.value.length || busy.value) return
  busy.value = true
  error.value = ''
  try {
    const run = await $fetch<ActionWorkflowRun>(base.value, { method: 'POST', body: { ...body(), pageIds: included.value.map(page => page.pageId) } })
    store.upsertWorkflowRun(run, props.projectName || props.projectId)
    resetReview()
    void store.refreshProjectRuns(props.workspaceId, props.projectId, props.projectName).catch(() => {})
    emit('started')
  } catch (cause) {
    error.value = extractApiErrorDetails(cause, 'Could not start workflow.').message
  } finally {
    busy.value = false
  }
}
function updateStep(step: ActionWorkflowStep) {
  steps.value[selectedStepIndex.value] = step
}
function include(pageId: string, value: boolean) {
  excluded.value = value ? excluded.value.filter(id => id !== pageId) : [...excluded.value, pageId]
}
onMounted(load)
onBeforeUnmount(() => {
  processorGeneration++
  generation++
  emit('busy', false)
})
</script>

<template>
  <div class="space-y-5">
    <p class="text-sm text-muted">
      Choose a saved workflow and adjust each step for this run. Changes here apply only to this run.
    </p>
    <UFormField label="Workflow">
      <USelectMenu
        v-model="selectedId"
        :items="workflows.map(workflow => ({ label: workflow.name, value: workflow.id }))"
        value-key="value"
        :loading="loading"
        :disabled="busy"
        placeholder="Choose a workflow…"
        class="w-full"
      />
    </UFormField>
    <p v-if="!loading && !workflows.length && !error" class="text-sm text-muted">
      No workflows are activated. Add an available workflow in Workspace settings → Actions, or build one in Admin → Actions.
    </p>
    <template v-if="selected">
      <p v-if="selected.description" class="text-sm text-toned">
        {{ selected.description }}
      </p>
      <UFormField label="Pages">
        <USelect
          v-model="scope"
          :items="[{ label: 'All project pages', value: 'all' }, ...(pageIds.length ? [{ label: `Selected pages (${pageIds.length})`, value: 'selection' }] : [])]"
          :disabled="busy"
          class="w-full"
        />
      </UFormField>
      <UAlert
        v-if="blocked.length"
        color="warning"
        title="Workflow cannot run"
        :description="blocked.join(' ')"
      />
      <div class="grid gap-4 sm:grid-cols-[minmax(0,1fr)_minmax(0,1fr)]">
        <ClientOnly>
          <ActionWorkflowGraph
            :steps="steps"
            :processors="processorDefinitions"
            :selected="selectedStepIndex"
            :disabled="busy"
            @select="selectedStepIndex = $event"
          />
        </ClientOnly>
        <div class="max-h-[440px] space-y-4 overflow-auto rounded-lg border border-default p-4">
          <template v-if="selectedStep && selectedProcessor">
            <h3 class="font-semibold">
              Step {{ selectedStepIndex + 1 }} · {{ selectedProcessor.name }}
            </h3>
            <ActionWorkflowStepSettings
              :step="selectedStep"
              :processor="selectedProcessor"
              :workspace-id="workspaceId"
              :project-id="projectId"
              :workflow-id="selectedId"
              :disabled="busy"
              @update="updateStep"
            />
          </template>
          <p v-else class="text-sm text-muted">
            This step’s processor is unavailable.
          </p>
        </div>
      </div>
      <UButton
        v-if="!impact"
        label="Review workflow"
        icon="i-lucide-play"
        :loading="busy"
        :disabled="!steps.length || !processorsReady || loadingProcessors || !!blocked.length"
        @click="review"
      />
      <section v-else class="space-y-3 rounded-lg border border-default p-4" aria-label="Review workflow pages">
        <h3 class="font-semibold">
          Review affected pages
        </h3>
        <p class="text-sm text-muted">
          The following annotation levels may be replaced across the workflow. Exclude pages you want to keep.
        </p>
        <div class="max-h-64 space-y-3 overflow-auto">
          <UCheckbox
            v-for="page in impact.pages"
            :key="page.pageId"
            :label="page.name || page.pageId"
            :description="page.warningPrecision === 'UNKNOWN' ? 'A step does not declare which annotations it replaces.' : page.affectedLevels.map(level => annotationLevelLabels[level]).join(', ') || 'No declared annotation changes.'"
            :model-value="!excluded.includes(page.pageId)"
            :disabled="busy"
            @update:model-value="include(page.pageId, $event === true)"
          />
        </div>
        <p v-for="page in impact.skippedPages" :key="page.pageId" class="text-xs text-warning">
          {{ page.name }}: {{ page.reason }}
        </p>
        <div class="flex flex-wrap justify-end gap-2">
          <UButton
            label="Back"
            color="neutral"
            variant="ghost"
            :disabled="busy"
            @click="resetReview"
          />
          <UButton
            :label="`Start workflow on ${included.length} pages`"
            icon="i-lucide-play"
            :loading="busy"
            :disabled="!included.length"
            @click="start"
          />
        </div>
      </section>
    </template>
    <UAlert
      v-if="error"
      title="Workflow unavailable"
      :description="error"
      color="error"
    />
  </div>
</template>
