package de.uniwue.zpd.dachs.larex.backend;

import com.jayway.jsonpath.JsonPath;
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
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.file.Path;
import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class UploadReviewPermissionIntegrationTest {

    private static final String OWNER = "owner";
    private static final String CURATOR = "curator";
    private static final String EDITOR = "editor";
    private static final String GLOBAL_ADMIN = "global-admin";

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
    private Page page;

    @BeforeEach
    void setUp() {
        workspace = workspaces.save(new TeamWorkspace("review-" + UUID.randomUUID(), "", OWNER)).getId();
        project = projects.save(new Project("Project", null, libraries.save(new Library(workspace, "Library"))));
        members.save(new WorkspaceMember(CURATOR, Role.CURATOR, InvitationStatus.ACCEPTED, workspace));
        members.save(new WorkspaceMember(EDITOR, Role.EDITOR, InvitationStatus.ACCEPTED, workspace));
        page = new Page("0001", null, project);
        page.setWorkflowState(Page.WorkflowState.IN_REVIEW);
        page = pages.save(page);
    }

    @ParameterizedTest
    @ValueSource(strings = {OWNER, CURATOR, GLOBAL_ADMIN})
    void reviewersCanUploadToAPageInReview(String user) throws Exception {
        UploadSessionFile file = upload(user);

        assertThat(file)
                .returns(null, UploadSessionFile::getErrorMessage)
                .returns(UploadFileStatus.COMPLETED, UploadSessionFile::getStatus);
        assertThat(images.findByPageId(page.getId())).hasSize(1);
    }

    @Test
    void globalAdminsCannotUploadToADonePage() throws Exception {
        page.setWorkflowState(Page.WorkflowState.DONE);
        page = pages.save(page);

        UploadSessionFile file = upload(GLOBAL_ADMIN);

        assertThat(file)
                .returns("Page is locked: Page workflow state is Done", UploadSessionFile::getErrorMessage)
                .returns(UploadFileStatus.SKIPPED, UploadSessionFile::getStatus);
        assertThat(images.findByPageId(page.getId())).isEmpty();
    }

    @Test
    void editorsCannotStartAnUpload() throws Exception {
        mvc.perform(post(sessionsUrl()).with(as(EDITOR))
                        .contentType("application/json").content(sessionRequest(1)))
                .andExpect(status().isForbidden());
    }

    private UploadSessionFile upload(String user) throws Exception {
        byte[] image = png();
        String response = mvc.perform(post(sessionsUrl()).with(as(user))
                        .contentType("application/json").content(sessionRequest(image.length)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String sessionId = JsonPath.read(response, "$.id");
        String fileId = sessionFiles.findBySessionIdAndStatus(sessionId, UploadFileStatus.PENDING).getFirst().getId();

        mvc.perform(multipart(sessionsUrl() + "/{sessionId}/files/{fileId}/chunks", sessionId, fileId)
                        .file(new MockMultipartFile("file", "0001.png", "image/png", image))
                        .param("chunkIndex", "0").param("totalChunks", "1").with(as(user)))
                .andExpect(status().isOk());
        mvc.perform(post(sessionsUrl() + "/{sessionId}/finalize", sessionId).with(as(user)))
                .andExpect(status().isAccepted());

        await().atMost(Duration.ofSeconds(20)).untilAsserted(() ->
                assertThat(sessions.findById(sessionId).orElseThrow().getStatus())
                        .isEqualTo(UploadSession.UploadSessionStatus.COMPLETED));
        return sessionFiles.findById(fileId).orElseThrow();
    }

    private String sessionsUrl() {
        return "/workspaces/" + workspace + "/projects/" + project.getId() + "/upload-sessions";
    }

    private String sessionRequest(int size) {
        return "{\"files\": [{\"fileName\": \"0001.png\", \"fileSize\": %d, \"mimeType\": \"image/png\"}]}".formatted(size);
    }

    private RequestPostProcessor as(String user) {
        var request = jwt().jwt(jwt -> jwt.subject(user));
        return GLOBAL_ADMIN.equals(user)
                ? request.authorities(new SimpleGrantedAuthority("ROLE_GLOBAL_ADMIN"))
                : request;
    }

    private byte[] png() throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(8, 8, BufferedImage.TYPE_INT_RGB), "png", out);
        return out.toByteArray();
    }
}
