<script setup lang="ts">
import { actionImageSelectionValues } from '@/utils/action-input-requirements'
import { DATASET_SPLIT_LABELS, pageCountLabel, useDatasetRun } from '@/composables/use-dataset-run'
import type { DatasetRunKind, DatasetSplit } from '@/composables/use-dataset-run'
import type { ActionDefinitionResponse, ActionParameterDefinition, TrainingInputItem } from '@/types/action'
import { useDatasetRunPageSelection, useProjectRunSource } from '@/composables/use-project-run-source'
import type { DatasetDetail, DatasetSummary } from '@/types/dataset'

const props = defineProps<{ kind: DatasetRunKind }>()

const COPY = {
  training: {
    title: 'New Training',
    formTitle: 'Set up training',
    icon: 'i-lucide-brain-circuit',
    runsLabel: 'Training runs',
    actionLabel: 'Training Action',
    emptyActions: 'No training Actions are available',
    startLabel: 'Start training'
  },
  evaluation: {
    title: 'New Evaluation',
    formTitle: 'Set up evaluation',
    icon: 'i-lucide-chart-no-axes-combined',
    runsLabel: 'Evaluation runs',
    actionLabel: 'Evaluation Action',
    emptyActions: 'No evaluation Actions are available',
    startLabel: 'Start evaluation'
  }
}
const copy = COPY[props.kind]

type RunSource = 'PROJECTS' | 'DATASET'

await useWorkspaceBootstrap()

const route = useRoute()
const workspaceStore = useWorkspaceStore()
const workspaceId = computed(() => workspaceStore.selectedWorkspaceId)

const { data: datasets, refresh: refreshDatasets } = await useFetch<DatasetSummary[]>(
  () => `/api/workspaces/${workspaceId.value}/datasets`,
  { watch: [workspaceId], default: () => [] }
)
const { data: processors } = await useFetch<ActionDefinitionResponse[]>(
  () => `/api/workspaces/${workspaceId.value}/actions/${props.kind}/processors`,
  { watch: [workspaceId], default: () => [] }
)

const { data: projects } = await useFetch<Array<{ id: string, name: string, pageCount?: number }>>(
  () => `/api/workspaces/${workspaceId.value}/projects`,
  { watch: [workspaceId], default: () => [] }
)

const initialProjectId = typeof route.query.project === 'string' ? route.query.project : undefined
const initialDatasetId = typeof route.query.dataset === 'string' ? route.query.dataset : undefined
// Runs started from projects link to their hidden dataset, which only its source projects can select again.
const hiddenDatasetId = initialDatasetId && !datasets.value.some(dataset => dataset.id === initialDatasetId)
  ? initialDatasetId
  : undefined
const { data: hiddenDataset } = await useFetch<DatasetDetail>(
  () => `/api/workspaces/${workspaceId.value}/datasets/${hiddenDatasetId}`,
  { immediate: Boolean(hiddenDatasetId) }
)
const hiddenDatasetProjectIds = [...new Set(hiddenDataset.value?.items.map(item => item.sourceProjectId))]
const initialProjectIds = initialProjectId ? [initialProjectId] : hiddenDatasetProjectIds
const pageSelection = useDatasetRunPageSelection()
const initialPageIds = pageSelection.value?.projectId === initialProjectId ? pageSelection.value?.pageIds : undefined
pageSelection.value = null

const source = ref<RunSource>(initialProjectIds.length || (!initialDatasetId && !datasets.value.length) ? 'PROJECTS' : 'DATASET')
const usesProjectPages = computed(() => source.value === 'PROJECTS')
const projectSource = useProjectRunSource({
  workspaceId,
  initialProjectIds
})
const { projectIds: selectedProjectIds, dataset: projectDataset, pagesWithoutXml, syncing, error: syncError } = projectSource
const runDatasets = computed(() => {
  if (!usesProjectPages.value) return datasets.value
  return projectDataset.value ? [projectDataset.value] : []
})
onMounted(() => {
  void projectSource.sync()
})

const {
  selectedDatasetId,
  selectedProcessorId,
  selectedDataset,
  selectedProcessor,
  selectionMode,
  evaluateAllPages,
  allPagesSplit,
  selectedItemIds,
  globalVariants,
  itemImageIdLists,
  parameters,
  parameterChoices,
  loadingInputs,
  inputError,
  starting,
  allItems,
  usableItems,
  reviewedItems,
  activeItems,
  saveableItems,
  trainingOverlapCount,
  skippedReasons,
  splitSummary,
  tagCounts,
  variantOptions,
  acceptsImages,
  multipleImages,
  blockers,
  canStart,
  usesItem,
  skipReason,
  resolvedImages,
  selectableImages,
  selectedImageIds,
  setItemSelected,
  setItemsSelected,
  start
} = useDatasetRun({
  kind: props.kind,
  workspaceId,
  datasets: runDatasets,
  processors,
  initialDatasetId,
  initialPageIds,
  usesProjectPages
})

const saveDatasetOpen = ref(false)
const pagesOpen = ref(false)
const advancedOpen = ref(false)
const itemSearch = ref('')
const splitFilter = ref<'ALL' | DatasetSplit>('ALL')

const processorOptions = computed(() => processors.value.map(processor => ({
  label: processor.name,
  value: processor.id,
  description: processor.description ?? undefined
})))

const datasetOptions = computed(() => datasets.value.map((dataset) => {
  const splits = dataset.stats.countsBySplit
  return {
    label: dataset.name,
    value: dataset.id,
    description: `${pageCountLabel(dataset.stats.totalItems)} · ${splits.TRAIN ?? 0} training · ${splits.VAL ?? 0} validation · ${splits.TEST ?? 0} test`,
    tags: dataset.tags.join(' ')
  }
}))

const sourceOptions = computed(() => [
  { label: 'Projects', value: 'PROJECTS' },
  { label: 'Dataset', value: 'DATASET', disabled: !datasets.value.length }
])

const projectOptions = computed(() => projects.value.map(project => ({
  label: project.name,
  value: project.id,
  description: project.pageCount === undefined ? undefined : pageCountLabel(project.pageCount)
})))

const evaluationScope = computed({
  get: () => evaluateAllPages.value ? 'ALL' : 'HELD_OUT',
  set: (scope) => {
    evaluateAllPages.value = scope === 'ALL'
  }
})

const evaluationScopeOptions = computed(() => [
  { label: 'Held-out pages only', description: 'Pages set aside from training', value: 'HELD_OUT' },
  {
    label: 'All selected pages',
    description: `For this run, every page counts as ${allPagesSplit.value ? DATASET_SPLIT_LABELS[allPagesSplit.value].toLowerCase() : ''} data`,
    value: 'ALL'
  }
])

const selectionOptions = computed(() => [
  ...(usesProjectPages.value
    ? [{ label: 'Pages marked Done', description: `${reviewedItems.value.length} of ${usableItems.value.length} usable pages`, value: 'REVIEWED' }]
    : []),
  { label: 'All usable pages', description: `${usableItems.value.length} of ${allItems.value.length + pagesWithoutXmlShown.value} pages`, value: 'ALL' },
  { label: 'Choose pages', description: 'Pick the pages to include', value: 'SELECTED' }
])

const splitFilterOptions = [
  { label: 'All splits', value: 'ALL' },
  ...(Object.entries(DATASET_SPLIT_LABELS).map(([value, label]) => ({ label, value })))
]

const pagesWithoutXmlShown = computed(() => usesProjectPages.value ? pagesWithoutXml.value : 0)
const pagesLoading = computed(() => loadingInputs.value || (usesProjectPages.value && syncing.value))
const pagesError = computed(() => (usesProjectPages.value ? syncError.value : undefined) ?? inputError.value)

const skippedSummary = computed(() => {
  if (!pagesWithoutXmlShown.value) return skippedReasons.value
  const rows = skippedReasons.value.filter(entry => entry.reason !== 'No PAGE XML')
  const existing = skippedReasons.value.find(entry => entry.reason === 'No PAGE XML')?.count ?? 0
  return [{ reason: 'No PAGE XML', count: existing + pagesWithoutXmlShown.value }, ...rows]
})

const formBlockers = computed(() => usesProjectPages.value && !selectedProjectIds.value.length
  ? ['Choose at least one project.']
  : blockers.value)

const canSaveDataset = computed(() => usesProjectPages.value
  && Boolean(selectedDataset.value?.capabilities?.canEdit)
  && saveableItems.value.length > 0)

const showPages = computed(() => selectionMode.value === 'SELECTED' || pagesOpen.value)
const visibleItems = computed(() => {
  const needle = itemSearch.value.trim().toLowerCase()
  return allItems.value.filter(item =>
    (splitFilter.value === 'ALL' || item.split === splitFilter.value)
    && (!needle || item.pageName.toLowerCase().includes(needle))
  )
})

const parameterEntries = computed(() => Object.entries(selectedProcessor.value?.parameters ?? {}))
const primaryParameters = computed(() => parameterEntries.value.filter(([, definition]) => isPrimaryParameter(definition)))
const advancedParameters = computed(() => parameterEntries.value.filter(([, definition]) => !isPrimaryParameter(definition)))

const imageSummary = computed(() => {
  if (globalVariants.value.length) return globalVariants.value.join(', ')
  return activeItems.value.some(item => resolvedImages(item).length) ? 'Chosen per page' : 'None'
})

function isPrimaryParameter(definition: ActionParameterDefinition) {
  return definition.required === true || (definition.defaultValue ?? definition.default) === undefined
}

function isUsable(item: TrainingInputItem) {
  return usesItem(item) && skipReason(item) === null
}

function itemDetail(item: TrainingInputItem) {
  if (!usesItem(item)) return 'Not used by this Action'
  const reason = skipReason(item)
  if (reason) return `Skipped: ${reason}`
  const names = acceptsImages.value ? resolvedImages(item).map(image => image.variant || image.fileName) : []
  return [
    names.length ? `PAGE XML · ${names.join(', ')}` : 'PAGE XML',
    ...(item.usedForTraining ? ['Used in training'] : [])
  ].join(' · ')
}

function itemImageOptions(item: TrainingInputItem) {
  return selectableImages(item).map(image => ({ label: image.variant || image.fileName, value: image.id }))
}
</script>

<template>
  <UDashboardPanel :id="`${kind}-new`">
    <template #header>
      <UDashboardNavbar :title="copy.title" :icon="copy.icon">
        <template #right>
          <UButton
            :to="`/${kind}`"
            color="neutral"
            variant="outline"
            icon="i-lucide-arrow-left"
          >
            {{ copy.runsLabel }}
          </UButton>
        </template>
      </UDashboardNavbar>
    </template>

    <template #body>
      <UEmpty
        v-if="!processors.length"
        variant="naked"
        icon="i-lucide-zap-off"
        :title="copy.emptyActions"
        description="An Action has to be assigned to this workspace before it can run here."
      >
        <template #actions>
          <UButton to="/actions" color="neutral" variant="outline">
            View Actions
          </UButton>
        </template>
      </UEmpty>

      <UEmpty
        v-else-if="!datasets.length && !projects.length"
        variant="naked"
        icon="i-lucide-folder-open"
        title="Add a project first"
        description="Training and evaluation run on the pages of your projects."
      >
        <template #actions>
          <UButton to="/" icon="i-lucide-plus">
            Go to Projects
          </UButton>
        </template>
      </UEmpty>

      <div v-else class="mx-auto grid w-full max-w-6xl items-start gap-6 lg:grid-cols-[minmax(0,1fr)_20rem]">
        <UCard class="min-w-0" :ui="{ header: 'relative isolate overflow-hidden' }">
          <template #header>
            <UiDitherWaves />
            <h2 class="font-semibold">
              {{ copy.formTitle }}
            </h2>
          </template>
          <div class="divide-y divide-default">
            <section class="space-y-4 py-6 first:pt-0 last:pb-0">
              <h3 class="font-semibold">
                Action
              </h3>
              <UFormField :label="copy.actionLabel" required>
                <USelectMenu
                  v-model="selectedProcessorId"
                  :items="processorOptions"
                  value-key="value"
                  class="w-full"
                />
              </UFormField>
              <p v-if="selectedProcessor?.description" class="text-sm text-muted">
                {{ selectedProcessor.description }}
              </p>
              <UAlert
                v-if="selectedProcessor?.info?.trim()"
                color="info"
                variant="subtle"
                icon="i-lucide-info"
                :description="selectedProcessor.info"
                :ui="{ description: 'whitespace-pre-wrap' }"
              />
            </section>
            <section class="space-y-4 py-6 first:pt-0 last:pb-0">
              <h3 class="font-semibold">
                Pages
              </h3>
              <URadioGroup
                v-model="source"
                orientation="horizontal"
                :items="sourceOptions"
                legend="Use pages from"
              />

              <UFormField v-if="usesProjectPages" label="Projects" required>
                <USelectMenu
                  v-model="selectedProjectIds"
                  :items="projectOptions"
                  :filter-fields="['label']"
                  multiple
                  value-key="value"
                  placeholder="Choose projects"
                  class="w-full"
                />
                <template #help>
                  LAREX puts each page into training (70%), validation (15%), or test (15%) and keeps that split on every run.
                </template>
              </UFormField>

              <UFormField v-else label="Dataset" required>
                <USelectMenu
                  v-model="selectedDatasetId"
                  :items="datasetOptions"
                  :filter-fields="['label', 'description', 'tags']"
                  value-key="value"
                  class="w-full"
                />
                <template #help>
                  <span class="flex flex-wrap items-center gap-x-2">
                    <span v-if="selectedDataset?.description">{{ selectedDataset.description }}</span>
                    <ULink v-if="selectedDataset" :to="`/datasets/${selectedDataset.id}`" class="text-primary">Open dataset</ULink>
                  </span>
                </template>
              </UFormField>

              <p v-if="pagesLoading" class="py-4 text-sm text-muted">
                Loading pages…
              </p>
              <UAlert
                v-else-if="pagesError"
                color="error"
                icon="i-lucide-circle-alert"
                title="Could not load pages"
                :description="pagesError"
              />
              <template v-else-if="selectedDataset">
                <URadioGroup
                  v-if="allPagesSplit"
                  v-model="evaluationScope"
                  variant="card"
                  orientation="horizontal"
                  :items="evaluationScopeOptions"
                  :ui="{ fieldset: 'grid gap-2 sm:grid-cols-2' }"
                  legend="Evaluate on"
                />

                <URadioGroup
                  v-model="selectionMode"
                  variant="card"
                  orientation="horizontal"
                  :items="selectionOptions"
                  :ui="{ fieldset: `grid gap-2 ${selectionOptions.length === 3 ? 'sm:grid-cols-3' : 'sm:grid-cols-2'}` }"
                  aria-label="Pages to include"
                />

                <UFormField
                  v-if="acceptsImages && variantOptions.length > 1"
                  :label="multipleImages ? 'Image variants' : 'Image variant'"
                >
                  <USelectMenu
                    :model-value="multipleImages ? globalVariants : globalVariants[0]"
                    :multiple="multipleImages"
                    :items="variantOptions"
                    value-key="value"
                    placeholder="Chosen per page"
                    class="w-full"
                    @update:model-value="globalVariants = actionImageSelectionValues($event, globalVariants, true)"
                  />
                </UFormField>

                <UButton
                  v-if="selectionMode === 'ALL'"
                  color="neutral"
                  variant="link"
                  class="px-0"
                  :trailing-icon="pagesOpen ? 'i-lucide-chevron-up' : 'i-lucide-chevron-down'"
                  :aria-expanded="pagesOpen"
                  @click="pagesOpen = !pagesOpen"
                >
                  {{ pagesOpen ? 'Hide pages' : 'Show pages' }}
                </UButton>

                <div v-if="showPages" class="space-y-3">
                  <div class="flex flex-wrap items-center gap-2">
                    <UInput
                      v-model="itemSearch"
                      icon="i-lucide-search"
                      placeholder="Filter pages…"
                      aria-label="Filter pages by name"
                      class="min-w-48 flex-1"
                    />
                    <USelect
                      v-model="splitFilter"
                      :items="splitFilterOptions"
                      aria-label="Filter pages by split"
                      class="w-36"
                    />
                    <template v-if="selectionMode === 'SELECTED'">
                      <UButton
                        size="sm"
                        color="neutral"
                        variant="outline"
                        @click="setItemsSelected(visibleItems, true)"
                      >
                        Select shown
                      </UButton>
                      <UButton
                        size="sm"
                        color="neutral"
                        variant="ghost"
                        @click="setItemsSelected(visibleItems, false)"
                      >
                        Clear shown
                      </UButton>
                    </template>
                  </div>

                  <div class="max-h-[28rem] divide-y divide-default overflow-y-auto rounded-lg border border-default">
                    <div v-for="item in visibleItems" :key="item.itemId" class="flex items-center gap-3 p-3">
                      <UCheckbox
                        v-if="selectionMode === 'SELECTED' && isUsable(item)"
                        :model-value="selectedItemIds.has(item.itemId)"
                        :aria-label="`Include ${item.pageName}`"
                        @update:model-value="setItemSelected(item.itemId, Boolean($event))"
                      />
                      <UIcon
                        v-else
                        :name="isUsable(item) ? 'i-lucide-check' : 'i-lucide-minus'"
                        class="size-5 shrink-0"
                        :class="isUsable(item) ? 'text-primary' : 'text-muted'"
                      />
                      <div class="min-w-0 flex-1">
                        <p class="truncate text-sm font-medium">
                          {{ item.pageName }}
                        </p>
                        <p class="truncate text-xs text-muted">
                          {{ DATASET_SPLIT_LABELS[item.split] }} · {{ itemDetail(item) }}
                        </p>
                      </div>
                      <USelect
                        v-if="isUsable(item) && itemImageOptions(item).length > 1"
                        :model-value="multipleImages ? selectedImageIds(item) : selectedImageIds(item)[0]"
                        :multiple="multipleImages"
                        :items="itemImageOptions(item)"
                        :aria-label="`Image for ${item.pageName}`"
                        class="w-40"
                        @update:model-value="itemImageIdLists[item.itemId] = actionImageSelectionValues($event, selectedImageIds(item), true)"
                      />
                    </div>
                    <UEmpty
                      v-if="!visibleItems.length"
                      icon="i-lucide-filter-x"
                      title="No matching pages"
                      variant="naked"
                      class="m-5"
                    />
                  </div>
                </div>
              </template>
            </section>
            <section v-if="parameterEntries.length" class="space-y-4 py-6 first:pt-0 last:pb-0">
              <h3 class="font-semibold">
                Settings
              </h3>
              <div v-if="primaryParameters.length" class="grid gap-4 md:grid-cols-2">
                <ActionParameterField
                  v-for="[key, definition] in primaryParameters"
                  :key="key"
                  v-model="parameters[key]"
                  :name="key"
                  :definition="definition"
                  :discovered-values="parameterChoices"
                />
              </div>

              <UCollapsible v-if="advancedParameters.length" v-model:open="advancedOpen">
                <UButton
                  color="neutral"
                  variant="link"
                  class="px-0"
                  :trailing-icon="advancedOpen ? 'i-lucide-chevron-up' : 'i-lucide-chevron-down'"
                >
                  Advanced settings ({{ advancedParameters.length }})
                </UButton>
                <template #content>
                  <div class="grid gap-4 pt-3 md:grid-cols-2">
                    <ActionParameterField
                      v-for="[key, definition] in advancedParameters"
                      :key="key"
                      v-model="parameters[key]"
                      :name="key"
                      :definition="definition"
                      :discovered-values="parameterChoices"
                    />
                  </div>
                </template>
              </UCollapsible>
            </section>
          </div>
        </UCard>

        <aside class="lg:sticky lg:top-0" aria-label="Run summary">
          <UCard>
            <div class="space-y-4">
              <div>
                <p class="text-2xl font-semibold tabular-nums">
                  {{ pageCountLabel(activeItems.length) }}
                </p>
                <p class="truncate text-sm text-muted">
                  {{ selectedDataset?.name }}
                </p>
              </div>

              <dl class="space-y-1 text-sm">
                <div v-for="row in splitSummary" :key="row.split" class="flex justify-between gap-3">
                  <dt class="text-muted">
                    {{ row.label }}
                  </dt>
                  <dd v-if="row.level === 'NONE'" class="text-muted">
                    {{ row.unusedCount ? `${row.unusedCount} held out` : 'Not used' }}
                  </dd>
                  <dd v-else class="font-medium tabular-nums" :class="{ 'text-error': row.level === 'REQUIRED' && row.count === 0 }">
                    {{ row.count }}
                  </dd>
                </div>
                <div v-if="acceptsImages" class="flex justify-between gap-3">
                  <dt class="text-muted">
                    Images
                  </dt>
                  <dd class="truncate font-medium">
                    {{ imageSummary }}
                  </dd>
                </div>
              </dl>

              <div v-if="skippedSummary.length" class="text-sm">
                <p class="font-medium">
                  Skipped
                </p>
                <ul class="mt-1 space-y-1 text-muted">
                  <li v-for="entry in skippedSummary" :key="entry.reason" class="flex justify-between gap-3">
                    <span class="min-w-0 truncate">{{ entry.reason }}</span>
                    <span class="tabular-nums">{{ entry.count }}</span>
                  </li>
                </ul>
              </div>

              <div v-if="tagCounts.length" class="flex flex-wrap gap-1">
                <UBadge
                  v-for="[tag, count] in tagCounts"
                  :key="tag"
                  size="sm"
                  color="neutral"
                  variant="outline"
                >
                  {{ tag }} {{ count }}
                </UBadge>
              </div>

              <UAlert
                v-if="trainingOverlapCount"
                color="warning"
                variant="subtle"
                icon="i-lucide-triangle-alert"
                :title="`${pageCountLabel(trainingOverlapCount)} used in training`"
                description="An earlier training run used these pages. If your model came from that run, its scores will look better than they should."
              />

              <ul v-if="formBlockers.length" class="space-y-1 text-sm text-error">
                <li v-for="blocker in formBlockers" :key="blocker" class="flex gap-2">
                  <UIcon name="i-lucide-circle-alert" class="mt-0.5 size-4 shrink-0" />
                  <span>{{ blocker }}</span>
                </li>
              </ul>

              <UButton
                block
                icon="i-lucide-play"
                :disabled="!canStart || pagesLoading"
                :loading="starting"
                @click="start"
              >
                {{ copy.startLabel }}
              </UButton>
              <UButton
                v-if="canSaveDataset"
                block
                color="neutral"
                variant="outline"
                icon="i-lucide-bookmark-plus"
                @click="saveDatasetOpen = true"
              >
                Save as dataset
              </UButton>
              <p class="text-xs text-muted">
                LAREX copies these pages when the run starts, so later edits won't change it.
              </p>
            </div>
          </UCard>
        </aside>
      </div>

      <ActionSaveDatasetModal
        v-if="selectedDataset"
        v-model:open="saveDatasetOpen"
        :workspace-id="workspaceId"
        :source-dataset="selectedDataset"
        :item-ids="saveableItems.map(item => item.itemId)"
        @saved="refreshDatasets()"
      />
    </template>
  </UDashboardPanel>
</template>
