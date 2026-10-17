<script setup lang="ts">
import { isTerminalActionRun, actionRunStatusLabel, actionRunStatusColor } from '@/utils/action-run-status'
import type { ActionDefinition, ActionParameterChoice, ActionParameterDefinition, ActionParameterValue, ActionRun, ActionRunDetail, IntegrationInputItem, IntegrationInputResponse, StartActionRunResponse } from '@/types/action'
import { actionParameterChoices, actionParameterDefaultValue, coerceActionParameterInput, hasAllowedActionParameterValue } from '@/utils/action-parameter-values'
import { actionImageSelectionValues, actionInputLevelForTarget } from '@/utils/action-input-requirements'
import { extractApiErrorDetails } from '@/utils/api-error'

const props = defineProps<{ workspaceId: string, datasetId: string, datasetName: string, itemIds?: string[] }>()
const emit = defineEmits<{ close: [boolean] }>()
const store = useActionRunsStore()
const processors = ref<ActionDefinition[]>([])
const inputs = ref<IntegrationInputItem[]>([])
const selectedProcessorId = ref('')
const selectionMode = ref<'ALL' | 'SELECTED'>(props.itemIds?.length ? 'SELECTED' : 'ALL')
const parameters = ref<Record<string, ActionParameterValue>>({})
const discovered = ref<Record<string, ActionParameterChoice[]>>({})
const globalVariants = ref<string[]>([])
const itemImageIdLists = ref<Record<string, string[]>>({})
const loading = ref(true)
const starting = ref(false)
const discovering = ref(false)
const changed = ref(false)
const error = ref<string | null>(null)
const discoveryError = ref<string | null>(null)
const enqueueIfBusy = ref(true)
const details = ref<Record<string, ActionRunDetail>>({})
const expandedRunId = ref<string | null>(null)
const history = ref<ActionRun[]>([])
const baseUrl = computed(() => `/api/workspaces/${props.workspaceId}/actions/integration`)
const processor = computed(() => processors.value.find(item => item.id === selectedProcessorId.value))
const multipleImages = computed(() => processor.value?.inputs.images.multiple === true)
watch(multipleImages, (multiple) => {
  if (multiple) return
  globalVariants.value = globalVariants.value.slice(0, 1)
  for (const key of Object.keys(itemImageIdLists.value)) {
    itemImageIdLists.value[key] = itemImageIdLists.value[key]?.slice(0, 1) ?? []
  }
}, { immediate: true })

const processorOptions = computed(() => processors.value.map(item => ({ label: item.name, value: item.id })))
const selectedIds = computed(() => new Set(props.itemIds ?? []))
const acceptsImages = computed(() => processor.value && actionInputLevelForTarget(processor.value.inputs.images, 'PAGE') !== 'NONE')
const requiredImages = computed(() => processor.value && actionInputLevelForTarget(processor.value.inputs.images, 'PAGE') === 'REQUIRED')
const acceptsXml = computed(() => processor.value && actionInputLevelForTarget(processor.value.inputs.xml, 'PAGE') !== 'NONE')
const requiredXml = computed(() => processor.value && actionInputLevelForTarget(processor.value.inputs.xml, 'PAGE') === 'REQUIRED')
const scopedItems = computed(() => inputs.value.filter(item => selectionMode.value === 'ALL' || selectedIds.value.has(item.itemId)))
const compatibleItems = computed(() => scopedItems.value.filter(item => item.status === 'READY'
  && (!requiredImages.value || item.images.length > 0) && (!requiredXml.value || item.xmlAvailable)
  && ((acceptsImages.value && item.images.length > 0) || (acceptsXml.value && item.xmlAvailable))))
const skippedCount = computed(() => scopedItems.value.length - compatibleItems.value.length)
const variantOptions = computed(() => [...new Set(compatibleItems.value.flatMap(item => item.images.flatMap(image => image.variant ? [image.variant] : [])))].sort())
const runs = computed(() => history.value.map(run => store.runsById.get(run.id) ?? run))

function resolvedImages(item: IntegrationInputItem) {
  const override = itemImageIdLists.value[item.itemId]
  if (override?.length) return item.images.filter(image => override.includes(image.id))
  if (globalVariants.value.length) return item.images.filter(image => globalVariants.value.includes(image.variant ?? ''))
  return item.images.length === 1 ? item.images : []
}

const canStart = computed(() => Boolean(processor.value) && !loading.value && !starting.value && !discovering.value && !discoveryError.value
  && compatibleItems.value.length > 0 && (selectionMode.value === 'ALL' || skippedCount.value === 0)
  && (!acceptsImages.value || compatibleItems.value.every(item => !item.images.length || resolvedImages(item).length > 0))
  && Object.entries(processor.value?.parameters ?? {}).every(([key, definition]) => {
    const value = parameters.value[key]
    return (!definition.required || (value !== undefined && value !== '')) && hasAllowedActionParameterValue(definition, value, discovered.value)
  }))

function choices(definition: ActionParameterDefinition) {
  return actionParameterChoices(definition, discovered.value).map((choice, index) => ({ label: choice.label, value: String(index) }))
}
function selectedChoice(key: string, definition: ActionParameterDefinition) {
  const index = actionParameterChoices(definition, discovered.value).findIndex(choice => choice.value === parameters.value[key])
  return index < 0 ? undefined : String(index)
}
function chooseParameter(key: string, definition: ActionParameterDefinition, index: string) {
  const choice = actionParameterChoices(definition, discovered.value)[Number(index)]
  if (choice) parameters.value[key] = choice.value
}

let discoverySequence = 0
watch(processor, async (value) => {
  const sequence = ++discoverySequence
  parameters.value = Object.fromEntries(Object.entries(value?.parameters ?? {}).map(([key, definition]) => [key, actionParameterDefaultValue(definition)]))
  discovered.value = {}
  discoveryError.value = null
  discovering.value = false
  if (!value || !Object.values(value.parameters).some(definition => definition.allowedValues?.provider)) return
  discovering.value = true
  try {
    const response = await $fetch<{ values: Record<string, ActionParameterChoice[]> }>(`${baseUrl.value}/processors/${value.id}/parameter-values`)
    if (sequence === discoverySequence) discovered.value = response.values
  } catch (cause) {
    if (sequence === discoverySequence) discoveryError.value = extractApiErrorDetails(cause, 'Could not load parameter choices').message
  } finally {
    if (sequence === discoverySequence) discovering.value = false
  }
})

async function refreshHistory() {
  history.value = await $fetch<ActionRun[]>(`${baseUrl.value}/datasets/${props.datasetId}/runs`)
  store.upsertRuns(history.value, props.datasetName)
}

async function load() {
  loading.value = true
  error.value = null
  try {
    const [definitions, response] = await Promise.all([
      $fetch<ActionDefinition[]>(`${baseUrl.value}/processors`),
      $fetch<IntegrationInputResponse>(`${baseUrl.value}/datasets/${props.datasetId}/inputs`)
    ])
    processors.value = definitions
    inputs.value = response.items
    selectedProcessorId.value = definitions[0]?.id ?? ''
    await refreshHistory()
  } catch (cause) {
    error.value = extractApiErrorDetails(cause, 'Could not load Integration Actions').message
  } finally {
    loading.value = false
  }
}

async function start() {
  if (!canStart.value || !processor.value) return
  starting.value = true
  error.value = null
  try {
    const result = await $fetch<StartActionRunResponse>(`${baseUrl.value}/datasets/${props.datasetId}/runs`, {
      method: 'POST',
      body: {
        processorDefinitionId: processor.value.id,
        selection: { mode: selectionMode.value, itemIds: selectionMode.value === 'SELECTED' ? props.itemIds : [] },
        imageSelection: acceptsImages.value ? { globalVariants: globalVariants.value, itemImageIdLists: itemImageIdLists.value } : null,
        parameters: parameters.value,
        enqueueIfBusy: enqueueIfBusy.value
      }
    })
    store.upsertRun(result.run, props.datasetName)
    history.value.unshift(result.run)
    changed.value = true
  } catch (cause) {
    error.value = extractApiErrorDetails(cause, 'Could not start Integration Action').message
  } finally {
    starting.value = false
  }
}

async function cancel(run: ActionRun) {
  error.value = null
  try {
    const tracked = store.runsById.get(run.id)
    if (tracked) await store.cancelRun(tracked)
  } catch (cause) {
    error.value = extractApiErrorDetails(cause, 'Could not cancel Action').message
  }
}
async function toggleDetails(run: ActionRun) {
  if (expandedRunId.value === run.id) {
    expandedRunId.value = null
    return
  }
  error.value = null
  try {
    details.value[run.id] = await $fetch<ActionRunDetail>(`/api/workspaces/${props.workspaceId}/actions/runs/${run.id}`)
    expandedRunId.value = run.id
  } catch (cause) {
    error.value = extractApiErrorDetails(cause, 'Could not load run details').message
  }
}

onMounted(load)
</script>

<template>
  <UiResponsiveSlideover
    header-accent
    :title="`Run Action · ${datasetName}`"
    description="Integration Actions for dataset inputs"
    :close="{ onClick: () => emit('close', changed) }"
    :dismissible="!starting"
    :ui="{ content: 'max-w-2xl' }"
  >
    <template #header>
      <UiSlideoverHeader
        :title="`Run Action · ${datasetName}`"
        icon="i-lucide-play"
        description="Integration Actions for dataset inputs."
      />
    </template>
    <template #body>
      <div class="space-y-6">
        <UAlert
          icon="i-lucide-plug"
          color="info"
          title="External work with status feedback"
          description="Selected saved files are frozen before starting or queueing. Editing remains available. All dataset splits are accepted."
        />
        <UAlert
          v-if="error"
          color="error"
          title="Action unavailable"
          :description="error"
        />
        <p v-if="loading" role="status">
          Loading Actions and inputs…
        </p>
        <p v-else-if="!processors.length" class="text-muted">
          No Integration Actions supporting pages are enabled for this workspace.
        </p>
        <template v-else>
          <UFormField label="Action">
            <USelect
              v-model="selectedProcessorId"
              :items="processorOptions"
              class="w-full"
              :disabled="starting"
            />
          </UFormField>
          <p v-if="processor?.description" class="text-sm text-muted">
            {{ processor.description }}
          </p>
          <UAlert
            v-if="processor?.info?.trim()"
            color="info"
            variant="subtle"
            icon="i-lucide-info"
            :description="processor?.info ?? undefined"
            :ui="{ description: 'whitespace-pre-wrap' }"
          />
          <UFormField label="Inputs">
            <URadioGroup v-model="selectionMode" :items="[{ label: 'All compatible items', value: 'ALL' }, { label: `Selected items (${itemIds?.length ?? 0})`, value: 'SELECTED', disabled: !itemIds?.length }]" :disabled="starting" />
          </UFormField>
          <p role="status" class="text-sm text-muted">
            {{ compatibleItems.length }} compatible inputs · {{ skippedCount }} unavailable inputs
          </p>
          <UAlert
            v-if="selectionMode === 'SELECTED' && skippedCount"
            color="warning"
            title="Some selected items are incompatible"
            description="Adjust the dataset selection or choose an Action matching the available files."
          />
          <template v-if="acceptsImages">
            <UFormField label="Image variants">
              <div class="flex gap-2">
                <USelect
                  :model-value="multipleImages ? globalVariants : globalVariants[0]"
                  :multiple="multipleImages"
                  :items="variantOptions"
                  placeholder="Choose per item below"
                  class="w-full"
                  :disabled="starting"
                  @update:model-value="globalVariants = actionImageSelectionValues($event, globalVariants, requiredImages)"
                />
                <UButton
                  v-if="globalVariants.length && !requiredImages"
                  color="neutral"
                  variant="ghost"
                  @click="globalVariants = []"
                >
                  Clear
                </UButton>
              </div>
            </UFormField>
            <div class="max-h-60 space-y-3 overflow-y-auto">
              <UFormField
                v-for="item in compatibleItems.filter(item => item.images.length > 0)"
                :key="item.itemId"
                :label="item.pageName"
                :error="!resolvedImages(item).length ? 'Choose images for this item' : undefined"
              >
                <USelect
                  :model-value="multipleImages ? itemImageIdLists[item.itemId] : itemImageIdLists[item.itemId]?.[0]"
                  :multiple="multipleImages"
                  :items="item.images.map(image => ({ label: `${image.variant || 'Image'} · ${image.fileName}`, value: image.id }))"
                  :placeholder="resolvedImages(item).map(image => image.fileName).join(', ') || 'Choose images'"
                  class="w-full"
                  :disabled="starting"
                  @update:model-value="itemImageIdLists[item.itemId] = actionImageSelectionValues($event, itemImageIdLists[item.itemId], requiredImages)"
                />
              </UFormField>
            </div>
          </template>
          <UAlert
            v-if="discoveryError"
            color="error"
            title="Parameter discovery failed"
            :description="discoveryError"
          />
          <UFormField
            v-for="(definition, key) in processor?.parameters"
            :key="key"
            :label="key"
            :description="definition.description"
            :required="definition.required"
          >
            <USelect
              v-if="definition.allowedValues"
              :model-value="selectedChoice(key, definition)"
              :items="choices(definition)"
              :loading="discovering"
              :disabled="starting || discovering"
              class="w-full"
              @update:model-value="chooseParameter(key, definition, $event)"
            />
            <USwitch
              v-else-if="definition.type === 'boolean'"
              :model-value="parameters[key] === true"
              :disabled="starting"
              @update:model-value="parameters[key] = $event"
            />
            <UInput
              v-else
              :model-value="String(parameters[key] ?? '')"
              :type="definition.type === 'number' || definition.type === 'integer' ? 'number' : 'text'"
              :min="definition.min"
              :max="definition.max"
              :step="definition.type === 'integer' ? 1 : 'any'"
              :disabled="starting"
              class="w-full"
              @update:model-value="parameters[key] = coerceActionParameterInput(definition, $event)"
            />
          </UFormField>
          <UCheckbox v-model="enqueueIfBusy" label="Queue when the processor is busy" :disabled="starting" />
        </template>
        <div class="space-y-3 border-t border-default pt-4">
          <div class="flex items-center justify-between">
            <h3 class="font-medium">
              Run history
            </h3>
            <UButton
              color="neutral"
              variant="ghost"
              icon="i-lucide-refresh-cw"
              aria-label="Refresh history"
              @click="load"
            >
              Refresh
            </UButton>
          </div>
          <p v-if="!runs.length" class="text-sm text-muted">
            No Integration runs yet.
          </p>
          <div v-for="run in runs" :key="run.id" class="space-y-2 rounded border border-default p-3">
            <div class="flex items-center justify-between gap-2">
              <span class="flex items-center gap-1 font-medium"><UIcon name="i-lucide-plug" aria-label="Integration" />{{ run.processorName }}</span>
              <UBadge :color="actionRunStatusColor(run.status)">
                {{ actionRunStatusLabel(run.status) }} · {{ run.progressPercent }}%
              </UBadge>
            </div>
            <p class="text-sm">
              {{ run.statusMessage || run.errorMessage }}
            </p>
            <p class="text-xs text-muted">
              {{ run.inputCount }} inputs · {{ new Date(run.created).toLocaleString() }}
            </p>
            <div class="flex gap-2">
              <UButton
                size="xs"
                color="neutral"
                variant="ghost"
                @click="toggleDetails(run)"
              >
                Details
              </UButton>
              <UButton
                v-if="run.canCancel && !isTerminalActionRun(run.status)"
                size="xs"
                color="error"
                variant="ghost"
                :loading="store.isCancelling(run.id)"
                @click="cancel(run)"
              >
                {{ run.status === 'CANCEL_REQUESTED' ? 'Force cancel' : 'Cancel' }}
              </UButton>
            </div>
            <ActionRunReport v-if="expandedRunId === run.id && details[run.id]?.runReport" :report="details[run.id]!.runReport!" :run-id="run.id" />
            <pre v-if="expandedRunId === run.id" class="max-h-48 overflow-auto whitespace-pre-wrap text-xs">{{ details[run.id]?.logText || 'No log messages.' }}</pre>
          </div>
        </div>
      </div>
    </template>
    <template #footer>
      <div class="flex w-full justify-end gap-2">
        <UButton
          color="neutral"
          variant="ghost"
          :disabled="starting"
          @click="emit('close', changed)"
        >
          Close
        </UButton>
        <UButton
          icon="i-lucide-plug"
          :disabled="!canStart"
          :loading="starting"
          @click="start"
        >
          Start Action
        </UButton>
      </div>
    </template>
  </UiResponsiveSlideover>
</template>
