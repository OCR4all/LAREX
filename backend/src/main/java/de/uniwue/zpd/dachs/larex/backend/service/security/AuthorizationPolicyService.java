package de.uniwue.zpd.dachs.larex.backend.service.security;

import de.uniwue.zpd.dachs.larex.backend.config.security.GlobalAdminService;
import de.uniwue.zpd.dachs.larex.backend.dto.AuthorizationCapabilitiesDto;
import de.uniwue.zpd.dachs.larex.backend.entity.Project;
import de.uniwue.zpd.dachs.larex.backend.entity.Task;
import de.uniwue.zpd.dachs.larex.backend.entity.WorkspaceMember;
import de.uniwue.zpd.dachs.larex.backend.entity.workspace.AbstractWorkspace;
import de.uniwue.zpd.dachs.larex.backend.repository.workspace.WorkspaceMemberRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.workspace.WorkspaceQueryService;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class AuthorizationPolicyService {

    private final WorkspaceQueryService workspaceQueryService;
    private final WorkspaceMemberRepository workspaceMemberRepository;
    private final GlobalAdminService globalAdminService;

    public AuthorizationPolicyService(WorkspaceQueryService workspaceQueryService,
                                      WorkspaceMemberRepository workspaceMemberRepository,
                                      GlobalAdminService globalAdminService) {
        this.workspaceQueryService = workspaceQueryService;
        this.workspaceMemberRepository = workspaceMemberRepository;
        this.globalAdminService = globalAdminService;
    }

    public boolean isGlobalAdmin() {
        return globalAdminService.isGlobalAdmin();
    }

    public boolean isGlobalCurator() {
        return globalAdminService.isGlobalCurator();
    }

    public boolean canCreateTeamWorkspace() {
        return globalAdminService.canCreateWorkspaces();
    }

    public boolean canAccessWorkspace(String workspaceId, String userId) {
        if (globalAdminService.isGlobalAdmin()) {
            return true;
        }

        Optional<AbstractWorkspace> workspaceOpt = resolveWorkspace(workspaceId);
        if (workspaceOpt.isEmpty()) {
            return false;
        }

        AbstractWorkspace workspace = workspaceOpt.get();
        if (isWorkspaceOwner(workspace, userId)) {
            return workspace.getOwnerUserId().equals(userId);
        }

        return resolveAcceptedMembership(workspaceId, userId).isPresent();
    }

    /**
     * Owner-level workspace administration (delete workspace, owner-governed metadata).
     */
    public boolean canAdminWorkspace(String workspaceId, String userId) {
        if (globalAdminService.isGlobalAdmin()) {
            return true;
        }

        Optional<AbstractWorkspace> workspaceOpt = resolveWorkspace(workspaceId);
        if (workspaceOpt.isEmpty()) {
            return false;
        }

        AbstractWorkspace workspace = workspaceOpt.get();
        return isWorkspaceOwner(workspace, userId);
    }

    /**
     * Owner + Curator operational access for workspace-scoped mutations.
     */
    public boolean canManageWorkspaceOperations(String workspaceId, String userId) {
        if (globalAdminService.isGlobalAdmin()) {
            return true;
        }

        Optional<AbstractWorkspace> workspaceOpt = resolveWorkspace(workspaceId);
        if (workspaceOpt.isEmpty()) {
            return false;
        }

        AbstractWorkspace workspace = workspaceOpt.get();
        if (isWorkspaceOwner(workspace, userId)) {
            return true;
        }

        return resolveAcceptedMembership(workspaceId, userId)
                .map(member -> member.getRole().isCuratorLike())
                .orElse(false);
    }

    public boolean canManageMembers(String workspaceId, String userId) {
        return canManageWorkspaceOperations(workspaceId, userId);
    }

    public boolean canEditWorkspace(String workspaceId, String userId) {
        return canAdminWorkspace(workspaceId, userId);
    }

    public boolean canEditWorkspaceTextIndexDefaults(String workspaceId, String userId) {
        return canSetPresets(workspaceId, userId);
    }

    public boolean canManageProjects(String workspaceId, String userId) {
        return canManageWorkspaceOperations(workspaceId, userId);
    }

    /**
     * Release/share management is intentionally restricted to owner, curator-like members, and global admins.
     * Keep this separate from broader project-management semantics.
     */
    public boolean canManageProjectReleasesAndShares(String workspaceId, String userId) {
        return canManageWorkspaceOperations(workspaceId, userId);
    }

    public boolean canManageTasks(String workspaceId, String userId) {
        return canManageWorkspaceOperations(workspaceId, userId);
    }

    public boolean canManageToolkit(String workspaceId, String userId) {
        return canManageWorkspaceOperations(workspaceId, userId);
    }

    public boolean canSetPresets(String workspaceId, String userId) {
        return canManageWorkspaceOperations(workspaceId, userId);
    }

    public AuthorizationCapabilitiesDto.WorkspaceCapabilities resolveWorkspaceCapabilities(String workspaceId, String userId) {
        boolean canAccessWorkspace = canAccessWorkspace(workspaceId, userId);
        boolean canAdminWorkspace = canAdminWorkspace(workspaceId, userId);
        boolean canManageMembers = canManageMembers(workspaceId, userId);
        boolean canEditWorkspace = canEditWorkspace(workspaceId, userId);
        boolean canEditWorkspaceTextIndexDefaults = canEditWorkspaceTextIndexDefaults(workspaceId, userId);
        boolean canManageProjects = canManageProjects(workspaceId, userId);
        boolean canManageTasks = canManageTasks(workspaceId, userId);
        boolean canManageToolkit = canManageToolkit(workspaceId, userId);
        boolean canSetPresets = canSetPresets(workspaceId, userId);

        return new AuthorizationCapabilitiesDto.WorkspaceCapabilities(
                canAdminWorkspace,
                canManageMembers,
                canEditWorkspace,
                canEditWorkspaceTextIndexDefaults,
                canManageProjects,
                canManageTasks,
                canManageToolkit,
                canSetPresets
        );
    }

    public AuthorizationCapabilitiesDto.ProjectCapabilities resolveProjectCapabilities(Project project, String userId) {
        String workspaceId = project.getLibrary().getWorkspaceId();
        boolean canAccessWorkspace = canAccessWorkspace(workspaceId, userId);
        boolean canManageProjects = canManageProjects(workspaceId, userId);
        boolean canManageReleasesAndShares = canManageProjectReleasesAndShares(workspaceId, userId);

        boolean canEdit = canManageProjects && !project.isLocked();
        boolean canShare = canManageReleasesAndShares && !project.isLocked();
        boolean canDelete = canManageProjects && !project.isLocked();
        boolean canDeletePages = canManageProjects && !project.isLocked();
        boolean canUpload = canManageProjects && !project.isLocked();
        boolean canExportPackage = canAccessWorkspace;
        boolean canExecuteActions = canAccessWorkspace && !project.isLocked();
        boolean canManageActions = canManageProjects;
        boolean canChangePageState = canAccessWorkspace && !project.isLocked();

        return new AuthorizationCapabilitiesDto.ProjectCapabilities(
                canEdit,
                canShare,
                canDelete,
                canDeletePages,
                canUpload,
                canExportPackage,
                canExecuteActions,
                canManageActions,
                canChangePageState
        );
    }

    public AuthorizationCapabilitiesDto.DatasetCapabilities resolveDatasetCapabilities(String workspaceId, String userId) {
        boolean canAccessWorkspace = canAccessWorkspace(workspaceId, userId);
        boolean canManageProjects = canManageProjects(workspaceId, userId);

        return new AuthorizationCapabilitiesDto.DatasetCapabilities(
                canManageProjects,
                canManageProjects,
                canManageProjects,
                canManageProjects,
                canAccessWorkspace
        );
    }

    public AuthorizationCapabilitiesDto.TaskCapabilities resolveTaskCapabilities(Task task, String userId) {
        String workspaceId = task.getWorkspaceId();
        boolean canAccessWorkspace = canAccessWorkspace(workspaceId, userId);
        if (!canAccessWorkspace) {
            return new AuthorizationCapabilitiesDto.TaskCapabilities(false, false, false, false);
        }

        boolean canManageTasks = canManageTasks(workspaceId, userId);

        return new AuthorizationCapabilitiesDto.TaskCapabilities(
                canManageTasks,
                canManageTasks,
                canManageTasks,
                canManageTasks
        );
    }

    public AuthorizationCapabilitiesDto.ResourceCapabilities resolveWorkspaceResourceCapabilities(String workspaceId, String userId) {
        boolean canEdit = canManageToolkit(workspaceId, userId);
        boolean canShare = canEdit;
        boolean canDelete = canEdit;
        return new AuthorizationCapabilitiesDto.ResourceCapabilities(canEdit, canShare, canDelete);
    }

    private Optional<AbstractWorkspace> resolveWorkspace(String workspaceId) {
        return workspaceQueryService.findWorkspaceById(workspaceId);
    }

    private Optional<WorkspaceMember> resolveAcceptedMembership(String workspaceId, String userId) {
        return workspaceMemberRepository.findByWorkspaceIdAndUserId(workspaceId, userId)
                .filter(member -> member.getInvitationStatus() == WorkspaceMember.InvitationStatus.ACCEPTED);
    }

    private boolean isWorkspaceOwner(AbstractWorkspace workspace, String userId) {
        return workspace != null
                && workspace.getOwnerUserId() != null
                && workspace.getOwnerUserId().equals(userId);
    }
}
