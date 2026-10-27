import { describe, expect, it } from 'vitest'
import { actionImageSelectionValues, actionInputLevelForTarget, compatibleActionImages, actionImageVariantOptions, selectableActionImages, defaultActionImageVariants } from '@/utils/action-input-requirements'

describe('actionInputLevelForTarget', () => {
  it('resolves target-specific requirements', () => {
    const requirement = {
      level: 'OPTIONAL' as const,
      requiredForTargets: ['REGION' as const]
    }

    expect(actionInputLevelForTarget(requirement, 'PAGE')).toBe('OPTIONAL')
    expect(actionInputLevelForTarget(requirement, 'REGION')).toBe('REQUIRED')
  })

  it('supports definitions returned by older servers', () => {
    expect(actionInputLevelForTarget(undefined, 'PAGE', true)).toBe('OPTIONAL')
    expect(actionInputLevelForTarget(undefined, 'PAGE', false)).toBe('NONE')
  })
})

describe('required image selections', () => {
  it('keeps the last required selection when cleared in single or multiple mode', () => {
    expect(actionImageSelectionValues([], ['original'], true)).toEqual(['original'])
    expect(actionImageSelectionValues(undefined, ['original'], true)).toEqual(['original'])
    expect(actionImageSelectionValues('', ['original'], true)).toEqual(['original'])
    expect(actionImageSelectionValues(['scan'], ['original', 'scan'], true)).toEqual(['scan'])
  })

  it('allows clearing optional inputs and changing required variants', () => {
    expect(actionImageSelectionValues([], ['original'], false)).toEqual([])
    expect(actionImageSelectionValues('scan', ['original'], true)).toEqual(['scan'])
    expect(actionImageSelectionValues(['original', 'scan'], ['original'], true)).toEqual(['original', 'scan'])
  })
})

describe('image type requirements', () => {
  const images = [
    { id: 'c', variant: 'original', imageType: 'COLOR' as const },
    { id: 'b', variant: 'processed', imageType: 'BINARY' as const },
    { id: 'g', variant: 'processed', imageType: 'GRAYSCALE' as const },
    { id: 'u', variant: 'unknown', imageType: 'UNKNOWN' as const }
  ]
  it('filters exact types and excludes unknown metadata only for constrained actions', () => {
    expect(compatibleActionImages(images, { level: 'REQUIRED', requiredForTargets: [], imageTypes: ['BINARY'] }).map(image => image.id)).toEqual(['b'])
    expect(compatibleActionImages(images, undefined)).toEqual(images)
  })
  it('keeps pending metadata available for server validation without accepting known incompatible types', () => {
    const pending = { id: 'pending', variant: 'scan', imageType: null }
    expect(compatibleActionImages([...images, pending], { level: 'REQUIRED', requiredForTargets: [], imageTypes: ['BINARY'] })
      .map(image => image.id)).toEqual(['b', 'pending'])
    expect(actionImageVariantOptions([pending])).toEqual([{ label: 'scan · type pending', value: 'scan' }])
  })
  it('labels variants with their detected types, including mixed encodings across pages', () => {
    expect(actionImageVariantOptions(images)).toContainEqual({ value: 'processed', label: 'processed · binary, grayscale' })
  })
})

describe('image choices and defaults', () => {
  const requirement = { level: 'REQUIRED' as const, requiredForTargets: [], imageTypes: ['BINARY' as const] }
  const binary = { id: 'binary', variant: 'processed', imageType: 'BINARY' as const }
  const pending = { id: 'pending', variant: 'scan', imageType: null }
  const color = { id: 'color', variant: 'original', imageType: 'COLOR' as const }

  it('offers only confirmed matches for required types while allowing pending optional choices', () => {
    expect(selectableActionImages([pending, color, binary], requirement, true)).toEqual([binary])
    expect(selectableActionImages([pending, color, binary], requirement, false)).toEqual([pending, binary])
    expect(selectableActionImages([pending, color, binary], undefined, true)).toEqual([pending, color, binary])
  })

  it('chooses a common variant or leaves differing names to per-page selection', () => {
    expect(defaultActionImageVariants([[binary], [binary, { ...binary, variant: 'other' }]], false)).toEqual(['processed'])
    expect(defaultActionImageVariants([[binary], [{ ...binary, variant: 'other' }]], false)).toEqual([])
    expect(defaultActionImageVariants([[binary], [{ ...binary, variant: 'other' }]], true)).toEqual(['other', 'processed'])
    expect(defaultActionImageVariants([[binary], []], false)).toEqual([])
    // A shared variant with several files must use per-item defaults in single-image mode.
    expect(defaultActionImageVariants([[binary, { ...binary, id: 'duplicate' }]], false)).toEqual([])
  })

  it('orders confirmed choices by variant, filename, and ID without mutating inputs', () => {
    const images = [{ ...binary, id: 'z', fileName: 'b.png' }, { ...binary, id: 'b', fileName: 'a.png' }, { ...binary, id: 'a', fileName: 'a.png' }]
    expect(selectableActionImages(images, requirement, true).map(image => image.id)).toEqual(['a', 'b', 'z'])
    expect(images.map(image => image.id)).toEqual(['z', 'b', 'a'])
  })
})
