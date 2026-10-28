<script setup lang="ts">
import type { ActionRunImpact, ExecutableActionProcessor } from '~/types/action'
import type { ActionWorkflow, ActionWorkflowRun, ActionWorkflowStep } from '~/types/action-workflow'
import { extractApiErrorDetails } from '~/utils/api-error'
import { annotationLevelLabels } from '~/utils/action-impact'
import { describeWorkflowRole, workflowCuratorSteps, workflowExecuteRole } from '~/utils/action-workflow'

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
const reviewOpen = ref(false)
const reviewError = ref('')
const reviewSearch = ref('')
const reviewTab = ref('pages')
const impact = ref<ActionRunImpact | null>(null)
const excluded = ref<string[]>([])
let generation = 0
const selected = computed(() => workflows.value.find(workflow => workflow.id === selectedId.value))
const processorResponses = computed(() => [...new Map([...props.processors, ...contextProcessors.value].map(item => [item.processor.id, item])).values()])
const processorDefinitions = computed(() => processorResponses.value.map(item => item.processor))
const chosenPages = computed(() => scope.value === 'selection' ? props.pageIds : [])
const included = computed(() => impact.value?.pages.filter(page => !excluded.value.includes(page.pageId)) ?? [])
const reviewTotal = computed(() => impact.value ? impact.value.pages.length + impact.value.skippedPages.length : 0)
const reviewProgress = computed(() => reviewTotal.value ? Math.round(included.value.length / reviewTotal.value * 100) : 0)
const visibleReviewPages = computed(() => impact.value?.pages.filter(page =>
  (page.name || page.pageId).toLocaleLowerCase().includes(reviewSearch.value.trim().toLocaleLowerCase())) ?? [])
const visibleSkippedPages = computed(() => impact.value?.skippedPages.filter(page =>
  [page.name, page.pageId, page.reason].some(value => value?.toLocaleLowerCase().includes(reviewSearch.value.trim().toLocaleLowerCase()))) ?? [])
const reviewStatus = computed(() => busy.value ? impact.value ? 'Starting' : 'Checking' : reviewError.value ? 'Needs attention' : !included.value.length ? 'No pages selected' : impact.value?.skippedPages.length ? 'Pages skipped' : 'Ready')
// Steps run as whoever starts the workflow, so one curator-only step makes the whole workflow curator-only.
const curatorSteps = computed(() => workflowCuratorSteps(steps.value, processorDefinitions.value))
const requiredRole = computed(() => workflowExecuteRole(steps.value, processorDefinitions.value))
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
  reviewOpen.value = false
  reviewError.value = ''
  reviewSearch.value = ''
  reviewTab.value = 'pages'
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
  const previousExcluded = new Set(excluded.value)
  busy.value = true
  reviewOpen.value = true
  reviewError.value = ''
  impact.value = null
  try {
    impact.value = await $fetch<ActionRunImpact>(`${base.value}/impact`, { method: 'POST', body: body() })
    excluded.value = impact.value.pages.filter(page => previousExcluded.has(page.pageId)).map(page => page.pageId)
    if (!impact.value.pages.length) reviewError.value = 'No pages satisfy the first step’s input requirements.'
  } catch (cause) {
    reviewError.value = extractApiErrorDetails(cause, 'Could not review workflow.').message
  } finally {
    busy.value = false
  }
}
async function start() {
  if (!selected.value || !included.value.length || busy.value) return
  busy.value = true
  reviewError.value = ''
  try {
    const run = await $fetch<ActionWorkflowRun>(base.value, { method: 'POST', body: { ...body(), pageIds: included.value.map(page => page.pageId) } })
    store.upsertWorkflowRun(run, props.projectName || props.projectId)
    resetReview()
    void store.refreshProjectRuns(props.workspaceId, props.projectId, props.projectName).catch(() => {})
    emit('started')
  } catch (cause) {
    reviewError.value = extractApiErrorDetails(cause, 'Could not start workflow.').message
  } finally {
    busy.value = false
  }
}
function updateStep(index: number, step: ActionWorkflowStep) {
  if (busy.value || !steps.value[index]) return
  steps.value[index] = step
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
    <p v-if="loading" role="status" class="text-sm text-muted">
      Loading workflow…
    </p>
    <p v-else-if="!selected && !error" class="text-sm text-muted">
      This workflow is no longer available. Go back to Actions to choose another.
    </p>
    <template v-if="selected">
      <div class="min-w-0 space-y-1">
        <p class="text-xs font-medium text-muted">
          Selected workflow
        </p>
        <h3 class="text-lg font-semibold tracking-tight text-highlighted">
          {{ selected.name }}
        </h3>
        <p v-if="selected.description" class="text-sm text-muted">
          {{ selected.description }}
        </p>
        <p v-if="requiredRole" class="flex flex-wrap items-center gap-2 pt-1 text-xs text-muted">
          <ActionExecuteRoleBadge :role="requiredRole" />
          <span>{{ describeWorkflowRole(curatorSteps, steps.length) }}</span>
        </p>
      </div>
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
      <UFormField
        label="Workflow steps"
        description="Adjust each step for this run. Changes here apply only to this run."
      >
        <div class="h-[min(75vh,48rem)] min-h-[32rem] overflow-hidden rounded-lg border border-default">
          <ClientOnly>
            <ActionWorkflowGraph
              :steps="steps"
              :processors="processorDefinitions"
              :selected="selectedStepIndex"
              :workspace-id="workspaceId"
              :project-id="projectId"
              :workflow-id="selectedId"
              :disabled="busy"
              inline-settings
              @select="selectedStepIndex = $event"
              @update="updateStep"
            />
          </ClientOnly>
        </div>
      </UFormField>
      <UPopover
        v-model:open="reviewOpen"
        :content="{ align: 'start', side: 'top', sideOffset: 10 }"
        :ui="{ content: 'z-[70] max-h-(--reka-popover-content-available-height) overflow-hidden rounded-3xl bg-default p-1.5 shadow-xl ring ring-default' }"
      >
        <UButton
          label="Review workflow"
          :icon="busy ? 'i-lucide-loader-circle' : 'i-lucide-play'"
          :ui="{ leadingIcon: busy ? 'animate-spin motion-reduce:animate-none' : '' }"
          :disabled="busy || !steps.length || !processorsReady || loadingProcessors || !!blocked.length"
          @click="!impact && review()"
        />
        <template #content>
          <div class="flex max-h-[min(36rem,calc(var(--reka-popover-content-available-height)-0.75rem))] w-112 max-w-[calc(100vw-2rem)] flex-col overflow-hidden rounded-2xl border border-default bg-default" aria-label="Review workflow pages" :aria-busy="busy">
            <div class="shrink-0 space-y-3 rounded-2xl border-b border-default bg-muted/50 p-3">
              <div class="flex items-start gap-3">
                <div class="flex size-11 shrink-0 items-center justify-center rounded-xl border border-default bg-default shadow-xs">
                  <UIcon name="i-lucide-workflow" class="size-5 text-highlighted" />
                </div>
                <div class="min-w-0 flex-1">
                  <h3 class="text-sm font-semibold text-highlighted">
                    Review affected pages
                  </h3>
                  <p class="mt-0.5 break-words text-sm text-muted">
                    {{ selected.name }}
                  </p>
                </div>
                <UButton
                  icon="i-lucide-x"
                  aria-label="Close workflow review"
                  size="xs"
                  color="neutral"
                  variant="ghost"
                  @click="reviewOpen = false"
                />
              </div>
              <div v-if="!reviewError || impact" class="flex flex-col gap-3 rounded-xl border border-default bg-default p-3 shadow-xs sm:flex-row sm:items-center">
                <div class="min-w-0 flex-1 space-y-2">
                  <div class="flex items-center justify-between gap-3 text-sm">
                    <span>{{ busy && !impact ? 'Checking pages' : 'Pages included' }}</span>
                    <span v-if="impact" class="shrink-0 text-muted tabular-nums">{{ included.length }} of {{ reviewTotal }}</span>
                  </div>
                  <UProgress
                    :model-value="impact ? reviewProgress : undefined"
                    color="neutral"
                    size="sm"
                    aria-label="Pages included in workflow"
                  />
                </div>
                <UButton
                  label="Start workflow"
                  icon="i-lucide-play"
                  class="w-full justify-center sm:w-auto"
                  :loading="busy && !!impact"
                  :disabled="busy || !impact || !included.length"
                  @click="start"
                />
              </div>
            </div>
            <div class="flex shrink-0 flex-wrap items-center justify-between gap-3 border-b border-default px-4 py-3 text-sm">
              <UButton
                label="Review again"
                icon="i-lucide-refresh-cw"
                size="sm"
                color="neutral"
                variant="ghost"
                :disabled="busy"
                @click="review"
              />
              <UBadge
                :color="reviewError ? 'error' : busy ? 'neutral' : !included.length || impact?.skippedPages.length ? 'warning' : 'success'"
                :icon="busy ? 'i-lucide-loader-circle' : reviewError ? 'i-lucide-circle-alert' : !included.length || impact?.skippedPages.length ? 'i-lucide-triangle-alert' : 'i-lucide-circle-check'"
                :ui="{ leadingIcon: busy ? 'animate-spin motion-reduce:animate-none' : '' }"
                variant="soft"
                class="rounded-full"
              >
                {{ reviewStatus }}
              </UBadge>
            </div>
            <div v-if="busy && !impact" class="min-h-0 space-y-3 overflow-y-auto p-5" role="status">
              <USkeleton class="h-4 w-2/3" />
              <USkeleton class="h-12 w-full" />
              <USkeleton class="h-12 w-full" />
              <p class="text-sm text-muted">
                Checking inputs and affected annotations…
              </p>
            </div>
            <div v-else-if="!impact && reviewError" class="min-h-0 space-y-4 overflow-y-auto px-5 py-6 text-center">
              <div class="mx-auto flex size-12 items-center justify-center rounded-full bg-muted">
                <UIcon name="i-lucide-cloud-alert" class="size-6 text-muted" />
              </div>
              <h3 class="font-semibold text-highlighted">
                Couldn’t review pages
              </h3>
              <p class="break-words text-sm text-muted" role="alert">
                {{ reviewError }}
              </p>
              <UButton
                label="Try again"
                icon="i-lucide-refresh-cw"
                color="neutral"
                variant="outline"
                @click="review"
              />
            </div>
            <div v-else-if="impact" class="min-h-0 overflow-y-auto">
              <div class="flex items-center justify-between gap-3 border-b border-default px-4 py-3 text-sm">
                <span class="flex items-center gap-2 text-muted"><UIcon name="i-lucide-folder" class="size-4" />Project</span>
                <span class="min-w-0 break-words rounded-full bg-muted px-2.5 py-1 text-right">{{ projectName || projectId }}</span>
              </div>
              <div class="flex items-center justify-between gap-3 border-b border-default px-4 py-3 text-sm">
                <span class="flex items-center gap-2 text-muted"><UIcon name="i-lucide-list-ordered" class="size-4" />Workflow steps</span>
                <span class="rounded-full bg-muted px-2.5 py-1 tabular-nums">{{ steps.length }}</span>
              </div>
              <div v-if="reviewError" class="border-b border-default bg-error/5 px-4 py-3 text-sm text-error" role="alert">
                {{ reviewError }}
              </div>
              <div class="space-y-3 p-4">
                <p class="text-sm text-muted">
                  Annotations listed below may be replaced. Uncheck pages you want to keep.
                </p>
                <UTabs
                  v-model="reviewTab"
                  :items="[{ label: `Pages (${impact.pages.length})`, value: 'pages' }, { label: `Skipped (${impact.skippedPages.length})`, value: 'skipped' }]"
                  :content="false"
                  color="neutral"
                  size="sm"
                  :ui="{ trigger: 'tabular-nums' }"
                />
                <UInput
                  v-model="reviewSearch"
                  icon="i-lucide-search"
                  placeholder="Find pages…"
                  aria-label="Find workflow review pages"
                  class="w-full"
                />
                <template v-if="reviewTab === 'pages'">
                  <div class="flex items-center justify-between gap-2 text-xs text-muted">
                    <span class="tabular-nums">{{ included.length }} included · {{ excluded.length }} excluded</span>
                    <UButton
                      :label="included.length ? 'Exclude all' : 'Include all'"
                      size="xs"
                      color="neutral"
                      variant="ghost"
                      :disabled="busy"
                      @click="excluded = included.length ? impact.pages.map(page => page.pageId) : []"
                    />
                  </div>
                  <div class="space-y-3" aria-label="Workflow page selection">
                    <UCheckbox
                      v-for="page in visibleReviewPages"
                      :key="page.pageId"
                      :label="page.name || page.pageId"
                      :description="page.warningPrecision === 'UNKNOWN' ? 'A step does not declare which annotations it replaces.' : page.affectedLevels.map(level => annotationLevelLabels[level]).join(', ') || 'No declared annotation changes.'"
                      :model-value="!excluded.includes(page.pageId)"
                      :disabled="busy"
                      :ui="{ label: 'break-words', description: 'break-words' }"
                      @update:model-value="include(page.pageId, $event === true)"
                    />
                    <p v-if="!visibleReviewPages.length" class="text-sm text-muted">
                      No matching pages.
                    </p>
                  </div>
                </template>
                <div v-else class="divide-y divide-default" aria-label="Skipped workflow pages">
                  <div v-for="page in visibleSkippedPages" :key="page.pageId" class="space-y-1 py-3 first:pt-0 last:pb-0">
                    <p class="break-words text-sm font-medium">
                      {{ page.name || page.pageId }}
                    </p>
                    <p class="break-words text-xs text-muted">
                      {{ page.reason }}
                    </p>
                  </div>
                  <p v-if="!visibleSkippedPages.length" class="text-sm text-muted">
                    {{ impact.skippedPages.length ? 'No matching skipped pages.' : 'No pages will be skipped.' }}
                  </p>
                </div>
              </div>
            </div>
          </div>
        </template>
      </UPopover>
    </template>
    <UAlert
      v-if="error"
      title="Workflow unavailable"
      :description="error"
      color="error"
    />
  </div>
</template>
