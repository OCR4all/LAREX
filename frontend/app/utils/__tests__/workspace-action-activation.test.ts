import { describe, expect, it } from 'vitest'
import type { ActionAssignmentResponse, ActionDefinitionResponse } from '~/types/action'
import { filterWorkspaceActions, groupWorkspaceActions, unconfiguredActions } from '../workspace-action-activation'

const processor: ActionDefinitionResponse = {
  id: 'action', name: 'Copy pages', processorKey: 'copy', description: 'Preserve full images and XML',
  yaml: '', endpointUrl: '', endpointTimeoutSeconds: 30, kind: 'PROCESSING', executeRole: 'CURATOR',
  lockMode: 'NONE', category: 'WORKFLOW', targets: ['PAGE'],
  inputs: { images: { level: 'NONE', requiredForTargets: [] }, xml: { level: 'NONE', requiredForTargets: [] } },
  acceptsImages: false, acceptsXml: false, outputsImages: false, outputsXml: false, outputsFiles: false,
  enabled: true, global: true, created: '', updated: '', trainingSplits: null, parameters: {}
}
function assignment(projectId: string | null, enabled = true): ActionAssignmentResponse {
  return { id: projectId ?? 'workspace', workspaceId: 'workspace', projectId, enabled, processor }
}

describe('workspace Action table', () => {
  it('groups project assignments into one row with unique projects', () => {
    const rows = groupWorkspaceActions([assignment('a'), assignment('b'), assignment('a')])
    expect(rows).toHaveLength(1)
    expect(rows[0]).toMatchObject({ scope: 'PROJECTS', projectIds: ['a', 'b'], enabled: true })
  })

  it('gives an enabled workspace assignment precedence over legacy project assignments', () => {
    expect(groupWorkspaceActions([assignment('a'), assignment(null)])[0])
      .toMatchObject({ scope: 'WORKSPACE', projectIds: [], enabled: true })
    expect(groupWorkspaceActions([assignment(null, false), assignment('a')])[0])
      .toMatchObject({ scope: 'PROJECTS', projectIds: ['a'], enabled: true })
  })

  it('retains scope for disabled Actions', () => {
    expect(groupWorkspaceActions([assignment('a', false), assignment('b', false)])[0])
      .toMatchObject({ scope: 'PROJECTS', projectIds: ['a', 'b'], enabled: false })
    expect(groupWorkspaceActions([assignment(null, false), assignment('a', false)])[0])
      .toMatchObject({ scope: 'WORKSPACE', enabled: false })
  })

  it('includes workspace-wide Actions in every project filter and searches descriptions and keys', () => {
    const workspace = groupWorkspaceActions([assignment(null)])
    expect(filterWorkspaceActions(workspace, 'full images', 'any')).toHaveLength(1)
    expect(filterWorkspaceActions(workspace, 'copy', '')).toHaveLength(1)
    expect(filterWorkspaceActions(workspace, 'missing', '')).toHaveLength(0)
    const scoped = groupWorkspaceActions([assignment('a', false)])
    expect(filterWorkspaceActions(scoped, '', 'a')).toHaveLength(1)
    expect(filterWorkspaceActions(scoped, '', 'b')).toHaveLength(0)
  })

  it('excludes configured Actions including disabled and project-only assignments from Add', () => {
    expect(unconfiguredActions([processor], [assignment('a', false)])).toEqual([])
    expect(unconfiguredActions([processor], [])).toEqual([processor])
  })
})
