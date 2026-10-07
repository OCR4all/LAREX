import type { ActionAssignmentResponse, ActionDefinitionResponse } from '~/types/action'

export function actionActivationOptions(
  definitions: ActionDefinitionResponse[],
  assignments: ActionAssignmentResponse[],
  projectId: string | null
) {
  return definitions
    .filter(definition => !projectId || (definition.kind ?? 'PROCESSING') === 'PROCESSING')
    .filter(definition => !assignments.some(assignment =>
      assignment.processor.id === definition.id && assignment.enabled
    ))
    .map(definition => ({
      label: `${definition.name} · ${definition.global ? 'Global availability' : 'Workspace availability'}`,
      value: definition.id
    }))
}
