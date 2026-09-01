import type {
  ActionParameterChoice,
  ActionParameterDefinition,
  ActionParameterValue
} from '@/types/action'

export function actionParameterDefaultValue(definition: ActionParameterDefinition): ActionParameterValue | '' {
  const configured = definition.defaultValue ?? definition.default
  if (typeof configured === 'string' || typeof configured === 'number' || typeof configured === 'boolean') {
    return configured
  }
  if (definition.type === 'boolean') return false
  if (definition.type === 'number' || definition.type === 'integer') return 0
  return ''
}

export function actionParameterChoices(
  definition: ActionParameterDefinition,
  discoveredValues: Record<string, ActionParameterChoice[]>
): ActionParameterChoice[] {
  if (definition.allowedValues?.values) return definition.allowedValues.values
  const provider = definition.allowedValues?.provider
  return provider ? (discoveredValues[provider] ?? []) : []
}

export function hasAllowedActionParameterValue(
  definition: ActionParameterDefinition,
  value: unknown,
  discoveredValues: Record<string, ActionParameterChoice[]>
): boolean {
  if (!definition.allowedValues) return true
  return actionParameterChoices(definition, discoveredValues)
    .some(choice => typeof choice.value === typeof value && choice.value === value)
}

export function coerceActionParameterInput(
  definition: ActionParameterDefinition,
  value: string | number | null | undefined
): ActionParameterValue | '' {
  if (value === null || value === undefined || value === '') return ''
  if (definition.type === 'integer') {
    const parsed = typeof value === 'number' ? value : Number(value)
    return Number.isInteger(parsed) ? parsed : ''
  }
  if (definition.type === 'number') {
    const parsed = typeof value === 'number' ? value : Number(value)
    return Number.isFinite(parsed) ? parsed : ''
  }
  return String(value)
}
