package de.uniwue.zpd.dachs.larex.backend;

import de.uniwue.zpd.dachs.larex.backend.entity.Library;
import de.uniwue.zpd.dachs.larex.backend.entity.Page;
import de.uniwue.zpd.dachs.larex.backend.entity.Project;
import de.uniwue.zpd.dachs.larex.backend.entity.UploadSession;
import de.uniwue.zpd.dachs.larex.backend.entity.UploadSessionFile;
import de.uniwue.zpd.dachs.larex.backend.entity.UploadSessionFile.UploadFileStatus;
import de.uniwue.zpd.dachs.larex.backend.entity.WorkspaceMember;
import de.uniwue.zpd.dachs.larex.backend.entity.WorkspaceMember.InvitationStatus;
import de.uniwue.zpd.dachs.larex.backend.entity.WorkspaceMember.Role;
import de.uniwue.zpd.dachs.larex.backend.entity.workspace.TeamWorkspace;
import de.uniwue.zpd.dachs.larex.backend.repository.library.LibraryRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.page.PageImageRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.page.PageRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.project.ProjectRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.upload.UploadSessionFileRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.upload.UploadSessionRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.workspace.TeamWorkspaceRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.workspace.WorkspaceMemberRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class UploadConflictAccessIntegrationTest {

    private static final String OWNER = "owner";
    private static final String CURATOR = "curator";
    private static final String EDITOR = "editor";
    private static final String INVITED_CURATOR = "invited-curator";
    private static final String OUTSIDER = "outsider";

    @TempDir static Path uploadDir;
    @TempDir static Path tempDirectory;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("file.upload-dir", () -> uploadDir.toString());
        registry.add("larex.upload.temp-directory", () -> tempDirectory.toString());
    }

    @Autowired MockMvc mvc;
    @Autowired TeamWorkspaceRepository workspaces;
    @Autowired WorkspaceMemberRepository members;
    @Autowired LibraryRepository libraries;
    @Autowired ProjectRepository projects;
    @Autowired PageRepository pages;
    @Autowired PageImageRepository images;
    @Autowired UploadSessionRepository sessions;
    @Autowired UploadSessionFileRepository sessionFiles;

    private String workspace;
    private Project project;
    private Project sibling;
    private String foreignWorkspace;
    private Project foreignProject;
    private Page page;
    private UploadSessionFile conflict;

    @BeforeEach
    void setUp() throws Exception {
        workspace = createWorkspace(OWNER);
        Library library = libraries.save(new Library(workspace, "Library"));
        project = projects.save(new Project("Project", null, library));
        sibling = projects.save(new Project("Sibling", null, library));
        members.save(new WorkspaceMember(CURATOR, Role.CURATOR, InvitationStatus.ACCEPTED, workspace));
        members.save(new WorkspaceMember(EDITOR, Role.EDITOR, InvitationStatus.ACCEPTED, workspace));
        members.save(new WorkspaceMember(INVITED_CURATOR, Role.CURATOR, InvitationStatus.PENDING, workspace));

        foreignWorkspace = createWorkspace(OUTSIDER);
        foreignProject = projects.save(new Project("Foreign", null,
                libraries.save(new Library(foreignWorkspace, "Library"))));

        page = pages.save(new Page("0001", null, project));
        conflict = createConflict(workspace, project);
    }

    @ParameterizedTest
    @ValueSource(strings = {OUTSIDER, INVITED_CURATOR})
    void nonMembersAreRejected(String user) throws Exception {
        read(user, workspace, project, "conflicts").andExpect(status().isForbidden());
        read(user, workspace, project, "status").andExpect(status().isForbidden());
        resolve(user, workspace, project, "REPLACE", conflict).andExpect(status().isForbidden());

        assertUntouched(conflict);
    }

    @Test
    void editorsCanReadButNotResolve() throws Exception {
        read(EDITOR, workspace, project, "conflicts")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].conflictId").value(conflict.getId()));
        read(EDITOR, workspace, project, "status")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hasUnresolvedConflicts").value(true));
        resolve(EDITOR, workspace, project, "REPLACE", conflict).andExpect(status().isForbidden());
        resolve(EDITOR, workspace, project, "KEEP_EXISTING", conflict).andExpect(status().isForbidden());

        assertUntouched(conflict);
    }

    @ParameterizedTest
    @ValueSource(strings = {OWNER, CURATOR})
    void curatorsAndOwnersCanListAndResolve(String user) throws Exception {
        read(user, workspace, project, "conflicts")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].conflictId").value(conflict.getId()))
                .andExpect(jsonPath("$[0].newFileName").value("0001.png"))
                .andExpect(jsonPath("$[0].pageId").value(page.getId()))
                .andExpect(content().string(not(containsString(conflict.getSession().getId()))));
        resolve(user, workspace, project, "REPLACE", conflict)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.skipped", hasSize(0)));

        assertThat(sessionFiles.findById(conflict.getId()).orElseThrow().getStatus()).isEqualTo(UploadFileStatus.COMPLETED);
        assertThat(images.findByPageId(page.getId())).hasSize(1);
        read(user, workspace, project, "conflicts").andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(0)));
        read(user, workspace, project, "status").andExpect(jsonPath("$.hasUnresolvedConflicts").value(false));
        resolve(user, workspace, project, "REPLACE", conflict).andExpect(status().isOk());
    }

    @Test
    void projectsOfAnotherWorkspaceAreNotFound() throws Exception {
        read(OUTSIDER, foreignWorkspace, project, "conflicts").andExpect(status().isNotFound());
        read(OUTSIDER, foreignWorkspace, project, "status").andExpect(status().isNotFound());
        resolve(OUTSIDER, foreignWorkspace, project, "REPLACE", conflict).andExpect(status().isNotFound());

        assertUntouched(conflict);
    }

    @ParameterizedTest
    @ValueSource(strings = {"REPLACE", "KEEP_EXISTING"})
    void conflictsOfAnotherProjectAreRejectedWithoutResolvingAny(String action) throws Exception {
        pages.save(new Page("0001", null, sibling));
        UploadSessionFile siblingConflict = createConflict(workspace, sibling);
        UploadSessionFile foreignConflict = createConflict(foreignWorkspace, foreignProject);

        resolve(CURATOR, workspace, project, action, conflict, siblingConflict).andExpect(status().isNotFound());
        resolve(CURATOR, workspace, project, action, conflict, foreignConflict).andExpect(status().isNotFound());
        resolve(OUTSIDER, foreignWorkspace, foreignProject, action, conflict).andExpect(status().isNotFound());

        assertUntouched(conflict);
        assertUntouched(siblingConflict);
        assertUntouched(foreignConflict);
    }

    private String createWorkspace(String owner) {
        return workspaces.save(new TeamWorkspace("conflicts-" + UUID.randomUUID(), "", owner)).getId();
    }

    private UploadSessionFile createConflict(String workspaceId, Project target) throws IOException {
        UploadSession session = new UploadSession(target.getId(), workspaceId, OWNER, 1, 1);
        session.setStatus(UploadSession.UploadSessionStatus.COMPLETED);
        session = sessions.save(session);
        Path path = tempDirectory.resolve(session.getId()).resolve("0001.png");
        Files.createDirectories(path.getParent());
        Files.writeString(path, "new image");
        UploadSessionFile file = new UploadSessionFile("0001.png", Files.size(path), "image/png", "0001", "png", 1);
        file.setSession(session);
        file.setTempFilePath(path.toString());
        file.setStatus(UploadFileStatus.CONFLICT);
        file.setConflictType("IMAGE_VARIANT_EXISTS");
        return sessionFiles.save(file);
    }

    private ResultActions read(String user, String workspaceId, Project target, String endpoint) throws Exception {
        return mvc.perform(get("/workspaces/{workspaceId}/projects/{projectId}/{endpoint}", workspaceId, target.getId(), endpoint)
                .with(jwt().jwt(jwt -> jwt.subject(user))));
    }

    private ResultActions resolve(String user, String workspaceId, Project target, String action, UploadSessionFile... files)
            throws Exception {
        String resolutions = Arrays.stream(files)
                .map(file -> "{\"conflictId\": \"%s\", \"action\": \"%s\"}".formatted(file.getId(), action))
                .collect(Collectors.joining(","));
        return mvc.perform(post("/workspaces/{workspaceId}/projects/{projectId}/conflicts/resolve", workspaceId, target.getId())
                .with(jwt().jwt(jwt -> jwt.subject(user)))
                .contentType("application/json")
                .content("{\"resolutions\": [" + resolutions + "]}"));
    }

    private void assertUntouched(UploadSessionFile file) {
        assertThat(sessionFiles.findById(file.getId()).orElseThrow().getStatus()).isEqualTo(UploadFileStatus.CONFLICT);
        assertThat(Path.of(file.getTempFilePath())).exists();
        assertThat(images.findByPageId(page.getId())).isEmpty();
    }
}
