package de.uniwue.zpd.dachs.larex.backend.service.backup;

import de.uniwue.zpd.dachs.larex.backend.config.BackupProperties;
import de.uniwue.zpd.dachs.larex.backend.entity.workspace.AbstractWorkspace;
import de.uniwue.zpd.dachs.larex.backend.entity.workspace.PersonalWorkspace;
import de.uniwue.zpd.dachs.larex.backend.entity.workspace.TeamWorkspace;
import de.uniwue.zpd.dachs.larex.backend.repository.project.ProjectRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.workspace.PersonalWorkspaceRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.workspace.TeamWorkspaceRepository;
import de.uniwue.zpd.dachs.larex.backend.service.project.ProjectPackageService;
import de.uniwue.zpd.dachs.larex.backend.service.toolkit.ToolkitPackageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BackupJobProcessorTest {

    @Mock
    private BackupJobService backupJobService;
    @Mock
    private ProjectRepository projectRepository;
    @Mock
    private PersonalWorkspaceRepository personalWorkspaceRepository;
    @Mock
    private TeamWorkspaceRepository teamWorkspaceRepository;
    @Mock
    private ArchiveIoService archiveIoService;
    @Mock
    private ProjectPackageService projectPackageService;
    @Mock
    private ToolkitPackageService toolkitPackageService;
    @Mock
    private ObjectMapper objectMapper;

    private BackupJobProcessor processor;

    @BeforeEach
    void setUp() {
        processor = new BackupJobProcessor(
                backupJobService,
                projectRepository,
                personalWorkspaceRepository,
                teamWorkspaceRepository,
                archiveIoService,
                projectPackageService,
                toolkitPackageService,
                objectMapper,
                new BackupProperties()
        );
    }

    @Test
    void loadWorkspaces_withoutSelectionReturnsAllSortedByName() {
        TeamWorkspace zeta = teamWorkspace("team-zeta", "Zeta");
        TeamWorkspace alpha = teamWorkspace("team-alpha", "Alpha");
        PersonalWorkspace personal = personalWorkspace("personal-user");
        when(personalWorkspaceRepository.findAll()).thenReturn(List.of(personal));
        when(teamWorkspaceRepository.findAll()).thenReturn(List.of(zeta, alpha));

        List<AbstractWorkspace> result = processor.loadWorkspaces(null);

        assertEquals(List.of("Alpha", "Personal Workspace", "Zeta"),
                result.stream().map(AbstractWorkspace::getName).toList());
    }

    @Test
    void loadWorkspaces_withSelectionReturnsOnlySelectedWorkspace() {
        TeamWorkspace selected = teamWorkspace("team-selected", "Selected");
        when(personalWorkspaceRepository.findById("team-selected")).thenReturn(Optional.empty());
        when(teamWorkspaceRepository.findById("team-selected")).thenReturn(Optional.of(selected));

        List<AbstractWorkspace> result = processor.loadWorkspaces("team-selected");

        assertEquals(List.of(selected), result);
        verify(personalWorkspaceRepository).findById("team-selected");
        verify(teamWorkspaceRepository).findById("team-selected");
    }

    @Test
    void loadWorkspaces_withUnknownSelectionFails() {
        when(personalWorkspaceRepository.findById("missing")).thenReturn(Optional.empty());
        when(teamWorkspaceRepository.findById("missing")).thenReturn(Optional.empty());

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> processor.loadWorkspaces("missing")
        );

        assertEquals("Workspace not found: missing", error.getMessage());
    }

    private TeamWorkspace teamWorkspace(String id, String name) {
        TeamWorkspace workspace = new TeamWorkspace(name, null, "owner");
        workspace.setId(id);
        return workspace;
    }

    private PersonalWorkspace personalWorkspace(String id) {
        PersonalWorkspace workspace = new PersonalWorkspace("user");
        workspace.setId(id);
        return workspace;
    }
}
