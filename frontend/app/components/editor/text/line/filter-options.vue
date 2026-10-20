<script setup lang="ts">
const model = defineModel<string>({ required: true })

defineProps<{
  label: string
  icon: string
  items: Array<{ label: string, value: string }>
  help?: string
}>()
</script>

<template>
  <div class="space-y-1.5">
    <div class="flex items-center gap-2 text-sm font-medium text-highlighted">
      <UIcon :name="icon" class="size-4 text-muted" />
      {{ label }}
    </div>
    <UTabs
      v-model="model"
      :items="items"
      :aria-label="label"
      :content="false"
      size="sm"
    />
    <p v-if="help" class="text-xs text-muted">
      {{ help }}
    </p>
  </div>
</template>
