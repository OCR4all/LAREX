<script setup lang="ts">
import { parse } from 'yaml'
import { extractApiErrorDetails } from '@/utils/api-error'
import { useBlockEditorCanvasInteractions } from '@/composables/editor/use-canvas-interaction-blocker'
import type {
  ActionParameterDefinition,
  ActionRun,
  ActionRunDetail,
  StartActionRunResponse,
  ExecutableActionProcessorResponse,
  ClearActionRunsResponse,
  ActionCategory,
  ActionTargetSelection,
  ActionTarget,
  ActionImageVariantSelection
} from '@/types/action'

useBlockEditorCanvasInteractions()

type ActionRunPageImageVariantSummary = {
  id: string
  fileName: string
  variant?: string | null
}

type ActionRunPageSummary = {
  id: string
  name: string
  imageCount: number
  xmlFileCount: number
  imageVariants?: ActionRunPageImageVariantSummary[]
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
const imageVariantMode = ref<'global' | 'perPage'>('global')
const selectedImageVariant = ref('')
const fallbackImage = ref(false)
const pageImageVariants = reactive<Record<string, string>>({})
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
    if (pagesMissingSelectedVariant.value.length > 0) {
      warnings.push(fallbackImage.value
        ? `${pagesMissingSelectedVariant.value.length} selected page${pagesMissingSelectedVariant.value.length === 1 ? '' : 's'} will use a fallback image.`
        : `${pagesMissingSelectedVariant.value.length} selected page${pagesMissingSelectedVariant.value.length === 1 ? '' : 's'} will be skipped because the selected image variant is missing.`)
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
const selectedProcessorAcceptsImages = computed(() => selectedProcessor.value?.processor.acceptsImages === true)
const imageVariantOptions = computed(() => {
  const variants = new Set<string>()
  for (const page of scopedPages.value) {
    for (const image of page.imageVariants ?? []) {
      const variant = image.variant?.trim()
      if (variant) variants.add(variant)
    }
  }
  return Array.from(variants)
    .sort((left, right) => left.localeCompare(right))
    .map(variant => ({ label: variant, value: variant }))
})
const imageVariantModeItems = computed(() => [
  { label: 'Global', value: 'global', icon: 'i-lucide-globe' },
  { label: 'Per page', value: 'perPage', icon: 'i-lucide-files' }
])
const imageVariantByPageId = computed(() => {
  const result: Record<string, Set<string>> = {}
  for (const page of scopedPages.value) {
    result[page.id] = new Set((page.imageVariants ?? [])
      .map(image => image.variant?.trim())
      .filter((variant): variant is string => Boolean(variant)))
  }
  return result
})
const pagesMissingSelectedVariant = computed(() => {
  if (!selectedProcessorAcceptsImages.value) return []
  return scopedPages.value.filter((page) => {
    const available = imageVariantByPageId.value[page.id] ?? new Set<string>()
    const wanted = imageVariantMode.value === 'global' ? selectedImageVariant.value : pageImageVariants[page.id]
    return typeof wanted === 'string' && wanted.length > 0 && !available.has(wanted)
  })
})
const selectedImageVariantSummary = computed(() => {
  if (!selectedProcessorAcceptsImages.value || imageVariantOptions.value.length === 0) return null
  const missing = pagesMissingSelectedVariant.value.length
  if (imageVariantMode.value === 'global') {
    return `${selectedImageVariant.value || 'No variant'} · ${fallbackImage.value ? 'fallback enabled' : 'missing pages skipped'}${missing > 0 ? ` · ${missing} missing` : ''}`
  }
  return `${scopedPages.value.length} page variants · ${fallbackImage.value ? 'fallback enabled' : 'missing pages skipped'}${missing > 0 ? ` · ${missing} missing` : ''}`
})
const submittedImageVariantSelection = computed<ActionImageVariantSelection | null>(() => {
  if (!selectedProcessorAcceptsImages.value || imageVariantOptions.value.length === 0) return null
  if (imageVariantMode.value === 'global') {
    if (!selectedImageVariant.value) return null
    return {
      mode: 'GLOBAL',
      variant: selectedImageVariant.value,
      fallbackImage: fallbackImage.value
    }
  }
  const pageVariants: Record<string, string> = {}
  for (const page of scopedPages.value) {
    const variant = pageImageVariants[page.id]
    if (variant) {
      pageVariants[page.id] = variant
    }
  }
  if (Object.keys(pageVariants).length === 0) return null
  return {
    mode: 'PER_PAGE',
    pageVariants,
    fallbackImage: fallbackImage.value
  }
})
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

const clearableHistoryRuns = computed(() => runs.value.filter(run => run.status === 'COMPLETED' || run.status === 'FAILED'))
const paginatedRuns = computed(() => {
  const start = (runHistoryPage.value - 1) * runHistoryItemsPerPage.value
  return runs.value.slice(start, start + runHistoryItemsPerPage.value)
})
const openPanels = ref<string[]>([])
const accordionItems = computed(() => {
  const items = []
  if (selectedProcessorAcceptsImages.value) {
    items.push({
      label: 'Images',
      value: 'images',
      slot: 'images',
      icon: 'i-lucide-image'
    })
  }
  items.push({
    label: `Run History (${runs.value.length})`,
    value: 'run-history',
    slot: 'run-history',
    icon: 'i-lucide-history'
  })
  items.push({
    label: `Parameters (${parameterEntries.value.length})`,
    value: 'parameters',
    slot: 'parameters',
    icon: 'i-lucide-sliders-horizontal'
  })
  return items
})

const canStart = computed(() =>
  Boolean(selectedProcessor.value?.executable)
  && !starting.value
  && (scope.value === 'all' || selectedPageIds.value.length > 0)
)

onMounted(async () => {
  await Promise.all([loadProcessors(), loadRuns()])
})

watch(() => actionRunsStore.runsArray, (trackedRuns) => {
  const projectRuns = trackedRuns.filter(run => run.projectId === props.projectId)
  if (projectRuns.length === 0 && runs.value.length > 0) return
  runs.value = projectRuns
  for (const run of projectRuns) {
    const detail = runDetails.value[run.id]
    if (detail) detail.run = run
  }
}, { deep: false })

watch(selectedProcessorId, () => {
  resetParameters()
  reconcileImageVariantSelection()
})

watch(executableProcessors, () => {
  reconcileSelectedProcessor()
})

watch([scopedPages, imageVariantOptions], () => {
  reconcileImageVariantSelection()
}, { immediate: true })

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
  reconcileImageVariantSelection()
}

function reconcileImageVariantSelection() {
  const options = imageVariantOptions.value
  if (options.length === 0) {
    selectedImageVariant.value = ''
    Object.keys(pageImageVariants).forEach(key => Reflect.deleteProperty(pageImageVariants, key))
    return
  }

  if (!options.some(item => item.value === selectedImageVariant.value)) {
    selectedImageVariant.value = options[0]?.value ?? ''
  }

  const scopedPageIds = new Set(scopedPages.value.map(page => page.id))
  Object.keys(pageImageVariants).forEach((pageId) => {
    if (!scopedPageIds.has(pageId)) {
      Reflect.deleteProperty(pageImageVariants, pageId)
    }
  })

  for (const page of scopedPages.value) {
    const available = Array.from(imageVariantByPageId.value[page.id] ?? [])
    if (available.length === 0) continue
    const current = pageImageVariants[page.id]
    if (!current || !available.includes(current)) {
      pageImageVariants[page.id] = available.includes(selectedImageVariant.value)
        ? selectedImageVariant.value
        : (available[0] ?? selectedImageVariant.value)
    }
  }
}

function imageVariantOptionsForPage(page: ActionRunPageSummary) {
  const variants = Array.from(imageVariantByPageId.value[page.id] ?? [])
  return variants.map(variant => ({ label: variant, value: variant }))
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
    }
    runDetails.value = Object.fromEntries(
      Object.entries(runDetails.value).filter(([runId]) => !deletedRunIds.has(runId))
    )
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
    parameterValues[key] = undefined
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

function concurrencyErrorDetails(error: unknown) {
  const details = extractApiErrorDetails(error, 'This Action has reached its concurrency limit.')
  const normalizedMessage = details.message.toLowerCase()
  const isConcurrencyError = details.code === 'ACTION_CONCURRENCY_LIMIT_REACHED'
    || (details.status === 409 && normalizedMessage.includes('concurrency limit'))
  return { details, isConcurrencyError }
}

function queuePositionText(run: Pick<ActionRun, 'queuePosition'>) {
  if (!run.queuePosition || run.queuePosition < 1) return null
  return `Queue position ${run.queuePosition}`
}

function runSummaryText(run: ActionRun) {
  const queueText = queuePositionText(run)
  if (queueText) {
    return `${run.pageIds.length} pages · ${queueText}`
  }
  return `${run.pageIds.length} pages · ${run.statusMessage || run.processorKey}`
}

function queuedToastDescription(run: ActionRun) {
  const queueText = queuePositionText(run)
  return queueText
    ? `${queueText}. The Action will start automatically when a slot becomes available.`
    : 'The Action will start automatically when a slot becomes available.'
}

async function submitRun(options: { enqueueIfBusy?: boolean } = {}) {
  if (!selectedProcessor.value || !canStart.value) return null
  starting.value = true
  try {
    const result = await $fetch<StartActionRunResponse>(`/api/workspaces/${props.workspaceId}/actions/projects/${props.projectId}/runs`, {
      method: 'POST',
      body: {
        processorDefinitionId: selectedProcessor.value.processor.id,
        pageIds: submittedPageIds.value,
        targetSelection: submittedTargetSelection.value,
        imageVariantSelection: submittedImageVariantSelection.value,
        enqueueIfBusy: options.enqueueIfBusy ?? false
      }
    })
    actionRunsStore.upsertRun(result.run, props.projectName || props.projectId)
    changed.value = true
    toast.add({
      title: result.run.status === 'QUEUED' ? 'Action run queued' : 'Action run started',
      description: result.run.status === 'QUEUED' ? queuedToastDescription(result.run) : undefined,
      color: result.run.status === 'QUEUED' ? 'warning' : 'success',
      icon: result.run.status === 'QUEUED' ? 'i-lucide-list-ordered' : 'i-lucide-play'
    })
    close()
    return result
  } catch (error: unknown) {
    const { details, isConcurrencyError } = concurrencyErrorDetails(error)
    if (!options.enqueueIfBusy && isConcurrencyError) {
      toast.add({
        title: 'Action is already running',
        description: details.message,
        color: 'warning',
        icon: 'i-lucide-clock-3',
        actions: [
          {
            label: 'Schedule',
            color: 'warning',
            variant: 'solid',
            onClick: () => {
              void submitRun({ enqueueIfBusy: true })
            }
          },
          {
            label: 'Later',
            color: 'neutral',
            variant: 'outline',
            onClick: () => {}
          }
        ]
      })
      return null
    }
    toast.add({ title: 'Run failed', description: details.message, color: 'error' })
    return null
  } finally {
    starting.value = false
  }
}

async function startRun() {
  await submitRun()
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

async function retryRun(run: ActionRun, options: { enqueueIfBusy?: boolean } = {}) {
  retryingRunId.value = run.id
  try {
    const result = await $fetch<StartActionRunResponse>(
      `/api/workspaces/${props.workspaceId}/actions/projects/${props.projectId}/runs/${run.id}/retry`,
      {
        method: 'POST',
        query: {
          enqueueIfBusy: options.enqueueIfBusy ?? false
        }
      }
    )
    actionRunsStore.upsertRun(result.run, props.projectName || props.projectId)
    changed.value = true
    await loadRuns()
    toast.add({
      title: result.run.status === 'QUEUED' ? 'Action retry queued' : 'Action retry started',
      description: result.run.status === 'QUEUED' ? queuedToastDescription(result.run) : undefined,
      color: result.run.status === 'QUEUED' ? 'warning' : 'success',
      icon: result.run.status === 'QUEUED' ? 'i-lucide-list-ordered' : 'i-lucide-rotate-cw'
    })
  } catch (error: unknown) {
    const { details, isConcurrencyError } = concurrencyErrorDetails(error)
    if (!options.enqueueIfBusy && isConcurrencyError) {
      toast.add({
        title: 'Action is already running',
        description: details.message,
        color: 'warning',
        icon: 'i-lucide-clock-3',
        actions: [
          {
            label: 'Schedule',
            color: 'warning',
            variant: 'solid',
            onClick: () => {
              void retryRun(run, { enqueueIfBusy: true })
            }
          },
          {
            label: 'Later',
            color: 'neutral',
            variant: 'outline',
            onClick: () => {}
          }
        ]
      })
      return
    }
    toast.add({ title: 'Retry failed', description: details.message, color: 'error' })
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
  if (status === 'QUEUED' || status === 'CANCEL_REQUESTED') return 'warning'
  return 'primary'
}

function isActiveRun(run: ActionRun) {
  return ['QUEUED', 'PENDING', 'DISPATCHING', 'RUNNING', 'IMPORTING_RESULTS', 'CANCEL_REQUESTED'].includes(run.status)
}

function canCancelRun(run: ActionRun) {
  return run.canCancel && isActiveRun(run)
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
  <UiResponsiveSlideover
    side="right"
    :ui="{ content: 'max-w-3xl' }"
    :close="{ onClick: close }"
  >
    <template #header>
      <UiSlideoverHeader title="Run Action" icon="i-lucide-play" />
    </template>

    <template #body>
      <div class="space-y-5">
        <div class="space-y-4">
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

          <UTabs
            v-if="!props.targetSelection"
            v-model="scope"
            :items="scopeItems"
            variant="pill"
            color="neutral"
            :content="false"
            class="w-full"
          />
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
          <template #images>
            <div class="space-y-4 p-1">
              <UAlert
                v-if="imageVariantOptions.length === 0"
                color="neutral"
                variant="subtle"
                icon="i-lucide-image-off"
                title="No image variants found for this scope."
                description="The processor will receive image inputs as they are currently stored."
              />

              <template v-else>
                <div class="grid gap-3 sm:grid-cols-[1fr_auto] sm:items-end">
                  <UFormField label="Variant scope">
                    <UTabs
                      v-model="imageVariantMode"
                      :items="imageVariantModeItems"
                      variant="pill"
                      color="neutral"
                      :content="false"
                    />
                  </UFormField>

                  <UFormField label="Fallback Image">
                    <USwitch v-model="fallbackImage" />
                  </UFormField>
                </div>

                <UFormField
                  v-if="imageVariantMode === 'global'"
                  label="Image variant"
                  :hint="selectedImageVariantSummary || undefined"
                >
                  <USelectMenu
                    v-model="selectedImageVariant"
                    :items="imageVariantOptions"
                    value-key="value"
                    searchable
                    searchable-placeholder="Filter variants..."
                    class="w-full"
                  />
                </UFormField>

                <div v-else class="space-y-2">
                  <div
                    v-for="page in scopedPages"
                    :key="page.id"
                    class="grid gap-2 rounded-sm border border-default p-3 sm:grid-cols-[minmax(0,1fr)_minmax(12rem,18rem)] sm:items-center"
                  >
                    <div class="min-w-0">
                      <p class="truncate text-sm font-medium">
                        {{ page.name }}
                      </p>
                      <p class="truncate text-xs text-muted">
                        {{ imageVariantOptionsForPage(page).length }} variant{{ imageVariantOptionsForPage(page).length === 1 ? '' : 's' }}
                      </p>
                    </div>
                    <USelectMenu
                      v-if="imageVariantOptionsForPage(page).length > 0"
                      v-model="pageImageVariants[page.id]"
                      :items="imageVariantOptionsForPage(page)"
                      value-key="value"
                      searchable
                      searchable-placeholder="Filter variants..."
                    />
                    <UBadge v-else color="warning" variant="soft">
                      No images
                    </UBadge>
                  </div>
                </div>
              </template>
            </div>
          </template>

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
                        {{ runSummaryText(run) }}
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
                        v-if="canCancelRun(run)"
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
  </UiResponsiveSlideover>
</template>
