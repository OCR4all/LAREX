import { describe, expect, it } from 'vitest'
import { actionImageSelectionValues, actionInputLevelForTarget } from '@/utils/action-input-requirements'

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
