package de.uniwue.zpd.dachs.larex.backend.service.security;

import de.uniwue.zpd.dachs.larex.backend.config.security.GlobalAdminService;
import de.uniwue.zpd.dachs.larex.backend.dto.AuthorizationCapabilitiesDto;
import de.uniwue.zpd.dachs.larex.backend.entity.Library;
import de.uniwue.zpd.dachs.larex.backend.entity.Project;
import de.uniwue.zpd.dachs.larex.backend.entity.Task;
import de.uniwue.zpd.dachs.larex.backend.entity.WorkspaceMember;
import de.uniwue.zpd.dachs.larex.backend.entity.workspace.AbstractWorkspace;
import de.uniwue.zpd.dachs.larex.backend.entity.workspace.PersonalWorkspace;
import de.uniwue.zpd.dachs.larex.backend.entity.workspace.TeamWorkspace;
import de.uniwue.zpd.dachs.larex.backend.repository.workspace.WorkspaceMemberRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.workspace.WorkspaceQueryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthorizationPolicyServiceTest {

    @Mock
    private WorkspaceQueryService workspaceQueryService;

    @Mock
    private WorkspaceMemberRepository workspaceMemberRepository;

    @Mock
    private GlobalAdminService globalAdminService;

    private AuthorizationPolicyService service;

    @BeforeEach
    void setUp() {
        service = new AuthorizationPolicyService(
                workspaceQueryService,
                workspaceMemberRepository,
                globalAdminService
        );
        when(globalAdminService.isGlobalAdmin()).thenReturn(false);
    }

    @Test
    void resolveWorkspaceCapabilities_globalAdmin_overrideAllWorkspaceMutations() {
        when(globalAdminService.isGlobalAdmin()).thenReturn(true);

        AuthorizationCapabilitiesDto.WorkspaceCapabilities caps = service.resolveWorkspaceCapabilities("ws-1", "any-user");

        assertTrue(caps.canAdminWorkspace());
        assertTrue(caps.canManageMembers());
        assertTrue(caps.canEditWorkspace());
        assertTrue(caps.canEditWorkspaceTextIndexDefaults());
        assertTrue(caps.canManageProjects());
        assertTrue(caps.canManageTasks());
    }

    @Test
    void workspaceRoleMatrix_teamWorkspace_adminMemberPendingNonMember_areConsistent() {
        String workspaceId = "ws-1";
        TeamWorkspace workspace = teamWorkspace(workspaceId, "owner-1");
        when(workspaceQueryService.findWorkspaceById(workspaceId)).thenReturn(Optional.of(workspace));

        when(workspaceMemberRepository.findByWorkspaceIdAndUserId(workspaceId, "admin"))
                .thenReturn(Optional.of(member("admin", WorkspaceMember.Role.ADMINISTRATOR, WorkspaceMember.InvitationStatus.ACCEPTED, workspaceId)));
        when(workspaceMemberRepository.findByWorkspaceIdAndUserId(workspaceId, "member"))
                .thenReturn(Optional.of(member("member", WorkspaceMember.Role.MEMBER, WorkspaceMember.InvitationStatus.ACCEPTED, workspaceId)));
        when(workspaceMemberRepository.findByWorkspaceIdAndUserId(workspaceId, "pending"))
                .thenReturn(Optional.of(member("pending", WorkspaceMember.Role.ADMINISTRATOR, WorkspaceMember.InvitationStatus.PENDING, workspaceId)));
        when(workspaceMemberRepository.findByWorkspaceIdAndUserId(workspaceId, "outsider"))
                .thenReturn(Optional.empty());

        AuthorizationCapabilitiesDto.WorkspaceCapabilities adminCaps = service.resolveWorkspaceCapabilities(workspaceId, "admin");
        assertTrue(adminCaps.canManageMembers());
        assertTrue(adminCaps.canManageProjects());
        assertTrue(adminCaps.canManageTasks());
        assertTrue(adminCaps.canEditWorkspace());

        AuthorizationCapabilitiesDto.WorkspaceCapabilities memberCaps = service.resolveWorkspaceCapabilities(workspaceId, "member");
        assertFalse(memberCaps.canManageMembers());
        assertTrue(memberCaps.canManageProjects());
        assertTrue(memberCaps.canManageTasks());
        assertFalse(memberCaps.canEditWorkspace());

        AuthorizationCapabilitiesDto.WorkspaceCapabilities pendingCaps = service.resolveWorkspaceCapabilities(workspaceId, "pending");
        assertFalse(pendingCaps.canManageMembers());
        assertFalse(pendingCaps.canManageProjects());
        assertFalse(pendingCaps.canManageTasks());
        assertFalse(pendingCaps.canEditWorkspace());

        AuthorizationCapabilitiesDto.WorkspaceCapabilities outsiderCaps = service.resolveWorkspaceCapabilities(workspaceId, "outsider");
        assertFalse(outsiderCaps.canManageMembers());
        assertFalse(outsiderCaps.canManageProjects());
        assertFalse(outsiderCaps.canManageTasks());
        assertFalse(outsiderCaps.canEditWorkspace());
    }

    @Test
    void workspaceRoleMatrix_personalWorkspace_ownerVsOther() {
        String workspaceId = "personal-owner-1";
        PersonalWorkspace workspace = new PersonalWorkspace("owner-1");
        workspace.setId(workspaceId);
        when(workspaceQueryService.findWorkspaceById(workspaceId)).thenReturn(Optional.of(workspace));

        AuthorizationCapabilitiesDto.WorkspaceCapabilities ownerCaps = service.resolveWorkspaceCapabilities(workspaceId, "owner-1");
        assertTrue(ownerCaps.canAdminWorkspace());
        assertTrue(ownerCaps.canManageMembers());
        assertTrue(ownerCaps.canEditWorkspace());
        assertTrue(ownerCaps.canEditWorkspaceTextIndexDefaults());
        assertTrue(ownerCaps.canManageProjects());
        assertTrue(ownerCaps.canManageTasks());

        AuthorizationCapabilitiesDto.WorkspaceCapabilities otherCaps = service.resolveWorkspaceCapabilities(workspaceId, "user-2");
        assertFalse(otherCaps.canAdminWorkspace());
        assertFalse(otherCaps.canManageMembers());
        assertFalse(otherCaps.canEditWorkspace());
        assertFalse(otherCaps.canEditWorkspaceTextIndexDefaults());
        assertFalse(otherCaps.canManageProjects());
        assertFalse(otherCaps.canManageTasks());
    }

    @Test
    void workspaceCapabilities_parity_withCoreChecks() {
        String workspaceId = "ws-2";
        TeamWorkspace workspace = teamWorkspace(workspaceId, "owner-2");
        when(workspaceQueryService.findWorkspaceById(workspaceId)).thenReturn(Optional.of(workspace));
        when(workspaceMemberRepository.findByWorkspaceIdAndUserId(workspaceId, "member"))
                .thenReturn(Optional.of(member("member", WorkspaceMember.Role.MEMBER, WorkspaceMember.InvitationStatus.ACCEPTED, workspaceId)));

        AuthorizationCapabilitiesDto.WorkspaceCapabilities caps = service.resolveWorkspaceCapabilities(workspaceId, "member");

        assertEquals(service.canAdminWorkspace(workspaceId, "member"), caps.canAdminWorkspace());
        assertEquals(service.canAdminWorkspace(workspaceId, "member"), caps.canManageMembers());
        assertEquals(service.canAdminWorkspace(workspaceId, "member"), caps.canEditWorkspace());
        assertEquals(service.canAccessWorkspace(workspaceId, "member"), caps.canManageProjects());
        assertEquals(service.canAccessWorkspace(workspaceId, "member"), caps.canManageTasks());
    }

    @Test
    void projectCapabilities_parity_withAuthorizationOutcomes_andLockedRules() {
        String workspaceId = "ws-3";
        Project unlockedProject = project(workspaceId, false);
        Project lockedProject = project(workspaceId, true);

        TeamWorkspace workspace = teamWorkspace(workspaceId, "owner-3");
        when(workspaceQueryService.findWorkspaceById(workspaceId)).thenReturn(Optional.of(workspace));
        when(workspaceMemberRepository.findByWorkspaceIdAndUserId(workspaceId, "admin"))
                .thenReturn(Optional.of(member("admin", WorkspaceMember.Role.ADMINISTRATOR, WorkspaceMember.InvitationStatus.ACCEPTED, workspaceId)));
        when(workspaceMemberRepository.findByWorkspaceIdAndUserId(workspaceId, "member"))
                .thenReturn(Optional.of(member("member", WorkspaceMember.Role.MEMBER, WorkspaceMember.InvitationStatus.ACCEPTED, workspaceId)));
        when(workspaceMemberRepository.findByWorkspaceIdAndUserId(workspaceId, "pending"))
                .thenReturn(Optional.of(member("pending", WorkspaceMember.Role.ADMINISTRATOR, WorkspaceMember.InvitationStatus.PENDING, workspaceId)));

        AuthorizationCapabilitiesDto.ProjectCapabilities memberCaps = service.resolveProjectCapabilities(unlockedProject, "member");
        assertTrue(memberCaps.canEdit());
        assertTrue(memberCaps.canShare());
        assertTrue(memberCaps.canUpload());
        assertFalse(memberCaps.canDelete());
        assertFalse(memberCaps.canDeletePages());
        assertTrue(memberCaps.canExportPackage());

        AuthorizationCapabilitiesDto.ProjectCapabilities adminLockedCaps = service.resolveProjectCapabilities(lockedProject, "admin");
        assertFalse(adminLockedCaps.canEdit());
        assertFalse(adminLockedCaps.canShare());
        assertFalse(adminLockedCaps.canUpload());
        assertFalse(adminLockedCaps.canDelete());
        assertFalse(adminLockedCaps.canDeletePages());
        assertTrue(adminLockedCaps.canExportPackage());

        AuthorizationCapabilitiesDto.ProjectCapabilities pendingCaps = service.resolveProjectCapabilities(unlockedProject, "pending");
        assertFalse(pendingCaps.canEdit());
        assertFalse(pendingCaps.canShare());
        assertFalse(pendingCaps.canUpload());
        assertFalse(pendingCaps.canDelete());
        assertFalse(pendingCaps.canDeletePages());
        assertFalse(pendingCaps.canExportPackage());
    }

    @Test
    void taskCapabilities_parity_withAuthorizationOutcomes() {
        String workspaceId = "ws-4";
        TeamWorkspace workspace = teamWorkspace(workspaceId, "owner-4");
        when(workspaceQueryService.findWorkspaceById(workspaceId)).thenReturn(Optional.of(workspace));
        when(workspaceMemberRepository.findByWorkspaceIdAndUserId(workspaceId, "admin"))
                .thenReturn(Optional.of(member("admin", WorkspaceMember.Role.ADMINISTRATOR, WorkspaceMember.InvitationStatus.ACCEPTED, workspaceId)));
        when(workspaceMemberRepository.findByWorkspaceIdAndUserId(workspaceId, "creator"))
                .thenReturn(Optional.of(member("creator", WorkspaceMember.Role.MEMBER, WorkspaceMember.InvitationStatus.ACCEPTED, workspaceId)));
        when(workspaceMemberRepository.findByWorkspaceIdAndUserId(workspaceId, "assignee"))
                .thenReturn(Optional.of(member("assignee", WorkspaceMember.Role.MEMBER, WorkspaceMember.InvitationStatus.ACCEPTED, workspaceId)));
        when(workspaceMemberRepository.findByWorkspaceIdAndUserId(workspaceId, "outsider"))
                .thenReturn(Optional.empty());

        Task task = new Task("Review", "desc", "creator", Task.TaskPriority.MEDIUM, workspaceId);
        task.setAssignedUserIds(List.of("assignee"));

        AuthorizationCapabilitiesDto.TaskCapabilities adminCaps = service.resolveTaskCapabilities(task, "admin");
        assertTrue(adminCaps.canEdit());
        assertTrue(adminCaps.canDelete());
        assertTrue(adminCaps.canAssignOthers());
        assertTrue(adminCaps.canUpdateStatus());

        AuthorizationCapabilitiesDto.TaskCapabilities creatorCaps = service.resolveTaskCapabilities(task, "creator");
        assertTrue(creatorCaps.canEdit());
        assertTrue(creatorCaps.canDelete());
        assertFalse(creatorCaps.canAssignOthers());
        assertTrue(creatorCaps.canUpdateStatus());

        AuthorizationCapabilitiesDto.TaskCapabilities assigneeCaps = service.resolveTaskCapabilities(task, "assignee");
        assertFalse(assigneeCaps.canEdit());
        assertFalse(assigneeCaps.canDelete());
        assertFalse(assigneeCaps.canAssignOthers());
        assertTrue(assigneeCaps.canUpdateStatus());

        AuthorizationCapabilitiesDto.TaskCapabilities outsiderCaps = service.resolveTaskCapabilities(task, "outsider");
        assertFalse(outsiderCaps.canEdit());
        assertFalse(outsiderCaps.canDelete());
        assertFalse(outsiderCaps.canAssignOthers());
        assertFalse(outsiderCaps.canUpdateStatus());
    }

    private TeamWorkspace teamWorkspace(String workspaceId, String ownerUserId) {
        TeamWorkspace workspace = new TeamWorkspace("Team", "Desc", ownerUserId);
        workspace.setId(workspaceId);
        return workspace;
    }

    private WorkspaceMember member(String userId,
                                   WorkspaceMember.Role role,
                                   WorkspaceMember.InvitationStatus status,
                                   String workspaceId) {
        return new WorkspaceMember(userId, role, status, workspaceId);
    }

    private Project project(String workspaceId, boolean locked) {
        Library library = new Library(workspaceId, "lib");
        Project project = new Project("Project", "desc", library);
        project.setLocked(locked);
        return project;
    }
}
