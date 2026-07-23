package de.uniwue.zpd.dachs.larex.backend.service.project;

import de.uniwue.zpd.dachs.larex.backend.entity.Codec;
import de.uniwue.zpd.dachs.larex.backend.entity.ControlledDictionary;
import de.uniwue.zpd.dachs.larex.backend.entity.LabelSet;
import de.uniwue.zpd.dachs.larex.backend.entity.Library;
import de.uniwue.zpd.dachs.larex.backend.entity.Project;
import de.uniwue.zpd.dachs.larex.backend.entity.VirtualKeyboard;
import de.uniwue.zpd.dachs.larex.backend.entity.workspace.TeamWorkspace;
import de.uniwue.zpd.dachs.larex.backend.exception.ResourceNotFoundException;
import de.uniwue.zpd.dachs.larex.backend.repository.codec.CodecRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.dictionary.ControlledDictionaryRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.label.LabelSetRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.library.LibraryRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.normalization.NormalizationProfileRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.keyboard.VirtualKeyboardRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.project.ProjectRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.tag.TagSetRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.validation.ValidationRulesetRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.workspace.WorkspaceMemberRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.workspace.WorkspaceQueryService;
import de.uniwue.zpd.dachs.larex.backend.service.notification.NotificationService;
import de.uniwue.zpd.dachs.larex.backend.service.project.ProjectStarService;
import de.uniwue.zpd.dachs.larex.backend.service.storage.WorkspaceQuotaRefreshService;
import de.uniwue.zpd.dachs.larex.backend.service.workspace.WorkspaceAccessService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
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
    private ControlledDictionaryRepository dictionaryRepository;

    @Mock
    private LabelSetRepository labelSetRepository;

    @Mock
    private TagSetRepository tagSetRepository;
    @Mock
    private NormalizationProfileRepository normalizationProfileRepository;
    @Mock
    private ValidationRulesetRepository validationRulesetRepository;
    @Mock
    private VirtualKeyboardRepository virtualKeyboardRepository;

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
    @Mock
    private WorkspaceQuotaRefreshService workspaceQuotaRefreshService;

    private ProjectCrudService service;

    @BeforeEach
    void setUp() {
        service = new ProjectCrudService(
                projectRepository,
                libraryRepository,
                codecRepository,
                dictionaryRepository,
                labelSetRepository,
                tagSetRepository,
                normalizationProfileRepository,
                validationRulesetRepository,
                virtualKeyboardRepository,
                workspaceMemberRepository,
                workspaceQueryService,
                workspaceAccessService,
                notificationService,
                projectStarService,
                projectFileService,
                workspaceQuotaRefreshService
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
                null,
                null,
                null,
                null,
                null,
                null,
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
                null,
                null,
                null,
                null,
                null,
                null,
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
        when(labelSetRepository.findByIdAndWorkspaceId("labelset-explicit", WORKSPACE_ID)).thenReturn(Optional.of(explicit));

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
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                USER_ID
        );

        assertTrue(created.isPresent());
        assertSame(explicit, created.get().getLabelSet());
        verify(labelSetRepository).findByIdAndWorkspaceId("labelset-explicit", WORKSPACE_ID);
        verify(labelSetRepository, never()).findByNameAndWorkspaceId(anyString(), anyString());
    }

    @Test
    void getWorkspaceProjects_returnsProjectsWhenWorkspaceAccessIsGranted() {
        List<Project> projects = List.of(projectInWorkspace());

        when(workspaceAccessService.hasWorkspaceAccess(WORKSPACE_ID, USER_ID)).thenReturn(true);
        when(projectRepository.findByLibraryWorkspaceId(WORKSPACE_ID)).thenReturn(projects);

        List<Project> result = service.getWorkspaceProjects(WORKSPACE_ID, USER_ID);

        assertSame(projects, result);
        verify(projectRepository).findByLibraryWorkspaceId(WORKSPACE_ID);
    }

    @Test
    void deleteProject_schedulesQuotaRefresh() {
        Library library = new Library(WORKSPACE_ID, "Library");
        library.setId(LIBRARY_ID);

        Project project = new Project(PROJECT_NAME, "desc", library);
        project.setId("project-1");

        when(projectRepository.findWithAssociationsById("project-1")).thenReturn(Optional.of(project));
        when(workspaceAccessService.hasWorkspaceAccess(WORKSPACE_ID, USER_ID)).thenReturn(true);
        when(workspaceAccessService.canManageProjects(WORKSPACE_ID, USER_ID)).thenReturn(true);
        when(workspaceMemberRepository.findByWorkspaceId(WORKSPACE_ID)).thenReturn(List.of());

        assertTrue(service.deleteProject("project-1", USER_ID));

        verify(projectFileService).deleteProjectFiles(project);
        verify(projectRepository).delete(project);
        verify(workspaceQuotaRefreshService).scheduleUsageRefresh(WORKSPACE_ID);
    }

    @Test
    void updateToolkitPresets_managerCanSetResourcesAndLocks() {
        Project project = projectInWorkspace();
        Codec codec = new Codec();
        codec.setId("codec-1");
        ControlledDictionary dictionary = new ControlledDictionary();
        dictionary.setId("dictionary-1");
        VirtualKeyboard keyboard = new VirtualKeyboard();
        keyboard.setId("keyboard-1");

        when(projectRepository.findByIdAndLibraryWorkspaceIdForUpdate("project-1", WORKSPACE_ID))
                .thenReturn(Optional.of(project));
        when(codecRepository.findByIdAndLibraryWorkspaceId("codec-1", WORKSPACE_ID)).thenReturn(Optional.of(codec));
        when(dictionaryRepository.findByIdAndLibraryWorkspaceId("dictionary-1", WORKSPACE_ID)).thenReturn(Optional.of(dictionary));
        when(virtualKeyboardRepository.findByIdAndWorkspaceId("keyboard-1", WORKSPACE_ID)).thenReturn(Optional.of(keyboard));
        when(projectRepository.save(any(Project.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Optional<Project> updated = service.updateToolkitPresets(
                WORKSPACE_ID,
                "project-1",
                "codec-1",
                null,
                "dictionary-1",
                null,
                null,
                null,
                "keyboard-1",
                false,
                false,
                false,
                null,
                null,
                null,
                null,
                USER_ID
        );

        assertTrue(updated.isPresent());
        assertSame(codec, updated.get().getCodec());
        assertSame(dictionary, updated.get().getDictionary());
        assertSame(keyboard, updated.get().getVirtualKeyboard());
        assertTrue(!updated.get().isAllowCodecOverride());
        assertTrue(!updated.get().isAllowDictionaryOverride());
        assertTrue(!updated.get().isAllowVirtualKeyboardOverride());
    }

    @Test
    void updateToolkitPresets_editorCannotSaveDefaults() {
        doThrow(new SecurityException("Preset management access required"))
                .when(workspaceAccessService).requireSetPresetsAccess(WORKSPACE_ID, USER_ID);

        assertThrows(SecurityException.class, () -> service.updateToolkitPresets(
                WORKSPACE_ID,
                "project-1",
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                USER_ID
        ));

        verify(projectRepository, never()).findByIdAndLibraryWorkspaceIdForUpdate(anyString(), anyString());
    }

    @Test
    void updateToolkitPresets_rejectsResourceOutsideWorkspace() {
        Project project = projectInWorkspace();
        when(projectRepository.findByIdAndLibraryWorkspaceIdForUpdate("project-1", WORKSPACE_ID))
                .thenReturn(Optional.of(project));
        when(codecRepository.findByIdAndLibraryWorkspaceId("codec-other", WORKSPACE_ID)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> service.updateToolkitPresets(
                WORKSPACE_ID,
                "project-1",
                "codec-other",
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                USER_ID
        ));
    }

    private void prepareCreateProjectBase(TeamWorkspace workspace) {
        Library library = new Library(WORKSPACE_ID, "Library");
        library.setId(LIBRARY_ID);

        when(libraryRepository.findByWorkspaceId(WORKSPACE_ID)).thenReturn(Optional.of(library));
        when(projectRepository.existsByNameAndLibraryId(eq(PROJECT_NAME), eq(LIBRARY_ID))).thenReturn(false);
        when(workspaceQueryService.findWorkspaceById(WORKSPACE_ID)).thenReturn(Optional.of(workspace));
        when(workspaceMemberRepository.findByWorkspaceId(WORKSPACE_ID)).thenReturn(List.of());
        when(projectRepository.save(any(Project.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    private Project projectInWorkspace() {
        Library library = new Library(WORKSPACE_ID, "Library");
        library.setId(LIBRARY_ID);
        Project project = new Project(PROJECT_NAME, "desc", library);
        project.setId("project-1");
        return project;
    }
}
