import type { ActionInputLevel, ActionInputRequirement, ActionTarget } from '@/types/action'

export function actionInputLevelForTarget(
  requirement: ActionInputRequirement | null | undefined,
  target: ActionTarget,
  legacyAccepted = false
): ActionInputLevel {
  if (!requirement) return legacyAccepted ? 'OPTIONAL' : 'NONE'
  return requirement.requiredForTargets?.includes(target) ? 'REQUIRED' : requirement.level
}

export function actionImageSelectionValues(value: unknown): string[] {
  if (typeof value === 'string') return value ? [value] : []
  return Array.isArray(value) ? value.filter((item): item is string => typeof item === 'string') : []
}
