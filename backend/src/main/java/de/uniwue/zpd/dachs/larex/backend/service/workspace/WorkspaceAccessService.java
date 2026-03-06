package de.uniwue.zpd.dachs.larex.backend.service.workspace;

import de.uniwue.zpd.dachs.larex.backend.service.security.AuthorizationPolicyService;
import org.springframework.stereotype.Service;

@Service
public class WorkspaceAccessService {

    private final AuthorizationPolicyService authorizationPolicyService;

    public WorkspaceAccessService(AuthorizationPolicyService authorizationPolicyService) {
        this.authorizationPolicyService = authorizationPolicyService;
    }

    public boolean hasWorkspaceAccess(String workspaceId, String userId) {
        return authorizationPolicyService.canAccessWorkspace(workspaceId, userId);
    }

    public void requireWorkspaceAccess(String workspaceId, String userId) {
        if (!hasWorkspaceAccess(workspaceId, userId)) {
            throw new SecurityException("Access denied to workspace: " + workspaceId);
        }
    }

    public void requireAdminAccess(String workspaceId, String userId) {
        if (!isUserAdministrator(workspaceId, userId)) {
            throw new SecurityException("Admin access required for workspace: " + workspaceId);
        }
    }

    public boolean isWorkspaceOwner(String workspaceId, String userId) {
        return authorizationPolicyService.canAdminWorkspace(workspaceId, userId);
    }

    public void requireWorkspaceOwnerAccess(String workspaceId, String userId) {
        if (!isWorkspaceOwner(workspaceId, userId)) {
            throw new SecurityException("Workspace owner access required for workspace: " + workspaceId);
        }
    }

    public void requireCreateWorkspaceAccess(String userId) {
        if (!authorizationPolicyService.canCreateTeamWorkspace()) {
            throw new SecurityException("Creating team workspaces requires GLOBAL_ADMIN or GLOBAL_CURATOR.");
        }
    }

    public boolean isUserAdministrator(String workspaceId, String userId) {
        return authorizationPolicyService.canManageWorkspaceOperations(workspaceId, userId);
    }

    public boolean canManageProjects(String workspaceId, String userId) {
        return authorizationPolicyService.canManageProjects(workspaceId, userId);
    }

    public void requireManageProjectsAccess(String workspaceId, String userId) {
        if (!canManageProjects(workspaceId, userId)) {
            throw new SecurityException("Project management access required for workspace: " + workspaceId);
        }
    }

    public boolean canManageTasks(String workspaceId, String userId) {
        return authorizationPolicyService.canManageTasks(workspaceId, userId);
    }

    public void requireManageTasksAccess(String workspaceId, String userId) {
        if (!canManageTasks(workspaceId, userId)) {
            throw new SecurityException("Task management access required for workspace: " + workspaceId);
        }
    }

    public boolean canManageUtilities(String workspaceId, String userId) {
        return authorizationPolicyService.canManageUtilities(workspaceId, userId);
    }

    public void requireManageUtilitiesAccess(String workspaceId, String userId) {
        if (!canManageUtilities(workspaceId, userId)) {
            throw new SecurityException("Utility management access required for workspace: " + workspaceId);
        }
    }

    public boolean canSetPresets(String workspaceId, String userId) {
        return authorizationPolicyService.canSetPresets(workspaceId, userId);
    }

    public void requireSetPresetsAccess(String workspaceId, String userId) {
        if (!canSetPresets(workspaceId, userId)) {
            throw new SecurityException("Preset management access required for workspace: " + workspaceId);
        }
    }
}
