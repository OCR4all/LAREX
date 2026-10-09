<script setup lang="ts">
import { extractApiErrorDetails } from '@/utils/api-error'
import { annotationLevelLabels, retainedImpactPages, reviewedActionConfiguration } from '@/utils/action-impact'
import { actionInputLevelForTarget } from '@/utils/action-input-requirements'
import {
  actionParameterChoices,
  actionParameterDefaultValue,
  coerceActionParameterInput,
  hasAllowedActionParameterValue
} from '@/utils/action-parameter-values'
import { useBlockEditorCanvasInteractions } from '@/composables/editor/use-canvas-interaction-blocker'
import type {
  ActionRunImpact,
  ActionRunConfiguration,
  ActionParameterDefinition,
  ActionRun,
  ActionRunDetail,
  StartActionRunResponse,
  ExecutableActionProcessorResponse,
  ClearActionRunsResponse,
  ActionTargetSelection,
  ActionTarget,
  ActionInputLevel,
  ActionImageVariantSelection,
  ActionParameterChoice,
  ActionParameterValuesResponse,
  ActionParameterValue
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
type RunActionView = 'choose' | 'configure' | 'history'
const view = ref<RunActionView>('choose')
const historyReturnView = ref<'choose' | 'configure'>('choose')
const viewHeader = ref<HTMLElement | null>(null)
const configurationSectionUi = {
  root: 'shadow-xs divide-default/60',
  header: 'px-4 py-3 sm:px-5',
  body: 'p-4 sm:p-5'
}
const parameterValues = reactive<Record<string, ActionParameterValue | '' | undefined>>({})
const discoveredParameterValues = ref<Record<string, ActionParameterChoice[]>>({})
const parameterDiscoveryLoading = ref(false)
const parameterDiscoveryError = ref<string | null>(null)
const parameterFieldErrors = reactive<Record<string, string>>({})
let parameterDiscoveryRequest = 0
const scope = ref<'all' | 'selection'>(props.targetSelection || (props.pageIds?.length ?? 0) > 0 ? 'selection' : 'all')
const actionSearch = ref('')
const selectedActionTags = ref<string[]>([])
const actionTablePage = ref(1)
const actionNameDescending = ref(false)
const actionTableColumns = [
  { id: 'name', header: 'Action', enableHiding: false, meta: { class: { th: 'w-44' } } },
  { id: 'description', header: 'Description', enableHiding: false },
  { id: 'tags', header: 'Tags', enableHiding: false, meta: { class: { th: 'w-36' } } },
  { id: 'actions', header: '', enableHiding: false, meta: { class: { th: 'w-12', td: 'w-12' } } }
]
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

type ReviewedSubmission = { configuration: ActionRunConfiguration } | { retryRunId: string, excludedPageIds: string[] }
const impact = ref<ActionRunImpact | null>(null)
const impactLoading = ref(false)
const impactError = ref<string | null>(null)
const reviewSearch = ref('')
const reviewPanel = ref<HTMLElement | null>(null)
const excludedPageIds = ref<string[]>([])
const reviewConfiguration = ref<ActionRunConfiguration | null>(null)
const reviewRetryRunId = ref<string | null>(null)
let impactGeneration = 0
const affectedPages = computed(() => impact.value?.pages.filter(page => page.affected) ?? [])
const visibleAffectedPages = computed(() => affectedPages.value.filter(page =>
  (page.name || page.pageId).toLocaleLowerCase().includes(reviewSearch.value.toLocaleLowerCase())
))
const includedImpactPages = computed(() => impact.value ? retainedImpactPages(impact.value, excludedPageIds.value) : [])
const unaffectedPageCount = computed(() => impact.value?.pages.filter(page => !page.affected).length ?? 0)

function resetReview() {
  impactGeneration++
  impact.value = null
  impactError.value = null
  impactLoading.value = false
  excludedPageIds.value = []
  reviewSearch.value = ''
  reviewConfiguration.value = null
  reviewRetryRunId.value = null
}

function setPageIncluded(pageId: string, included: boolean) {
  excludedPageIds.value = included
    ? excludedPageIds.value.filter(id => id !== pageId)
    : [...new Set([...excludedPageIds.value, pageId])]
}

const selectedPageIds = computed(() => props.targetSelection?.pages.map(page => page.pageId) ?? props.pageIds ?? [])
const targetType = computed<ActionTarget>(() => props.targetSelection?.type ?? 'PAGE')
const targetCompatibleProcessors = computed(() => processors.value.filter(item => item.processor.targets?.includes(targetType.value)))
const executableProcessors = computed(() => targetCompatibleProcessors.value.filter(item => item.executable))
const selectedProcessor = computed(() => executableProcessors.value.find(item => item.processor.id === selectedProcessorId.value) ?? null)
const actionTagOptions = computed(() => [...new Set(targetCompatibleProcessors.value.flatMap(item => item.processor.tags))]
  .sort((left, right) => left.localeCompare(right)))
const filteredActionProcessors = computed(() => {
  const needle = actionSearch.value.trim().toLocaleLowerCase()
  return targetCompatibleProcessors.value.filter(item =>
    (!needle || [item.processor.name, item.processor.description].some(value => value?.toLocaleLowerCase().includes(needle)))
    && (!selectedActionTags.value.length || selectedActionTags.value.some(tag => item.processor.tags.includes(tag)))
  ).toSorted((left, right) => (actionNameDescending.value ? -1 : 1) * left.processor.name.localeCompare(right.processor.name))
})
const visibleActionProcessors = computed(() => filteredActionProcessors.value.slice((actionTablePage.value - 1) * 10, actionTablePage.value * 10))
const hasActionFilters = computed(() => Boolean(actionSearch.value.trim() || selectedActionTags.value.length))
const actionTableEmptyMessage = computed(() => {
  if (!processors.value.length) return 'No Actions are assigned to this project or workspace.'
  if (!targetCompatibleProcessors.value.length) return 'No Actions support this target.'
  return 'No matching Actions. Adjust the current filters.'
})
function clearActionFilters() {
  actionSearch.value = ''
  selectedActionTags.value = []
}
// UTable makes rows focusable but only handles pointer selection.
function activateActionRow(event: KeyboardEvent) {
  if (!(event.target instanceof HTMLElement) || !event.target.matches('tr[role="button"]')) return
  event.preventDefault()
  event.target.click()
}
function selectAction(item: ExecutableActionProcessorResponse) {
  if (!item.executable || navigationBusy.value) return
  selectedProcessorId.value = item.processor.id
  view.value = 'configure'
}
watch([actionSearch, selectedActionTags, actionNameDescending], () => {
  actionTablePage.value = 1
}, { deep: true })
watch(filteredActionProcessors, () => {
  actionTablePage.value = Math.min(actionTablePage.value, Math.max(1, Math.ceil(filteredActionProcessors.value.length / 10)))
})
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
function selectedInputLevel(type: 'images' | 'xml'): ActionInputLevel {
  const requirement = selectedProcessor.value?.processor.inputs?.[type]
  const legacyAccepted = type === 'images'
    ? selectedProcessor.value?.processor.acceptsImages
    : selectedProcessor.value?.processor.acceptsXml
  return actionInputLevelForTarget(requirement, targetType.value, legacyAccepted)
}

const selectedImageInputLevel = computed(() => selectedInputLevel('images'))
const selectedXmlInputLevel = computed(() => selectedInputLevel('xml'))
const selectedProcessorAcceptsImages = computed(() => selectedImageInputLevel.value !== 'NONE')
const selectedProcessorRequiresImages = computed(() => selectedImageInputLevel.value === 'REQUIRED')
const selectedProcessorRequiresXml = computed(() => selectedXmlInputLevel.value === 'REQUIRED')
const compatibilityWarnings = computed(() => {
  if (!selectedProcessor.value || scopedPages.value.length === 0) return []

  const warnings: Array<{ input: 'images' | 'xml', title: string, description: string }> = []
  if (selectedProcessorAcceptsImages.value) {
    const missingImages = scopedPages.value.filter(page => page.imageCount <= 0)
    if (missingImages.length > 0 && (selectedProcessorRequiresImages.value || imageVariantOptions.value.length > 0)) {
      warnings.push({
        input: 'images',
        title: `${missingImages.length} page${missingImages.length === 1 ? '' : 's'} ${missingImages.length === 1 ? 'has' : 'have'} no images.`,
        description: selectedProcessorRequiresImages.value
          ? 'Those pages will be skipped because this Action requires image input.'
          : 'Those pages will be skipped because no image is available for the selected variant input.'
      })
    }
    if (pagesMissingSelectedVariant.value.length > 0) {
      warnings.push({
        input: 'images',
        title: fallbackImage.value
          ? `${pagesMissingSelectedVariant.value.length} page${pagesMissingSelectedVariant.value.length === 1 ? '' : 's'} will use a fallback image.`
          : `${pagesMissingSelectedVariant.value.length} page${pagesMissingSelectedVariant.value.length === 1 ? '' : 's'} will be skipped because the selected image variant is missing.`,
        description: fallbackImage.value
          ? 'LAREX will use the first available image on those pages.'
          : 'Choose another variant or enable fallback to include those pages.'
      })
    }
  }
  if (selectedProcessorRequiresXml.value) {
    const missingXml = scopedPages.value.filter(page => page.xmlFileCount <= 0)
    if (missingXml.length > 0) {
      warnings.push({
        input: 'xml',
        title: `${missingXml.length} page${missingXml.length === 1 ? '' : 's'} ${missingXml.length === 1 ? 'has' : 'have'} no XML.`,
        description: 'Those pages will be skipped because this Action requires XML input.'
      })
    }
  }
  return warnings
})
const scopeSummary = computed(() => scope.value === 'selection' ? `${selectedPageIds.value.length} selected pages` : `All pages (${props.pages?.length ?? 0})`)
const targetSummary = computed(() => props.targetSelection
  ? props.targetSummary || `${targetType.value.replace('_', ' ').toLowerCase()} target`
  : scopeSummary.value)
const imageWarnings = computed(() => compatibilityWarnings.value.filter(warning => warning.input === 'images'))
const scopeWarnings = computed(() => compatibilityWarnings.value.filter(warning => warning.input === 'xml'))
const navigationBusy = computed(() => starting.value || impactLoading.value)
const viewTitle = computed(() => impact.value ? 'Review affected pages' : view.value === 'history' ? 'Project run history' : 'Run Action')
const viewDescription = computed(() => view.value === 'choose'
  ? `Choose an Action for ${targetSummary.value}.`
  : view.value === 'history' ? 'Previous Action runs for this project.' : 'Configure this Action and start the run.')
const lockNote = computed(() => {
  if (selectedProcessor.value?.processor.lockMode === 'PROJECT') return 'Locks the full project while running.'
  if (selectedProcessor.value?.processor.lockMode === 'PAGES') return 'Locks these pages while running.'
  return null
})
function changeAction() {
  if (navigationBusy.value) return
  resetReview()
  view.value = 'choose'
}
function openHistory() {
  if (navigationBusy.value || view.value === 'history') return
  historyReturnView.value = view.value
  resetReview()
  view.value = 'history'
}
function backFromHistory() {
  if (navigationBusy.value) return
  resetReview()
  view.value = historyReturnView.value
}
watch([view, () => Boolean(impact.value)], async () => {
  await nextTick()
  const heading = viewHeader.value?.querySelector('h2')
  heading?.setAttribute('tabindex', '-1')
  heading?.focus()
})
const scopeItems = computed(() => [
  { label: `All pages (${props.pages?.length ?? 0})`, value: 'all', icon: 'i-lucide-files', disabled: navigationBusy.value },
  { label: `Selected pages (${selectedPageIds.value.length})`, value: 'selection', icon: 'i-lucide-check-square', disabled: navigationBusy.value || !hasSelection.value }
])
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
    if (page.imageCount <= 0) return false
    const available = imageVariantByPageId.value[page.id] ?? new Set<string>()
    const wanted = imageVariantMode.value === 'global' ? selectedImageVariant.value : pageImageVariants[page.id]
    return typeof wanted === 'string' && wanted.length > 0 && !available.has(wanted)
  })
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
const incompatibleScopedPages = computed(() => scopedPages.value.filter((page) => {
  if (selectedProcessorRequiresImages.value && page.imageCount <= 0) return true
  if (selectedProcessorRequiresXml.value && page.xmlFileCount <= 0) return true
  if (!selectedProcessorAcceptsImages.value) return false

  const available = imageVariantByPageId.value[page.id] ?? new Set<string>()
  const wanted = imageVariantMode.value === 'global' ? selectedImageVariant.value : pageImageVariants[page.id]
  if (!wanted || available.has(wanted)) return false
  return !fallbackImage.value || page.imageCount <= 0
}))
const hasCompatiblePages = computed(() =>
  scopedPages.value.length === 0 || incompatibleScopedPages.value.length < scopedPages.value.length
)
const parameterEntries = computed(() => {
  const parameters = selectedProcessor.value?.processor.parameters ?? {}
  return Object.entries(parameters).map(([key, definition]) => ({ key, definition }))
})
const hasDynamicParameters = computed(() => parameterEntries.value.some(
  entry => Boolean(entry.definition.allowedValues?.provider)
))
const parameterValuesReady = computed(() =>
  !parameterDiscoveryLoading.value
  && !parameterDiscoveryError.value
  && parameterEntries.value.every(entry =>
    hasAllowedActionParameterValue(entry.definition, parameterValues[entry.key], discoveredParameterValues.value)
  )
)

const clearableHistoryRuns = computed(() => runs.value.filter(run => run.status === 'COMPLETED' || run.status === 'FAILED'))
const paginatedRuns = computed(() => {
  const start = (runHistoryPage.value - 1) * runHistoryItemsPerPage.value
  return runs.value.slice(start, start + runHistoryItemsPerPage.value)
})
const canStart = computed(() =>
  view.value === 'configure'
  && Boolean(selectedProcessor.value?.executable)
  && !starting.value
  && hasCompatiblePages.value
  && parameterValuesReady.value
  && (props.targetSelection ? selectedPageIds.value.length > 0 : scope.value === 'all' || selectedPageIds.value.length > 0)
)

watch([
  selectedProcessorId, scope, parameterValues, submittedImageVariantSelection,
  () => props.targetSelection, () => props.pageIds, () => props.pages
], resetReview, { deep: true, flush: 'sync' })

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
  void refreshParameterValues()
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
    if (view.value === 'configure') view.value = 'choose'
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
    Reflect.deleteProperty(parameterValues, key)
  })
  parameterEntries.value.forEach(({ key, definition }) => {
    parameterValues[key] = actionParameterDefaultValue(definition)
  })
  discoveredParameterValues.value = {}
  parameterDiscoveryError.value = null
  Object.keys(parameterFieldErrors).forEach(key => Reflect.deleteProperty(parameterFieldErrors, key))
}

function parameterInputValue(key: string): string {
  return String(parameterValues[key] ?? '')
}

function updateParameterInputValue(
  key: string,
  definition: ActionParameterDefinition,
  value: string | number | null | undefined
) {
  parameterValues[key] = coerceActionParameterInput(definition, value)
}

function updateBooleanParameterValue(key: string, value: boolean) {
  parameterValues[key] = value
}

function allowedChoices(definition: ActionParameterDefinition) {
  return actionParameterChoices(definition, discoveredParameterValues.value)
}

function updateAllowedParameterValue(
  key: string,
  definition: ActionParameterDefinition,
  value: unknown
) {
  if (typeof value !== 'string' && typeof value !== 'number' && typeof value !== 'boolean') return
  parameterValues[key] = value
  if (hasAllowedActionParameterValue(definition, value, discoveredParameterValues.value)) {
    Reflect.deleteProperty(parameterFieldErrors, key)
  }
}

async function refreshParameterValues() {
  const request = ++parameterDiscoveryRequest
  parameterDiscoveryError.value = null
  Object.keys(parameterFieldErrors).forEach(key => Reflect.deleteProperty(parameterFieldErrors, key))
  if (!selectedProcessor.value || !hasDynamicParameters.value) {
    discoveredParameterValues.value = {}
    parameterDiscoveryLoading.value = false
    return
  }
  parameterDiscoveryLoading.value = true
  try {
    const response = await $fetch<ActionParameterValuesResponse>(
      `/api/workspaces/${props.workspaceId}/actions/projects/${props.projectId}/processors/${selectedProcessor.value.processor.id}/parameter-values`
    )
    if (request !== parameterDiscoveryRequest) return
    discoveredParameterValues.value = response.values
    for (const entry of parameterEntries.value) {
      if (!entry.definition.allowedValues) continue
      const choices = allowedChoices(entry.definition)
      if (choices.length === 0) {
        parameterFieldErrors[entry.key] = 'No allowed values are currently available.'
      } else if (!hasAllowedActionParameterValue(
        entry.definition,
        parameterValues[entry.key],
        discoveredParameterValues.value
      )) {
        parameterValues[entry.key] = undefined
        parameterFieldErrors[entry.key] = 'Select an allowed value.'
      }
    }
  } catch (error: unknown) {
    if (request !== parameterDiscoveryRequest) return
    parameterDiscoveryError.value = extractApiErrorDetails(
      error,
      'Could not discover allowed parameter values.'
    ).message
    discoveredParameterValues.value = {}
  } finally {
    if (request === parameterDiscoveryRequest) parameterDiscoveryLoading.value = false
  }
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

function currentRunConfiguration(): ActionRunConfiguration {
  return JSON.parse(JSON.stringify({
    processorDefinitionId: selectedProcessor.value!.processor.id,
    pageIds: submittedPageIds.value,
    targetSelection: submittedTargetSelection.value,
    imageVariantSelection: submittedImageVariantSelection.value,
    parameters: { ...parameterValues }
  }))
}

async function reviewRun(retryRunId: string | null = null) {
  if (impactLoading.value || starting.value || (!retryRunId && !canStart.value)) return
  resetReview()
  reviewRetryRunId.value = retryRunId
  const configuration = retryRunId ? null : currentRunConfiguration()
  reviewConfiguration.value = configuration
  const generation = impactGeneration
  impactLoading.value = true
  try {
    const preview = await $fetch<ActionRunImpact>(
      `/api/workspaces/${props.workspaceId}/actions/projects/${props.projectId}/runs${retryRunId ? `/${retryRunId}` : ''}/impact`,
      { method: 'POST', ...(configuration ? { body: configuration } : {}) }
    )
    if (generation !== impactGeneration) return
    if (!preview.pages.length) throw new Error('No pages satisfy this Action’s required inputs.')
    impact.value = preview
    if (!preview.pages.some(page => page.affected)) {
      await confirmReviewedRun()
    } else {
      await nextTick()
      reviewPanel.value?.focus()
    }
  } catch (error: unknown) {
    if (generation === impactGeneration) {
      impactError.value = extractApiErrorDetails(error, 'Could not inspect the selected pages. Try again.').message
    }
  } finally {
    if (generation === impactGeneration) impactLoading.value = false
  }
}

async function confirmReviewedRun() {
  if (!impact.value || !includedImpactPages.value.length || starting.value) return
  const submission: ReviewedSubmission = reviewRetryRunId.value
    ? { retryRunId: reviewRetryRunId.value, excludedPageIds: [...excludedPageIds.value, ...impact.value.skippedPages.map(page => page.pageId)] }
    : { configuration: reviewedActionConfiguration(reviewConfiguration.value!, impact.value, excludedPageIds.value) }
  await submitReviewedRun(submission)
}

async function submitReviewedRun(submission: ReviewedSubmission, enqueueIfBusy = false) {
  if (starting.value) return
  starting.value = true
  const isRetry = 'retryRunId' in submission
  retryingRunId.value = isRetry ? submission.retryRunId : null
  try {
    const result = await $fetch<StartActionRunResponse>(
      `/api/workspaces/${props.workspaceId}/actions/projects/${props.projectId}/runs${isRetry ? `/${submission.retryRunId}/retry` : ''}`,
      {
        method: 'POST',
        ...(isRetry
          ? { query: { enqueueIfBusy }, body: { excludedPageIds: submission.excludedPageIds } }
          : { body: { ...submission.configuration, enqueueIfBusy } })
      }
    )
    actionRunsStore.upsertRun(result.run, props.projectName || props.projectId)
    changed.value = true
    close()
  } catch (error: unknown) {
    const { details, isConcurrencyError } = concurrencyErrorDetails(error)
    if (!enqueueIfBusy && isConcurrencyError) {
      toast.add({
        title: 'Action is already running', description: details.message, color: 'warning', icon: 'i-lucide-clock-3',
        actions: [
          { label: 'Schedule', color: 'warning', variant: 'solid', onClick: () => { void submitReviewedRun(submission, true) } },
          { label: 'Later', color: 'neutral', variant: 'outline', onClick: () => {} }
        ]
      })
      return
    }
    toast.add({ title: isRetry ? 'Retry failed' : 'Run failed', description: details.message, color: 'error' })
  } finally {
    starting.value = false
    retryingRunId.value = null
  }
}

async function startRun() {
  await reviewRun()
}

async function cancelRun(run: ActionRun) {
  cancellingRunId.value = run.id
  try {
    const updated = await $fetch<ActionRun>(
      `/api/workspaces/${props.workspaceId}/actions/projects/${props.projectId}/runs/${run.id}/cancel${run.status === 'CANCEL_REQUESTED' ? '?force=true' : ''}`,
      { method: 'POST' }
    )
    actionRunsStore.upsertRun(updated, props.projectName || props.projectId)
    changed.value = true
    await loadRuns()
    toast.add({ title: updated.status === 'CANCELLED' ? 'Action run cancelled' : 'Action cancellation requested', color: 'success' })
  } catch (error: unknown) {
    const message = error instanceof Error ? error.message : 'Could not cancel Action run.'
    toast.add({ title: 'Cancel failed', description: message, color: 'error' })
  } finally {
    cancellingRunId.value = null
  }
}

async function retryRun(run: ActionRun) {
  await reviewRun(run.id)
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
  resetReview()
  emit('close', changed.value)
}
</script>

<template>
  <UiResponsiveSlideover
    side="right"
    :ui="{ content: 'max-w-3xl xl:max-w-4xl' }"
    :close="{ onClick: close }"
  >
    <template #header>
      <div ref="viewHeader" class="flex w-full flex-wrap items-start justify-between gap-3 [&_h2]:outline-none">
        <UiSlideoverHeader :title="viewTitle" :description="impact ? 'Review pages before starting the Action.' : viewDescription" :icon="view === 'history' && !impact ? 'i-lucide-history' : 'i-lucide-play'" />
        <UButton
          v-if="!impact && view !== 'history'"
          color="neutral"
          variant="ghost"
          size="sm"
          icon="i-lucide-history"
          :disabled="navigationBusy"
          @click="openHistory"
        >
          History ({{ runs.length }})
        </UButton>
      </div>
    </template>

    <template #body>
      <div
        v-if="impact"
        ref="reviewPanel"
        tabindex="-1"
        role="region"
        aria-label="Review affected pages"
        class="space-y-4"
      >
        <UAlert
          color="warning"
          variant="subtle"
          icon="i-lucide-triangle-alert"
          title="Existing annotations may be replaced"
          description="Review the pages below. Uncheck a page to remove it from this Action run. Listed levels describe potential replacement or deletion."
        />
        <p role="status" class="text-sm text-muted">
          {{ includedImpactPages.length }} included · {{ excludedPageIds.length }} excluded · {{ unaffectedPageCount }} unaffected pages included automatically
        </p>
        <UInput
          v-model="reviewSearch"
          icon="i-lucide-search"
          placeholder="Find affected pages…"
          aria-label="Find affected pages"
          class="w-full"
        />
        <div class="flex flex-wrap gap-2">
          <UButton color="neutral" variant="outline" @click="excludedPageIds = affectedPages.map(page => page.pageId)">
            Exclude all affected pages
          </UButton>
          <UButton color="neutral" variant="outline" @click="excludedPageIds = []">
            Include all affected pages
          </UButton>
        </div>
        <ul class="divide-y divide-default rounded-sm border border-default">
          <li v-for="page in visibleAffectedPages" :key="page.pageId" class="space-y-2 p-3">
            <UCheckbox
              :model-value="!excludedPageIds.includes(page.pageId)"
              :label="page.name || page.pageId"
              :aria-label="`Include ${page.name || page.pageId} in Action processing`"
              @update:model-value="setPageIncluded(page.pageId, $event === true)"
            />
            <p v-if="page.warningPrecision === 'UNKNOWN'" class="pl-6 text-sm text-muted">
              Existing annotations may be replaced.
            </p>
            <div v-else class="flex flex-wrap gap-1 pl-6">
              <UBadge
                v-for="level in page.affectedLevels"
                :key="level"
                color="warning"
                variant="soft"
              >
                {{ annotationLevelLabels[level] }}
              </UBadge>
            </div>
          </li>
        </ul>
        <p v-if="!visibleAffectedPages.length" class="text-sm text-muted">
          No affected pages match your search.
        </p>
        <div v-if="impact.skippedPages.length" class="space-y-1 text-sm text-muted">
          <p>{{ impact.skippedPages.length }} pages will be skipped:</p>
          <p v-for="page in impact.skippedPages" :key="page.pageId">
            {{ page.name || page.pageId }}: {{ page.reason }}
          </p>
        </div>
      </div>
      <div v-else class="space-y-6">
        <UAlert
          v-if="impactError"
          color="error"
          title="Page review failed"
          :description="impactError"
        >
          <template #actions>
            <UButton
              color="neutral"
              variant="outline"
              :loading="impactLoading"
              @click="reviewRun(reviewRetryRunId)"
            >
              Try again
            </UButton>
          </template>
        </UAlert>
        <template v-if="view === 'choose'">
          <div class="space-y-3">
            <div class="flex flex-wrap gap-2">
              <UInput
                v-model="actionSearch"
                icon="i-lucide-search"
                placeholder="Search name or description…"
                aria-label="Search Actions"
                class="w-full sm:flex-1"
              />
              <USelectMenu
                v-model="selectedActionTags"
                :items="actionTagOptions"
                multiple
                placeholder="Filter by tags"
                aria-label="Filter Actions by tags"
                class="w-full sm:w-48"
              />
              <AppTableClearFiltersButton :active="hasActionFilters" @clear="clearActionFilters" />
            </div>
            <AppTable
              v-if="loading || filteredActionProcessors.length"
              table-id="run-action-processors"
              :columns="actionTableColumns"
              :data="visibleActionProcessors"
              :loading="loading"
              :get-row-id="(item: ExecutableActionProcessorResponse) => item.processor.id"
              :ui="{ base: 'w-full min-w-[36rem] table-fixed border-separate border-spacing-0', td: 'border-b border-default whitespace-normal align-top' }"
              :meta="{ class: { tr: (row: { original: ExecutableActionProcessorResponse }) => row.original.executable ? 'cursor-pointer hover:bg-accented/50! focus-visible:bg-accented/50' : 'cursor-not-allowed opacity-60' } }"
              @select="(_event: Event, row: { original: ExecutableActionProcessorResponse }) => selectAction(row.original)"
              @keydown.enter="activateActionRow"
              @keydown.space="activateActionRow"
            >
              <template #name-header>
                <UButton
                  color="neutral"
                  variant="ghost"
                  :icon="actionNameDescending ? 'i-lucide-arrow-down-a-z' : 'i-lucide-arrow-up-a-z'"
                  aria-label="Toggle Action name sort order"
                  @click="actionNameDescending = !actionNameDescending"
                >
                  Action
                </UButton>
              </template>
              <template #actions-cell="{ row }">
                <UButton
                  color="neutral"
                  variant="ghost"
                  size="xs"
                  icon="i-lucide-chevron-right"
                  :disabled="!row.original.executable || navigationBusy"
                  :aria-label="`Configure ${row.original.processor.name}`"
                  @click="selectAction(row.original)"
                />
              </template>
              <template #name-cell="{ row }">
                <p class="font-medium text-highlighted">
                  {{ row.original.processor.name }}
                </p>
                <p v-if="!row.original.executable" class="mt-1 text-xs text-muted">
                  {{ row.original.blockedReason || 'Unavailable' }}
                </p>
              </template>
              <template #description-cell="{ row }">
                <p class="text-muted">
                  {{ row.original.processor.description || 'No description provided.' }}
                </p>
              </template>
              <template #tags-cell="{ row }">
                <div v-if="row.original.processor.tags.length" class="flex flex-wrap gap-1">
                  <UBadge
                    v-for="tag in row.original.processor.tags"
                    :key="tag"
                    color="neutral"
                    variant="subtle"
                  >
                    {{ tag }}
                  </UBadge>
                </div>
                <span v-else>—</span>
              </template>
              <template #loading>
                <p role="status" class="py-6 text-left text-muted">
                  Loading Actions…
                </p>
              </template>
            </AppTable>
            <p v-else role="status" class="py-6 text-center text-sm text-muted">
              {{ actionTableEmptyMessage }}
            </p>
            <UPagination
              v-if="filteredActionProcessors.length > 10"
              v-model:page="actionTablePage"
              :total="filteredActionProcessors.length"
              :items-per-page="10"
            />
            <UAlert
              v-if="!loading && targetCompatibleProcessors.length && !executableProcessors.length"
              color="warning"
              variant="subtle"
              icon="i-lucide-lock"
              title="No Actions are available for your role right now."
            />
          </div>
        </template>
        <template v-else-if="view === 'configure' && selectedProcessor">
          <div class="flex flex-col items-start justify-between gap-3 sm:flex-row">
            <div class="min-w-0 space-y-1">
              <p class="text-xs font-medium text-muted">
                Selected Action
              </p>
              <h3 class="text-lg font-semibold tracking-tight text-highlighted">
                {{ selectedProcessor.processor.name }}
              </h3>
              <p v-if="selectedProcessor.processor.description" class="text-sm text-muted">
                {{ selectedProcessor.processor.description }}
              </p>
            </div>
            <UButton
              color="neutral"
              variant="outline"
              icon="i-lucide-arrow-left"
              :disabled="navigationBusy"
              class="shrink-0"
              @click="changeAction"
            >
              Change Action
            </UButton>
          </div>
          <UiSlideoverSection
            :title="props.targetSelection ? 'Target' : 'Pages'"
            icon="i-lucide-files"
            variant="outline"
            :ui="configurationSectionUi"
          >
            <div class="space-y-3">
              <p v-if="props.targetSelection" class="text-sm">
                {{ targetSummary }}
              </p>
              <UTabs
                v-else
                v-model="scope"
                :items="scopeItems"
                variant="pill"
                color="neutral"
                :content="false"
                :ui="{ trigger: 'px-2 sm:px-3', leadingIcon: 'hidden sm:block' }"
                class="w-full"
              />
              <UAlert
                v-for="warning in scopeWarnings"
                :key="warning.title"
                color="warning"
                variant="subtle"
                icon="i-lucide-triangle-alert"
                :title="warning.title"
                :description="warning.description"
              />
            </div>
          </UiSlideoverSection>
          <template v-if="selectedProcessorAcceptsImages">
            <UiSlideoverSection
              title="Images"
              icon="i-lucide-image"
              variant="outline"
              :ui="configurationSectionUi"
            >
              <div class="space-y-4">
                <p v-if="imageVariantOptions.length === 0" class="text-sm text-muted">
                  Image inputs will be used as currently stored.
                </p>
                <template v-else>
                  <UFormField label="Image variant" :description="imageVariantMode === 'perPage' ? 'Using a separate variant for each page.' : undefined">
                    <USelectMenu
                      v-model="selectedImageVariant"
                      :items="imageVariantOptions"
                      value-key="value"
                      :disabled="navigationBusy || imageVariantMode === 'perPage'"
                      class="w-full"
                    />
                  </UFormField>
                  <USwitch v-model="fallbackImage" label="Use another image if unavailable" :disabled="navigationBusy" />
                  <UCollapsible :open="imageVariantMode === 'perPage'" :disabled="navigationBusy" @update:open="imageVariantMode = $event ? 'perPage' : 'global'">
                    <UButton
                      color="primary"
                      variant="link"
                      :icon="imageVariantMode === 'perPage' ? 'i-lucide-chevron-up' : 'i-lucide-chevron-down'"
                      class="px-0"
                    >
                      Choose variants per page
                    </UButton>
                    <template #content>
                      <div class="space-y-2">
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
                            :disabled="navigationBusy"
                          />
                          <UBadge v-else color="warning" variant="soft">
                            No images
                          </UBadge>
                        </div>
                      </div>
                    </template>
                  </UCollapsible>
                </template>
                <UAlert
                  v-for="warning in imageWarnings"
                  :key="warning.title"
                  color="warning"
                  variant="subtle"
                  icon="i-lucide-triangle-alert"
                  :title="warning.title"
                  :description="warning.description"
                />
              </div>
            </UiSlideoverSection>
          </template>
          <template v-if="parameterEntries.length">
            <UiSlideoverSection
              title="Parameters"
              icon="i-lucide-sliders-horizontal"
              variant="outline"
              :ui="configurationSectionUi"
            >
              <div class="space-y-3">
                <div v-if="hasDynamicParameters" class="flex justify-end">
                  <UButton
                    label="Refresh values"
                    icon="i-lucide-refresh-cw"
                    color="neutral"
                    variant="ghost"
                    size="sm"
                    :loading="parameterDiscoveryLoading"
                    :disabled="navigationBusy"
                    @click="refreshParameterValues"
                  />
                </div>
                <UAlert
                  v-if="parameterDiscoveryError"
                  color="error"
                  variant="subtle"
                  icon="i-lucide-triangle-alert"
                  title="Allowed values unavailable"
                  :description="parameterDiscoveryError"
                />

                <div class="grid gap-3">
                  <UFormField
                    v-for="entry in parameterEntries"
                    :key="entry.key"
                    :label="entry.key"
                    :description="entry.definition.description"
                    :required="entry.definition.required"
                    :error="parameterFieldErrors[entry.key]"
                  >
                    <USelectMenu
                      v-if="entry.definition.allowedValues"
                      :model-value="parameterValues[entry.key]"
                      :items="allowedChoices(entry.definition)"
                      value-key="value"
                      searchable
                      searchable-placeholder="Filter allowed values..."
                      :loading="parameterDiscoveryLoading && Boolean(entry.definition.allowedValues.provider)"
                      :disabled="navigationBusy || parameterDiscoveryLoading"
                      placeholder="Select an allowed value"
                      class="w-full"
                      @update:model-value="updateAllowedParameterValue(entry.key, entry.definition, $event)"
                    />
                    <USwitch
                      v-else-if="entry.definition.type === 'boolean'"
                      :model-value="Boolean(parameterValues[entry.key])"
                      :disabled="navigationBusy"
                      @update:model-value="updateBooleanParameterValue(entry.key, $event)"
                    />
                    <UInput
                      v-else
                      :model-value="parameterInputValue(entry.key)"
                      :type="entry.definition.type === 'number' || entry.definition.type === 'integer' ? 'number' : 'text'"
                      :min="entry.definition.min"
                      :max="entry.definition.max"
                      :disabled="navigationBusy"
                      @update:model-value="updateParameterInputValue(entry.key, entry.definition, $event)"
                    />
                  </UFormField>
                </div>
              </div>
            </UiSlideoverSection>
          </template>
        </template>
        <template v-else-if="view === 'history'">
          <UButton
            color="neutral"
            variant="link"
            icon="i-lucide-arrow-left"
            :disabled="navigationBusy"
            class="px-0"
            @click="backFromHistory"
          >
            Back
          </UButton>
          <div class="space-y-3">
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
                  :disabled="clearableHistoryRuns.length === 0 || navigationBusy"
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
                      :disabled="navigationBusy"
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
                      :disabled="navigationBusy"
                      :aria-label="run.status === 'CANCEL_REQUESTED' ? 'Force cancel Action run' : 'Cancel Action run'"
                      :title="run.status === 'CANCEL_REQUESTED' ? 'Force cancel Action run' : 'Cancel Action run'"
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
      </div>
    </template>

    <template #footer>
      <div class="flex w-full flex-wrap items-center justify-end gap-3">
        <p v-if="view === 'configure' && !impact && lockNote" class="mr-auto flex w-full items-center gap-1.5 text-xs text-muted sm:w-auto">
          <UIcon name="i-lucide-lock-keyhole" class="size-4 shrink-0" />
          {{ lockNote }}
        </p>
        <UButton
          color="neutral"
          variant="ghost"
          :disabled="starting"
          @click="close"
        >
          Close
        </UButton>
        <UButton
          v-if="impact"
          color="neutral"
          variant="outline"
          :disabled="starting"
          @click="resetReview"
        >
          Back
        </UButton>
        <UButton
          v-if="impact"
          icon="i-lucide-play"
          :loading="starting"
          :disabled="includedImpactPages.length === 0 || starting"
          @click="confirmReviewedRun"
        >
          {{ reviewRetryRunId ? 'Retry' : 'Start' }} Action on {{ includedImpactPages.length }} pages
        </UButton>
        <UButton
          v-else-if="view === 'configure'"
          icon="i-lucide-play"
          :loading="starting || impactLoading"
          :disabled="!canStart || impactLoading"
          @click="startRun"
        >
          Start Action
        </UButton>
      </div>
    </template>
  </UiResponsiveSlideover>
</template>
