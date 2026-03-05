package de.uniwue.zpd.dachs.larex.backend.service.project;

import de.uniwue.zpd.dachs.larex.backend.entity.LabelSet;
import de.uniwue.zpd.dachs.larex.backend.entity.Library;
import de.uniwue.zpd.dachs.larex.backend.entity.Project;
import de.uniwue.zpd.dachs.larex.backend.entity.workspace.TeamWorkspace;
import de.uniwue.zpd.dachs.larex.backend.repository.codec.CodecRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.label.LabelSetRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.library.LibraryRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.project.ProjectRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.tag.TagSetRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.workspace.WorkspaceMemberRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.workspace.WorkspaceQueryService;
import de.uniwue.zpd.dachs.larex.backend.service.notification.NotificationService;
import de.uniwue.zpd.dachs.larex.backend.service.project.ProjectStarService;
import de.uniwue.zpd.dachs.larex.backend.service.workspace.WorkspaceAccessService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProjectCrudServiceTest {

    private static final String WORKSPACE_ID = "ws-1";
    private static final String USER_ID = "user-1";
    private static final String LIBRARY_ID = "lib-1";
    private static final String PROJECT_NAME = "Project One";

    @Mock
    private ProjectRepository projectRepository;

    @Mock
    private LibraryRepository libraryRepository;

    @Mock
    private CodecRepository codecRepository;

    @Mock
    private LabelSetRepository labelSetRepository;

    @Mock
    private TagSetRepository tagSetRepository;

    @Mock
    private WorkspaceMemberRepository workspaceMemberRepository;

    @Mock
    private WorkspaceQueryService workspaceQueryService;

    @Mock
    private WorkspaceAccessService workspaceAccessService;

    @Mock
    private NotificationService notificationService;

    @Mock
    private ProjectStarService projectStarService;

    @Mock
    private ProjectFileService projectFileService;

    private ProjectCrudService service;

    @BeforeEach
    void setUp() {
        service = new ProjectCrudService(
                projectRepository,
                libraryRepository,
                codecRepository,
                labelSetRepository,
                tagSetRepository,
                workspaceMemberRepository,
                workspaceQueryService,
                workspaceAccessService,
                notificationService,
                projectStarService,
                projectFileService
        );
    }

    @Test
    void createProject_withoutWorkspaceDefault_usesPageXmlStandardLabelSet() {
        TeamWorkspace workspace = new TeamWorkspace("Team", "desc", USER_ID);
        workspace.setId(WORKSPACE_ID);

        LabelSet pageXmlStandard = new LabelSet();
        pageXmlStandard.setId("labelset-page-xml");
        pageXmlStandard.setName("PAGE XML Standard");

        prepareCreateProjectBase(workspace);
        when(labelSetRepository.findByNameAndWorkspaceId("PAGE XML Standard", WORKSPACE_ID))
                .thenReturn(Optional.of(pageXmlStandard));

        Optional<Project> created = service.createProject(
                WORKSPACE_ID,
                PROJECT_NAME,
                "desc",
                null,
                null,
                null,
                null,
                null,
                null,
                USER_ID
        );

        assertTrue(created.isPresent());
        assertSame(pageXmlStandard, created.get().getLabelSet());
        verify(labelSetRepository).findByNameAndWorkspaceId("PAGE XML Standard", WORKSPACE_ID);
    }

    @Test
    void createProject_withWorkspaceDefault_doesNotFallbackToPageXmlStandard() {
        TeamWorkspace workspace = new TeamWorkspace("Team", "desc", USER_ID);
        workspace.setId(WORKSPACE_ID);

        LabelSet workspaceDefault = new LabelSet();
        workspaceDefault.setId("labelset-workspace-default");
        workspaceDefault.setName("Workspace Default");
        workspace.setLabelSet(workspaceDefault);

        prepareCreateProjectBase(workspace);

        Optional<Project> created = service.createProject(
                WORKSPACE_ID,
                PROJECT_NAME,
                "desc",
                null,
                null,
                null,
                null,
                null,
                null,
                USER_ID
        );

        assertTrue(created.isPresent());
        assertSame(workspaceDefault, created.get().getLabelSet());
        verify(labelSetRepository, never()).findByNameAndWorkspaceId(anyString(), anyString());
    }

    @Test
    void createProject_withExplicitLabelSetId_takesPrecedenceOverWorkspaceAndFallbackDefaults() {
        TeamWorkspace workspace = new TeamWorkspace("Team", "desc", USER_ID);
        workspace.setId(WORKSPACE_ID);

        LabelSet workspaceDefault = new LabelSet();
        workspaceDefault.setId("labelset-workspace-default");
        workspaceDefault.setName("Workspace Default");
        workspace.setLabelSet(workspaceDefault);

        LabelSet explicit = new LabelSet();
        explicit.setId("labelset-explicit");
        explicit.setName("Explicit");

        prepareCreateProjectBase(workspace);
        when(labelSetRepository.findById("labelset-explicit")).thenReturn(Optional.of(explicit));

        Optional<Project> created = service.createProject(
                WORKSPACE_ID,
                PROJECT_NAME,
                "desc",
                null,
                null,
                "labelset-explicit",
                null,
                null,
                null,
                USER_ID
        );

        assertTrue(created.isPresent());
        assertSame(explicit, created.get().getLabelSet());
        verify(labelSetRepository).findById("labelset-explicit");
        verify(labelSetRepository, never()).findByNameAndWorkspaceId(anyString(), anyString());
    }

    private void prepareCreateProjectBase(TeamWorkspace workspace) {
        Library library = new Library(WORKSPACE_ID, "Library");
        library.setId(LIBRARY_ID);

        when(workspaceAccessService.hasWorkspaceAccess(WORKSPACE_ID, USER_ID)).thenReturn(true);
        when(libraryRepository.findByWorkspaceId(WORKSPACE_ID)).thenReturn(Optional.of(library));
        when(projectRepository.existsByNameAndLibraryId(eq(PROJECT_NAME), eq(LIBRARY_ID))).thenReturn(false);
        when(workspaceQueryService.findWorkspaceById(WORKSPACE_ID)).thenReturn(Optional.of(workspace));
        when(workspaceMemberRepository.findByWorkspaceId(WORKSPACE_ID)).thenReturn(List.of());
        when(projectRepository.save(any(Project.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }
}
