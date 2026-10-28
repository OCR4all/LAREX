<script setup lang="ts">
import type { ActionDefinition, ActionParameterValuesResponse, ActionParameterChoice, ActionParameterValue } from '~/types/action'
import type { KnownImageType } from '~/types/image'
import type { ActionWorkflowStep } from '~/types/action-workflow'
import { actionParameterChoices, actionParameterDefaultValue, coerceActionParameterInput } from '~/utils/action-parameter-values'
import { extractApiErrorDetails } from '~/utils/api-error'

const props = defineProps<{
  processor: ActionDefinition
  step: ActionWorkflowStep
  workspaceId?: string
  workflowId?: string
  projectId?: string
  disabled?: boolean
  compact?: boolean
}>()
const imageTypeOptions = computed(() => (props.processor.inputs.images.imageTypes?.length
  ? props.processor.inputs.images.imageTypes
  : ['COLOR', 'GRAYSCALE', 'BINARY'] as KnownImageType[]).map(value => ({ label: value.toLowerCase(), value })))
const effectiveImageTypes = computed(() => props.step.imageTypes?.length ? props.step.imageTypes : props.processor.inputs.images.imageTypes)
const automaticImagePlaceholder = computed(() => effectiveImageTypes.value?.length
  ? `Automatic: ${effectiveImageTypes.value.join(', ').toLowerCase()} images`
  : 'Automatic')
function updateImageTypes(value: KnownImageType[]) {
  emit('update', { ...props.step, imageTypes: value.length ? value : null })
}
const emit = defineEmits<{ update: [step: ActionWorkflowStep] }>()
const id = useId()
const discovered = ref<Record<string, ActionParameterChoice[]>>({})
const loading = ref(false)
const error = ref('')
let generation = 0
const dynamic = computed(() => Object.values(props.processor.parameters).some(parameter => parameter.allowedValues?.provider))
async function discover() {
  const request = ++generation
  discovered.value = {}
  error.value = ''
  loading.value = false
  if (!props.projectId || !dynamic.value) return
  loading.value = true
  try {
    const processorPath = props.workflowId ? `workflows/${props.workflowId}/processors` : 'processors'
    const result = await $fetch<ActionParameterValuesResponse>(`/api/workspaces/${props.workspaceId}/actions/projects/${props.projectId}/${processorPath}/${props.processor.id}/parameter-values`)
    if (request === generation) discovered.value = result.values
  } catch (cause) {
    if (request === generation) error.value = extractApiErrorDetails(cause, 'Could not load allowed parameter values.').message
  } finally {
    if (request === generation) loading.value = false
  }
}
watch(() => [props.workspaceId, props.projectId, props.processor.id, props.workflowId], discover, { immediate: true })
onBeforeUnmount(() => {
  generation++
})
function value(key: string) {
  return props.step.parameters[key] ?? actionParameterDefaultValue(props.processor.parameters[key]!)
}
function inputValue(key: string): string {
  const current = value(key)
  return typeof current === 'boolean' ? '' : String(current)
}
function update(key: string, next: ActionParameterValue | undefined) {
  emit('update', { ...props.step, parameters: { ...props.step.parameters, [key]: next } })
}
function updateImages(value: string | number) {
  const variants = String(value).split(',').map(variant => variant.trim()).filter(Boolean)
  emit('update', { ...props.step, imageVariantSelection: variants.length
    ? { mode: 'GLOBAL', variants, fallbackImage: false }
    : null })
}
</script>

<template>
  <div class="space-y-5">
    <p v-if="processor.info && !compact" class="whitespace-pre-line text-sm text-toned">
      {{ processor.info }}
    </p>
    <section class="space-y-4" role="group" :aria-labelledby="`${id}-parameters`">
      <h4 :id="`${id}-parameters`" class="flex items-center gap-1.5 text-xs font-semibold tracking-wide text-muted uppercase">
        <UIcon name="i-lucide-sliders-horizontal" class="size-3.5" />
        Parameters
      </h4>
      <UAlert
        v-if="error"
        title="Allowed values unavailable"
        :description="error"
        color="error"
      >
        <template #actions>
          <UButton
            label="Retry"
            variant="outline"
            color="error"
            @click="discover"
          />
        </template>
      </UAlert>
      <UFormField
        v-for="(definition, key) in processor.parameters"
        :key="key"
        :label="String(key)"
        :description="definition.description"
        :required="definition.required"
      >
        <USelectMenu
          v-if="definition.allowedValues && (definition.allowedValues.values || projectId)"
          :model-value="value(String(key))"
          :items="actionParameterChoices(definition, discovered)"
          value-key="value"
          :loading="loading"
          :disabled="disabled || loading"
          class="w-full"
          placeholder="Select an allowed value"
          @update:model-value="update(String(key), $event)"
        />
        <USwitch
          v-else-if="definition.type === 'boolean'"
          :model-value="Boolean(value(String(key)))"
          :disabled="disabled"
          @update:model-value="update(String(key), $event)"
        />
        <UInput
          v-else
          :model-value="inputValue(String(key))"
          :type="definition.type === 'number' || definition.type === 'integer' ? 'number' : 'text'"
          :step="definition.type === 'integer' ? '1' : 'any'"
          :min="definition.min"
          :max="definition.max"
          :disabled="disabled"
          class="w-full"
          @update:model-value="update(String(key), coerceActionParameterInput(definition, $event))"
        />
      </UFormField>
      <p v-if="!Object.keys(processor.parameters).length" class="text-sm text-muted">
        This processor has no parameters.
      </p>
    </section>
    <section
      v-if="processor.acceptsImages"
      class="space-y-4 border-t border-default pt-4"
      role="group"
      :aria-labelledby="`${id}-images`"
    >
      <div class="space-y-1">
        <h4 :id="`${id}-images`" class="flex items-center gap-1.5 text-xs font-semibold tracking-wide text-muted uppercase">
          <UIcon name="i-lucide-image" class="size-3.5" />
          Image inputs
        </h4>
        <p class="text-xs text-muted">
          Which images this step reads. Leave empty to inherit the processor’s defaults.
        </p>
      </div>
      <UFormField label="Required image types" description="Choosing types requires a matching image on every page.">
        <USelectMenu
          :model-value="step.imageTypes ?? []"
          :items="imageTypeOptions"
          value-key="value"
          multiple
          :placeholder="processor.inputs.images.imageTypes?.length ? `Inherit: ${processor.inputs.images.imageTypes.join(', ').toLowerCase()}` : 'Inherit processor requirements'"
          :disabled="disabled"
          class="w-full"
          @update:model-value="updateImageTypes($event)"
        />
      </UFormField>
      <UFormField label="Image variants" :description="compact ? 'Automatic, or variants separated by commas.' : 'Separate multiple variants with commas. A later step can use a variant produced by an earlier step.'">
        <UInput
          :model-value="step.imageVariantSelection?.variants?.join(', ') ?? step.imageVariantSelection?.variant ?? ''"
          :placeholder="automaticImagePlaceholder"
          :disabled="disabled"
          class="w-full"
          @update:model-value="updateImages"
        />
      </UFormField>
    </section>
  </div>
</template>
