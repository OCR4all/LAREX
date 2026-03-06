package de.uniwue.zpd.dachs.larex.backend.controller.workspace;

import de.uniwue.zpd.dachs.larex.backend.dto.WorkspaceDto;
import de.uniwue.zpd.dachs.larex.backend.dto.WorkspaceMemberDto;
import de.uniwue.zpd.dachs.larex.backend.entity.WorkspaceMember;
import de.uniwue.zpd.dachs.larex.backend.entity.workspace.AbstractWorkspace;
import de.uniwue.zpd.dachs.larex.backend.entity.workspace.PersonalWorkspace;
import de.uniwue.zpd.dachs.larex.backend.entity.workspace.TeamWorkspace;
import de.uniwue.zpd.dachs.larex.backend.exception.ResourceNotFoundException;
import de.uniwue.zpd.dachs.larex.backend.service.security.AuthorizationPolicyService;
import de.uniwue.zpd.dachs.larex.backend.service.workspace.WorkspaceService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;

/**
 * Main workspace controller that handles operations across all workspace types
 */
@RestController
@RequestMapping("/workspaces")
public class WorkspaceController {

    private final WorkspaceService workspaceService;
    private final AuthorizationPolicyService authorizationPolicyService;

    public WorkspaceController(WorkspaceService workspaceService,
                               AuthorizationPolicyService authorizationPolicyService) {
        this.workspaceService = workspaceService;
        this.authorizationPolicyService = authorizationPolicyService;
    }

    /**
     * Get all workspaces (personal and team) for the authenticated user
     */
    @GetMapping
    public ResponseEntity<List<WorkspaceDto.Response>> getUserWorkspaces(
            @AuthenticationPrincipal(expression = "subject") String userId) {

        List<AbstractWorkspace> workspaces = workspaceService.getUserWorkspaces(userId);

        List<WorkspaceDto.Response> response = workspaces.stream()
                .map(workspace -> mapToResponse(workspace, userId))
                .toList();

        return ResponseEntity.ok(response);
    }

    /**
     * Get specific workspace by ID
     */
    @GetMapping("/{workspaceId}")
    public ResponseEntity<WorkspaceDto.Response> getWorkspace(
            @PathVariable String workspaceId,
            @AuthenticationPrincipal(expression = "subject") String userId) {

        Optional<AbstractWorkspace> workspaceOpt = workspaceService.getWorkspaceById(workspaceId, userId);

        return workspaceOpt.map(workspace -> mapToResponse(workspace, userId))
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/{workspaceId}/capabilities")
    public ResponseEntity<de.uniwue.zpd.dachs.larex.backend.dto.AuthorizationCapabilitiesDto.WorkspaceCapabilities> getWorkspaceCapabilities(
            @PathVariable String workspaceId,
            @AuthenticationPrincipal(expression = "subject") String userId) {
        if (!authorizationPolicyService.canAccessWorkspace(workspaceId, userId)) {
            throw new SecurityException("Access denied to workspace: " + workspaceId);
        }
        return ResponseEntity.ok(authorizationPolicyService.resolveWorkspaceCapabilities(workspaceId, userId));
    }

    /**
     * Create team workspace
     */
    @PreAuthorize("hasAnyRole('GLOBAL_ADMIN', 'GLOBAL_CURATOR')")
    @PostMapping
    public ResponseEntity<WorkspaceDto.Response> createWorkspace(
            @Valid @RequestBody WorkspaceDto.CreateTeamWorkspaceRequest request,
            @AuthenticationPrincipal(expression = "subject") String userId) {

        TeamWorkspace teamWorkspace = workspaceService.createTeamWorkspace(
                request.name(),
                request.description(),
                userId
        );

        if (request.initialInvites() != null && !request.initialInvites().isEmpty()) {
            for (WorkspaceDto.InviteRequest invite : request.initialInvites()) {
                WorkspaceMember.Role role = parseWorkspaceRole(invite.role());
                workspaceService.inviteUserToWorkspace(
                        teamWorkspace.getId(),
                        userId,
                        invite.userId(),
                        role
                );
            }
        }

        WorkspaceDto.TeamWorkspaceResponse response = new WorkspaceDto.TeamWorkspaceResponse(
                teamWorkspace.getId(),
                teamWorkspace.getName(),
                teamWorkspace.getDescription(),
                teamWorkspace.getAvatar(),
                teamWorkspace.getCreated(),
                teamWorkspace.getUpdated(),
                teamWorkspace.getOwnerUserId(),
                teamWorkspace.getCodec() != null ? teamWorkspace.getCodec().getId() : null,
                teamWorkspace.getLabelSet() != null ? teamWorkspace.getLabelSet().getId() : null,
                teamWorkspace.getTagSet() != null ? teamWorkspace.getTagSet().getId() : null,
                teamWorkspace.getEffectiveDefaultGtIndex(),
                teamWorkspace.getDefaultRecognitionIndicesList(),
                authorizationPolicyService.resolveWorkspaceCapabilities(teamWorkspace.getId(), userId)
        );

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Update workspace (handles both personal and team workspaces)
     */
    @PutMapping("/{workspaceId}")
    public ResponseEntity<WorkspaceDto.Response> updateWorkspace(
            @PathVariable String workspaceId,
            @Valid @RequestBody WorkspaceDto.UpdateTeamWorkspaceRequest request,
            @AuthenticationPrincipal(expression = "subject") String userId) {

        Optional<AbstractWorkspace> workspaceOpt = workspaceService.updateWorkspace(
                workspaceId,
                request.name(),
                request.description(),
                request.avatar(),
                request.codecId(),
                request.labelSetId(),
                request.tagSetId(),
                request.defaultGtIndex(),
                request.defaultRecognitionIndices(),
                userId
        );

        return workspaceOpt.map(workspace -> mapToResponse(workspace, userId))
                .map(ResponseEntity::ok)
                .orElseThrow(() -> new ResourceNotFoundException("Workspace", workspaceId));
    }

    /**
     * Delete workspace (only team workspaces can be deleted)
     */
    @PreAuthorize("@workspaceSecurity.isOwner(#workspaceId, authentication.name)")
    @DeleteMapping("/{workspaceId}")
    public ResponseEntity<Void> deleteWorkspace(
            @PathVariable String workspaceId,
            @AuthenticationPrincipal(expression = "subject") String userId) {

        // First check if workspace exists
        Optional<AbstractWorkspace> workspaceOpt = workspaceService.getWorkspaceById(workspaceId, userId);
        if (workspaceOpt.isEmpty()) {
            throw new ResourceNotFoundException("Workspace", workspaceId);
        }

        AbstractWorkspace workspace = workspaceOpt.get();
        if (workspace.isPersonal()) {
            throw new IllegalArgumentException("Personal workspaces cannot be deleted");
        }

        boolean deleted = workspaceService.deleteWorkspace(workspaceId, userId);
        if (!deleted) {
            throw new SecurityException("You don't have permission to delete this workspace");
        }

        return ResponseEntity.noContent().build();
    }

    /**
     * Invite user to workspace (only for team workspaces)
     */
    @PreAuthorize("@workspaceSecurity.isAdmin(#workspaceId, authentication.name)")
    @PostMapping("/{workspaceId}/invitations")
    public ResponseEntity<Void> inviteUser(
            @PathVariable String workspaceId,
            @Valid @RequestBody WorkspaceDto.InviteRequest request,
            @AuthenticationPrincipal(expression = "subject") String userId) {

        // Check if workspace exists
        Optional<AbstractWorkspace> workspaceOpt = workspaceService.getWorkspaceById(workspaceId, userId);
        if (workspaceOpt.isEmpty()) {
            throw new ResourceNotFoundException("Workspace", workspaceId);
        }

        // Check if workspace supports invitations
        AbstractWorkspace workspace = workspaceOpt.get();
        if (!workspace.canInviteUsers()) {
            throw new IllegalArgumentException("Cannot invite users to personal workspaces");
        }

        WorkspaceMember.Role role = parseWorkspaceRole(request.role());

        boolean invited = workspaceService.inviteUserToWorkspace(
                workspaceId,
                userId,
                request.userId(),
                role
        );

        if (!invited) {
            throw new IllegalArgumentException("Unable to invite user. They may already be a member of this workspace or you don't have permission");
        }

        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    /**
     * Accept workspace invitation
     */
    @PostMapping("/{workspaceId}/invitations/accept")
    public ResponseEntity<Void> acceptInvitation(
            @PathVariable String workspaceId,
            @AuthenticationPrincipal(expression = "subject") String userId) {

        boolean accepted = workspaceService.acceptWorkspaceInvitation(workspaceId, userId);

        if (!accepted) {
            throw new IllegalArgumentException("No pending invitation found for this workspace");
        }

        return ResponseEntity.ok().build();
    }

    /**
     * Decline workspace invitation
     */
    @PostMapping("/{workspaceId}/invitations/decline")
    public ResponseEntity<Void> declineInvitation(
            @PathVariable String workspaceId,
            @AuthenticationPrincipal(expression = "subject") String userId) {

        boolean declined = workspaceService.declineWorkspaceInvitation(workspaceId, userId);

        if (!declined) {
            throw new IllegalArgumentException("No pending invitation found for this workspace");
        }

        return ResponseEntity.ok().build();
    }

    /**
     * Get workspace members with enriched user data (only for team workspaces)
     */
    @GetMapping("/{workspaceId}/members")
    public ResponseEntity<List<WorkspaceMemberDto>> getWorkspaceMembers(
            @PathVariable String workspaceId,
            @AuthenticationPrincipal(expression = "subject") String userId) {

        List<WorkspaceMemberDto> members = workspaceService.getWorkspaceMembers(workspaceId, userId);

        return ResponseEntity.ok(members);
    }

    /**
     * Get only accepted workspace members (useful for task assignment).
     * Returns members with ACCEPTED invitation status only.
     */
    @GetMapping("/{workspaceId}/members/accepted")
    public ResponseEntity<List<WorkspaceMemberDto>> getAcceptedWorkspaceMembers(
            @PathVariable String workspaceId,
            @AuthenticationPrincipal(expression = "subject") String userId) {

        List<WorkspaceMemberDto> members = workspaceService.getAcceptedWorkspaceMembers(workspaceId, userId);

        return ResponseEntity.ok(members);
    }

    /**
     * Remove user from workspace (only for team workspaces)
     */
    @PreAuthorize("@workspaceSecurity.isAdmin(#workspaceId, authentication.name)")
    @DeleteMapping("/{workspaceId}/members/{memberId}")
    public ResponseEntity<Void> removeUser(
            @PathVariable String workspaceId,
            @PathVariable String memberId,
            @AuthenticationPrincipal(expression = "subject") String userId) {

        boolean removed = workspaceService.removeUserFromWorkspace(workspaceId, userId, memberId);

        if (!removed) {
            throw new SecurityException("Cannot remove user from workspace. You may not have permission or the user may not be a member");
        }

        return ResponseEntity.noContent().build();
    }

    /**
     * Update member role (only for team workspaces, admin only)
     */
    @PreAuthorize("@workspaceSecurity.isAdmin(#workspaceId, authentication.name)")
    @PutMapping("/{workspaceId}/members/{memberId}")
    public ResponseEntity<Void> updateMemberRole(
            @PathVariable String workspaceId,
            @PathVariable String memberId,
            @Valid @RequestBody WorkspaceDto.UpdateMemberRoleRequest request,
            @AuthenticationPrincipal(expression = "subject") String userId) {

        WorkspaceMember.Role role = parseWorkspaceRole(request.role());

        boolean updated = workspaceService.updateMemberRole(workspaceId, memberId, userId, role);

        if (!updated) {
            throw new SecurityException("Cannot update member role. You may not have permission or this would leave the workspace without an administrator.");
        }

        return ResponseEntity.ok().build();
    }

    /**
     * Leave workspace (user removes themselves from a team workspace)
     */
    @PostMapping("/{workspaceId}/leave")
    public ResponseEntity<Void> leaveWorkspace(
            @PathVariable String workspaceId,
            @AuthenticationPrincipal(expression = "subject") String userId) {

        boolean left = workspaceService.leaveWorkspace(workspaceId, userId);

        if (!left) {
            throw new IllegalArgumentException("Cannot leave workspace. You may not be a member or you are the last administrator.");
        }

        return ResponseEntity.ok().build();
    }

    /**
     * Get pending invitations for the current user
     */
    @GetMapping("/invitations")
    public ResponseEntity<List<WorkspaceDto.InvitationResponse>> getPendingInvitations(
            @AuthenticationPrincipal(expression = "subject") String userId) {
        return ResponseEntity.ok(workspaceService.getPendingInvitationResponsesForUser(userId));
    }

    /**
     * Map workspace entity to appropriate DTO response
     */
    private WorkspaceDto.Response mapToResponse(AbstractWorkspace workspace, String userId) {
        String codecId = workspace.getCodec() != null ? workspace.getCodec().getId() : null;
        String labelSetId = workspace.getLabelSet() != null ? workspace.getLabelSet().getId() : null;
        String tagSetId = workspace.getTagSet() != null ? workspace.getTagSet().getId() : null;
        Integer defaultGtIndex = workspace.getEffectiveDefaultGtIndex();
        List<Integer> defaultRecognitionIndices = workspace.getDefaultRecognitionIndicesList();
        var capabilities = authorizationPolicyService.resolveWorkspaceCapabilities(workspace.getId(), userId);

        if (workspace instanceof PersonalWorkspace personalWorkspace) {
            return new WorkspaceDto.PersonalWorkspaceResponse(
                    personalWorkspace.getId(),
                    personalWorkspace.getDescription(),
                    personalWorkspace.getAvatar(),
                    personalWorkspace.getCreated(),
                    personalWorkspace.getUpdated(),
                    personalWorkspace.getOwnerUserId(),
                    codecId,
                    labelSetId,
                    tagSetId,
                    defaultGtIndex,
                    defaultRecognitionIndices,
                    capabilities
            );
        } else if (workspace instanceof TeamWorkspace teamWorkspace) {
            return new WorkspaceDto.TeamWorkspaceResponse(
                    teamWorkspace.getId(),
                    teamWorkspace.getName(),
                    teamWorkspace.getDescription(),
                    teamWorkspace.getAvatar(),
                    teamWorkspace.getCreated(),
                    teamWorkspace.getUpdated(),
                    teamWorkspace.getOwnerUserId(),
                    codecId,
                    labelSetId,
                    tagSetId,
                    defaultGtIndex,
                    defaultRecognitionIndices,
                    capabilities
            );
        } else {
            throw new IllegalArgumentException("Unknown workspace type: " + workspace.getClass());
        }
    }

    private WorkspaceMember.Role parseWorkspaceRole(String rawRole) {
        return WorkspaceMember.Role.fromApiValue(rawRole);
    }
}
