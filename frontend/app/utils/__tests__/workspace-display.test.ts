import { describe, expect, it } from 'vitest'
import {
  getWorkspaceDisplayName,
  getWorkspaceOwnerLabel,
  getWorkspaceSearchText,
  getWorkspaceSecondaryLabel
} from '../workspace-display'

describe('workspace display utils', () => {
  it('adds the owner label to personal workspaces', () => {
    const workspace = {
      id: 'ws-123',
      name: 'Personal Workspace',
      isPersonal: true,
      ownerUsername: 'alice'
    }

    expect(getWorkspaceOwnerLabel(workspace)).toBe('alice')
    expect(getWorkspaceDisplayName(workspace)).toBe('Personal Workspace (alice)')
    expect(getWorkspaceSecondaryLabel(workspace)).toBe('Owner: alice')
  })

  it('falls back to the owner id when no username is available', () => {
    expect(getWorkspaceDisplayName({
      id: 'ws-123',
      isPersonal: true,
      ownerUserId: 'user-456'
    })).toBe('Personal Workspace (user-456)')
  })

  it('keeps team workspace names unchanged while making owner data searchable', () => {
    const searchText = getWorkspaceSearchText({
      id: 'team-1',
      name: 'Manuscripts',
      isPersonal: false,
      ownerUsername: 'curator'
    })

    expect(getWorkspaceDisplayName({ name: 'Manuscripts', isPersonal: false })).toBe('Manuscripts')
    expect(searchText).toContain('Manuscripts')
    expect(searchText).toContain('curator')
    expect(searchText).toContain('team-1')
  })
})
