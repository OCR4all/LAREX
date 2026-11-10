import type {
  ActionParameterChoice,
  ActionParameterDefinition,
  ActionParameterScalar,
  ActionParameterValue
} from '@/types/action'

function isActionParameterScalar(value: unknown): value is ActionParameterScalar {
  return typeof value === 'string' || typeof value === 'number' || typeof value === 'boolean'
}

export function actionParameterDefaultValue(definition: ActionParameterDefinition): ActionParameterValue | '' {
  const configured = definition.defaultValue ?? definition.default
  if (definition.multiple) {
    return Array.isArray(configured) ? configured.filter(isActionParameterScalar) : []
  }
  if (typeof configured === 'string' || typeof configured === 'number' || typeof configured === 'boolean') {
    return configured
  }
  if (definition.type === 'boolean') return false
  if (definition.type === 'number' || definition.type === 'integer') return 0
  return ''
}

export function actionParameterLabel(key: string): string {
  const words = key.replace(/([a-z0-9])([A-Z])/g, '$1 $2').replace(/[_-]+/g, ' ').trim().toLowerCase()
  return words.charAt(0).toUpperCase() + words.slice(1)
}

export function actionParameterChoices(
  definition: ActionParameterDefinition,
  discoveredValues: Record<string, ActionParameterChoice[]>
): ActionParameterChoice[] {
  if (definition.allowedValues?.values) return definition.allowedValues.values
  const provider = definition.allowedValues?.provider
  return provider ? (discoveredValues[provider] ?? []) : []
}

function isAllowedChoice(choices: ActionParameterChoice[], value: unknown): boolean {
  return choices.some(choice => typeof choice.value === typeof value && choice.value === value)
}

export function hasAllowedActionParameterValue(
  definition: ActionParameterDefinition,
  value: unknown,
  discoveredValues: Record<string, ActionParameterChoice[]>
): boolean {
  if (!definition.allowedValues) return true
  const choices = actionParameterChoices(definition, discoveredValues)
  if (definition.multiple) return Array.isArray(value) && value.every(item => isAllowedChoice(choices, item))
  return isAllowedChoice(choices, value)
}

export function allowedActionParameterValue(
  definition: ActionParameterDefinition,
  value: unknown,
  discoveredValues: Record<string, ActionParameterChoice[]>
): ActionParameterValue | undefined {
  const choices = actionParameterChoices(definition, discoveredValues)
  if (definition.multiple) {
    return Array.isArray(value) ? value.filter(item => isAllowedChoice(choices, item)) : []
  }
  return isAllowedChoice(choices, value) ? value as ActionParameterScalar : undefined
}

export function initialActionParameterValue(
  definition: ActionParameterDefinition,
  discoveredValues: Record<string, ActionParameterChoice[]>
): ActionParameterValue | '' {
  const fallback = actionParameterDefaultValue(definition)
  if (!definition.allowedValues?.provider) return fallback
  const configured = definition.defaultValue ?? definition.default
  if (configured !== undefined && configured !== null
    && hasAllowedActionParameterValue(definition, fallback, discoveredValues)) {
    return fallback
  }
  const marked = actionParameterChoices(definition, discoveredValues)
    .filter(choice => choice.default === true)
    .map(choice => choice.value)
  if (definition.multiple) return marked.length ? marked : fallback
  return marked.length === 1 ? marked[0]! : fallback
}

export function applyDiscoveredActionParameterDefaults(
  definitions: Record<string, ActionParameterDefinition>,
  values: Record<string, ActionParameterValue | '' | undefined>,
  discoveredValues: Record<string, ActionParameterChoice[]>,
  isUnchanged: (key: string, definition: ActionParameterDefinition) => boolean
    = (key, definition) => JSON.stringify(values[key]) === JSON.stringify(actionParameterDefaultValue(definition))
) {
  for (const [key, definition] of Object.entries(definitions)) {
    if (!definition.allowedValues?.provider || !isUnchanged(key, definition)) continue
    values[key] = initialActionParameterValue(definition, discoveredValues)
  }
}

export function isBlankActionParameterValue(value: unknown): boolean {
  if (value === undefined || value === null) return true
  if (Array.isArray(value)) return value.length === 0
  return typeof value === 'string' && value.trim().length === 0
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
