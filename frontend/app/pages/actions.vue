<script setup lang="ts">
import type { TableColumn } from '@nuxt/ui'
import type { ActionDefinitionResponse, ActionKind, ActionTarget } from '@/types/action'

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

const actionKindMeta: Record<ActionKind, { label: string, icon: string }> = {
  PROCESSING: { label: 'Processing', icon: 'i-lucide-play' },
  TRAINING: { label: 'Training', icon: 'i-lucide-brain-circuit' },
  EVALUATION: { label: 'Evaluation', icon: 'i-lucide-chart-no-axes-combined' }
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

const columns: TableColumn<ActionDefinitionResponse>[] = [
  { accessorKey: 'name', header: 'Action' },
  { accessorKey: 'description', header: 'Description' },
  { accessorKey: 'kind', header: 'Kind' },
  { accessorKey: 'tags', header: 'Tags' },
  { id: 'scope', header: 'Scope' },
  { accessorKey: 'targets', header: 'Targets' },
  { id: 'inputs', header: 'Inputs' },
  { id: 'outputs', header: 'Outputs' }
]

const searchFilter = ref('')
const selectedKinds = ref<ActionKind[]>([])
const selectedTags = ref<string[]>([])
const selectedTargets = ref<ActionTarget[]>([])
const selectedInputs = ref<string[]>([])
const selectedOutputs = ref<string[]>([])

const tagOptions = computed(() => [...new Set(processors.value.flatMap(processor => processor.tags))]
  .sort((left, right) => left.localeCompare(right))
  .map(value => ({ label: value, value })))
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
    if (selectedTags.value.length > 0 && !selectedTags.value.some(tag => processor.tags.includes(tag))) return false
    if (selectedTargets.value.length > 0 && !selectedTargets.value.some(target => processor.targets.includes(target))) return false
    if (selectedInputs.value.length > 0 && !selectedInputs.value.some(input => hasInput(processor, input))) return false
    if (selectedOutputs.value.length > 0 && !selectedOutputs.value.some(output => hasOutput(processor, output))) return false
    return true
  })
})

const hasActiveFilters = computed(() => Boolean(
  searchFilter.value.trim()
  || selectedKinds.value.length
  || selectedTags.value.length
  || selectedTargets.value.length
  || selectedInputs.value.length
  || selectedOutputs.value.length
))

function clearFilters() {
  searchFilter.value = ''
  selectedKinds.value = []
  selectedTags.value = []
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
            v-model="selectedTags"
            :items="tagOptions"
            value-key="value"
            multiple
            placeholder="Tags"
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
        <template #right>
          <AppTableColumnsDropdown table-id="workspace-actions" :columns="columns" />
        </template>
      </UDashboardToolbar>
    </template>

    <template #body>
      <UAlert
        v-if="error"
        color="error"
        variant="subtle"
        icon="i-lucide-circle-alert"
        title="Could not load Actions"
        :description="error.message || 'Try refreshing the page.'"
      />

      <AppTable
        v-else
        table-id="workspace-actions"
        :columns="columns"
        :data="filteredProcessors"
        :loading="loading"
        class="flex-1"
      >
        <template #name-cell="{ row }">
          <div class="min-w-0">
            <p class="truncate font-medium text-highlighted" :title="row.original.name">
              {{ row.original.name }}
            </p>
            <p class="truncate font-mono text-xs text-muted" :title="row.original.processorKey">
              {{ row.original.processorKey }}
            </p>
          </div>
        </template>
        <template #description-cell="{ row }">
          <p class="line-clamp-2 whitespace-normal text-muted" :title="row.original.description || undefined">
            {{ row.original.description || 'No description provided.' }}
          </p>
        </template>
        <template #kind-cell="{ row }">
          <UBadge color="neutral" variant="subtle" :icon="actionKindDetails(row.original.kind).icon">
            {{ actionKindDetails(row.original.kind).label }}
          </UBadge>
        </template>
        <template #tags-cell="{ row }">
          <div v-if="row.original.tags.length" class="flex flex-wrap gap-1">
            <UBadge
              v-for="tag in row.original.tags"
              :key="tag"
              color="neutral"
              variant="subtle"
            >
              {{ tag }}
            </UBadge>
          </div>
          <span v-else>—</span>
        </template>
        <template #scope-cell="{ row }">
          <UBadge :color="row.original.global ? 'primary' : 'neutral'" variant="subtle">
            {{ row.original.global ? 'Global' : 'Workspace' }}
          </UBadge>
        </template>
        <template #targets-cell="{ row }">
          <span class="whitespace-normal">
            {{ row.original.targets.length ? row.original.targets.map(targetLabel).join(', ') : '—' }}
          </span>
        </template>
        <template #inputs-cell="{ row }">
          <span class="whitespace-normal">{{ inputLabels(row.original).join(', ') }}</span>
        </template>
        <template #outputs-cell="{ row }">
          <span class="whitespace-normal">{{ outputLabels(row.original).join(', ') }}</span>
        </template>
        <template #loading>
          <div class="flex items-center justify-center gap-2 py-6 text-muted">
            <UIcon name="i-lucide-loader-circle" class="size-5 animate-spin" />
            <span>Loading Actions…</span>
          </div>
        </template>
        <template #empty>
          <UEmpty
            variant="naked"
            :icon="processors.length ? 'i-lucide-filter-x' : 'i-lucide-scan-text'"
            :title="processors.length ? 'No matching Actions' : 'No Actions available'"
            :description="processors.length ? 'Adjust the current filters.' : 'A workspace curator or administrator must enable Actions for this workspace.'"
          />
        </template>
      </AppTable>
    </template>
  </UDashboardPanel>
</template>
