<script setup lang="ts">
import type {
  ActionDefinitionResponse,
  ActionParameterChoice,
  ActionParameterValue,
  StartActionRunResponse,
  TrainingInputItem,
  TrainingInputResponse
} from '@/types/action'
import type { DatasetDetail, DatasetSummary } from '@/types/dataset'

await useWorkspaceBootstrap()

const workspaceStore = useWorkspaceStore()
const toast = useToast()
const workspaceId = computed(() => workspaceStore.selectedWorkspaceId)
const selectedDatasetId = ref<string>()
const selectedProcessorId = ref<string>()
const selectionMode = ref<'ALL' | 'SELECTED'>('ALL')
const selectedItemIds = ref<Set<string>>(new Set())
const globalVariant = ref<string>()
const itemImageIds = ref<Record<string, string>>({})
const parameters = ref<Record<string, ActionParameterValue>>({})
const parameterChoices = ref<Record<string, ActionParameterChoice[]>>({})
const starting = ref(false)
const trainingInputs = ref<TrainingInputResponse | null>(null)
const loadingInputs = ref(false)
const inputLoadError = ref<string>()
const datasetDetail = ref<DatasetDetail | null>(null)
const datasetDetailLoading = ref(false)
const datasetDetailError = ref<string>()
const datasetSearch = ref('')
const datasetTagFilter = ref<string | undefined>()
const itemSearch = ref('')
const splitFilter = ref<'ALL' | 'TRAIN' | 'VAL' | 'TEST'>('ALL')
const showOnlyReady = ref(false)
const currentStep = ref(0)
let inputRequestSequence = 0

const { data: datasets } = await useFetch<DatasetSummary[]>(
  () => `/api/workspaces/${workspaceId.value}/datasets`,
  { watch: [workspaceId], default: () => [] }
)
const { data: processors } = await useFetch<ActionDefinitionResponse[]>(
  () => `/api/workspaces/${workspaceId.value}/actions/training/processors`,
  { watch: [workspaceId], default: () => [] }
)

// Initialize the dependent selections before creating the input request. During
// SSR/hydration the immediate watchers below may otherwise run only after
// useAsyncData has cached its initial null result.
selectedDatasetId.value = datasets.value[0]?.id
selectedProcessorId.value = processors.value[0]?.id

async function refreshTrainingInputs() {
  const requestSequence = ++inputRequestSequence
  const currentWorkspaceId = workspaceId.value
  const currentDatasetId = selectedDatasetId.value
  inputLoadError.value = undefined

  if (!currentWorkspaceId || !currentDatasetId) {
    trainingInputs.value = null
    return
  }

  loadingInputs.value = true
  try {
    const response = await $fetch<TrainingInputResponse>(
      `/api/workspaces/${currentWorkspaceId}/actions/training/datasets/${currentDatasetId}/inputs`
    )
    if (requestSequence === inputRequestSequence) trainingInputs.value = response
  } catch (error: unknown) {
    if (requestSequence === inputRequestSequence) {
      trainingInputs.value = null
      inputLoadError.value = error instanceof Error ? error.message : 'Could not load dataset inputs'
    }
  } finally {
    if (requestSequence === inputRequestSequence) loadingInputs.value = false
  }
}

async function refreshDatasetDetail() {
  const currentWorkspaceId = workspaceId.value
  const currentDatasetId = selectedDatasetId.value
  datasetDetailError.value = undefined
  if (!currentWorkspaceId || !currentDatasetId) {
    datasetDetail.value = null
    return
  }
  datasetDetailLoading.value = true
  try {
    datasetDetail.value = await $fetch<DatasetDetail>(`/api/workspaces/${currentWorkspaceId}/datasets/${currentDatasetId}`)
  } catch (error: unknown) {
    datasetDetail.value = null
    datasetDetailError.value = error instanceof Error ? error.message : 'Could not load dataset details'
  } finally {
    datasetDetailLoading.value = false
  }
}

const selectedProcessor = computed(() => processors.value.find(item => item.id === selectedProcessorId.value))
const splitRequirements = computed(() => selectedProcessor.value?.trainingSplits)
const processorOptions = computed(() => processors.value.map(item => ({ label: item.name, value: item.id })))
const steps = [
  { title: 'Dataset', description: 'Choose a dataset and subset', icon: 'i-lucide-database' },
  { title: 'Inputs', description: 'Filter and freeze pages', icon: 'i-lucide-list-checks' },
  { title: 'Parameters', description: 'Configure the Action', icon: 'i-lucide-sliders-horizontal' },
  { title: 'Review', description: 'Confirm and start', icon: 'i-lucide-rocket' }
]
const availableDatasetTags = computed(() => [...new Set(datasets.value.flatMap(dataset => dataset.tags || []))].sort())
const filteredDatasets = computed(() => {
  const needle = datasetSearch.value.trim().toLowerCase()
  return datasets.value.filter((dataset) => {
    if (needle && !`${dataset.name} ${dataset.description || ''}`.toLowerCase().includes(needle)) return false
    return !datasetTagFilter.value || dataset.tags?.includes(datasetTagFilter.value)
  })
})
const selectedDataset = computed(() => datasets.value.find(dataset => dataset.id === selectedDatasetId.value))

function splitLevel(item: TrainingInputItem) {
  const requirements = splitRequirements.value
  if (!requirements) return 'NONE'
  return requirements[item.split.toLowerCase() as 'train' | 'val' | 'test']
}

const compatibleItems = computed(() => (trainingInputs.value?.items ?? []).filter(item =>
  splitLevel(item) !== 'NONE' && item.status === 'READY' && item.xmlAvailable && item.images.length > 0
))
const excludedItems = computed(() => (trainingInputs.value?.items ?? []).filter(item => !compatibleItems.value.includes(item)))
const allModeIncompleteItems = computed(() => (trainingInputs.value?.items ?? []).filter(item =>
  splitLevel(item) !== 'NONE' && item.status === 'READY' && (!item.xmlAvailable || item.images.length === 0)
))
const activeItems = computed(() => selectionMode.value === 'ALL'
  ? compatibleItems.value
  : compatibleItems.value.filter(item => selectedItemIds.value.has(item.itemId)))
const splitCounts = computed(() => activeItems.value.reduce<Record<string, number>>((counts, item) => {
  counts[item.split] = (counts[item.split] ?? 0) + 1
  return counts
}, {}))
const filteredInputItems = computed(() => (trainingInputs.value?.items ?? []).filter((item) => {
  if (splitFilter.value !== 'ALL' && item.split !== splitFilter.value) return false
  if (showOnlyReady.value && item.status !== 'READY') return false
  const needle = itemSearch.value.trim().toLowerCase()
  return !needle || item.pageName.toLowerCase().includes(needle)
}))
const selectedTagCounts = computed(() => {
  const selectedIds = new Set(activeItems.value.map(item => item.itemId))
  const counts: Record<string, number> = {}
  for (const item of datasetDetail.value?.items ?? []) {
    if (!selectedIds.has(item.id)) continue
    for (const tag of item.sourcePageTags || []) counts[tag] = (counts[tag] ?? 0) + 1
  }
  return Object.entries(counts).sort((left, right) => right[1] - left[1])
})
const variantOptions = computed(() => [...new Set(compatibleItems.value.flatMap(item =>
  item.images.map(image => image.variant).filter((variant): variant is string => Boolean(variant))
))].sort().map(variant => ({ label: variant, value: variant })))

function resolvedImage(item: TrainingInputItem) {
  const override = itemImageIds.value[item.itemId]
  if (override) return item.images.find(image => image.id === override)
  if (globalVariant.value) return item.images.find(image => image.variant === globalVariant.value)
  return item.images.length === 1 ? item.images[0] : undefined
}

const requiredSplitsPresent = computed(() => {
  const requirements = splitRequirements.value
  if (!requirements) return false
  return (['TRAIN', 'VAL', 'TEST'] as const).every(split =>
    requirements[split.toLowerCase() as 'train' | 'val' | 'test'] !== 'REQUIRED' || (splitCounts.value[split] ?? 0) > 0
  )
})
const requiredParametersPresent = computed(() => Object.entries(selectedProcessor.value?.parameters ?? {}).every(([key, definition]) =>
  !definition.required || (parameters.value[key] !== undefined && String(parameters.value[key]).trim().length > 0)
))
const inputsReady = computed(() => Boolean(
  selectedDatasetId.value && selectedProcessorId.value && activeItems.value.length > 0
  && (selectionMode.value !== 'ALL' || allModeIncompleteItems.value.length === 0)
  && requiredSplitsPresent.value
))
const imageSelectionReady = computed(() => activeItems.value.every(resolvedImage))
const canStart = computed(() => inputsReady.value && imageSelectionReady.value && requiredParametersPresent.value)

watch(datasets, (items) => {
  if (!selectedDatasetId.value || !items.some(item => item.id === selectedDatasetId.value)) {
    selectedDatasetId.value = items[0]?.id
  }
}, { immediate: true })
watch([workspaceId, selectedDatasetId], refreshTrainingInputs, { immediate: true })
watch(processors, (items) => {
  if (!selectedProcessorId.value || !items.some(item => item.id === selectedProcessorId.value)) {
    selectedProcessorId.value = items[0]?.id
  }
}, { immediate: true })
watch([workspaceId, selectedDatasetId], async ([, id]) => {
  selectedItemIds.value = new Set()
  itemImageIds.value = {}
  globalVariant.value = undefined
  itemSearch.value = ''
  splitFilter.value = 'ALL'
  showOnlyReady.value = false
  if (!id) return
  await refreshDatasetDetail()
}, { immediate: true })
watch(selectedProcessor, async (processor) => {
  parameters.value = {}
  parameterChoices.value = {}
  if (!processor) return
  for (const [key, definition] of Object.entries(processor.parameters ?? {})) {
    const defaultValue = definition.defaultValue ?? definition.default
    parameters.value[key] = (defaultValue ?? (definition.type === 'boolean' ? false : '')) as ActionParameterValue
  }
  if (Object.values(processor.parameters ?? {}).some(definition => definition.allowedValues?.provider)) {
    const response = await $fetch<{ values: Record<string, ActionParameterChoice[]> }>(
      `/api/workspaces/${workspaceId.value}/actions/training/processors/${processor.id}/parameter-values`
    )
    parameterChoices.value = response.values
  }
})

function toggleItem(itemId: string, checked: boolean) {
  const next = new Set(selectedItemIds.value)
  if (checked) next.add(itemId)
  else next.delete(itemId)
  selectedItemIds.value = next
}

function selectVisibleItems() {
  const next = new Set(selectedItemIds.value)
  for (const item of filteredInputItems.value) {
    if (compatibleItems.value.includes(item)) next.add(item.itemId)
  }
  selectedItemIds.value = next
}

function clearVisibleItems() {
  const visibleIds = new Set(filteredInputItems.value.map(item => item.itemId))
  selectedItemIds.value = new Set([...selectedItemIds.value].filter(id => !visibleIds.has(id)))
}

function exclusionReason(item: TrainingInputItem) {
  if (splitLevel(item) === 'NONE') return `${item.split} is unsupported by this Action`
  if (item.status !== 'READY') return item.brokenReason || 'Dataset item is broken'
  if (!item.xmlAvailable) return 'PAGE XML is missing'
  if (!item.images.length) return 'No selected image is available'
  return 'Not compatible'
}

async function startTraining() {
  if (!canStart.value || !selectedDatasetId.value || !selectedProcessorId.value) return
  starting.value = true
  try {
    const response = await $fetch<StartActionRunResponse>(
      `/api/workspaces/${workspaceId.value}/actions/training/datasets/${selectedDatasetId.value}/runs`,
      {
        method: 'POST',
        body: {
          processorDefinitionId: selectedProcessorId.value,
          selection: selectionMode.value === 'ALL'
            ? { mode: 'ALL' }
            : { mode: 'SELECTED', itemIds: [...selectedItemIds.value] },
          imageSelection: { globalVariant: globalVariant.value || null, itemImageIds: itemImageIds.value },
          parameters: parameters.value,
          enqueueIfBusy: true
        }
      }
    )
    toast.add({ title: response.run.status === 'QUEUED' ? 'Training queued' : 'Training started', color: 'success' })
    await navigateTo('/training')
  } catch (error: unknown) {
    toast.add({ title: 'Could not start training', description: error instanceof Error ? error.message : undefined, color: 'error' })
  } finally {
    starting.value = false
  }
}
</script>

<template>
  <UDashboardPanel id="training-new">
    <template #header>
      <UDashboardNavbar title="New Training" icon="i-lucide-brain-circuit">
        <template #right>
          <UButton
            to="/training"
            color="neutral"
            variant="outline"
            icon="i-lucide-arrow-left"
          >
            Training runs
          </UButton>
        </template>
      </UDashboardNavbar>
    </template>
    <template #body>
      <div class="mx-auto flex w-full max-w-7xl flex-col gap-6">
        <UStepper v-model="currentStep" :items="steps" class="w-full" />

        <UCard v-if="currentStep === 0">
          <template #header>
            <div class="flex items-center justify-between gap-3">
              <div>
                <h2 class="font-semibold">
                  Choose a dataset
                </h2>
                <p class="text-sm text-muted">
                  Select one dataset, then narrow it to compatible splits or pages.
                </p>
              </div>
              <UBadge color="neutral">
                {{ filteredDatasets.length }} dataset{{ filteredDatasets.length === 1 ? '' : 's' }}
              </UBadge>
            </div>
          </template>
          <div class="grid gap-5 lg:grid-cols-[minmax(16rem,0.8fr)_minmax(0,1.2fr)]">
            <div class="space-y-3">
              <UInput v-model="datasetSearch" icon="i-lucide-search" placeholder="Filter datasets…" />
              <USelect
                v-model="datasetTagFilter"
                :items="availableDatasetTags.map(tag => ({ label: tag, value: tag }))"
                clearable
                placeholder="Filter by tag"
              />
              <div class="max-h-96 space-y-2 overflow-y-auto pr-1">
                <button
                  v-for="dataset in filteredDatasets"
                  :key="dataset.id"
                  type="button"
                  class="w-full rounded-lg border p-3 text-left transition"
                  :class="selectedDatasetId === dataset.id ? 'border-primary bg-primary/5' : 'border-default hover:bg-elevated/50'"
                  @click="selectedDatasetId = dataset.id"
                >
                  <div class="flex items-center justify-between gap-2">
                    <span class="truncate font-medium">{{ dataset.name }}</span>
                    <UBadge size="xs" color="neutral">
                      {{ dataset.itemCount }}
                    </UBadge>
                  </div>
                  <p class="mt-1 line-clamp-2 text-xs text-muted">
                    {{ dataset.description || 'No description' }}
                  </p>
                  <div class="mt-2 flex flex-wrap gap-1">
                    <UBadge
                      v-for="tag in dataset.tags.slice(0, 3)"
                      :key="tag"
                      size="xs"
                      color="neutral"
                      variant="outline"
                    >
                      {{ tag }}
                    </UBadge>
                  </div>
                </button>
                <UEmpty
                  v-if="!filteredDatasets.length"
                  icon="i-lucide-database"
                  title="No datasets found"
                  variant="naked"
                />
              </div>
            </div>
            <div class="rounded-lg border border-default bg-elevated/20 p-4">
              <div v-if="datasetDetailLoading" class="mb-4 text-sm text-muted">
                Loading dataset details…
              </div>
              <UAlert
                v-else-if="datasetDetailError"
                color="error"
                title="Could not load dataset details"
                :description="datasetDetailError"
                class="mb-4"
              />
              <template v-if="selectedDataset">
                <div class="flex items-start justify-between gap-3">
                  <div>
                    <h3 class="font-semibold">
                      {{ selectedDataset.name }}
                    </h3>
                    <p class="text-sm text-muted">
                      {{ selectedDataset.description || 'No description' }}
                    </p>
                  </div>
                  <UButton
                    :to="`/datasets/${selectedDataset.id}`"
                    variant="link"
                    size="sm"
                    trailing-icon="i-lucide-external-link"
                  >
                    Open dataset
                  </UButton>
                </div>
                <div class="mt-5 grid grid-cols-2 gap-3 sm:grid-cols-4">
                  <div class="rounded-md bg-background p-3">
                    <p class="text-xs text-muted">
                      Items
                    </p><p class="text-lg font-semibold">
                      {{ selectedDataset.stats.totalItems }}
                    </p>
                  </div>
                  <div class="rounded-md bg-background p-3">
                    <p class="text-xs text-muted">
                      Train
                    </p><p class="text-lg font-semibold">
                      {{ selectedDataset.stats.countsBySplit.TRAIN || 0 }}
                    </p>
                  </div>
                  <div class="rounded-md bg-background p-3">
                    <p class="text-xs text-muted">
                      Validation
                    </p><p class="text-lg font-semibold">
                      {{ selectedDataset.stats.countsBySplit.VAL || 0 }}
                    </p>
                  </div>
                  <div class="rounded-md bg-background p-3">
                    <p class="text-xs text-muted">
                      Broken
                    </p><p class="text-lg font-semibold text-error">
                      {{ selectedDataset.stats.brokenItems }}
                    </p>
                  </div>
                </div>
                <div class="mt-5">
                  <UFormField label="Training Action" required>
                    <USelect v-model="selectedProcessorId" :items="processorOptions" class="w-full" />
                  </UFormField>
                  <p v-if="selectedProcessor" class="mt-2 text-sm text-muted">
                    {{ selectedProcessor.description || 'No Action description available.' }}
                  </p>
                </div>
              </template>
              <UEmpty
                v-else
                icon="i-lucide-database"
                title="Select a dataset"
                description="Dataset details and compatible Actions will appear here."
                variant="naked"
              />
            </div>
          </div>
        </UCard>

        <UCard v-else-if="currentStep === 1">
          <template #header>
            <div class="flex flex-wrap items-center justify-between gap-3">
              <div>
                <h2 class="font-semibold">
                  Filter and select training inputs
                </h2>
                <p class="text-sm text-muted">
                  {{ selectedDataset?.name || 'Dataset' }} · selected data is shown as a flat list
                </p>
              </div>
              <div class="flex flex-wrap gap-2">
                <UBadge v-for="split in ['TRAIN', 'VAL', 'TEST']" :key="split" color="neutral">
                  {{ split }} {{ splitCounts[split] || 0 }}
                </UBadge>
              </div>
            </div>
          </template>
          <div class="grid gap-5 lg:grid-cols-[minmax(15rem,0.7fr)_minmax(0,1.3fr)]">
            <div class="space-y-4">
              <div>
                <p class="mb-2 text-sm font-medium">
                  Selection mode
                </p>
                <div class="grid grid-cols-2 gap-2">
                  <UButton :variant="selectionMode === 'ALL' ? 'solid' : 'outline'" @click="selectionMode = 'ALL'">
                    All compatible
                  </UButton>
                  <UButton :variant="selectionMode === 'SELECTED' ? 'solid' : 'outline'" @click="selectionMode = 'SELECTED'">
                    Subset
                  </UButton>
                </div>
              </div>
              <UInput v-model="itemSearch" icon="i-lucide-search" placeholder="Filter pages…" />
              <USelect v-model="splitFilter" :items="[{ label: 'All splits', value: 'ALL' }, { label: 'Train', value: 'TRAIN' }, { label: 'Validation', value: 'VAL' }, { label: 'Test', value: 'TEST' }]" />
              <UFormField label="Global image variant" hint="Per-item choices below override this value.">
                <USelectMenu
                  v-model="globalVariant"
                  :items="variantOptions"
                  value-key="value"
                  placeholder="Use sole image or choose variant"
                  class="w-full"
                />
              </UFormField>
              <UCheckbox v-model="showOnlyReady" label="Only show ready items" />
              <div v-if="selectionMode === 'SELECTED'" class="flex gap-2">
                <UButton size="sm" variant="outline" @click="selectVisibleItems">
                  Select visible
                </UButton>
                <UButton size="sm" variant="ghost" @click="clearVisibleItems">
                  Clear visible
                </UButton>
              </div>
              <UAlert
                v-if="!requiredSplitsPresent"
                color="warning"
                title="Required split missing"
                description="Every required split must contain at least one selected item."
              />
              <UAlert
                v-else-if="selectionMode === 'ALL' && allModeIncompleteItems.length"
                color="warning"
                title="Incomplete items in All mode"
                description="Switch to Subset or repair the missing image/XML pairs before continuing."
              />
              <div class="rounded-lg border border-default p-3 text-sm">
                <div class="flex justify-between">
                  <span class="text-muted">Selected inputs</span><strong>{{ activeItems.length }}</strong>
                </div>
                <div class="mt-1 flex justify-between">
                  <span class="text-muted">Compatible inputs</span><span>{{ compatibleItems.length }}</span>
                </div>
                <div class="mt-1 flex justify-between">
                  <span class="text-muted">Excluded inputs</span><span>{{ excludedItems.length }}</span>
                </div>
              </div>
              <div class="rounded-lg border border-default p-3 text-sm">
                <p class="font-medium">
                  Selected tag distribution
                </p>
                <div v-if="selectedTagCounts.length" class="mt-2 flex flex-wrap gap-1">
                  <UBadge
                    v-for="[tag, count] in selectedTagCounts"
                    :key="tag"
                    size="xs"
                    color="neutral"
                    variant="outline"
                  >
                    {{ tag }} {{ count }}
                  </UBadge>
                </div>
                <p v-else class="mt-1 text-muted">
                  No tags on selected pages.
                </p>
              </div>
            </div>
            <div>
              <div v-if="loadingInputs" class="py-10 text-center text-muted">
                Loading dataset inputs…
              </div>
              <UAlert
                v-else-if="inputLoadError"
                color="error"
                title="Could not load dataset inputs"
                :description="inputLoadError"
              />
              <div v-else class="max-h-[32rem] divide-y divide-default overflow-y-auto rounded-lg border border-default">
                <div v-for="item in filteredInputItems" :key="item.itemId" class="flex items-center gap-3 p-3">
                  <UCheckbox
                    v-if="selectionMode === 'SELECTED' && compatibleItems.includes(item)"
                    :model-value="selectedItemIds.has(item.itemId)"
                    @update:model-value="toggleItem(item.itemId, Boolean($event))"
                  />
                  <UIcon v-else-if="compatibleItems.includes(item)" name="i-lucide-check-circle-2" class="size-5 text-primary" />
                  <UIcon v-else name="i-lucide-circle-slash-2" class="size-5 text-error" />
                  <UBadge :color="item.status === 'READY' ? 'neutral' : 'error'" size="sm">
                    {{ item.split }}
                  </UBadge>
                  <div class="min-w-0 flex-1">
                    <div class="truncate font-medium">
                      {{ item.pageName }}
                    </div>
                    <div class="truncate text-xs text-muted">
                      {{ compatibleItems.includes(item) ? `${item.images.length} image variant(s) · PAGE XML ready` : exclusionReason(item) }}
                    </div>
                  </div>
                  <USelect
                    v-if="compatibleItems.includes(item) && item.images.length"
                    v-model="itemImageIds[item.itemId]"
                    :items="item.images.map(image => ({ label: image.variant || image.fileName, value: image.id }))"
                    placeholder="Override image"
                    class="w-44"
                  />
                </div>
                <UEmpty
                  v-if="!filteredInputItems.length"
                  icon="i-lucide-filter-x"
                  title="No matching inputs"
                  description="Adjust the filters or choose another dataset."
                  variant="naked"
                  class="m-5"
                />
              </div>
              <div v-if="excludedItems.length" class="mt-4 rounded-lg border border-warning/50 bg-warning/5 p-3 text-sm">
                <p class="font-medium">
                  Excluded or unsupported items
                </p>
                <p v-for="item in excludedItems.slice(0, 8)" :key="item.itemId" class="mt-1 text-muted">
                  {{ item.pageName }}: {{ exclusionReason(item) }}
                </p>
                <p v-if="excludedItems.length > 8" class="mt-1 text-muted">
                  and {{ excludedItems.length - 8 }} more…
                </p>
              </div>
            </div>
          </div>
        </UCard>

        <UCard v-else-if="currentStep === 2">
          <template #header>
            <h2 class="font-semibold">
              Configure training parameters
            </h2>
          </template>
          <div class="rounded-lg border border-default p-4 text-sm">
            <p class="font-medium">
              Selected data
            </p>
            <p class="mt-1 text-muted">
              {{ activeItems.length }} inputs · {{ splitCounts.TRAIN || 0 }} train · {{ splitCounts.VAL || 0 }} validation
            </p>
          </div>
          <div class="mt-6 grid gap-5 md:grid-cols-2">
            <UFormField
              v-for="(definition, key) in selectedProcessor?.parameters"
              :key="key"
              :label="key"
              :hint="definition.description"
              :required="definition.required"
            >
              <USelect
                v-if="parameterChoices[key]?.length"
                v-model="parameters[key]"
                :items="parameterChoices[key].map(choice => ({ label: choice.label, value: choice.value }))"
                class="w-full"
              />
              <UCheckbox v-else-if="definition.type === 'boolean'" :model-value="Boolean(parameters[key])" @update:model-value="parameters[key] = Boolean($event)" />
              <UInput
                v-else
                v-model="parameters[key]"
                :type="definition.type === 'integer' || definition.type === 'number' ? 'number' : 'text'"
                :min="definition.min"
                :max="definition.max"
                class="w-full"
              />
            </UFormField>
          </div>
        </UCard>

        <UCard v-else>
          <template #header>
            <h2 class="font-semibold">
              Review training
            </h2>
            <p class="text-sm text-muted">
              Verify the frozen input set before dispatch.
            </p>
          </template>
          <div class="grid gap-4 md:grid-cols-2">
            <div class="rounded-lg border border-default p-4">
              <p class="text-xs uppercase tracking-wide text-muted">
                Dataset
              </p>
              <p class="mt-1 font-semibold">
                {{ selectedDataset?.name || '—' }}
              </p>
              <p class="mt-3 text-xs uppercase tracking-wide text-muted">
                Training Action
              </p>
              <p class="mt-1 font-semibold">
                {{ selectedProcessor?.name || '—' }}
              </p>
              <p class="mt-3 text-xs uppercase tracking-wide text-muted">
                Image variant
              </p>
              <p class="mt-1">
                {{ globalVariant || 'Per-item / sole available image' }}
              </p>
            </div>
            <div class="rounded-lg border border-default p-4">
              <p class="text-xs uppercase tracking-wide text-muted">
                Inputs
              </p>
              <p class="mt-1 text-2xl font-semibold">
                {{ activeItems.length }}
              </p>
              <div class="mt-2 flex flex-wrap gap-2">
                <UBadge v-for="split in ['TRAIN', 'VAL', 'TEST']" :key="split" color="neutral">
                  {{ split }} {{ splitCounts[split] || 0 }}
                </UBadge>
              </div>
              <p class="mt-4 text-xs uppercase tracking-wide text-muted">
                Tag distribution
              </p>
              <div v-if="selectedTagCounts.length" class="mt-2 flex flex-wrap gap-1">
                <UBadge
                  v-for="[tag, count] in selectedTagCounts"
                  :key="tag"
                  color="neutral"
                  variant="outline"
                >
                  {{ tag }} {{ count }}
                </UBadge>
              </div>
              <p v-else class="mt-1 text-sm text-muted">
                No tags on selected pages.
              </p>
            </div>
          </div>
          <div class="mt-5 rounded-lg bg-elevated/30 p-4">
            <p class="text-sm font-medium">
              Parameters
            </p>
            <dl class="mt-2 grid gap-2 sm:grid-cols-2">
              <div v-for="(value, key) in parameters" :key="key" class="flex justify-between gap-3 text-sm">
                <dt class="text-muted">
                  {{ key }}
                </dt><dd class="font-medium">
                  {{ value }}
                </dd>
              </div>
            </dl>
          </div>
          <UAlert
            v-if="!canStart"
            class="mt-5"
            color="warning"
            title="Training cannot start yet"
            description="Go back and resolve required splits, missing image/XML pairs, image variants, or required parameters."
          />
        </UCard>

        <div class="flex items-center justify-between gap-3 border-t border-default pt-4">
          <UButton
            v-if="currentStep > 0"
            color="neutral"
            variant="outline"
            icon="i-lucide-arrow-left"
            @click="currentStep--"
          >
            Back
          </UButton>
          <UButton
            v-else
            to="/training"
            color="neutral"
            variant="ghost"
          >
            Cancel
          </UButton>
          <UButton
            v-if="currentStep < steps.length - 1"
            icon="i-lucide-arrow-right"
            trailing
            :disabled="(currentStep === 0 && (!selectedDatasetId || !selectedProcessorId)) || (currentStep === 1 && !inputsReady) || (currentStep === 2 && !canStart)"
            @click="currentStep++"
          >
            Continue
          </UButton>
          <UButton
            v-else
            icon="i-lucide-play"
            :disabled="!canStart"
            :loading="starting"
            @click="startTraining"
          >
            Start training
          </UButton>
        </div>
      </div>
    </template>
  </UDashboardPanel>
</template>
