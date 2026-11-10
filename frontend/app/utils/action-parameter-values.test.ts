import { describe, expect, it } from 'vitest'
import {
  actionParameterChoices,
  actionParameterDefaultValue,
  allowedActionParameterValue,
  applyDiscoveredActionParameterDefaults,
  initialActionParameterValue,
  isBlankActionParameterValue,
  actionParameterLabel,
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

  it('handles multiple selections as lists', () => {
    const definition = { type: 'string' as const, multiple: true, allowedValues: { provider: 'models' } }
    const discovered = { models: [{ value: 'a', label: 'A' }, { value: 'b', label: 'B' }] }

    expect(actionParameterDefaultValue(definition)).toEqual([])
    expect(actionParameterDefaultValue({ ...definition, default: ['a', { nested: true }] })).toEqual(['a'])
    expect(hasAllowedActionParameterValue(definition, ['a', 'b'], discovered)).toBe(true)
    expect(hasAllowedActionParameterValue(definition, [], discovered)).toBe(true)
    expect(hasAllowedActionParameterValue(definition, ['a', 'gone'], discovered)).toBe(false)
    expect(hasAllowedActionParameterValue(definition, 'a', discovered)).toBe(false)
    expect(allowedActionParameterValue(definition, ['a', 'gone'], discovered)).toEqual(['a'])
    expect(allowedActionParameterValue({ type: 'string', allowedValues: { provider: 'models' } }, 'gone', discovered)).toBeUndefined()
  })

  it('prefers an offered YAML default over the provider default', () => {
    const provider = { allowedValues: { provider: 'models' } }
    const discovered = { models: [{ value: 'a', label: 'A' }, { value: 'b', label: 'B', default: true }] }

    expect(initialActionParameterValue({ ...provider, default: 'a' }, discovered)).toBe('a')
    expect(initialActionParameterValue({ ...provider, default: 'gone' }, discovered)).toBe('b')
    expect(initialActionParameterValue(provider, discovered)).toBe('b')
    expect(initialActionParameterValue({ ...provider, default: 'gone' }, { models: [{ value: 'a', label: 'A' }] })).toBe('gone')
    expect(initialActionParameterValue({ allowedValues: { values: discovered.models }, default: 'a' }, {})).toBe('a')
  })

  it('uses every marked provider default for multiple parameters and none when single is ambiguous', () => {
    const discovered = { models: [{ value: 'a', label: 'A', default: true }, { value: 'b', label: 'B', default: true }] }

    expect(initialActionParameterValue({ multiple: true, allowedValues: { provider: 'models' } }, discovered)).toEqual(['a', 'b'])
    expect(initialActionParameterValue({ multiple: true, default: ['a'], allowedValues: { provider: 'models' } }, discovered)).toEqual(['a'])
    expect(initialActionParameterValue({ allowedValues: { provider: 'models' } }, discovered)).toBe('')
  })

  it('applies discovered defaults only to values that were not changed', () => {
    const definitions = {
      untouched: { allowedValues: { provider: 'models' } },
      chosen: { allowedValues: { provider: 'models' } },
      free: { type: 'string' as const }
    }
    const values = { untouched: '', chosen: 'a', free: '' }

    applyDiscoveredActionParameterDefaults(definitions, values, {
      models: [{ value: 'a', label: 'A' }, { value: 'b', label: 'B', default: true }]
    })

    expect(values).toEqual({ untouched: 'b', chosen: 'a', free: '' })
  })

  it('treats empty strings and empty lists as blank', () => {
    expect(isBlankActionParameterValue([])).toBe(true)
    expect(isBlankActionParameterValue('  ')).toBe(true)
    expect(isBlankActionParameterValue(['a'])).toBe(false)
    expect(isBlankActionParameterValue(false)).toBe(false)
  })

  it('coerces numeric inputs to numbers and rejects fractional integers', () => {
    expect(coerceActionParameterInput({ type: 'integer' }, '3')).toBe(3)
    expect(coerceActionParameterInput({ type: 'integer' }, '3.5')).toBe('')
    expect(coerceActionParameterInput({ type: 'number' }, '0.75')).toBe(0.75)
  })

  it('turns parameter keys into readable labels', () => {
    expect(actionParameterLabel('modelName')).toBe('Model name')
    expect(actionParameterLabel('max_epochs')).toBe('Max epochs')
    expect(actionParameterLabel('delay-seconds')).toBe('Delay seconds')
    expect(actionParameterLabel('epochs')).toBe('Epochs')
  })
})
