<script setup lang="ts">
import type { ActionDefinition } from '~/types/action'
import type { ActionWorkflow, ActionWorkflowStep, ActionWorkflowTest } from '~/types/action-workflow'
import { createWorkflowStep } from '~/utils/action-workflow'
import { extractApiErrorDetails } from '~/utils/api-error'

const props = defineProps<{ workflow?: ActionWorkflow | null, processors: ActionDefinition[], workspaceId?: string, initialName?: string, initialDescription?: string, toolbarTarget?: string }>()
const emit = defineEmits<{ saved: [workflow: ActionWorkflow], dirty: [value: boolean], busy: [value: boolean], metadata: [value: { name: string, description: string }] }>()
const name = ref('')
const description = ref('')
const entries = ref<{ id: string, step: ActionWorkflowStep }[]>([])
const selected = ref(0)
const query = ref('')
const busy = ref(false)
const testing = ref(false)
const testResult = ref<ActionWorkflowTest | null>(null)
const testError = ref('')
const testOpen = ref(false)
const testList = ref<HTMLElement | null>(null)
type StepCheck = ActionWorkflowTest['steps'][number]
const testChecks = ref<(StepCheck & { state: 'PENDING' | 'CHECKING' | 'PASSED' | 'FAILED' })[]>([])
const completedSteps = computed(() => testChecks.value.filter(step => step.state === 'PASSED' || step.state === 'FAILED').length)
const passedSteps = computed(() => testChecks.value.filter(step => step.state === 'PASSED').length)
const testedStepCount = computed(() => testChecks.value.length || steps.value.length)
let testGeneration = 0
const error = ref('')
const announcement = ref('')
const baseline = ref('')
const undoStack = ref<{ entries: typeof entries.value, selected: number }[]>([])
const redoStack = ref<{ entries: typeof entries.value, selected: number }[]>([])
let nextId = 0
let savedRevision: { id: string, version: number } | null = null
const processors = computed(() => props.processors.filter(processor => !processor.workflowId && processor.enabled && processor.kind === 'PROCESSING' && processor.targets.includes('PAGE')))
const filtered = computed(() => {
  const terms = query.value.toLowerCase().trim().split(/\s+/)
  return processors.value.filter(processor => terms.every(term => `${processor.name} ${processor.description ?? ''} ${processor.tags.join(' ')}`.toLowerCase().includes(term)))
})
const steps = computed(() => entries.value.map(entry => entry.step))
const snapshot = computed(() => JSON.stringify({ name: name.value, description: description.value, steps: steps.value }))
const dirty = computed(() => snapshot.value !== baseline.value)
const missing = computed(() => steps.value.some(step => !processors.value.some(processor => processor.id === step.processorDefinitionId)))
watch(dirty, value => emit('dirty', value))
watch(() => busy.value || testing.value, value => emit('busy', value), { flush: 'sync' })
watch([name, description], () => emit('metadata', { name: name.value, description: description.value }))
watch(() => JSON.stringify(steps.value), () => {
  testGeneration++
  testResult.value = null
  testChecks.value = []
  testError.value = ''
})
watch(() => props.workflow, (workflow) => {
  if (workflow && workflow.id === savedRevision?.id && workflow.version === savedRevision.version) return
  name.value = workflow?.name ?? props.initialName ?? ''
  description.value = workflow?.description ?? props.initialDescription ?? ''
  entries.value = (workflow?.steps ?? []).map(step => ({ id: `step-${nextId++}`, step: structuredClone(toRaw(step)) }))
  selected.value = 0
  error.value = ''
  undoStack.value = []
  redoStack.value = []
  baseline.value = snapshot.value
}, { immediate: true })
function capture(): { entries: typeof entries.value, selected: number } {
  // Drafts are JSON data, like the save payload. Serialization also unwraps
  // nested Vue proxies retained by object spreads in the node parameter forms.
  return { entries: JSON.parse(JSON.stringify(entries.value)), selected: selected.value }
}
function remember() {
  undoStack.value = [...undoStack.value.slice(-49), capture()]
  redoStack.value = []
}
function undo() {
  const state = undoStack.value.at(-1)
  if (busy.value || !state) return
  redoStack.value.push(capture())
  undoStack.value.pop()
  entries.value = structuredClone(toRaw(state.entries))
  selected.value = state.selected
  announcement.value = 'Change undone.'
}
function redo() {
  const state = redoStack.value.at(-1)
  if (busy.value || !state) return
  undoStack.value.push(capture())
  redoStack.value.pop()
  entries.value = structuredClone(toRaw(state.entries))
  selected.value = state.selected
  announcement.value = 'Change restored.'
}
function keyboard(event: KeyboardEvent) {
  if (!(event.ctrlKey || event.metaKey)) return
  if (event.key.toLowerCase() === 's') {
    event.preventDefault()
    void save()
    return
  }
  if (event.key.toLowerCase() !== 'z') return
  if ((event.target as HTMLElement).closest('input, textarea, [contenteditable="true"]')) return
  event.preventDefault()
  if (event.shiftKey) redo()
  else undo()
}
function insert(processorId: string, index = entries.value.length) {
  const processor = processors.value.find(item => item.id === processorId)
  if (busy.value || !processor || entries.value.length >= 30) return
  remember()
  const position = Math.max(0, Math.min(index, entries.value.length))
  entries.value.splice(position, 0, { id: `step-${nextId++}`, step: createWorkflowStep(processor) })
  selected.value = position
  announcement.value = `Added ${processor.name} as step ${position + 1}.`
}
function reorder(from: number, to: number) {
  if (busy.value || from === to || from < 0 || to < 0 || from >= entries.value.length || to >= entries.value.length) return
  remember()
  const [entry] = entries.value.splice(from, 1)
  entries.value.splice(to, 0, entry!)
  selected.value = to
  announcement.value = `Moved step ${from + 1} to position ${to + 1}.`
}
function remove(index: number) {
  if (busy.value) return
  remember()
  entries.value.splice(index, 1)
  selected.value = Math.max(0, Math.min(selected.value, entries.value.length - 1))
  announcement.value = `Removed step ${index + 1}.`
}
function update(index: number, step: ActionWorkflowStep) {
  if (!busy.value && entries.value[index]) {
    remember()
    entries.value[index].step = step
  }
}
function drag(event: DragEvent, id: string) {
  if (!event.dataTransfer || busy.value || entries.value.length >= 30) return
  event.dataTransfer.setData('application/larex-action', id)
  event.dataTransfer.effectAllowed = 'copy'
}
async function save() {
  if (busy.value || testing.value || !name.value.trim() || !steps.value.length || missing.value || !dirty.value) return
  busy.value = true
  error.value = ''
  try {
    const base = props.workspaceId ? `/api/workspaces/${props.workspaceId}/actions/workflows` : '/api/admin/actions/workflows'
    const definition = props.processors.find(item => item.id === props.workflow?.processorDefinitionId)
    const saved = await $fetch<ActionWorkflow>(`${base}${props.workflow ? `/${props.workflow.id}` : ''}`, {
      method: props.workflow ? 'PUT' : 'POST',
      body: { name: name.value.trim(), description: description.value.trim(), enabled: definition?.enabled ?? props.workflow?.enabled ?? true, steps: steps.value, version: props.workflow?.version }
    })
    savedRevision = { id: saved.id, version: saved.version }
    name.value = saved.name
    description.value = saved.description ?? ''
    baseline.value = snapshot.value
    announcement.value = 'Workflow saved.'
    emit('dirty', false)
    emit('saved', saved)
  } catch (cause) {
    error.value = extractApiErrorDetails(cause, 'Could not save workflow. Your changes are still here.').message
  } finally {
    busy.value = false
  }
}
async function testWorkflow() {
  if (testing.value || busy.value || !steps.value.length || props.workspaceId) return
  const snapshot: ActionWorkflowStep[] = JSON.parse(JSON.stringify(steps.value))
  const generation = ++testGeneration
  testing.value = true
  testError.value = ''
  testResult.value = null
  testChecks.value = snapshot.map((step, index) => ({
    step: index + 1,
    processorDefinitionId: step.processorDefinitionId,
    processorName: props.processors.find(processor => processor.id === step.processorDefinitionId)?.name ?? 'Unavailable processor',
    ok: false, message: '', endpoint: null, state: 'PENDING'
  }))
  const results: StepCheck[] = []
  try {
    for (const [index, step] of snapshot.entries()) {
      if (generation !== testGeneration) return
      testChecks.value[index]!.state = 'CHECKING'
      void nextTick(() => {
        const list = testList.value
        const active = list?.querySelector<HTMLElement>('[data-check-state="CHECKING"]')
        if (!list || !active) return
        const top = active.getBoundingClientRect().top - list.getBoundingClientRect().top + list.scrollTop
        if (top < list.scrollTop) list.scrollTop = top
        else if (top + active.offsetHeight > list.scrollTop + list.clientHeight) list.scrollTop = top + active.offsetHeight - list.clientHeight
      })
      // Keep fast checks visible long enough to show which processor is being tested.
      const minimumDisplay = new Promise<void>(resolve => setTimeout(resolve, 350))
      let check: StepCheck
      try {
        const result = await $fetch<ActionWorkflowTest>('/api/admin/actions/workflows/test', { method: 'POST', body: { steps: [step] } })
        if (!result.steps[0]) throw new Error('The processor check returned no result.')
        check = { ...result.steps[0], step: index + 1 }
      } catch (cause) {
        check = {
          step: index + 1, processorDefinitionId: step.processorDefinitionId,
          processorName: testChecks.value[index]?.processorName ?? 'Unavailable processor',
          ok: false, endpoint: null,
          message: extractApiErrorDetails(cause, 'Could not check this processor.').message
        }
      }
      await minimumDisplay
      if (generation !== testGeneration) return
      results.push(check)
      testChecks.value[index] = { ...check, state: check.ok ? 'PASSED' : 'FAILED' }
    }
    testResult.value = { ok: results.every(step => step.ok), steps: results }
  } catch (cause) {
    if (generation === testGeneration) testError.value = extractApiErrorDetails(cause, 'Could not test workflow.').message
  } finally {
    testing.value = false
  }
}
function beforeUnload(event: BeforeUnloadEvent) {
  if (dirty.value) event.preventDefault()
}
onMounted(() => window.addEventListener('beforeunload', beforeUnload))
onBeforeUnmount(() => {
  testGeneration++
  window.removeEventListener('beforeunload', beforeUnload)
})
defineExpose({ dirty, busy })
</script>

<template>
  <section
    class="overflow-hidden rounded-sm border border-default bg-default"
    aria-label="Workflow editor"
    @keydown="keyboard"
  >
    <div class="flex flex-wrap items-start gap-4 border-b border-default p-4">
      <div class="min-w-48 flex-1 space-y-2">
        <UInput
          v-model="name"
          aria-label="Workflow name"
          placeholder="Name your workflow"
          :maxlength="120"
          :disabled="busy"
          variant="soft"
          class="w-full"
          :ui="{ base: 'font-semibold' }"
        />
        <UInput
          v-model="description"
          aria-label="Workflow description"
          placeholder="Add a description…"
          :maxlength="4000"
          :disabled="busy"
          variant="none"
          class="w-full"
          size="sm"
        />
      </div>
      <Teleport :to="toolbarTarget || 'body'" :disabled="!toolbarTarget">
        <div class="flex items-center gap-2" :class="{ 'pt-1': !toolbarTarget }">
          <div class="flex gap-1">
            <UButton
              icon="i-lucide-undo-2"
              aria-label="Undo workflow change"
              title="Undo (⌘/Ctrl Z)"
              color="neutral"
              variant="ghost"
              :disabled="busy || !undoStack.length"
              @click="undo"
            />
            <UButton
              icon="i-lucide-redo-2"
              aria-label="Redo workflow change"
              title="Redo (⌘/Ctrl Shift Z)"
              color="neutral"
              variant="ghost"
              :disabled="busy || !redoStack.length"
              @click="redo"
            />
          </div>
          <span class="text-xs text-muted" role="status">{{ busy ? 'Saving…' : dirty ? 'Unsaved changes' : workflow ? 'All changes saved' : 'New workflow' }}</span>
          <UPopover
            v-if="!workspaceId"
            v-model:open="testOpen"
            :content="{ align: 'end', side: 'bottom', sideOffset: 10 }"
            :ui="{ content: 'rounded-[22px] bg-default p-1.5 shadow-xl ring-1 ring-default' }"
          >
            <UButton
              label="Test workflow"
              :icon="testing ? 'i-lucide-loader-circle' : 'i-lucide-heart-pulse'"
              :ui="{ leadingIcon: testing ? 'animate-spin motion-reduce:animate-none' : '' }"
              color="neutral"
              variant="outline"
              :disabled="busy || !steps.length"
              @click="!testResult && testWorkflow()"
            />
            <template #content>
              <section class="w-[420px] max-w-[calc(100vw-2rem)] overflow-hidden rounded-2xl border border-default bg-default" aria-label="Workflow test results" aria-live="polite">
                <div class="rounded-2xl border-b border-default bg-muted/50 p-1.5">
                  <div class="flex items-start gap-3 p-3">
                    <span class="flex size-11 shrink-0 items-center justify-center rounded-xl border border-default bg-default shadow-xs"><UIcon name="i-lucide-workflow" class="size-5 text-highlighted" /></span>
                    <div class="min-w-0 flex-1 pt-0.5">
                      <h3 class="text-sm font-semibold text-highlighted">
                        Workflow check
                      </h3>
                      <p class="mt-1 truncate text-xs text-muted">
                        {{ name || 'Untitled workflow' }}
                      </p>
                    </div>
                    <UButton
                      icon="i-lucide-x"
                      aria-label="Close workflow report"
                      color="neutral"
                      variant="ghost"
                      size="xs"
                      @click="testOpen = false"
                    />
                  </div>
                  <div class="flex items-center gap-4 rounded-xl border border-default bg-default px-3 py-3 shadow-xs">
                    <div class="min-w-0 flex-1 space-y-2">
                      <div class="flex items-center justify-between gap-3 text-xs">
                        <span class="font-medium text-highlighted">{{ testing ? 'Checking processors' : testError ? 'Check unavailable' : testResult?.ok ? 'Endpoints verified' : 'Review failed steps' }}</span>
                        <span class="shrink-0 text-muted">{{ testing ? `${completedSteps} of ${testedStepCount}` : `${passedSteps} of ${testedStepCount}` }}</span>
                      </div>
                      <UProgress
                        :model-value="testing ? completedSteps : passedSteps"
                        :max="Math.max(1, testedStepCount)"
                        size="sm"
                        color="neutral"
                        aria-label="Workflow checks completed"
                      />
                    </div>
                    <UButton
                      label="Run again"
                      color="neutral"
                      variant="outline"
                      size="sm"
                      :loading="testing"
                      :disabled="busy || !steps.length"
                      @click="testWorkflow"
                    />
                  </div>
                </div>
                <div class="flex items-center justify-between gap-3 border-b border-default px-4 py-3">
                  <span class="text-sm text-muted">Status</span>
                  <UBadge
                    :color="testing ? 'warning' : testError ? 'error' : testResult?.ok ? 'success' : 'warning'"
                    variant="subtle"
                    class="rounded-full"
                    :icon="testing ? 'i-lucide-loader-circle' : testResult?.ok ? 'i-lucide-circle-check' : 'i-lucide-circle-alert'"
                  >
                    {{ testing ? 'Checking' : testError ? 'Failed' : testResult?.ok ? 'Passed' : 'Needs attention' }}
                  </UBadge>
                </div>
                <p v-if="testError" class="px-4 py-4 text-sm text-error">
                  {{ testError }}
                </p>
                <div v-if="testChecks.length" ref="testList" class="max-h-[min(45vh,440px)] divide-y divide-default overflow-y-auto">
                  <div
                    v-for="step in testChecks"
                    :key="step.step"
                    :data-check-state="step.state"
                    class="flex items-start gap-3 px-4 py-3"
                  >
                    <span class="mt-0.5 flex size-6 shrink-0 items-center justify-center rounded-md bg-muted text-[11px] font-medium text-muted">{{ step.step }}</span>
                    <div class="min-w-0 flex-1">
                      <p class="text-xs font-medium text-highlighted">
                        {{ step.processorName }}
                      </p>
                      <p class="mt-1 text-xs leading-relaxed text-muted">
                        {{ step.message || (step.state === 'CHECKING' ? 'Checking endpoint and configuration…' : 'Waiting to check') }}
                      </p>
                      <p v-if="step.endpoint" class="mt-1 text-[11px] tabular-nums text-dimmed">
                        {{ step.endpoint.durationMillis }}ms
                      </p>
                    </div>
                    <UIcon
                      :name="step.state === 'CHECKING' ? 'i-lucide-loader-circle' : step.state === 'PENDING' ? 'i-lucide-circle-dashed' : step.ok ? 'i-lucide-circle-check' : 'i-lucide-circle-alert'"
                      :aria-label="step.state === 'CHECKING' ? 'Checking' : step.state === 'PENDING' ? 'Pending' : step.ok ? 'Passed' : 'Failed'"
                      class="mt-0.5 size-4 shrink-0"
                      :class="step.state === 'CHECKING' ? 'animate-spin text-primary motion-reduce:animate-none' : step.state === 'PENDING' ? 'text-dimmed' : step.ok ? 'text-success' : 'text-warning'"
                    />
                  </div>
                </div>
                <p class="border-t border-default bg-muted/20 px-4 py-3 text-[11px] leading-relaxed text-muted">
                  No pages are processed. Availability, permissions and page inputs are checked when running.
                </p>
              </section>
            </template>
          </UPopover>
          <UButton
            :label="toolbarTarget ? 'Save' : 'Save workflow'"
            icon="i-lucide-save"
            :loading="busy"
            :disabled="testing || !dirty || !name.trim() || !steps.length || missing"
            @click="save"
          />
        </div>
      </Teleport>
    </div>
    <UAlert
      v-if="error"
      class="m-4"
      color="error"
      title="Could not save workflow"
      :description="error"
    />
    <UAlert
      v-if="missing"
      class="m-4"
      color="warning"
      title="A processor is unavailable"
      description="Remove or replace disabled or missing processors before saving."
    />
    <div class="flex h-[680px] min-h-[560px] flex-col md:h-[calc(100dvh-350px)] md:flex-row">
      <aside class="flex max-h-56 shrink-0 flex-col border-b border-default bg-muted/25 md:max-h-none md:w-56 md:border-r md:border-b-0" aria-label="Processor library">
        <div class="space-y-3 p-4">
          <div class="flex items-center justify-between">
            <h3 class="text-sm font-semibold text-highlighted">
              Processors
            </h3>
            <UBadge color="neutral" variant="subtle" size="sm">
              {{ processors.length }}
            </UBadge>
          </div>
          <UInput
            v-model="query"
            icon="i-lucide-search"
            placeholder="Find a processor…"
            aria-label="Filter processors"
            class="w-full"
            size="sm"
          />
          <p class="text-xs leading-relaxed text-muted">
            Drag onto the canvas, or click + to append.
          </p>
        </div>
        <div class="min-h-0 flex-1 space-y-2 overflow-y-auto px-3 pb-4">
          <div
            v-for="processor in filtered"
            :key="processor.id"
            :draggable="!busy && steps.length < 30"
            class="group flex cursor-grab select-none items-start gap-2 rounded-lg border border-default bg-default p-3 active:cursor-grabbing hover:border-primary/50"
            @dragstart="drag($event, processor.id)"
          >
            <UIcon name="i-lucide-grip-vertical" class="mt-0.5 size-3.5 shrink-0 text-dimmed" />
            <div class="min-w-0 flex-1">
              <p class="text-xs font-semibold leading-5 text-highlighted">
                {{ processor.name }}
              </p>
              <p class="mt-1 line-clamp-3 text-[11px] leading-relaxed text-muted">
                {{ processor.description || 'Page-processing Action' }}
              </p>
            </div>
            <UButton
              icon="i-lucide-plus"
              :aria-label="`Add ${processor.name}`"
              color="neutral"
              variant="ghost"
              size="xs"
              :disabled="busy || steps.length >= 30"
              @click="insert(processor.id)"
            />
          </div>
          <p v-if="!filtered.length" class="px-2 py-6 text-center text-xs text-muted">
            {{ query ? 'No matching processors.' : 'No enabled page processors available.' }}
          </p>
        </div>
        <p class="hidden border-t border-default px-4 py-3 text-[11px] leading-relaxed text-muted md:block">
          Parameters are saved as defaults. They can be adjusted when running the workflow.
        </p>
      </aside>
      <div class="relative min-h-0 min-w-0 flex-1">
        <ClientOnly>
          <ActionWorkflowGraph
            :steps="steps"
            :node-keys="entries.map(entry => entry.id)"
            :processors="props.processors"
            :selected="selected"
            :disabled="busy"
            :workspace-id="workspaceId"
            editable
            inline-settings
            @select="selected = $event"
            @insert="insert"
            @reorder="reorder"
            @move="(index, direction) => reorder(index, index + direction)"
            @remove="remove"
            @update="update"
          />
        </ClientOnly>
      </div>
    </div>
    <div class="flex flex-wrap items-center justify-between gap-2 border-t border-default px-4 py-2.5 text-xs text-muted">
      <span class="flex items-center gap-2"><UIcon name="i-lucide-git-commit-horizontal" class="size-4 text-primary" />{{ steps.length }} / 30 steps · Runs from left to right</span>
      <span>Drag headers to reorder · Scroll to zoom · Drag canvas to pan</span>
    </div>
    <p class="sr-only" aria-live="polite">
      {{ announcement }}
    </p>
  </section>
</template>
