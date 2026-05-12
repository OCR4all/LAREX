export interface WorkspaceDisplayInput {
  id?: string | null
  name?: string | null
  isPersonal?: boolean | null
  ownerUsername?: string | null
  ownerUserId?: string | null
}

function normalizeLabel(value?: string | null): string | null {
  const trimmed = value?.trim()
  return trimmed ? trimmed : null
}

export function getWorkspaceOwnerLabel(workspace: WorkspaceDisplayInput): string | null {
  return normalizeLabel(workspace.ownerUsername) || normalizeLabel(workspace.ownerUserId)
}

export function getWorkspaceDisplayName(workspace: WorkspaceDisplayInput): string {
  const ownerLabel = getWorkspaceOwnerLabel(workspace)

  if (workspace.isPersonal) {
    return ownerLabel ? `Personal Workspace (${ownerLabel})` : 'Personal Workspace'
  }

  return normalizeLabel(workspace.name) || normalizeLabel(workspace.id) || 'Workspace'
}

export function getWorkspaceSecondaryLabel(workspace: WorkspaceDisplayInput): string | null {
  const ownerLabel = getWorkspaceOwnerLabel(workspace)

  if (ownerLabel) {
    return `Owner: ${ownerLabel}`
  }

  if (workspace.isPersonal) {
    return 'Personal workspace'
  }

  return normalizeLabel(workspace.id)
}

export function getWorkspaceSearchText(workspace: WorkspaceDisplayInput): string {
  return [
    getWorkspaceDisplayName(workspace),
    getWorkspaceSecondaryLabel(workspace),
    workspace.name,
    workspace.id,
    workspace.ownerUsername,
    workspace.ownerUserId
  ]
    .map(value => normalizeLabel(value))
    .filter((value): value is string => Boolean(value))
    .join(' ')
}
