package de.uniwue.zpd.dachs.larex.backend.service.workspace;

import de.uniwue.zpd.dachs.larex.backend.entity.workspace.AbstractWorkspace;
import de.uniwue.zpd.dachs.larex.backend.repository.workspace.WorkspaceQueryService;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * Base service for common workspace operations across all types
 */
@Transactional
public abstract class AbstractWorkspaceService {
    
    protected final WorkspaceQueryService workspaceQueryService;

    protected AbstractWorkspaceService(WorkspaceQueryService workspaceQueryService) {
        this.workspaceQueryService = workspaceQueryService;
    }

    /**
     * Get all workspaces (personal and team) for a user
     */
    public List<AbstractWorkspace> getAllWorkspacesForUser(String userId) {
        return workspaceQueryService.findAllWorkspacesForUser(userId);
    }

    /**
     * Get workspace by ID with access check
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
     * Check if user has access to workspace
     */
    protected boolean hasAccessToWorkspace(AbstractWorkspace workspace, String userId) {
        if (workspace.getOwnerUserId().equals(userId)) {
            return true;
        }
        
        // For team workspaces, check membership
        if (!workspace.isPersonal()) {
            return hasTeamWorkspaceMembership(workspace.getId(), userId);
        }
        
        return false;
    }

    /**
     * Abstract method to check team workspace membership
     */
    protected abstract boolean hasTeamWorkspaceMembership(String workspaceId, String userId);
}