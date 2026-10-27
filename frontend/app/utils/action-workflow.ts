import type { ActionDefinition } from '~/types/action'
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
