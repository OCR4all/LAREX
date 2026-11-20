<script setup lang="ts">
import type { ActionParameterChoice, ActionParameterDefinition, ActionParameterValue } from '@/types/action'

const props = defineProps<{
  name: string
  definition: ActionParameterDefinition
  discoveredValues: Record<string, ActionParameterChoice[]>
}>()

const value = defineModel<ActionParameterValue | undefined>()
const choices = computed(() => actionParameterChoices(props.definition, props.discoveredValues))
const inputValue = computed<string | number | undefined>(() =>
  typeof value.value === 'boolean' || Array.isArray(value.value) ? String(value.value) : value.value
)
</script>

<template>
  <UFormField
    :label="actionParameterLabel(name)"
    :description="definition.description"
    :required="definition.required"
  >
    <USelectMenu
      v-if="choices.length || definition.multiple"
      v-model="value"
      :items="choices"
      value-key="value"
      :multiple="Boolean(definition.multiple)"
      class="w-full"
    />
    <UCheckbox
      v-else-if="definition.type === 'boolean'"
      :model-value="Boolean(value)"
      @update:model-value="value = Boolean($event)"
    />
    <ActionParameterInput
      v-else
      :model-value="inputValue"
      :definition="definition"
      class="w-full"
      @update:model-value="value = coerceActionParameterInput(definition, $event)"
    />
  </UFormField>
</template>
