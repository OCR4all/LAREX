<!-- eslint-disable @stylistic/max-statements-per-line -->
<script setup lang="ts">
import type { ActionDefinitionResponse, ActionEvaluationDefinition, ActionParameterChoice, ActionParameterValue, StartActionRunResponse, TrainingInputItem, TrainingInputResponse } from '@/types/action'
import type { DatasetSummary } from '@/types/dataset'

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
const inputs = ref<TrainingInputResponse | null>(null)
const loadingInputs = ref(false)
const startPending = ref(false)
const currentStep = ref(0)
const itemSearch = ref('')
const splitFilter = ref<'ALL' | 'TRAIN' | 'VAL' | 'TEST'>('ALL')
const datasetSearch = ref('')

const { data: datasets } = await useFetch<DatasetSummary[]>(() => `/api/workspaces/${workspaceId.value}/datasets`, { watch: [workspaceId], default: () => [] })
const { data: processors } = await useFetch<ActionDefinitionResponse[]>(() => `/api/workspaces/${workspaceId.value}/actions/evaluation/processors`, { watch: [workspaceId], default: () => [] })
watch(datasets, (value) => { if (!selectedDatasetId.value || !value.some(item => item.id === selectedDatasetId.value)) selectedDatasetId.value = value[0]?.id }, { immediate: true })
watch(processors, (value) => { if (!selectedProcessorId.value || !value.some(item => item.id === selectedProcessorId.value)) selectedProcessorId.value = value[0]?.id }, { immediate: true })

const selectedProcessor = computed(() => processors.value.find(item => item.id === selectedProcessorId.value))
const evaluation = computed<ActionEvaluationDefinition | null>(() => selectedProcessor.value?.evaluation ?? null)
const splitRequirements = computed(() => evaluation.value?.splits)
const filteredDatasets = computed(() => datasets.value.filter(dataset => `${dataset.name} ${dataset.description || ''}`.toLowerCase().includes(datasetSearch.value.trim().toLowerCase())))
const compatibleItems = computed(() => (inputs.value?.items || []).filter(item => splitLevel(item) !== 'NONE' && item.status === 'READY' && item.xmlAvailable && item.images.length > 0))
const activeItems = computed(() => selectionMode.value === 'ALL' ? compatibleItems.value : compatibleItems.value.filter(item => selectedItemIds.value.has(item.itemId)))
const filteredItems = computed(() => (inputs.value?.items || []).filter(item => (splitFilter.value === 'ALL' || item.split === splitFilter.value) && item.pageName.toLowerCase().includes(itemSearch.value.trim().toLowerCase())))
const splitCounts = computed(() => activeItems.value.reduce<Record<string, number>>((result, item) => { result[item.split] = (result[item.split] || 0) + 1; return result }, {}))
const variantOptions = computed(() => [...new Set(compatibleItems.value.flatMap(item => item.images.map(image => image.variant).filter((value): value is string => Boolean(value))))].map(value => ({ label: value, value })))
const requiredSplitsPresent = computed(() => ['TRAIN', 'VAL', 'TEST'].every(split => splitLevelFor(split as 'TRAIN' | 'VAL' | 'TEST') !== 'REQUIRED' || (splitCounts.value[split] || 0) > 0))
const imageSelectionReady = computed(() => activeItems.value.every((item) => {
  const override = itemImageIds.value[item.itemId]
  if (override) return item.images.some(image => image.id === override)
  if (globalVariant.value) return item.images.some(image => image.variant === globalVariant.value)
  return item.images.length === 1
}))
const requiredParametersPresent = computed(() => Object.entries(selectedProcessor.value?.parameters || {}).every(([key, definition]) => !definition.required || (parameters.value[key] !== undefined && String(parameters.value[key]).trim().length > 0)))
const canStart = computed(() => Boolean(selectedDatasetId.value && selectedProcessorId.value && activeItems.value.length && requiredSplitsPresent.value && imageSelectionReady.value && requiredParametersPresent.value))
const steps = [{ title: 'Dataset', description: 'Choose a dataset', icon: 'i-lucide-database' }, { title: 'Subset', description: 'Select pages and splits', icon: 'i-lucide-list-checks' }, { title: 'Action', description: 'Select model and parameters', icon: 'i-lucide-sliders-horizontal' }, { title: 'Review', description: 'Confirm and start', icon: 'i-lucide-rocket' }]
const processorOptions = computed(() => processors.value.map(item => ({ label: `${item.name} · ${item.evaluation?.profile || 'evaluation'}`, value: item.id })))

function splitLevel(item: TrainingInputItem) { return splitLevelFor(item.split) }
function splitLevelFor(split: 'TRAIN' | 'VAL' | 'TEST') { return splitRequirements.value?.[split.toLowerCase() as 'train' | 'val' | 'test'] || 'NONE' }
async function loadInputs() {
  if (!workspaceId.value || !selectedDatasetId.value) return
  loadingInputs.value = true
  try { inputs.value = await $fetch<TrainingInputResponse>(`/api/workspaces/${workspaceId.value}/actions/evaluation/datasets/${selectedDatasetId.value}/inputs`) } catch { inputs.value = null } finally { loadingInputs.value = false }
}
watch([workspaceId, selectedDatasetId], () => { selectedItemIds.value = new Set(); itemImageIds.value = {}; globalVariant.value = undefined; void loadInputs() }, { immediate: true })
watch(selectedProcessor, async (processor) => {
  parameters.value = {}; parameterChoices.value = {}
  if (!processor) return
  for (const [key, definition] of Object.entries(processor.parameters || {})) parameters.value[key] = (definition.defaultValue ?? definition.default ?? (definition.type === 'boolean' ? false : '')) as ActionParameterValue
  if (Object.values(processor.parameters || {}).some(definition => definition.allowedValues?.provider)) {
    const result = await $fetch<{ values: Record<string, ActionParameterChoice[]> }>(`/api/workspaces/${workspaceId.value}/actions/evaluation/processors/${processor.id}/parameter-values`)
    parameterChoices.value = result.values
  }
})
function toggleItem(itemId: string, value: boolean) {
  const next = new Set(selectedItemIds.value)
  if (value) next.add(itemId)
  else next.delete(itemId)
  selectedItemIds.value = next
}
async function startEvaluation() {
  if (!canStart.value) return
  startPending.value = true
  try {
    const response = await $fetch<StartActionRunResponse>(`/api/workspaces/${workspaceId.value}/actions/evaluation/datasets/${selectedDatasetId.value}/runs`, { method: 'POST', body: { processorDefinitionId: selectedProcessorId.value, selection: selectionMode.value === 'ALL' ? { mode: 'ALL' } : { mode: 'SELECTED', itemIds: [...selectedItemIds.value] }, imageSelection: { globalVariant: globalVariant.value || null, itemImageIds: itemImageIds.value }, parameters: parameters.value, enqueueIfBusy: true } })
    toast.add({ title: response.run.status === 'QUEUED' ? 'Evaluation queued' : 'Evaluation started', color: 'success' }); await navigateTo('/evaluation')
  } catch (error: unknown) { toast.add({ title: 'Could not start evaluation', description: error instanceof Error ? error.message : undefined, color: 'error' }) } finally { startPending.value = false }
}
</script>

<template>
  <UDashboardPanel id="evaluation-new">
    <template #header>
      <UDashboardNavbar title="New Evaluation">
        <template #right>
          <UButton
            to="/evaluation"
            color="neutral"
            variant="outline"
            icon="i-lucide-arrow-left"
          >
            Evaluation runs
          </UButton>
        </template>
      </UDashboardNavbar>
    </template>
    <template #body>
      <div class="mx-auto flex w-full max-w-7xl flex-col gap-6">
        <UStepper v-model="currentStep" :items="steps" />
        <UCard v-if="currentStep === 0">
          <template #header>
            <h2 class="font-semibold">
              Choose a dataset
            </h2><p class="text-sm text-muted">
              The dataset is frozen when evaluation starts.
            </p>
          </template><div class="grid gap-5 lg:grid-cols-[minmax(16rem,.8fr)_minmax(0,1.2fr)]">
            <div class="space-y-3">
              <UInput v-model="datasetSearch" icon="i-lucide-search" placeholder="Filter datasets…" /><div class="max-h-96 space-y-2 overflow-y-auto">
                <button
                  v-for="dataset in filteredDatasets"
                  :key="dataset.id"
                  type="button"
                  class="w-full rounded-lg border p-3 text-left"
                  :class="selectedDatasetId === dataset.id ? 'border-primary bg-primary/5' : 'border-default'"
                  @click="selectedDatasetId = dataset.id"
                >
                  <div class="flex justify-between">
                    <span class="truncate font-medium">{{ dataset.name }}</span><UBadge size="xs" color="neutral">
                      {{ dataset.itemCount }}
                    </UBadge>
                  </div><p class="mt-1 text-xs text-muted">
                    {{ dataset.description || 'No description' }}
                  </p>
                </button>
              </div>
            </div><div class="rounded-lg border border-default bg-elevated/20 p-5">
              <h3 class="font-semibold">
                {{ datasets.find(item => item.id === selectedDatasetId)?.name || 'Select a dataset' }}
              </h3><p class="mt-2 text-sm text-muted">
                {{ datasets.find(item => item.id === selectedDatasetId)?.description || 'Select a dataset to continue.' }}
              </p><div class="mt-5 grid grid-cols-3 gap-3 text-center">
                <div>
                  <p class="text-xs text-muted">
                    Train
                  </p><p class="text-xl font-semibold">
                    {{ datasets.find(item => item.id === selectedDatasetId)?.stats.countsBySplit.TRAIN || 0 }}
                  </p>
                </div><div>
                  <p class="text-xs text-muted">
                    Val
                  </p><p class="text-xl font-semibold">
                    {{ datasets.find(item => item.id === selectedDatasetId)?.stats.countsBySplit.VAL || 0 }}
                  </p>
                </div><div>
                  <p class="text-xs text-muted">
                    Broken
                  </p><p class="text-xl font-semibold text-error">
                    {{ datasets.find(item => item.id === selectedDatasetId)?.stats.brokenItems || 0 }}
                  </p>
                </div>
              </div>
            </div>
          </div>
        </UCard>
        <UCard v-else-if="currentStep === 1">
          <template #header>
            <div class="flex items-center justify-between">
              <div>
                <h2 class="font-semibold">
                  Select evaluation inputs
                </h2><p class="text-sm text-muted">
                  Unsupported or incomplete pairs are shown with their reason.
                </p>
              </div><div class="flex gap-2">
                <UBadge v-for="split in ['TRAIN', 'VAL', 'TEST']" :key="split" color="neutral">
                  {{ split }} {{ splitCounts[split] || 0 }}
                </UBadge>
              </div>
            </div>
          </template><div class="grid gap-5 lg:grid-cols-[16rem_1fr]">
            <div class="space-y-3">
              <div class="grid grid-cols-2 gap-2">
                <UButton :variant="selectionMode === 'ALL' ? 'solid' : 'outline'" @click="selectionMode = 'ALL'">
                  All compatible
                </UButton><UButton :variant="selectionMode === 'SELECTED' ? 'solid' : 'outline'" @click="selectionMode = 'SELECTED'">
                  Subset
                </UButton>
              </div><UInput v-model="itemSearch" icon="i-lucide-search" placeholder="Filter pages…" /><USelect v-model="splitFilter" :items="[{ label: 'All splits', value: 'ALL' }, { label: 'Train', value: 'TRAIN' }, { label: 'Validation', value: 'VAL' }, { label: 'Test', value: 'TEST' }]" /><UAlert
                v-if="!requiredSplitsPresent"
                color="warning"
                title="Required split missing"
                description="Select at least one item in every required split."
              /><div class="rounded-lg border border-default p-3 text-sm">
                <div class="flex justify-between">
                  <span class="text-muted">Selected</span><strong>{{ activeItems.length }}</strong>
                </div><div class="mt-1 flex justify-between">
                  <span class="text-muted">Compatible</span><span>{{ compatibleItems.length }}</span>
                </div>
              </div>
            </div><div v-if="loadingInputs" class="py-10 text-center text-muted">
              Loading dataset inputs…
            </div><div v-else class="max-h-[34rem] divide-y divide-default overflow-y-auto rounded-lg border border-default">
              <div v-for="item in filteredItems" :key="item.itemId" class="flex items-center gap-3 p-3">
                <UCheckbox v-if="selectionMode === 'SELECTED' && compatibleItems.includes(item)" :model-value="selectedItemIds.has(item.itemId)" @update:model-value="toggleItem(item.itemId, Boolean($event))" /><UIcon v-else :name="compatibleItems.includes(item) ? 'i-lucide-check-circle-2' : 'i-lucide-circle-slash-2'" :class="compatibleItems.includes(item) ? 'text-primary' : 'text-error'" /><UBadge size="sm" color="neutral">
                  {{ item.split }}
                </UBadge><div class="min-w-0 flex-1">
                  <p class="truncate font-medium">
                    {{ item.pageName }}
                  </p><p class="truncate text-xs text-muted">
                    {{ compatibleItems.includes(item) ? `${item.images.length} image variant(s) · XML ready` : (item.brokenReason || 'Unsupported or incomplete input') }}
                  </p>
                </div><USelect
                  v-if="compatibleItems.includes(item) && item.images.length > 1"
                  v-model="itemImageIds[item.itemId]"
                  :items="item.images.map(image => ({ label: image.variant || image.fileName, value: image.id }))"
                  class="w-44"
                />
              </div>
            </div>
          </div>
        </UCard>
        <UCard v-else-if="currentStep === 2">
          <template #header>
            <h2 class="font-semibold">
              Choose model and parameters
            </h2>
          </template><div class="space-y-5">
            <UFormField label="Evaluation Action" required>
              <USelect v-model="selectedProcessorId" :items="processorOptions" class="w-full" />
            </UFormField><UFormField label="Global image variant">
              <USelect
                v-model="globalVariant"
                :items="variantOptions"
                placeholder="Sole available image or choose variant"
                class="w-full"
              />
            </UFormField><div class="grid gap-4 md:grid-cols-2">
              <UFormField
                v-for="(definition, key) in selectedProcessor?.parameters"
                :key="key"
                :label="key"
                :hint="definition.description"
                :required="definition.required"
              >
                <USelect v-if="parameterChoices[key]?.length" v-model="parameters[key]" :items="parameterChoices[key].map(choice => ({ label: choice.label, value: choice.value }))" /><UCheckbox v-else-if="definition.type === 'boolean'" :model-value="Boolean(parameters[key])" @update:model-value="parameters[key] = Boolean($event)" /><UInput
                  v-else
                  v-model="parameters[key]"
                  :type="definition.type === 'number' || definition.type === 'integer' ? 'number' : 'text'"
                  :min="definition.min"
                  :max="definition.max"
                />
              </UFormField>
            </div>
          </div>
        </UCard>
        <UCard v-else>
          <template #header>
            <h2 class="font-semibold">
              Review evaluation
            </h2>
          </template><div class="grid gap-4 md:grid-cols-2">
            <div class="rounded-lg border border-default p-4">
              <p class="text-xs uppercase text-muted">
                Dataset
              </p><p class="mt-1 font-semibold">
                {{ datasets.find(item => item.id === selectedDatasetId)?.name || '—' }}
              </p><p class="mt-4 text-xs uppercase text-muted">
                Action / profile
              </p><p class="mt-1 font-semibold">
                {{ selectedProcessor?.name || '—' }}
              </p><p class="text-sm text-muted">
                {{ evaluation?.profile || '—' }} v{{ evaluation?.profileVersion || '—' }}
              </p>
            </div><div class="rounded-lg border border-default p-4">
              <p class="text-xs uppercase text-muted">
                Frozen inputs
              </p><p class="mt-1 text-2xl font-semibold">
                {{ activeItems.length }}
              </p><div class="mt-2 flex flex-wrap gap-2">
                <UBadge v-for="split in ['TRAIN', 'VAL', 'TEST']" :key="split" color="neutral">
                  {{ split }} {{ splitCounts[split] || 0 }}
                </UBadge>
              </div>
            </div>
          </div><UAlert
            v-if="!canStart"
            class="mt-5"
            color="warning"
            title="Evaluation cannot start yet"
            description="Resolve required splits, image/XML pairs, image variants, or parameters."
          />
        </UCard>
        <div class="flex items-center justify-between border-t border-default pt-4">
          <UButton
            v-if="currentStep > 0"
            color="neutral"
            variant="outline"
            icon="i-lucide-arrow-left"
            @click="currentStep--"
          >
            Back
          </UButton><UButton
            v-else
            to="/evaluation"
            color="neutral"
            variant="ghost"
          >
            Cancel
          </UButton><UButton
            v-if="currentStep < steps.length - 1"
            trailing-icon="i-lucide-arrow-right"
            :disabled="(currentStep === 0 && !selectedDatasetId) || (currentStep === 1 && (!activeItems.length || !requiredSplitsPresent)) || (currentStep === 2 && !canStart)"
            @click="currentStep++"
          >
            Continue
          </UButton><UButton
            v-else
            icon="i-lucide-play"
            :disabled="!canStart"
            :loading="startPending"
            @click="startEvaluation"
          >
            Start evaluation
          </UButton>
        </div>
      </div>
    </template>
  </UDashboardPanel>
</template>
