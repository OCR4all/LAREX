import { describe, expect, it } from 'vitest'
import { actionInputLevelForTarget } from '@/utils/action-input-requirements'

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
