import type { DatasetItemSplit, DatasetSplitTemplate } from '@/types/dataset'

export function useDatasetSplitLayout() {
  const template = ref<DatasetSplitTemplate>('TRAIN_VAL_TEST')
  const percentages = ref(defaultSplitPercentages(splitsOfTemplate(template.value)))

  const splits = computed<DatasetItemSplit[]>({
    get: () => splitsOfTemplate(template.value),
    set: (next) => {
      if (!next.length) return
      template.value = templateOfSplits(next)
      percentages.value = defaultSplitPercentages(splitsOfTemplate(template.value))
    }
  })

  return { template, splits, percentages }
}
