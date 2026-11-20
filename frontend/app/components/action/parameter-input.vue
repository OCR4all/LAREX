<script setup lang="ts">
import type { ActionParameterDefinition } from '@/types/action'

const props = defineProps<{
  definition: ActionParameterDefinition
}>()

const value = defineModel<string | number | undefined>()
const revealed = ref(false)
const numeric = computed(() => props.definition.type === 'integer' || props.definition.type === 'number')
const secret = computed(() => Boolean(props.definition.secret) && !numeric.value)
const multiline = computed(() => Boolean(props.definition.multiline) && !numeric.value && !secret.value)
const textValue = computed({
  get: () => (value.value === undefined ? undefined : String(value.value)),
  set: (next: string | undefined) => { value.value = next }
})
const type = computed(() => {
  if (numeric.value) return 'number'
  return secret.value && !revealed.value ? 'password' : 'text'
})
</script>

<template>
  <UTextarea
    v-if="multiline"
    v-model="textValue"
    :rows="4"
    :maxrows="16"
    autoresize
    class="w-full"
  />
  <UInput
    v-else
    v-model="value"
    :type="type"
    :min="definition.min"
    :max="definition.max"
    :step="numeric ? (definition.type === 'integer' ? 1 : 'any') : undefined"
    :autocomplete="secret ? 'new-password' : undefined"
    :ui="secret ? { trailing: 'pe-1' } : undefined"
  >
    <template v-if="secret" #trailing>
      <UButton
        color="neutral"
        variant="link"
        size="sm"
        :icon="revealed ? 'i-lucide-eye-off' : 'i-lucide-eye'"
        :aria-label="revealed ? 'Hide value' : 'Show value'"
        :aria-pressed="revealed"
        @click="revealed = !revealed"
      />
    </template>
  </UInput>
</template>
