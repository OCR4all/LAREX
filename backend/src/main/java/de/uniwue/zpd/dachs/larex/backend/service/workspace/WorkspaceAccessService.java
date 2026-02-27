package de.uniwue.zpd.dachs.larex.backend.service.workspace;

import de.uniwue.zpd.dachs.larex.backend.config.security.GlobalAdminService;
import de.uniwue.zpd.dachs.larex.backend.entity.WorkspaceMember;
import de.uniwue.zpd.dachs.larex.backend.entity.workspace.AbstractWorkspace;
import de.uniwue.zpd.dachs.larex.backend.repository.WorkspaceMemberRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.workspace.WorkspaceQueryService;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class WorkspaceAccessService {

    private final WorkspaceQueryService workspaceQueryService;
    private final WorkspaceMemberRepository workspaceMemberRepository;
    private final GlobalAdminService globalAdminService;

    public WorkspaceAccessService(WorkspaceQueryService workspaceQueryService,
                                  WorkspaceMemberRepository workspaceMemberRepository,
                                  GlobalAdminService globalAdminService) {
        this.workspaceQueryService = workspaceQueryService;
        this.workspaceMemberRepository = workspaceMemberRepository;
        this.globalAdminService = globalAdminService;
    }

    public boolean hasWorkspaceAccess(String workspaceId, String userId) {
        if (globalAdminService.isGlobalAdmin()) {
            return true;
        }

        Optional<AbstractWorkspace> workspaceOpt = workspaceQueryService.findWorkspaceById(workspaceId);
        if (workspaceOpt.isEmpty()) {
            return false;
        }

        AbstractWorkspace workspace = workspaceOpt.get();

        if (workspace.isPersonal()) {
            return workspace.getOwnerUserId().equals(userId);
        }

        Optional<WorkspaceMember> memberOpt = workspaceMemberRepository.findByWorkspaceIdAndUserId(workspaceId, userId);
        return memberOpt.isPresent() && memberOpt.get().getInvitationStatus() == WorkspaceMember.InvitationStatus.ACCEPTED;
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
        if (globalAdminService.isGlobalAdmin()) {
            return true;
        }

        Optional<AbstractWorkspace> workspaceOpt = workspaceQueryService.findWorkspaceById(workspaceId);
        if (workspaceOpt.isEmpty()) {
            return false;
        }

        AbstractWorkspace workspace = workspaceOpt.get();

        if (workspace.isPersonal()) {
            return workspace.getOwnerUserId().equals(userId);
        }

        Optional<WorkspaceMember> memberOpt = workspaceMemberRepository.findByWorkspaceIdAndUserId(workspaceId, userId);
        return memberOpt.isPresent() &&
                memberOpt.get().getRole() == WorkspaceMember.Role.ADMINISTRATOR &&
                memberOpt.get().getInvitationStatus() == WorkspaceMember.InvitationStatus.ACCEPTED;
    }
}
