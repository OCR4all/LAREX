import { describe, expect, it } from 'vitest'
import {
  defaultSplitPercentages,
  percentagesFromThumbs,
  splitsOfTemplate,
  splitThumbs,
  templateOfSplits
} from './dataset-splits'

describe('dataset splits', () => {
  it('converts between splits and templates in a fixed order', () => {
    expect(splitsOfTemplate('TEST')).toEqual(['TEST'])
    expect(splitsOfTemplate('TRAIN_TEST')).toEqual(['TRAIN', 'TEST'])
    expect(splitsOfTemplate('TRAIN_VAL_TEST')).toEqual(['TRAIN', 'VAL', 'TEST'])
    expect(templateOfSplits(['TEST', 'TRAIN'])).toBe('TRAIN_TEST')
    expect(templateOfSplits(['VAL'])).toBe('VAL')
  })

  it('defaults to percentages that fill the dataset', () => {
    expect(defaultSplitPercentages(['TRAIN', 'VAL', 'TEST'])).toEqual({ TRAIN: 70, VAL: 15, TEST: 15 })
    expect(defaultSplitPercentages(['TRAIN', 'VAL'])).toEqual({ TRAIN: 80, VAL: 20, TEST: 0 })
    expect(defaultSplitPercentages(['TRAIN', 'TEST'])).toEqual({ TRAIN: 80, VAL: 0, TEST: 20 })
    expect(defaultSplitPercentages(['VAL', 'TEST'])).toEqual({ TRAIN: 0, VAL: 50, TEST: 50 })
    expect(defaultSplitPercentages(['TRAIN'])).toEqual({ TRAIN: 100, VAL: 0, TEST: 0 })
    expect(defaultSplitPercentages(['TEST'])).toEqual({ TRAIN: 0, VAL: 0, TEST: 100 })
  })

  it('places one thumb between each pair of splits', () => {
    expect(splitThumbs(['TRAIN', 'VAL', 'TEST'], { TRAIN: 70, VAL: 15, TEST: 15 })).toEqual([70, 85])
    expect(splitThumbs(['VAL', 'TEST'], { TRAIN: 0, VAL: 40, TEST: 60 })).toEqual([40])
    expect(splitThumbs(['TEST'], { TRAIN: 0, VAL: 0, TEST: 100 })).toEqual([])
  })

  it('turns thumbs into percentages that add up to 100', () => {
    expect(percentagesFromThumbs(['TRAIN', 'VAL', 'TEST'], [60, 80])).toEqual({ TRAIN: 60, VAL: 20, TEST: 20 })
    expect(percentagesFromThumbs(['TRAIN', 'TEST'], [30])).toEqual({ TRAIN: 30, VAL: 0, TEST: 70 })
    expect(percentagesFromThumbs(['TEST'], [])).toEqual({ TRAIN: 0, VAL: 0, TEST: 100 })
  })

  it('keeps every split at the minimum size', () => {
    expect(percentagesFromThumbs(['TRAIN', 'VAL', 'TEST'], [99, 99])).toEqual({ TRAIN: 90, VAL: 5, TEST: 5 })
    expect(percentagesFromThumbs(['TRAIN', 'VAL', 'TEST'], [1, 2])).toEqual({ TRAIN: 5, VAL: 5, TEST: 90 })
  })
})
