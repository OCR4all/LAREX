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

    public boolean isUserAdministrator(String workspaceId, String userId) {
        return authorizationPolicyService.canAdminWorkspace(workspaceId, userId);
    }
}
