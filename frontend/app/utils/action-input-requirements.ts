import type { ActionInputLevel, ActionInputRequirement, ActionTarget } from '@/types/action'

export function actionInputLevelForTarget(
  requirement: ActionInputRequirement | null | undefined,
  target: ActionTarget,
  legacyAccepted = false
): ActionInputLevel {
  if (!requirement) return legacyAccepted ? 'OPTIONAL' : 'NONE'
  return requirement.requiredForTargets?.includes(target) ? 'REQUIRED' : requirement.level
}
