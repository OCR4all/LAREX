package de.uniwue.zpd.dachs.larex.backend.service.workspace;

import de.uniwue.zpd.dachs.larex.backend.dto.WorkspaceDto;
import de.uniwue.zpd.dachs.larex.backend.dto.WorkspaceMemberDto;
import de.uniwue.zpd.dachs.larex.backend.entity.WorkspaceMember;
import de.uniwue.zpd.dachs.larex.backend.entity.workspace.AbstractWorkspace;
import de.uniwue.zpd.dachs.larex.backend.entity.workspace.PersonalWorkspace;
import de.uniwue.zpd.dachs.larex.backend.entity.workspace.TeamWorkspace;
import de.uniwue.zpd.dachs.larex.backend.repository.workspace.WorkspaceQueryService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Unified workspace service that delegates to specific workspace type services
 */
@Service
@Transactional
public class WorkspaceService {
    
    private final PersonalWorkspaceService personalWorkspaceService;
    private final TeamWorkspaceService teamWorkspaceService;
    private final WorkspaceQueryService workspaceQueryService;
    private final WorkspaceAccessService workspaceAccessService;

    public WorkspaceService(PersonalWorkspaceService personalWorkspaceService,
                           TeamWorkspaceService teamWorkspaceService,
                           WorkspaceQueryService workspaceQueryService,
                           WorkspaceAccessService workspaceAccessService) {
        this.personalWorkspaceService = personalWorkspaceService;
        this.teamWorkspaceService = teamWorkspaceService;
        this.workspaceQueryService = workspaceQueryService;
        this.workspaceAccessService = workspaceAccessService;
    }

    /**
     * Get all workspaces for user (ensures personal workspace exists)
     */
    public List<AbstractWorkspace> getUserWorkspaces(String userId) {
        // Ensure personal workspace exists
        personalWorkspaceService.ensurePersonalWorkspace(userId);
        
        return workspaceQueryService.findAllWorkspacesForUser(userId);
    }

    /**
     * Create team workspace
     */
    public TeamWorkspace createTeamWorkspace(String name, String description, String userId) {
        workspaceAccessService.requireCreateWorkspaceAccess(userId);
        return teamWorkspaceService.createTeamWorkspace(name, description, userId);
    }

    /**
     * Get workspace by ID (polymorphic)
     */
    public Optional<AbstractWorkspace> getWorkspaceById(String workspaceId, String userId) {
        Optional<AbstractWorkspace> workspace = workspaceQueryService.findWorkspaceById(workspaceId);
        
        if (workspace.isPresent()) {
            AbstractWorkspace ws = workspace.get();
            if (hasAccessToWorkspace(ws, userId)) {
                return workspace;
            }
        }
        
        return Optional.empty();
    }

    /**
     * Update workspace (handles both types)
     */
    public Optional<AbstractWorkspace> updateWorkspace(String workspaceId, String name, String description, String avatar,
                                                       String codecId, String labelSetId, String dictionaryId, String tagSetId,
                                                       String normalizationProfileId, String validationRulesetId,
                                                       Integer defaultGtIndex, List<Integer> defaultRecognitionIndices,
                                                       String userId) {
        Optional<AbstractWorkspace> workspaceOpt = workspaceQueryService.findWorkspaceById(workspaceId);
        if (workspaceOpt.isEmpty()) return Optional.empty();

        AbstractWorkspace workspace = workspaceOpt.get();
        if (workspace instanceof PersonalWorkspace) {
            Optional<PersonalWorkspace> personal = personalWorkspaceService.updatePersonalWorkspace(
                    workspaceId, description, avatar, codecId, labelSetId, dictionaryId, tagSetId,
                    normalizationProfileId, validationRulesetId, defaultGtIndex, defaultRecognitionIndices, userId
            );
            return personal.map(pw -> (AbstractWorkspace) pw);
        }

        Optional<TeamWorkspace> team = teamWorkspaceService.updateTeamWorkspace(
                workspaceId, name, description, avatar, codecId, labelSetId, dictionaryId, tagSetId,
                normalizationProfileId, validationRulesetId, defaultGtIndex, defaultRecognitionIndices, userId
        );
        return team.map(tw -> (AbstractWorkspace) tw);
    }

    /**
     * Delete workspace (handles both types)
     */
    public boolean deleteWorkspace(String workspaceId, String userId) {
        // Check if it's a personal workspace (personal workspaces generally shouldn't be deleted)
        Optional<PersonalWorkspace> personalWorkspace = personalWorkspaceService.getPersonalWorkspace(userId);
        if (personalWorkspace.isPresent() && personalWorkspace.get().getId().equals(workspaceId)) {
            return false; // Don't allow personal workspace deletion
        }
        
        // Try team workspace deletion
        return teamWorkspaceService.deleteTeamWorkspace(workspaceId, userId);
    }

    // Delegation methods for team workspace operations
    public boolean inviteUserToWorkspace(String workspaceId, String inviterId, String userId, de.uniwue.zpd.dachs.larex.backend.entity.WorkspaceMember.Role role) {
        return teamWorkspaceService.inviteUserToTeamWorkspace(workspaceId, inviterId, userId, role);
    }

    public boolean acceptWorkspaceInvitation(String workspaceId, String userId) {
        return teamWorkspaceService.acceptTeamWorkspaceInvitation(workspaceId, userId);
    }

    public boolean declineWorkspaceInvitation(String workspaceId, String userId) {
        return teamWorkspaceService.declineTeamWorkspaceInvitation(workspaceId, userId);
    }

    public List<WorkspaceMemberDto> getWorkspaceMembers(String workspaceId, String userId) {
        return teamWorkspaceService.getTeamWorkspaceMembers(workspaceId, userId);
    }

    /**
     * Get only accepted workspace members (useful for task assignment)
     */
    public List<WorkspaceMemberDto> getAcceptedWorkspaceMembers(String workspaceId, String userId) {
        return teamWorkspaceService.getAcceptedTeamWorkspaceMembers(workspaceId, userId);
    }

    public boolean removeUserFromWorkspace(String workspaceId, String adminUserId, String targetUserId) {
        return teamWorkspaceService.removeUserFromTeamWorkspace(workspaceId, adminUserId, targetUserId);
    }

    public boolean canInviteToWorkspace(String workspaceId, String userId) {
        Optional<AbstractWorkspace> workspace = getWorkspaceById(workspaceId, userId);
        return workspace.isPresent() && workspace.get().canInviteUsers();
    }

    /**
     * Get pending invitations for a user
     */
    public List<WorkspaceMember> getPendingInvitationsForUser(String userId) {
        return teamWorkspaceService.getPendingInvitationsForUser(userId);
    }

    public List<WorkspaceDto.InvitationResponse> getPendingInvitationResponsesForUser(String userId) {
        List<WorkspaceMember> invitations = getPendingInvitationsForUser(userId);
        if (invitations.isEmpty()) {
            return List.of();
        }

        Set<String> workspaceIds = invitations.stream()
                .map(WorkspaceMember::getWorkspaceId)
                .collect(java.util.stream.Collectors.toSet());
        var workspaceNames = workspaceQueryService.findWorkspaceNamesByIds(workspaceIds);

        return invitations.stream()
                .map(invitation -> new WorkspaceDto.InvitationResponse(
                        invitation.getId(),
                        invitation.getWorkspaceId(),
                        workspaceNames.getOrDefault(invitation.getWorkspaceId(), "Unknown Workspace"),
                        invitation.getRole().name(),
                        invitation.getCreated()
                ))
                .toList();
    }

    /**
     * Leave workspace
     */
    public boolean leaveWorkspace(String workspaceId, String userId) {
        return teamWorkspaceService.leaveTeamWorkspace(workspaceId, userId);
    }

    /**
     * Update member role
     */
    public boolean updateMemberRole(String workspaceId, String memberId, String adminUserId, de.uniwue.zpd.dachs.larex.backend.entity.WorkspaceMember.Role newRole) {
        return teamWorkspaceService.updateMemberRole(workspaceId, memberId, adminUserId, newRole);
    }

    /**
     * Check if user has access to workspace
     */
    private boolean hasAccessToWorkspace(AbstractWorkspace workspace, String userId) {
        return workspaceAccessService.hasWorkspaceAccess(workspace.getId(), userId);
    }
}
