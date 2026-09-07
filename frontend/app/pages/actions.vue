<script setup lang="ts">
import type { ActionCategory, ActionDefinitionResponse, ActionKind, ActionTarget } from '@/types/action'

const { selectedWorkspace } = await useWorkspaceBootstrap()

const processingProcessorsKey = computed(() =>
  selectedWorkspace.value
    ? wsKey(selectedWorkspace.value, 'actions', 'processing-processors')
    : 'pending:actions:processing-processors'
)

const processingRequest = await useFetch<ActionDefinitionResponse[]>(
  () => selectedWorkspace.value
    ? `/api/workspaces/${selectedWorkspace.value}/actions/inference/processors`
    : '/api/workspaces/none/actions/inference/processors',
  {
    key: processingProcessorsKey,
    default: () => []
  }
)
const trainingRequest = await useFetch<ActionDefinitionResponse[]>(
  () => selectedWorkspace.value
    ? `/api/workspaces/${selectedWorkspace.value}/actions/training/processors`
    : '/api/workspaces/none/actions/training/processors',
  {
    key: computed(() => selectedWorkspace.value
      ? wsKey(selectedWorkspace.value, 'actions', 'training-processors')
      : 'pending:actions:training-processors'),
    default: () => []
  }
)
const evaluationRequest = await useFetch<ActionDefinitionResponse[]>(
  () => selectedWorkspace.value
    ? `/api/workspaces/${selectedWorkspace.value}/actions/evaluation/processors`
    : '/api/workspaces/none/actions/evaluation/processors',
  {
    key: computed(() => selectedWorkspace.value
      ? wsKey(selectedWorkspace.value, 'actions', 'evaluation-processors')
      : 'pending:actions:evaluation-processors'),
    default: () => []
  }
)

const processors = computed(() => [
  ...(processingRequest.data.value ?? []),
  ...(trainingRequest.data.value ?? []),
  ...(evaluationRequest.data.value ?? [])
].sort((left, right) => left.name.localeCompare(right.name)))
const loading = computed(() => processingRequest.pending.value || trainingRequest.pending.value || evaluationRequest.pending.value)
const error = computed(() => processingRequest.error.value || trainingRequest.error.value || evaluationRequest.error.value)

const categoryMeta: Record<ActionCategory, { label: string, surface: string }> = {
  WORKFLOW: { label: 'Workflow', surface: 'bg-primary/10' },
  OCR_HTR: { label: 'OCR / HTR', surface: 'bg-info/10' },
  LAYOUT: { label: 'Layout', surface: 'bg-success/10' },
  POSTPROCESSING: { label: 'Post-processing', surface: 'bg-warning/10' }
}
const actionKindMeta: Record<ActionKind, { label: string, icon: string }> = {
  PROCESSING: { label: 'Processing', icon: 'i-lucide-play' },
  TRAINING: { label: 'Training', icon: 'i-lucide-brain-circuit' },
  EVALUATION: { label: 'Evaluation', icon: 'i-lucide-chart-no-axes-combined' }
}

function categoryDetails(category: ActionCategory) {
  return categoryMeta[category] ?? { label: category, surface: 'bg-elevated' }
}

function actionKindDetails(kind?: ActionKind) {
  return actionKindMeta[kind ?? 'PROCESSING']
}

function targetLabel(target: ActionTarget) {
  return target.replaceAll('_', ' ').toLowerCase().replace(/\b\w/g, character => character.toUpperCase())
}

function inputLabels(processor: ActionDefinitionResponse) {
  const inputs: string[] = []
  if (processor.acceptsImages) inputs.push('Images')
  if (processor.acceptsXml) inputs.push('PAGE XML')
  return inputs.length > 0 ? inputs : ['No files']
}

function outputLabels(processor: ActionDefinitionResponse) {
  const outputs: string[] = []
  if (processor.outputsImages) outputs.push('Images')
  if (processor.outputsXml) outputs.push('PAGE XML')
  if (processor.outputsFiles) outputs.push('Files')
  return outputs.length > 0 ? outputs : ['No LAREX outputs']
}

const searchFilter = ref('')
const selectedKinds = ref<ActionKind[]>([])
const selectedCategories = ref<ActionCategory[]>([])
const selectedTargets = ref<ActionTarget[]>([])
const selectedInputs = ref<string[]>([])
const selectedOutputs = ref<string[]>([])

const categoryOptions = Object.entries(categoryMeta).map(([value, meta]) => ({
  label: meta.label,
  value: value as ActionCategory
}))
const actionKindOptions = Object.entries(actionKindMeta).map(([value, meta]) => ({
  label: meta.label,
  value: value as ActionKind
}))
const targetOptions = (['PAGE', 'REGION', 'TEXT_LINE'] as ActionTarget[]).map(value => ({
  label: targetLabel(value),
  value
}))
const inputOptions = [
  { label: 'Images', value: 'IMAGES' },
  { label: 'PAGE XML', value: 'XML' },
  { label: 'No files', value: 'NONE' }
]
const outputOptions = [
  { label: 'Images', value: 'IMAGES' },
  { label: 'PAGE XML', value: 'XML' },
  { label: 'Files', value: 'FILES' },
  { label: 'No LAREX outputs', value: 'NONE' }
]

function hasInput(processor: ActionDefinitionResponse, input: string) {
  if (input === 'IMAGES') return processor.acceptsImages
  if (input === 'XML') return processor.acceptsXml
  return !processor.acceptsImages && !processor.acceptsXml
}

function hasOutput(processor: ActionDefinitionResponse, output: string) {
  if (output === 'IMAGES') return processor.outputsImages
  if (output === 'XML') return processor.outputsXml
  if (output === 'FILES') return processor.outputsFiles
  return !processor.outputsImages && !processor.outputsXml && !processor.outputsFiles
}

const filteredProcessors = computed(() => {
  const needle = searchFilter.value.trim().toLowerCase()
  return (processors.value ?? []).filter((processor) => {
    if (needle && ![processor.name, processor.processorKey, processor.description]
      .some(value => value?.toLowerCase().includes(needle))) return false
    if (selectedKinds.value.length > 0 && !selectedKinds.value.includes(processor.kind ?? 'PROCESSING')) return false
    if (selectedCategories.value.length > 0 && !selectedCategories.value.includes(processor.category)) return false
    if (selectedTargets.value.length > 0 && !selectedTargets.value.some(target => processor.targets.includes(target))) return false
    if (selectedInputs.value.length > 0 && !selectedInputs.value.some(input => hasInput(processor, input))) return false
    if (selectedOutputs.value.length > 0 && !selectedOutputs.value.some(output => hasOutput(processor, output))) return false
    return true
  })
})

const hasActiveFilters = computed(() => Boolean(
  searchFilter.value.trim()
  || selectedKinds.value.length
  || selectedCategories.value.length
  || selectedTargets.value.length
  || selectedInputs.value.length
  || selectedOutputs.value.length
))

function clearFilters() {
  searchFilter.value = ''
  selectedKinds.value = []
  selectedCategories.value = []
  selectedTargets.value = []
  selectedInputs.value = []
  selectedOutputs.value = []
}

function refreshProcessors() {
  return Promise.all([
    processingRequest.refresh(),
    trainingRequest.refresh(),
    evaluationRequest.refresh()
  ])
}
</script>

<template>
  <UDashboardPanel id="actions">
    <template #header>
      <UDashboardNavbar title="Actions">
        <template #right>
          <UButton
            color="neutral"
            variant="ghost"
            icon="i-lucide-refresh-cw"
            size="sm"
            square
            aria-label="Refresh Actions"
            title="Refresh"
            :loading="loading"
            @click="refreshProcessors"
          />
        </template>
      </UDashboardNavbar>
      <UDashboardToolbar>
        <template #left>
          <UInput
            v-model="searchFilter"
            icon="i-lucide-search"
            placeholder="Search name or description…"
            class="w-full sm:w-64"
          />
          <USelectMenu
            v-model="selectedKinds"
            :items="actionKindOptions"
            value-key="value"
            multiple
            placeholder="Action kind"
            class="w-full sm:w-40"
          />
          <USelectMenu
            v-model="selectedCategories"
            :items="categoryOptions"
            value-key="value"
            multiple
            placeholder="Category"
            class="w-full sm:w-44"
          />
          <USelectMenu
            v-model="selectedTargets"
            :items="targetOptions"
            value-key="value"
            multiple
            placeholder="Targets"
            class="w-full sm:w-40"
          />
          <USelectMenu
            v-model="selectedInputs"
            :items="inputOptions"
            value-key="value"
            multiple
            placeholder="Inputs"
            class="w-full sm:w-40"
          />
          <USelectMenu
            v-model="selectedOutputs"
            :items="outputOptions"
            value-key="value"
            multiple
            placeholder="Outputs"
            class="w-full sm:w-40"
          />
          <AppTableClearFiltersButton
            :active="hasActiveFilters"
            @clear="clearFilters"
          />
        </template>
      </UDashboardToolbar>
    </template>

    <template #body>
      <div class="mx-auto flex w-full max-w-7xl flex-col gap-6 px-4 py-6 sm:px-6 lg:px-8">
        <UAlert
          v-if="error"
          color="error"
          variant="subtle"
          icon="i-lucide-circle-alert"
          title="Could not load Actions"
          :description="error.message || 'Try refreshing the page.'"
        />

        <div v-else-if="loading" class="grid gap-4 md:grid-cols-2 xl:grid-cols-3">
          <UCard
            v-for="index in 6"
            :key="index"
            variant="subtle"
            class="min-h-64"
          >
            <div class="space-y-4">
              <USkeleton class="size-12 rounded-lg" />
              <USkeleton class="h-5 w-2/3" />
              <USkeleton class="h-4 w-full" />
              <USkeleton class="h-4 w-5/6" />
            </div>
          </UCard>
        </div>

        <UEmpty
          v-else-if="!processors?.length"
          variant="naked"
          icon="i-lucide-scan-text"
          title="No Actions available"
          description="A workspace administrator can enable Actions for this workspace."
        />

        <UEmpty
          v-else-if="!filteredProcessors.length"
          variant="naked"
          icon="i-lucide-filter-x"
          title="No matching Actions"
          description="Adjust the current filters."
        />

        <div v-else class="grid gap-5 md:grid-cols-2 xl:grid-cols-3">
          <UCard
            v-for="processor in filteredProcessors"
            :key="processor.id"
            variant="outline"
            class="h-full overflow-hidden rounded-3xl shadow-sm"
            :ui="{ body: 'p-3 sm:p-3' }"
          >
            <div class="flex h-full flex-col gap-4">
              <div
                :class="[
                  'relative aspect-[4/3] overflow-hidden rounded-2xl',
                  categoryDetails(processor.category).surface
                ]"
              >
                <AppAvatar
                  class="absolute inset-0 size-full scale-125"
                  :seed="processor.id"
                  :alt="processor.name"
                  :radius="24"
                  fluid
                />
                <div class="pointer-events-none absolute inset-0 z-10 bg-linear-to-br from-default/50 via-transparent to-primary/10" />

                <div class="absolute inset-x-4 top-4 z-20 flex items-start justify-between gap-3">
                  <div class="flex min-w-0 flex-wrap items-center gap-2">
                    <UBadge color="neutral" variant="outline" class="bg-default/75 backdrop-blur-sm">
                      <UIcon :name="actionKindDetails(processor.kind).icon" class="size-3.5" />
                      {{ actionKindDetails(processor.kind).label }}
                    </UBadge>
                    <UBadge color="neutral" variant="soft" class="bg-default/75 backdrop-blur-sm">
                      {{ categoryDetails(processor.category).label }}
                    </UBadge>
                  </div>
                  <UBadge :color="processor.global ? 'primary' : 'neutral'" variant="soft" class="bg-default/75 backdrop-blur-sm">
                    {{ processor.global ? 'Global' : 'Workspace' }}
                  </UBadge>
                </div>

                <div class="absolute inset-x-4 bottom-4 z-20 rounded-xl bg-default/75 px-3 py-2 backdrop-blur-sm">
                  <h2 class="truncate text-base font-semibold text-highlighted">
                    {{ processor.name }}
                  </h2>
                  <p class="truncate font-mono text-[11px] text-muted" :title="processor.processorKey">
                    {{ processor.processorKey }}
                  </p>
                </div>
              </div>

              <div class="px-2">
                <p class="line-clamp-2 min-h-10 text-sm leading-5 text-muted">
                  {{ processor.description || 'No description provided.' }}
                </p>
              </div>

              <div class="mt-auto grid grid-cols-3 gap-3 border-t border-default px-2 pt-4 text-xs">
                <div>
                  <p class="text-muted">
                    Targets
                  </p>
                  <p class="mt-1 truncate font-medium text-highlighted" :title="processor.targets.map(targetLabel).join(', ')">
                    {{ processor.targets.length ? processor.targets.map(targetLabel).join(', ') : '—' }}
                  </p>
                </div>
                <div>
                  <p class="text-muted">
                    Inputs
                  </p>
                  <p class="mt-1 truncate font-medium text-highlighted" :title="inputLabels(processor).join(', ')">
                    {{ inputLabels(processor).join(', ') }}
                  </p>
                </div>
                <div>
                  <p class="text-muted">
                    Outputs
                  </p>
                  <p class="mt-1 truncate font-medium text-highlighted" :title="outputLabels(processor).join(', ')">
                    {{ outputLabels(processor).join(', ') }}
                  </p>
                </div>
              </div>
            </div>
          </UCard>
        </div>
      </div>
    </template>
  </UDashboardPanel>
</template>
