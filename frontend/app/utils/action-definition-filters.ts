import type { ActionDefinition } from '~/types/action'

export interface ActionDefinitionFilters {
  status: 'ALL' | 'ENABLED' | 'DISABLED'
  scope: 'ALL' | 'GLOBAL' | 'SCOPED'
  kind: 'ALL' | 'ACTION' | 'WORKFLOW'
}

export function emptyActionDefinitionFilters(): ActionDefinitionFilters {
  return { status: 'ALL', scope: 'ALL', kind: 'ALL' }
}

/** Number of filters that currently narrow the list. */
export function activeActionDefinitionFilterCount(filters: ActionDefinitionFilters): number {
  return Object.values(filters).filter(value => value !== 'ALL').length
}

export function matchesActionDefinitionFilters(
  definition: Pick<ActionDefinition, 'enabled' | 'global' | 'workflowId'>,
  filters: ActionDefinitionFilters
): boolean {
  return (filters.status === 'ALL' || definition.enabled === (filters.status === 'ENABLED'))
    && (filters.scope === 'ALL' || definition.global === (filters.scope === 'GLOBAL'))
    && (filters.kind === 'ALL' || Boolean(definition.workflowId) === (filters.kind === 'WORKFLOW'))
}
