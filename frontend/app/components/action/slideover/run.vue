<script setup lang="ts">
import { parse } from 'yaml'
import type {
  ActionParameterDefinition,
  ActionRun,
  ActionRunDetail,
  StartActionRunResponse,
  ExecutableActionProcessorResponse,
  ClearActionRunsResponse,
  ActionCategory,
  ActionTargetSelection,
  ActionTarget
} from '@/types/action'

type ActionRunPageSummary = {
  id: string
  name: string
  imageCount: number
  xmlFileCount: number
}

const props = defineProps<{
  workspaceId: string
  projectId: string
  projectName?: string | null
  pageIds?: string[]
  pages?: ActionRunPageSummary[]
  targetSelection?: ActionTargetSelection | null
  targetSummary?: string | null
}>()

const emit = defineEmits<{
  close: [changed: boolean]
}>()

const toast = useToast()
const actionRunsStore = useActionRunsStore()

const processors = ref<ExecutableActionProcessorResponse[]>([])
const runs = ref<ActionRun[]>([])
const selectedProcessorId = ref('')
const parameterValues = reactive<Record<string, unknown>>({})
const scope = ref<'all' | 'selection'>(props.targetSelection || (props.pageIds?.length ?? 0) > 0 ? 'selection' : 'all')
const categoryFilter = ref<ActionCategory | 'ALL'>('ALL')
const loading = ref(false)
const starting = ref(false)
const cancellingRunId = ref<string | null>(null)
const retryingRunId = ref<string | null>(null)
const clearingHistory = ref(false)
const changed = ref(false)
const expandedRunIds = ref<string[]>([])
const loadingRunDetailIds = ref<string[]>([])
const runDetails = ref<Record<string, ActionRunDetail>>({})
const runHistoryPage = ref(1)
const runHistoryItemsPerPage = ref(5)
let pollTimer: ReturnType<typeof setInterval> | null = null

const selectedPageIds = computed(() => props.pageIds ?? [])
const targetType = computed<ActionTarget>(() => props.targetSelection?.type ?? 'PAGE')
const targetCompatibleProcessors = computed(() => processors.value.filter(item => item.processor.targets?.includes(targetType.value)))
const categoryCompatibleProcessors = computed(() => targetCompatibleProcessors.value.filter(item =>
  categoryFilter.value === 'ALL' || item.processor.category === categoryFilter.value
))
const executableProcessors = computed(() => categoryCompatibleProcessors.value.filter(item => item.executable))
const unavailableProcessors = computed(() => categoryCompatibleProcessors.value.filter(item => !item.executable))
const selectedProcessor = computed(() => executableProcessors.value.find(item => item.processor.id === selectedProcessorId.value) ?? null)
const hasSelection = computed(() => selectedPageIds.value.length > 0)
const submittedPageIds = computed(() => {
  if (props.targetSelection) return props.targetSelection.pages.map(page => page.pageId)
  return scope.value === 'selection' ? selectedPageIds.value : []
})
const submittedTargetSelection = computed<ActionTargetSelection | null>(() => {
  if (props.targetSelection) return props.targetSelection
  if (scope.value === 'selection') {
    return {
      type: 'PAGE',
      pages: selectedPageIds.value.map(pageId => ({ pageId, regionIds: [], textLineIds: [] }))
    }
  }
  return null
})
const scopedPages = computed(() => {
  const pages = props.pages ?? []
  if (scope.value === 'selection') {
    const selected = new Set(selectedPageIds.value)
    return pages.filter(page => selected.has(page.id))
  }
  return pages
})
const compatibilityWarnings = computed(() => {
  const processor = selectedProcessor.value?.processor
  if (!processor || scopedPages.value.length === 0) return []

  const warnings: string[] = []
  if (processor.acceptsImages) {
    const missingImages = scopedPages.value.filter(page => page.imageCount <= 0)
    if (missingImages.length > 0) {
      warnings.push(`${missingImages.length} selected page${missingImages.length === 1 ? '' : 's'} ${missingImages.length === 1 ? 'has' : 'have'} no images.`)
    }
  }
  if (processor.acceptsXml) {
    const missingXml = scopedPages.value.filter(page => page.xmlFileCount <= 0)
    if (missingXml.length > 0) {
      warnings.push(`${missingXml.length} selected page${missingXml.length === 1 ? '' : 's'} ${missingXml.length === 1 ? 'has' : 'have'} no XML.`)
    }
  }
  return warnings
})
const scopeSummary = computed(() => scope.value === 'selection' ? `${selectedPageIds.value.length} selected pages` : 'Total project')
const targetSummary = computed(() => props.targetSummary || (targetType.value === 'PAGE' ? scopeSummary.value : `${targetType.value.replace('_', ' ').toLowerCase()} target`))
const scopeItems = computed(() => [
  { label: 'All pages', value: 'all', icon: 'i-lucide-files' },
  { label: 'Selected pages', value: 'selection', icon: 'i-lucide-check-square', disabled: !hasSelection.value }
])
const categoryItems = computed(() => [
  { label: 'All', value: 'ALL' },
  { label: 'Workflow', value: 'WORKFLOW' },
  { label: 'OCR/HTR', value: 'OCR_HTR' },
  { label: 'Layout', value: 'LAYOUT' }
])
const processorOptions = computed(() => executableProcessors.value.map(item => ({
  label: item.processor.name,
  value: item.processor.id
})))
const parameterEntries = computed(() => {
  const yaml = selectedProcessor.value?.processor.yaml
  if (!yaml) return [] as Array<{ key: string, definition: ActionParameterDefinition }>
  try {
    const parsed = parse(yaml) as { parameters?: Record<string, ActionParameterDefinition> } | null
    return Object.entries(parsed?.parameters ?? {}).map(([key, definition]) => ({ key, definition }))
  } catch {
    return []
  }
})

const activeRuns = computed(() => runs.value.filter(run =>
  ['PENDING', 'DISPATCHING', 'RUNNING', 'IMPORTING_RESULTS', 'CANCEL_REQUESTED'].includes(run.status)
))
const clearableHistoryRuns = computed(() => runs.value.filter(run => run.status === 'COMPLETED' || run.status === 'FAILED'))
const paginatedRuns = computed(() => {
  const start = (runHistoryPage.value - 1) * runHistoryItemsPerPage.value
  return runs.value.slice(start, start + runHistoryItemsPerPage.value)
})
const openPanels = ref<string[]>(['run-history'])
const accordionItems = computed(() => [
  {
    label: `Run History (${runs.value.length})`,
    value: 'run-history',
    slot: 'run-history',
    icon: 'i-lucide-history'
  },
  {
    label: `Parameters (${parameterEntries.value.length})`,
    value: 'parameters',
    slot: 'parameters',
    icon: 'i-lucide-sliders-horizontal'
  }
])

const canStart = computed(() =>
  Boolean(selectedProcessor.value?.executable)
  && !starting.value
  && (scope.value === 'all' || selectedPageIds.value.length > 0)
)

onMounted(async () => {
  await Promise.all([loadProcessors(), loadRuns()])
  pollTimer = setInterval(() => {
    if (activeRuns.value.length > 0) {
      void loadRuns()
    }
  }, 5000)
})

onBeforeUnmount(() => {
  if (pollTimer) {
    clearInterval(pollTimer)
  }
})

watch(selectedProcessorId, () => {
  resetParameters()
})

watch(executableProcessors, () => {
  reconcileSelectedProcessor()
})

watch(() => runs.value.length, () => {
  const maxPage = Math.max(1, Math.ceil(runs.value.length / runHistoryItemsPerPage.value))
  if (runHistoryPage.value > maxPage) {
    runHistoryPage.value = maxPage
  }
})

function reconcileSelectedProcessor() {
  const stillExecutable = executableProcessors.value.some(item => item.processor.id === selectedProcessorId.value)
  if (!stillExecutable) {
    selectedProcessorId.value = executableProcessors.value[0]?.processor.id ?? ''
  }
}

async function loadProcessors() {
  loading.value = true
  try {
    processors.value = await $fetch<ExecutableActionProcessorResponse[]>(
      `/api/workspaces/${props.workspaceId}/actions/projects/${props.projectId}/processors`,
      { query: { target: targetType.value } }
    )
    reconcileSelectedProcessor()
  } catch (error: unknown) {
    const message = error instanceof Error ? error.message : 'Could not load assigned Actions.'
    toast.add({ title: 'Failed to load Actions', description: message, color: 'error' })
  } finally {
    loading.value = false
  }
}

async function loadRuns() {
  try {
    runs.value = await $fetch<ActionRun[]>(
      `/api/workspaces/${props.workspaceId}/actions/projects/${props.projectId}/runs`
    )
    actionRunsStore.upsertRuns(runs.value, props.projectName || props.projectId)
    for (const run of runs.value) {
      const detail = runDetails.value[run.id]
      if (detail) {
        detail.run = run
      }
    }
  } catch {
    // Keep the current history visible if a polling request fails.
  }
}

async function clearRunHistory() {
  if (clearableHistoryRuns.value.length === 0 || clearingHistory.value) return
  clearingHistory.value = true
  try {
    const deletedRunIds = new Set(clearableHistoryRuns.value.map(run => run.id))
    const result = await $fetch<ClearActionRunsResponse>(
      `/api/workspaces/${props.workspaceId}/actions/projects/${props.projectId}/runs/history`,
      { method: 'DELETE' }
    )
    runs.value = runs.value.filter(run => !deletedRunIds.has(run.id))
    for (const runId of deletedRunIds) {
      actionRunsStore.removeRun(runId)
      delete runDetails.value[runId]
    }
    expandedRunIds.value = expandedRunIds.value.filter(runId => !deletedRunIds.has(runId))
    await loadRuns()
    toast.add({
      title: 'Action history cleared',
      description: `${result.deletedCount} completed or failed run${result.deletedCount === 1 ? '' : 's'} removed.`,
      color: 'success',
      icon: 'i-lucide-trash-2'
    })
  } catch (error: unknown) {
    const message = error instanceof Error ? error.message : 'Could not clear completed and failed Action runs.'
    toast.add({ title: 'Clear failed', description: message, color: 'error' })
  } finally {
    clearingHistory.value = false
  }
}

function resetParameters() {
  Object.keys(parameterValues).forEach((key) => {
    delete parameterValues[key]
  })
  parameterEntries.value.forEach(({ key, definition }) => {
    parameterValues[key] = definition.defaultValue ?? definition.default ?? defaultParameterValue(definition)
  })
}

function defaultParameterValue(definition: ActionParameterDefinition) {
  if (definition.type === 'boolean') return false
  if (definition.type === 'number' || definition.type === 'integer') return 0
  return ''
}

async function startRun() {
  if (!selectedProcessor.value || !canStart.value) return
  starting.value = true
  try {
    const result = await $fetch<StartActionRunResponse>(`/api/workspaces/${props.workspaceId}/actions/projects/${props.projectId}/runs`, {
      method: 'POST',
      body: {
        processorDefinitionId: selectedProcessor.value.processor.id,
        pageIds: submittedPageIds.value,
        targetSelection: submittedTargetSelection.value
      }
    })
    actionRunsStore.upsertRun(result.run, props.projectName || props.projectId)
    changed.value = true
    await loadRuns()
    toast.add({ title: 'Action run started', color: 'success', icon: 'i-lucide-play' })
  } catch (error: unknown) {
    const message = error instanceof Error ? error.message : 'Could not start Action run.'
    toast.add({ title: 'Run failed', description: message, color: 'error' })
  } finally {
    starting.value = false
  }
}

async function cancelRun(run: ActionRun) {
  cancellingRunId.value = run.id
  try {
    const updated = await $fetch<ActionRun>(
      `/api/workspaces/${props.workspaceId}/actions/projects/${props.projectId}/runs/${run.id}/cancel`,
      { method: 'POST' }
    )
    actionRunsStore.upsertRun(updated, props.projectName || props.projectId)
    changed.value = true
    await loadRuns()
    toast.add({ title: 'Action cancellation requested', color: 'success' })
  } catch (error: unknown) {
    const message = error instanceof Error ? error.message : 'Could not cancel Action run.'
    toast.add({ title: 'Cancel failed', description: message, color: 'error' })
  } finally {
    cancellingRunId.value = null
  }
}

async function retryRun(run: ActionRun) {
  retryingRunId.value = run.id
  try {
    const result = await $fetch<StartActionRunResponse>(
      `/api/workspaces/${props.workspaceId}/actions/projects/${props.projectId}/runs/${run.id}/retry`,
      { method: 'POST' }
    )
    actionRunsStore.upsertRun(result.run, props.projectName || props.projectId)
    changed.value = true
    await loadRuns()
    toast.add({ title: 'Action retry started', color: 'success', icon: 'i-lucide-rotate-cw' })
  } catch (error: unknown) {
    const message = error instanceof Error ? error.message : 'Could not retry Action run.'
    toast.add({ title: 'Retry failed', description: message, color: 'error' })
  } finally {
    retryingRunId.value = null
  }
}

async function toggleRunExpanded(run: ActionRun) {
  expandedRunIds.value = expandedRunIds.value.includes(run.id)
    ? expandedRunIds.value.filter(id => id !== run.id)
    : [...expandedRunIds.value, run.id]
  if (expandedRunIds.value.includes(run.id) && !runDetails.value[run.id]) {
    await loadRunDetail(run.id)
  }
}

async function loadRunDetail(runId: string) {
  loadingRunDetailIds.value = [...loadingRunDetailIds.value, runId]
  try {
    const detail = await $fetch<ActionRunDetail>(
      `/api/workspaces/${props.workspaceId}/actions/projects/${props.projectId}/runs/${runId}`
    )
    runDetails.value = { ...runDetails.value, [runId]: detail }
  } catch (error: unknown) {
    const message = error instanceof Error ? error.message : 'Could not load Action run details.'
    toast.add({ title: 'Run detail failed', description: message, color: 'error' })
  } finally {
    loadingRunDetailIds.value = loadingRunDetailIds.value.filter(id => id !== runId)
  }
}

function statusColor(status: ActionRun['status']) {
  if (status === 'COMPLETED') return 'success'
  if (status === 'FAILED' || status === 'CANCELLED') return 'error'
  if (status === 'CANCEL_REQUESTED') return 'warning'
  return 'primary'
}

function isActiveRun(run: ActionRun) {
  return ['PENDING', 'DISPATCHING', 'RUNNING', 'IMPORTING_RESULTS', 'CANCEL_REQUESTED'].includes(run.status)
}

function canRetryRun(run: ActionRun) {
  return run.status === 'FAILED' || run.status === 'CANCELLED'
}

function isRunExpanded(run: ActionRun) {
  return expandedRunIds.value.includes(run.id)
}

function isRunDetailLoading(run: ActionRun) {
  return loadingRunDetailIds.value.includes(run.id)
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

function formatDuration(seconds: number | null | undefined) {
  if (seconds === null || seconds === undefined) return 'Running'
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

function formatRunLogs(detail: ActionRunDetail) {
  if (detail.logEvents?.length) {
    return detail.logEvents
      .map(event => `[${formatDate(event.created)}] ${event.level}: ${event.message}`)
      .join('\n')
  }
  return detail.logText || 'No logs recorded.'
}

function runDetailFor(run: ActionRun) {
  return runDetails.value[run.id] ?? null
}

function formatRunDetailCreated(run: ActionRun) {
  return formatDate(runDetailFor(run)?.run.created ?? null)
}

function formatRunDetailUpdated(run: ActionRun) {
  return formatDate(runDetailFor(run)?.run.updated ?? null)
}

function formatRunDetailDuration(run: ActionRun) {
  return formatDuration(runDetailFor(run)?.durationSeconds)
}

function formatRunDetailResultSummary(run: ActionRun) {
  return formatResultSummary(runDetailFor(run)?.resultSummary)
}

function formatRunDetailLogs(run: ActionRun) {
  const detail = runDetailFor(run)
  return detail ? formatRunLogs(detail) : 'No logs recorded.'
}

function close() {
  emit('close', changed.value)
}
</script>

<template>
  <USlideover
    side="right"
    :ui="{ content: 'max-w-3xl' }"
    title="Run Action"
    icon="i-lucide-play"
    :close="{ onClick: close }"
  >
    <template #body>
      <div class="space-y-5">
        <div class="space-y-4">
          <UFormField label="Action">
            <USelectMenu
              v-model="selectedProcessorId"
              :items="processorOptions"
              value-key="value"
              searchable
              searchable-placeholder="Filter Actions..."
              :loading="loading"
              placeholder="Select an Action"
              class="w-full"
            />
          </UFormField>

          <UAlert
            color="neutral"
            variant="subtle"
            icon="i-lucide-wand-sparkles"
            :title="`Target: ${targetSummary}`"
            :description="`Only Actions that support ${targetType.replace('_', ' ')} targets are shown.`"
          />

          <UTabs
            v-model="categoryFilter"
            :items="categoryItems"
            variant="pill"
            color="neutral"
            :content="false"
          />

          <UAlert
            v-if="!loading && processors.length === 0"
            color="neutral"
            variant="subtle"
            icon="i-lucide-circle-play"
            title="No Actions are assigned to this project or workspace."
          />

          <UAlert
            v-else-if="!loading && executableProcessors.length === 0"
            color="warning"
            variant="subtle"
            icon="i-lucide-lock"
            title="No Actions are available for your role right now."
          />

          <div v-if="unavailableProcessors.length > 0" class="space-y-2">
            <p class="text-xs font-medium text-muted">
              Unavailable Actions
            </p>
            <div class="divide-y divide-default rounded-sm border border-default">
              <div
                v-for="item in unavailableProcessors"
                :key="item.processor.id"
                class="flex items-center justify-between gap-3 px-3 py-2"
              >
                <div class="min-w-0">
                  <p class="truncate text-sm">
                    {{ item.processor.name }}
                  </p>
                  <p class="truncate text-xs text-muted">
                    {{ item.blockedReason || 'Unavailable' }}
                  </p>
                </div>
                <UBadge size="sm" variant="soft" color="neutral">
                  Hidden
                </UBadge>
              </div>
            </div>
          </div>

          <UAlert
            v-if="selectedProcessor"
            color="neutral"
            variant="subtle"
            icon="i-lucide-lock-keyhole"
            :title="selectedProcessor.processor.lockMode === 'PROJECT' ? 'This Action locks the full project while it runs.' : 'This Action locks the selected pages while it runs.'"
          />

          <UAlert
            v-for="warning in compatibilityWarnings"
            :key="warning"
            color="warning"
            variant="subtle"
            icon="i-lucide-triangle-alert"
            :title="warning"
            description="The Action can still run, but the processor will not receive that input type for those pages."
          />

          <UFormField label="Scope">
            <div class="space-y-2">
              <UTabs
                v-if="!props.targetSelection"
                v-model="scope"
                :items="scopeItems"
                variant="pill"
                color="neutral"
                :content="false"
                class="w-full"
              />
              <p class="text-xs text-muted">
                {{ targetSummary }}
              </p>
            </div>
          </UFormField>
        </div>

        <USeparator />

        <UAccordion
          v-model="openPanels"
          :items="accordionItems"
          type="multiple"
          :ui="{
            item: 'border-b border-default last:border-b-0',
            trigger: 'px-0 py-3 hover:bg-transparent',
            content: 'px-0 pb-4'
          }"
        >
          <template #parameters>
            <div class="space-y-3 p-1">
              <p v-if="parameterEntries.length > 0" class="text-sm text-muted">
                Parameter values are fixed by the Action definition.
              </p>

              <p v-if="parameterEntries.length === 0" class="text-sm text-muted">
                This Action does not declare parameters.
              </p>

              <div v-else class="grid gap-3">
                <UFormField
                  v-for="entry in parameterEntries"
                  :key="entry.key"
                  :label="entry.key"
                  :hint="entry.definition.description"
                >
                  <USwitch
                    v-if="entry.definition.type === 'boolean'"
                    :model-value="Boolean(parameterValues[entry.key])"
                    disabled
                  />
                  <UInput
                    v-else
                    :model-value="String(parameterValues[entry.key] ?? '')"
                    :type="entry.definition.type === 'number' || entry.definition.type === 'integer' ? 'number' : 'text'"
                    :min="entry.definition.min"
                    :max="entry.definition.max"
                    disabled
                  />
                </UFormField>
              </div>
            </div>
          </template>

          <template #run-history>
            <div class="space-y-3 p-1">
              <div class="flex flex-wrap items-center justify-between gap-2">
                <p class="text-xs text-muted">
                  {{ runs.length }} run{{ runs.length === 1 ? '' : 's' }}
                </p>
                <div class="flex items-center gap-2">
                  <UButton
                    icon="i-lucide-trash-2"
                    color="neutral"
                    variant="ghost"
                    size="sm"
                    :disabled="clearableHistoryRuns.length === 0"
                    :loading="clearingHistory"
                    @click="clearRunHistory"
                  >
                    Clear completed/failed
                  </UButton>
                  <UButton
                    icon="i-lucide-refresh-cw"
                    color="neutral"
                    variant="ghost"
                    size="sm"
                    @click="loadRuns"
                  >
                    Refresh
                  </UButton>
                </div>
              </div>

              <p v-if="runs.length === 0" class="text-sm text-muted">
                No Action runs for this project yet.
              </p>

              <div v-else class="divide-y divide-default">
                <div
                  v-for="run in paginatedRuns"
                  :key="run.id"
                  class="space-y-2 py-3 first:pt-0 last:pb-0"
                >
                  <div class="flex items-center justify-between gap-3">
                    <button type="button" class="min-w-0 text-left" @click="toggleRunExpanded(run)">
                      <p class="truncate text-sm font-medium">
                        {{ run.processorName }}
                      </p>
                      <p class="truncate text-xs text-muted">
                        {{ run.pageIds.length }} pages · {{ run.statusMessage || run.processorKey }}
                      </p>
                    </button>
                    <div class="flex items-center gap-2">
                      <UBadge size="sm" variant="soft" :color="statusColor(run.status)">
                        {{ run.status }}
                      </UBadge>
                      <UButton
                        v-if="canRetryRun(run)"
                        color="neutral"
                        variant="ghost"
                        icon="i-lucide-rotate-cw"
                        size="sm"
                        :loading="retryingRunId === run.id"
                        aria-label="Retry Action run"
                        @click="retryRun(run)"
                      />
                      <UButton
                        v-if="isActiveRun(run)"
                        color="warning"
                        variant="ghost"
                        icon="i-lucide-ban"
                        size="sm"
                        :loading="cancellingRunId === run.id"
                        @click="cancelRun(run)"
                      />
                      <UButton
                        color="neutral"
                        variant="ghost"
                        :icon="isRunExpanded(run) ? 'i-lucide-chevron-up' : 'i-lucide-chevron-down'"
                        size="sm"
                        aria-label="Show Action run details"
                        @click="toggleRunExpanded(run)"
                      />
                    </div>
                  </div>
                  <UProgress :model-value="run.progressPercent" />
                  <p v-if="run.errorMessage" class="text-xs text-error">
                    {{ run.errorMessage }}
                  </p>
                  <div v-if="isRunExpanded(run)" class="space-y-3 border-t border-default pt-3">
                    <div v-if="isRunDetailLoading(run)" class="space-y-2">
                      <USkeleton class="h-5 w-1/2" />
                      <USkeleton class="h-24 w-full" />
                    </div>
                    <template v-else-if="runDetails[run.id]">
                      <dl class="grid grid-cols-[auto_minmax(0,1fr)] gap-x-3 gap-y-1 text-xs">
                        <dt class="text-muted">
                          Created
                        </dt>
                        <dd>
                          {{ formatRunDetailCreated(run) }}
                        </dd>
                        <dt class="text-muted">
                          Updated
                        </dt>
                        <dd>
                          {{ formatRunDetailUpdated(run) }}
                        </dd>
                        <dt class="text-muted">
                          Duration
                        </dt>
                        <dd>
                          {{ formatRunDetailDuration(run) }}
                        </dd>
                      </dl>
                      <div>
                        <p class="mb-1 text-xs font-medium text-muted">
                          Result Summary
                        </p>
                        <pre class="max-h-40 overflow-auto rounded-sm bg-elevated p-2 text-xs">{{ formatRunDetailResultSummary(run) }}</pre>
                      </div>
                      <div>
                        <p class="mb-1 text-xs font-medium text-muted">
                          Logs
                        </p>
                        <pre class="max-h-56 overflow-auto rounded-sm bg-elevated p-2 text-xs">{{ formatRunDetailLogs(run) }}</pre>
                      </div>
                    </template>
                  </div>
                </div>
              </div>

              <div v-if="runs.length > runHistoryItemsPerPage" class="flex justify-end pt-1">
                <UPagination
                  v-model:page="runHistoryPage"
                  :total="runs.length"
                  :items-per-page="runHistoryItemsPerPage"
                  show-edges
                  :sibling-count="1"
                  size="sm"
                />
              </div>
            </div>
          </template>
        </UAccordion>
      </div>
    </template>

    <template #footer>
      <div class="flex justify-end gap-2">
        <UButton color="neutral" variant="ghost" @click="close">
          Close
        </UButton>
        <UButton
          icon="i-lucide-play"
          :loading="starting"
          :disabled="!canStart"
          @click="startRun"
        >
          Start Action
        </UButton>
      </div>
    </template>
  </USlideover>
</template>
