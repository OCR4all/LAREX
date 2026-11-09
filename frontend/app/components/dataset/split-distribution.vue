<script setup lang="ts">
import { DATASET_SPLIT_OPTIONS, MIN_SPLIT_PERCENTAGE, percentagesFromThumbs, splitThumbs } from '@/utils/dataset-splits'
import type { DatasetSplitPercentages } from '@/utils/dataset-splits'
import type { DatasetItemSplit } from '@/types/dataset'

const props = defineProps<{ splits: DatasetItemSplit[], disabled?: boolean }>()
const percentages = defineModel<DatasetSplitPercentages>({ required: true })

const BADGE_COLORS = { TRAIN: 'primary', VAL: 'neutral', TEST: 'warning' } as const

const options = computed(() => DATASET_SPLIT_OPTIONS.filter(option => props.splits.includes(option.value)))

const thumbs = computed<number[]>({
  get: () => splitThumbs(props.splits, percentages.value),
  set: (value) => {
    const values = Array.isArray(value) ? value.map(Number) : [Number(value)]
    percentages.value = percentagesFromThumbs(props.splits, values)
  }
})
</script>

<template>
  <div class="space-y-4 rounded-lg border border-default p-4">
    <div class="flex flex-wrap gap-2">
      <UBadge
        v-for="option in options"
        :key="option.value"
        :color="BADGE_COLORS[option.value]"
        variant="soft"
      >
        {{ option.label }} {{ percentages[option.value] }}%
      </UBadge>
    </div>
    <USlider
      v-if="splits.length > 1"
      v-model="thumbs"
      :min="MIN_SPLIT_PERCENTAGE"
      :max="100 - MIN_SPLIT_PERCENTAGE"
      :step="1"
      :min-steps-between-thumbs="MIN_SPLIT_PERCENTAGE"
      :disabled="disabled"
      tooltip
    />
  </div>
</template>
