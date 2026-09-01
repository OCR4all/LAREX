import { describe, expect, it } from 'vitest'
import {
  actionParameterChoices,
  coerceActionParameterInput,
  hasAllowedActionParameterValue
} from './action-parameter-values'

describe('Action parameter values', () => {
  it('resolves static labels while preserving typed values', () => {
    const definition = {
      type: 'integer' as const,
      allowedValues: { values: [{ value: 2, label: 'Two' }] }
    }

    expect(actionParameterChoices(definition, {})).toEqual([{ value: 2, label: 'Two' }])
    expect(hasAllowedActionParameterValue(definition, 2, {})).toBe(true)
    expect(hasAllowedActionParameterValue(definition, '2', {})).toBe(false)
  })

  it('resolves provider choices', () => {
    const definition = { type: 'string' as const, allowedValues: { provider: 'models' } }
    const discovered = { models: [{ value: 'model-a', label: 'Model A' }] }

    expect(actionParameterChoices(definition, discovered)).toEqual(discovered.models)
  })

  it('coerces numeric inputs to numbers and rejects fractional integers', () => {
    expect(coerceActionParameterInput({ type: 'integer' }, '3')).toBe(3)
    expect(coerceActionParameterInput({ type: 'integer' }, '3.5')).toBe('')
    expect(coerceActionParameterInput({ type: 'number' }, '0.75')).toBe(0.75)
  })
})
