<script setup lang="ts">
defineProps<{
  label: string
  description?: string
  disabled?: boolean
}>()

const model = defineModel<boolean>({ required: true })
const id = useId()
</script>

<template>
  <div class="flex items-start justify-between gap-4">
    <label :for="id" class="min-w-0" :class="disabled ? 'cursor-default' : 'cursor-pointer'">
      <span class="block text-sm font-medium text-highlighted">{{ label }}</span>
      <span v-if="description" class="block text-xs text-muted text-pretty">{{ description }}</span>
    </label>
    <USwitch
      :id="id"
      v-model="model"
      :disabled="disabled"
      class="shrink-0 mt-0.5"
    />
  </div>
</template>
