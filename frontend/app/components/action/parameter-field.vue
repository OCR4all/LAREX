<script setup lang="ts">
import type { ActionParameterChoice, ActionParameterDefinition, ActionParameterValue } from '@/types/action'

const props = defineProps<{
  name: string
  definition: ActionParameterDefinition
  discoveredValues: Record<string, ActionParameterChoice[]>
}>()

const value = defineModel<ActionParameterValue | undefined>()
const choices = computed(() => actionParameterChoices(props.definition, props.discoveredValues))
const numeric = computed(() => props.definition.type === 'integer' || props.definition.type === 'number')
</script>

<template>
  <UFormField
    :label="actionParameterLabel(name)"
    :description="definition.description"
    :required="definition.required"
  >
    <USelectMenu
      v-if="choices.length"
      v-model="value"
      :items="choices"
      value-key="value"
      class="w-full"
    />
    <UCheckbox
      v-else-if="definition.type === 'boolean'"
      :model-value="Boolean(value)"
      @update:model-value="value = Boolean($event)"
    />
    <UInput
      v-else
      :model-value="typeof value === 'boolean' ? String(value) : value"
      :type="numeric ? 'number' : 'text'"
      :min="definition.min"
      :max="definition.max"
      class="w-full"
      @update:model-value="value = coerceActionParameterInput(definition, $event)"
    />
  </UFormField>
</template>
