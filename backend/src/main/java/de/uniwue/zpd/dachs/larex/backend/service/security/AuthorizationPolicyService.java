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

    public boolean canAccessWorkspace(String workspaceId, String userId) {
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
        return memberOpt.isPresent()
                && memberOpt.get().getInvitationStatus() == WorkspaceMember.InvitationStatus.ACCEPTED;
    }

    public boolean canAdminWorkspace(String workspaceId, String userId) {
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
        return memberOpt.isPresent()
                && memberOpt.get().getRole() == WorkspaceMember.Role.ADMINISTRATOR
                && memberOpt.get().getInvitationStatus() == WorkspaceMember.InvitationStatus.ACCEPTED;
    }

    public boolean canEditWorkspaceTextIndexDefaults(String workspaceId, String userId) {
        if (globalAdminService.isGlobalAdmin()) {
            return true;
        }
        Optional<AbstractWorkspace> workspaceOpt = workspaceQueryService.findWorkspaceById(workspaceId);
        return workspaceOpt.filter(workspace -> workspace.getOwnerUserId().equals(userId)).isPresent();
    }

    public AuthorizationCapabilitiesDto.WorkspaceCapabilities resolveWorkspaceCapabilities(String workspaceId, String userId) {
        boolean canAccessWorkspace = canAccessWorkspace(workspaceId, userId);
        boolean canAdminWorkspace = canAdminWorkspace(workspaceId, userId);
        boolean canEditWorkspaceTextIndexDefaults = canEditWorkspaceTextIndexDefaults(workspaceId, userId);

        return new AuthorizationCapabilitiesDto.WorkspaceCapabilities(
                canAdminWorkspace,
                canAdminWorkspace,
                canAdminWorkspace,
                canEditWorkspaceTextIndexDefaults,
                canAccessWorkspace,
                canAccessWorkspace
        );
    }

    public AuthorizationCapabilitiesDto.ProjectCapabilities resolveProjectCapabilities(Project project, String userId) {
        String workspaceId = project.getLibrary().getWorkspaceId();
        boolean canAccessWorkspace = canAccessWorkspace(workspaceId, userId);
        boolean canAdminWorkspace = canAdminWorkspace(workspaceId, userId);

        boolean canEdit = canAccessWorkspace && !project.isLocked();
        boolean canShare = canAccessWorkspace && !project.isLocked();
        boolean canDelete = canAdminWorkspace && !project.isLocked();
        boolean canDeletePages = canAdminWorkspace && !project.isLocked();
        boolean canUpload = canAccessWorkspace && !project.isLocked();
        boolean canExportPackage = canAccessWorkspace;

        return new AuthorizationCapabilitiesDto.ProjectCapabilities(
                canEdit,
                canShare,
                canDelete,
                canDeletePages,
                canUpload,
                canExportPackage
        );
    }

    public AuthorizationCapabilitiesDto.TaskCapabilities resolveTaskCapabilities(Task task, String userId) {
        String workspaceId = task.getWorkspaceId();
        boolean canAccessWorkspace = canAccessWorkspace(workspaceId, userId);
        if (!canAccessWorkspace) {
            return new AuthorizationCapabilitiesDto.TaskCapabilities(false, false, false, false);
        }

        boolean canAdminWorkspace = canAdminWorkspace(workspaceId, userId);
        boolean isCreator = task.getCreatedByUserId() != null && task.getCreatedByUserId().equals(userId);
        boolean isAssignee = task.getAssignedUserIds() != null && task.getAssignedUserIds().contains(userId);

        boolean canEdit = canAdminWorkspace || isCreator;
        boolean canDelete = canAdminWorkspace || isCreator;
        boolean canAssignOthers = canAdminWorkspace;
        boolean canUpdateStatus = canAdminWorkspace || isCreator || isAssignee;

        return new AuthorizationCapabilitiesDto.TaskCapabilities(
                canEdit,
                canDelete,
                canAssignOthers,
                canUpdateStatus
        );
    }

    public AuthorizationCapabilitiesDto.ResourceCapabilities resolveWorkspaceResourceCapabilities(String workspaceId, String userId) {
        boolean canEdit = canAccessWorkspace(workspaceId, userId);
        boolean canDelete = canEdit;
        return new AuthorizationCapabilitiesDto.ResourceCapabilities(canEdit, canDelete);
    }
}
