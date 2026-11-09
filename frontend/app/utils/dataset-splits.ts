import type { DatasetItemSplit, DatasetSplitTemplate } from '@/types/dataset'

export type DatasetSplitPercentages = Record<DatasetItemSplit, number>

export const DATASET_SPLIT_OPTIONS: Array<{ label: string, value: DatasetItemSplit, description: string }> = [
  { label: 'Train', value: 'TRAIN', description: 'Pages a model learns from' },
  { label: 'Validation', value: 'VAL', description: 'Pages that guide training' },
  { label: 'Test', value: 'TEST', description: 'Held-out pages for evaluation' }
]

export const MIN_SPLIT_PERCENTAGE = 5

const SPLIT_ORDER = DATASET_SPLIT_OPTIONS.map(option => option.value)

export function splitsOfTemplate(template: DatasetSplitTemplate): DatasetItemSplit[] {
  const names = template.split('_')
  return SPLIT_ORDER.filter(split => names.includes(split))
}

export function templateOfSplits(splits: DatasetItemSplit[]): DatasetSplitTemplate {
  return SPLIT_ORDER.filter(split => splits.includes(split)).join('_') as DatasetSplitTemplate
}

// Keep in sync with defaultPercentage in DatasetSplitService.
export function defaultSplitPercentages(splits: DatasetItemSplit[]): DatasetSplitPercentages {
  const percentages: DatasetSplitPercentages = { TRAIN: 0, VAL: 0, TEST: 0 }
  const train = !splits.includes('TRAIN') ? 0 : splits.length === 3 ? 70 : splits.length === 2 ? 80 : 100
  const others = splits.filter(split => split !== 'TRAIN')
  if (splits.includes('TRAIN')) percentages.TRAIN = train
  for (const split of others) percentages[split] = (100 - train) / others.length
  return percentages
}

/** Slider positions between neighbouring splits, as running totals. */
export function splitThumbs(splits: DatasetItemSplit[], percentages: DatasetSplitPercentages): number[] {
  let total = 0
  return splits.slice(0, -1).map((split) => {
    total += percentages[split]
    return total
  })
}

export function percentagesFromThumbs(splits: DatasetItemSplit[], thumbs: number[]): DatasetSplitPercentages {
  const percentages: DatasetSplitPercentages = { TRAIN: 0, VAL: 0, TEST: 0 }
  let previous = 0
  splits.forEach((split, index) => {
    const splitsAfter = splits.length - index - 1
    const thumb = splitsAfter === 0
      ? 100
      : Math.min(100 - splitsAfter * MIN_SPLIT_PERCENTAGE, Math.max(previous + MIN_SPLIT_PERCENTAGE, thumbs[index] ?? previous))
    percentages[split] = thumb - previous
    previous = thumb
  })
  return percentages
}
