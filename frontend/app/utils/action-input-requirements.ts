import type { ActionInputLevel, ActionInputRequirement, ActionTarget } from '@/types/action'

export function actionInputLevelForTarget(
  requirement: ActionInputRequirement | null | undefined,
  target: ActionTarget,
  legacyAccepted = false
): ActionInputLevel {
  if (!requirement) return legacyAccepted ? 'OPTIONAL' : 'NONE'
  return requirement.requiredForTargets?.includes(target) ? 'REQUIRED' : requirement.level
}

export function actionImageSelectionValues(
  value: unknown,
  previous: string[] = [],
  required = false
): string[] {
  const selected = typeof value === 'string'
    ? (value ? [value] : [])
    : Array.isArray(value) ? value.filter((item): item is string => typeof item === 'string') : []
  return required && selected.length === 0 ? [...previous] : selected
}
