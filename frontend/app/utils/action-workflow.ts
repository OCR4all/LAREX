import type { ActionDefinition, ActionExecuteRole } from '~/types/action'
import type { ActionWorkflowStep } from '~/types/action-workflow'
import { actionParameterDefaultValue } from './action-parameter-values'

export function createWorkflowStep(processor: ActionDefinition): ActionWorkflowStep {
  return {
    processorDefinitionId: processor.id,
    parameters: Object.fromEntries(Object.entries(processor.parameters).map(([key, definition]) => [key, actionParameterDefaultValue(definition)])),
    imageVariantSelection: null
  }
}

export function moveWorkflowStep(steps: ActionWorkflowStep[], index: number, direction: -1 | 1) {
  const next = [...steps]
  const destination = index + direction
  if (index < 0 || index >= next.length || destination < 0 || destination >= next.length) return next
  const moving = next[index]!
  next[index] = next[destination]!
  next[destination] = moving
  return next
}

type RoleSource = Pick<ActionDefinition, 'id' | 'executeRole'>

/**
 * The role each step's Action needs, in step order. `null` marks a step whose Action is not in `processors`
 * (deleted or unavailable), whose role therefore cannot be known.
 */
export function workflowStepRoles(steps: ActionWorkflowStep[], processors: RoleSource[]): (ActionExecuteRole | null)[] {
  return steps.map(step => processors.find(processor => processor.id === step.processorDefinitionId)?.executeRole ?? null)
}

/** 1-based numbers of the steps only curators may run. */
export function workflowCuratorSteps(steps: ActionWorkflowStep[], processors: RoleSource[]): number[] {
  return workflowStepRoles(steps, processors).flatMap((role, index) => role === 'CURATOR' ? [index + 1] : [])
}

/**
 * Steps run as the user who starts the workflow, so the whole workflow needs the strictest step role. Returns
 * `null` when there are no steps, or when no step is known to need a curator but some step's role is unknown.
 */
export function workflowExecuteRole(steps: ActionWorkflowStep[], processors: RoleSource[]): ActionExecuteRole | null {
  const roles = workflowStepRoles(steps, processors)
  if (roles.includes('CURATOR')) return 'CURATOR'
  return roles.length && !roles.includes(null) ? 'EDITOR' : null
}

function joinNumbers(numbers: number[]): string {
  return numbers.length < 2 ? numbers.join('') : `${numbers.slice(0, -1).join(', ')} and ${numbers.at(-1)}`
}

/** Explains which steps decide who may run the workflow, e.g. "Steps 2 and 4 require curator access …". */
export function describeWorkflowRole(curatorSteps: number[], totalSteps: number): string {
  if (!curatorSteps.length) return 'Editors and curators can run every step.'
  if (curatorSteps.length === totalSteps) return 'Every step requires curator access, so only curators can run this workflow.'
  const subject = curatorSteps.length === 1 ? `Step ${curatorSteps[0]} requires` : `Steps ${joinNumbers(curatorSteps)} require`
  return `${subject} curator access, so only curators can run this workflow. The other steps are open to editors.`
}
