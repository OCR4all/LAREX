<script setup lang="ts">
import { DATASET_SPLIT_OPTIONS } from '@/utils/dataset-splits'
import type { DatasetItemSplit } from '@/types/dataset'

defineProps<{ disabled?: boolean }>()
const splits = defineModel<DatasetItemSplit[]>({ required: true })

const items = computed(() => DATASET_SPLIT_OPTIONS.map(option => ({
  ...option,
  disabled: splits.value.length === 1 && splits.value[0] === option.value
})))
</script>

<template>
  <USelectMenu
    v-model="splits"
    :items="items"
    :search-input="false"
    :disabled="disabled"
    :ui="{ itemTrailingIcon: 'hidden' }"
    multiple
    value-key="value"
    class="w-full"
  >
    <template #item-leading="{ item }">
      <UCheckbox
        :model-value="splits.includes(item.value)"
        tabindex="-1"
        aria-hidden="true"
        class="pointer-events-none"
      />
    </template>
  </USelectMenu>
</template>
