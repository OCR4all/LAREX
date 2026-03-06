package de.uniwue.zpd.dachs.larex.backend.service.security;

import de.uniwue.zpd.dachs.larex.backend.config.security.GlobalAdminService;
import de.uniwue.zpd.dachs.larex.backend.dto.AuthorizationCapabilitiesDto;
import de.uniwue.zpd.dachs.larex.backend.entity.Library;
import de.uniwue.zpd.dachs.larex.backend.entity.Project;
import de.uniwue.zpd.dachs.larex.backend.entity.Task;
import de.uniwue.zpd.dachs.larex.backend.entity.WorkspaceMember;
import de.uniwue.zpd.dachs.larex.backend.entity.workspace.PersonalWorkspace;
import de.uniwue.zpd.dachs.larex.backend.entity.workspace.TeamWorkspace;
import de.uniwue.zpd.dachs.larex.backend.repository.workspace.WorkspaceMemberRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.workspace.WorkspaceQueryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.lenient;
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
        lenient().when(globalAdminService.isGlobalAdmin()).thenReturn(false);
        lenient().when(globalAdminService.isGlobalCurator()).thenReturn(false);
        lenient().when(globalAdminService.canCreateWorkspaces()).thenReturn(false);
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
        assertTrue(caps.canManageUtilities());
        assertTrue(caps.canSetPresets());
    }

    @Test
    void workspaceRoleMatrix_teamWorkspace_ownerCuratorEditorPendingNonMember_areConsistent() {
        String workspaceId = "ws-1";
        TeamWorkspace workspace = teamWorkspace(workspaceId, "owner");
        when(workspaceQueryService.findWorkspaceById(workspaceId)).thenReturn(Optional.of(workspace));

        when(workspaceMemberRepository.findByWorkspaceIdAndUserId(workspaceId, "curator"))
                .thenReturn(Optional.of(member("curator", WorkspaceMember.Role.CURATOR, WorkspaceMember.InvitationStatus.ACCEPTED, workspaceId)));
        when(workspaceMemberRepository.findByWorkspaceIdAndUserId(workspaceId, "editor"))
                .thenReturn(Optional.of(member("editor", WorkspaceMember.Role.EDITOR, WorkspaceMember.InvitationStatus.ACCEPTED, workspaceId)));
        when(workspaceMemberRepository.findByWorkspaceIdAndUserId(workspaceId, "pending"))
                .thenReturn(Optional.of(member("pending", WorkspaceMember.Role.CURATOR, WorkspaceMember.InvitationStatus.PENDING, workspaceId)));
        when(workspaceMemberRepository.findByWorkspaceIdAndUserId(workspaceId, "outsider"))
                .thenReturn(Optional.empty());

        AuthorizationCapabilitiesDto.WorkspaceCapabilities ownerCaps = service.resolveWorkspaceCapabilities(workspaceId, "owner");
        assertTrue(ownerCaps.canAdminWorkspace());
        assertTrue(ownerCaps.canManageMembers());
        assertTrue(ownerCaps.canEditWorkspace());
        assertTrue(ownerCaps.canManageProjects());
        assertTrue(ownerCaps.canManageTasks());
        assertTrue(ownerCaps.canManageUtilities());
        assertTrue(ownerCaps.canSetPresets());

        AuthorizationCapabilitiesDto.WorkspaceCapabilities curatorCaps = service.resolveWorkspaceCapabilities(workspaceId, "curator");
        assertFalse(curatorCaps.canAdminWorkspace());
        assertTrue(curatorCaps.canManageMembers());
        assertFalse(curatorCaps.canEditWorkspace());
        assertTrue(curatorCaps.canEditWorkspaceTextIndexDefaults());
        assertTrue(curatorCaps.canManageProjects());
        assertTrue(curatorCaps.canManageTasks());
        assertTrue(curatorCaps.canManageUtilities());
        assertTrue(curatorCaps.canSetPresets());

        AuthorizationCapabilitiesDto.WorkspaceCapabilities editorCaps = service.resolveWorkspaceCapabilities(workspaceId, "editor");
        assertFalse(editorCaps.canAdminWorkspace());
        assertFalse(editorCaps.canManageMembers());
        assertFalse(editorCaps.canEditWorkspace());
        assertFalse(editorCaps.canEditWorkspaceTextIndexDefaults());
        assertFalse(editorCaps.canManageProjects());
        assertFalse(editorCaps.canManageTasks());
        assertFalse(editorCaps.canManageUtilities());
        assertFalse(editorCaps.canSetPresets());

        AuthorizationCapabilitiesDto.WorkspaceCapabilities pendingCaps = service.resolveWorkspaceCapabilities(workspaceId, "pending");
        assertFalse(pendingCaps.canAdminWorkspace());
        assertFalse(pendingCaps.canManageMembers());
        assertFalse(pendingCaps.canEditWorkspace());
        assertFalse(pendingCaps.canManageProjects());
        assertFalse(pendingCaps.canManageTasks());
        assertFalse(pendingCaps.canManageUtilities());
        assertFalse(pendingCaps.canSetPresets());

        AuthorizationCapabilitiesDto.WorkspaceCapabilities outsiderCaps = service.resolveWorkspaceCapabilities(workspaceId, "outsider");
        assertFalse(outsiderCaps.canAdminWorkspace());
        assertFalse(outsiderCaps.canManageMembers());
        assertFalse(outsiderCaps.canEditWorkspace());
        assertFalse(outsiderCaps.canManageProjects());
        assertFalse(outsiderCaps.canManageTasks());
        assertFalse(outsiderCaps.canManageUtilities());
        assertFalse(outsiderCaps.canSetPresets());
    }

    @Test
    void workspaceRoleMatrix_personalWorkspace_ownerVsOther() {
        String workspaceId = "personal-owner-1";
        PersonalWorkspace workspace = new PersonalWorkspace("owner");
        workspace.setId(workspaceId);
        when(workspaceQueryService.findWorkspaceById(workspaceId)).thenReturn(Optional.of(workspace));

        AuthorizationCapabilitiesDto.WorkspaceCapabilities ownerCaps = service.resolveWorkspaceCapabilities(workspaceId, "owner");
        assertTrue(ownerCaps.canAdminWorkspace());
        assertTrue(ownerCaps.canManageMembers());
        assertTrue(ownerCaps.canEditWorkspace());
        assertTrue(ownerCaps.canEditWorkspaceTextIndexDefaults());
        assertTrue(ownerCaps.canManageProjects());
        assertTrue(ownerCaps.canManageTasks());
        assertTrue(ownerCaps.canManageUtilities());
        assertTrue(ownerCaps.canSetPresets());

        AuthorizationCapabilitiesDto.WorkspaceCapabilities otherCaps = service.resolveWorkspaceCapabilities(workspaceId, "other");
        assertFalse(otherCaps.canAdminWorkspace());
        assertFalse(otherCaps.canManageMembers());
        assertFalse(otherCaps.canEditWorkspace());
        assertFalse(otherCaps.canEditWorkspaceTextIndexDefaults());
        assertFalse(otherCaps.canManageProjects());
        assertFalse(otherCaps.canManageTasks());
        assertFalse(otherCaps.canManageUtilities());
        assertFalse(otherCaps.canSetPresets());
    }

    @Test
    void workspaceCapabilities_parity_withCoreChecks() {
        String workspaceId = "ws-2";
        TeamWorkspace workspace = teamWorkspace(workspaceId, "owner");
        when(workspaceQueryService.findWorkspaceById(workspaceId)).thenReturn(Optional.of(workspace));
        when(workspaceMemberRepository.findByWorkspaceIdAndUserId(workspaceId, "curator"))
                .thenReturn(Optional.of(member("curator", WorkspaceMember.Role.CURATOR, WorkspaceMember.InvitationStatus.ACCEPTED, workspaceId)));

        AuthorizationCapabilitiesDto.WorkspaceCapabilities caps = service.resolveWorkspaceCapabilities(workspaceId, "curator");

        assertEquals(service.canAdminWorkspace(workspaceId, "curator"), caps.canAdminWorkspace());
        assertEquals(service.canManageMembers(workspaceId, "curator"), caps.canManageMembers());
        assertEquals(service.canEditWorkspace(workspaceId, "curator"), caps.canEditWorkspace());
        assertEquals(service.canEditWorkspaceTextIndexDefaults(workspaceId, "curator"), caps.canEditWorkspaceTextIndexDefaults());
        assertEquals(service.canManageProjects(workspaceId, "curator"), caps.canManageProjects());
        assertEquals(service.canManageTasks(workspaceId, "curator"), caps.canManageTasks());
        assertEquals(service.canManageUtilities(workspaceId, "curator"), caps.canManageUtilities());
        assertEquals(service.canSetPresets(workspaceId, "curator"), caps.canSetPresets());
    }

    @Test
    void projectCapabilities_followOwnerCuratorEditorRules_andLockedRules() {
        String workspaceId = "ws-3";
        Project unlockedProject = project(workspaceId, false);
        Project lockedProject = project(workspaceId, true);

        TeamWorkspace workspace = teamWorkspace(workspaceId, "owner");
        when(workspaceQueryService.findWorkspaceById(workspaceId)).thenReturn(Optional.of(workspace));
        when(workspaceMemberRepository.findByWorkspaceIdAndUserId(workspaceId, "curator"))
                .thenReturn(Optional.of(member("curator", WorkspaceMember.Role.CURATOR, WorkspaceMember.InvitationStatus.ACCEPTED, workspaceId)));
        when(workspaceMemberRepository.findByWorkspaceIdAndUserId(workspaceId, "editor"))
                .thenReturn(Optional.of(member("editor", WorkspaceMember.Role.EDITOR, WorkspaceMember.InvitationStatus.ACCEPTED, workspaceId)));
        when(workspaceMemberRepository.findByWorkspaceIdAndUserId(workspaceId, "pending"))
                .thenReturn(Optional.of(member("pending", WorkspaceMember.Role.CURATOR, WorkspaceMember.InvitationStatus.PENDING, workspaceId)));

        AuthorizationCapabilitiesDto.ProjectCapabilities curatorCaps = service.resolveProjectCapabilities(unlockedProject, "curator");
        assertTrue(curatorCaps.canEdit());
        assertTrue(curatorCaps.canShare());
        assertTrue(curatorCaps.canDelete());
        assertTrue(curatorCaps.canDeletePages());
        assertTrue(curatorCaps.canUpload());
        assertTrue(curatorCaps.canExportPackage());

        AuthorizationCapabilitiesDto.ProjectCapabilities editorCaps = service.resolveProjectCapabilities(unlockedProject, "editor");
        assertFalse(editorCaps.canEdit());
        assertFalse(editorCaps.canShare());
        assertFalse(editorCaps.canDelete());
        assertFalse(editorCaps.canDeletePages());
        assertFalse(editorCaps.canUpload());
        assertTrue(editorCaps.canExportPackage());

        AuthorizationCapabilitiesDto.ProjectCapabilities pendingCaps = service.resolveProjectCapabilities(unlockedProject, "pending");
        assertFalse(pendingCaps.canEdit());
        assertFalse(pendingCaps.canShare());
        assertFalse(pendingCaps.canDelete());
        assertFalse(pendingCaps.canDeletePages());
        assertFalse(pendingCaps.canUpload());
        assertFalse(pendingCaps.canExportPackage());

        AuthorizationCapabilitiesDto.ProjectCapabilities curatorLockedCaps = service.resolveProjectCapabilities(lockedProject, "curator");
        assertFalse(curatorLockedCaps.canEdit());
        assertFalse(curatorLockedCaps.canShare());
        assertFalse(curatorLockedCaps.canDelete());
        assertFalse(curatorLockedCaps.canDeletePages());
        assertFalse(curatorLockedCaps.canUpload());
        assertTrue(curatorLockedCaps.canExportPackage());
    }

    @Test
    void taskCapabilities_followManagerVsEditorRules() {
        String workspaceId = "ws-4";
        TeamWorkspace workspace = teamWorkspace(workspaceId, "owner");
        when(workspaceQueryService.findWorkspaceById(workspaceId)).thenReturn(Optional.of(workspace));
        when(workspaceMemberRepository.findByWorkspaceIdAndUserId(workspaceId, "curator"))
                .thenReturn(Optional.of(member("curator", WorkspaceMember.Role.CURATOR, WorkspaceMember.InvitationStatus.ACCEPTED, workspaceId)));
        when(workspaceMemberRepository.findByWorkspaceIdAndUserId(workspaceId, "editor"))
                .thenReturn(Optional.of(member("editor", WorkspaceMember.Role.EDITOR, WorkspaceMember.InvitationStatus.ACCEPTED, workspaceId)));
        when(workspaceMemberRepository.findByWorkspaceIdAndUserId(workspaceId, "outsider"))
                .thenReturn(Optional.empty());

        Task task = new Task("Review", "desc", "editor", Task.TaskPriority.MEDIUM, workspaceId);

        AuthorizationCapabilitiesDto.TaskCapabilities curatorCaps = service.resolveTaskCapabilities(task, "curator");
        assertTrue(curatorCaps.canEdit());
        assertTrue(curatorCaps.canDelete());
        assertTrue(curatorCaps.canAssignOthers());
        assertTrue(curatorCaps.canUpdateStatus());

        AuthorizationCapabilitiesDto.TaskCapabilities editorCaps = service.resolveTaskCapabilities(task, "editor");
        assertFalse(editorCaps.canEdit());
        assertFalse(editorCaps.canDelete());
        assertFalse(editorCaps.canAssignOthers());
        assertFalse(editorCaps.canUpdateStatus());

        AuthorizationCapabilitiesDto.TaskCapabilities outsiderCaps = service.resolveTaskCapabilities(task, "outsider");
        assertFalse(outsiderCaps.canEdit());
        assertFalse(outsiderCaps.canDelete());
        assertFalse(outsiderCaps.canAssignOthers());
        assertFalse(outsiderCaps.canUpdateStatus());
    }

    @Test
    void canCreateTeamWorkspace_usesGlobalRoleDecision() {
        when(globalAdminService.canCreateWorkspaces()).thenReturn(true);
        assertTrue(service.canCreateTeamWorkspace());

        when(globalAdminService.canCreateWorkspaces()).thenReturn(false);
        assertFalse(service.canCreateTeamWorkspace());
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
