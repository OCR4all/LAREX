<script setup lang="ts">
const props = withDefaults(defineProps<{
  placeholder?: string
  multiline?: boolean
  rows?: number
  disabled?: boolean
}>(), {
  placeholder: undefined,
  multiline: false,
  rows: 2,
  disabled: false
})

const model = defineModel<string | undefined>()

const isSet = computed(() => model.value !== undefined)

const shownPlaceholder = computed(() => {
  if (model.value === '') return 'Empty value'
  return props.placeholder ? `${props.placeholder} (not set)` : 'Not set'
})

function update(value: string | number | null | undefined) {
  model.value = value === null || value === undefined ? '' : String(value)
}
</script>

<template>
  <div class="flex items-start gap-1">
    <UTextarea
      v-if="multiline"
      class="flex-1"
      :model-value="model ?? ''"
      :placeholder="shownPlaceholder"
      :rows="rows"
      :disabled="disabled"
      :data-state="isSet ? (model === '' ? 'empty' : 'set') : 'unset'"
      @update:model-value="update"
    />
    <UInput
      v-else
      class="flex-1"
      :model-value="model ?? ''"
      :placeholder="shownPlaceholder"
      :disabled="disabled"
      :data-state="isSet ? (model === '' ? 'empty' : 'set') : 'unset'"
      @update:model-value="update"
    />
    <UButton
      v-if="isSet && !disabled"
      type="button"
      size="sm"
      color="neutral"
      variant="ghost"
      icon="i-lucide-x"
      aria-label="Remove attribute"
      title="Remove attribute"
      @click="model = undefined"
    />
  </div>
</template>
