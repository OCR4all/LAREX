package de.uniwue.zpd.dachs.larex.backend.dto;

import de.uniwue.zpd.dachs.larex.backend.entity.WorkspaceMember;

import java.time.LocalDateTime;

/**
 * Enhanced workspace member DTO that includes user profile information from Keycloak
 */
public record WorkspaceMemberDto(
        String id,
        String userId,
        WorkspaceMember.Role role,
        WorkspaceMember.InvitationStatus invitationStatus,
        LocalDateTime created,
        LocalDateTime updated,
        String workspaceId,
        // User profile information from Keycloak
        String username,
        String email,
        String firstName,
        String lastName,
        String displayName,
        String avatar
) {
    /**
     * Create WorkspaceMemberDto from WorkspaceMember entity and UserDto
     */
    public static WorkspaceMemberDto from(WorkspaceMember member, UserDto user) {
        String displayName = buildDisplayName(user);

        return new WorkspaceMemberDto(
                member.getId(),
                member.getUserId(),
                member.getRole(),
                member.getInvitationStatus(),
                member.getCreated(),
                member.getUpdated(),
                member.getWorkspaceId(),
                user.username(),
                user.email(),
                user.firstName(),
                user.lastName(),
                displayName,
                user.avatar() // Use avatar from UserDto
        );
    }

    /**
     * Create WorkspaceMemberDto with only member data (when user profile is not available)
     */
    public static WorkspaceMemberDto fromMemberOnly(WorkspaceMember member) {
        return new WorkspaceMemberDto(
                member.getId(),
                member.getUserId(),
                member.getRole(),
                member.getInvitationStatus(),
                member.getCreated(),
                member.getUpdated(),
                member.getWorkspaceId(),
                null, // username
                null, // email
                null, // firstName
                null, // lastName
                member.getUserId(), // fallback to userId as displayName
                null // avatar
        );
    }

    private static String buildDisplayName(UserDto user) {
        if (user.firstName() != null && user.lastName() != null) {
            return user.firstName() + " " + user.lastName();
        } else if (user.firstName() != null) {
            return user.firstName();
        } else if (user.lastName() != null) {
            return user.lastName();
        } else {
            return user.username();
        }
    }

}