package de.uniwue.zpd.dachs.larex.backend.dto;

import de.uniwue.zpd.dachs.larex.backend.entity.WorkspaceMember;

import java.time.LocalDateTime;

/**
 * DTO for workspace invitation details (for the user receiving invitations)
 */
public record WorkspaceInvitationDto(
        String id,
        String workspaceId,
        String workspaceName,
        WorkspaceMember.Role role,
        LocalDateTime invitedAt
) {}
