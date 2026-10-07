import type { ActionAssignmentResponse, ActionDefinitionResponse } from '~/types/action'

export interface WorkspaceActionRow {
  id: string
  processor: ActionDefinitionResponse
  assignments: ActionAssignmentResponse[]
  enabled: boolean
  scope: 'WORKSPACE' | 'PROJECTS'
  projectIds: string[]
}

export function groupWorkspaceActions(assignments: ActionAssignmentResponse[]): WorkspaceActionRow[] {
  const groups = new Map<string, ActionAssignmentResponse[]>()
  for (const assignment of assignments) {
    const group = groups.get(assignment.processor.id) ?? []
    group.push(assignment)
    groups.set(assignment.processor.id, group)
  }
  return [...groups.entries()].map(([id, group]) => {
    const enabledAssignments = group.filter(assignment => assignment.enabled)
    const relevant = enabledAssignments.length ? enabledAssignments : group
    const workspaceWide = relevant.some(assignment => assignment.projectId === null)
    return {
      id,
      processor: group[0]!.processor,
      assignments: group,
      enabled: enabledAssignments.length > 0,
      scope: workspaceWide ? 'WORKSPACE' as const : 'PROJECTS' as const,
      projectIds: workspaceWide ? [] : [...new Set(relevant.flatMap(assignment => assignment.projectId ? [assignment.projectId] : []))]
    }
  })
}

export function filterWorkspaceActions(rows: WorkspaceActionRow[], search: string, projectId: string) {
  const query = search.trim().toLowerCase()
  return rows.filter(row => (!projectId || row.scope === 'WORKSPACE' || row.projectIds.includes(projectId))
    && (!query || [row.processor.name, row.processor.processorKey, row.processor.description]
      .some(value => value?.toLowerCase().includes(query))))
}

export function unconfiguredActions(definitions: ActionDefinitionResponse[], assignments: ActionAssignmentResponse[]) {
  const configured = new Set(assignments.map(assignment => assignment.processor.id))
  return definitions.filter(definition => !configured.has(definition.id))
}
