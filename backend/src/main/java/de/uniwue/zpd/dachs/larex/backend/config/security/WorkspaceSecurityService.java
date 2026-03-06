package de.uniwue.zpd.dachs.larex.backend.config.security;

import de.uniwue.zpd.dachs.larex.backend.service.workspace.WorkspaceAccessService;
import org.springframework.stereotype.Component;

@Component("workspaceSecurity")
public class WorkspaceSecurityService {

    private final WorkspaceAccessService workspaceAccessService;

    public WorkspaceSecurityService(WorkspaceAccessService workspaceAccessService) {
        this.workspaceAccessService = workspaceAccessService;
    }

    public boolean hasAccess(String workspaceId, String userId) {
        return workspaceAccessService.hasWorkspaceAccess(workspaceId, userId);
    }

    public boolean isAdmin(String workspaceId, String userId) {
        return workspaceAccessService.isUserAdministrator(workspaceId, userId);
    }

    public boolean isOwner(String workspaceId, String userId) {
        return workspaceAccessService.isWorkspaceOwner(workspaceId, userId);
    }
}
