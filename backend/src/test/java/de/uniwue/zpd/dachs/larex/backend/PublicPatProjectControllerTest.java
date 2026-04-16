package de.uniwue.zpd.dachs.larex.backend;

import de.uniwue.zpd.dachs.larex.backend.dto.PatProjectDto;
import de.uniwue.zpd.dachs.larex.backend.service.machine.PatProjectReadService;
import de.uniwue.zpd.dachs.larex.backend.service.user.PrivateAccessTokenService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class PublicPatProjectControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private PrivateAccessTokenService privateAccessTokenService;

    @MockBean
    private PatProjectReadService patProjectReadService;

    @Test
    void listProjectsReturnsUnauthorizedWhenTokenMissing() throws Exception {
        when(privateAccessTokenService.authenticateBearerToken(any())).thenReturn(Optional.empty());

        mockMvc.perform(get("/public/pat/projects"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void listProjectsReturnsForbiddenWhenScopeMissing() throws Exception {
        when(privateAccessTokenService.authenticateBearerToken("Bearer no-read"))
                .thenReturn(Optional.of(new PrivateAccessTokenService.PrivateAccessTokenAuthContext(
                        "tok-1",
                        "user-1",
                        "ws-1",
                        List.of(PrivateAccessTokenService.SCOPE_XML_WRITE)
                )));

        mockMvc.perform(get("/public/pat/projects")
                        .header("Authorization", "Bearer no-read"))
                .andExpect(status().isForbidden());
    }

    @Test
    void listProjectsReturnsPayloadWhenAuthorized() throws Exception {
        when(privateAccessTokenService.authenticateBearerToken("Bearer ok"))
                .thenReturn(Optional.of(new PrivateAccessTokenService.PrivateAccessTokenAuthContext(
                        "tok-1",
                        "user-1",
                        "ws-1",
                        List.of(PrivateAccessTokenService.SCOPE_XML_READ)
                )));
        when(patProjectReadService.listProjects("ws-1"))
                .thenReturn(List.of(new PatProjectDto.ProjectSummaryResponse(
                        "p-1",
                        "ws-1",
                        "Project A",
                        "Training data",
                        List.of("train"),
                        2,
                        LocalDateTime.now(),
                        LocalDateTime.now()
                )));

        mockMvc.perform(get("/public/pat/projects")
                        .header("Authorization", "Bearer ok"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("p-1"))
                .andExpect(jsonPath("$[0].workspaceId").value("ws-1"))
                .andExpect(jsonPath("$[0].pageCount").value(2));
    }

    @Test
    void listProjectsByWorkspaceReturnsNotFoundWhenWorkspaceMismatchesToken() throws Exception {
        when(privateAccessTokenService.authenticateBearerToken("Bearer ok"))
                .thenReturn(Optional.of(new PrivateAccessTokenService.PrivateAccessTokenAuthContext(
                        "tok-1",
                        "user-1",
                        "ws-1",
                        List.of(PrivateAccessTokenService.SCOPE_XML_READ)
                )));

        mockMvc.perform(get("/public/pat/workspaces/ws-2/projects")
                        .header("Authorization", "Bearer ok"))
                .andExpect(status().isNotFound());
    }

    @Test
    void getProjectDetailsReturnsNotFoundWhenProjectMissing() throws Exception {
        when(privateAccessTokenService.authenticateBearerToken("Bearer ok"))
                .thenReturn(Optional.of(new PrivateAccessTokenService.PrivateAccessTokenAuthContext(
                        "tok-1",
                        "user-1",
                        "ws-1",
                        List.of(PrivateAccessTokenService.SCOPE_XML_READ)
                )));
        when(patProjectReadService.getProjectDetail("ws-1", "p-missing"))
                .thenReturn(Optional.empty());

        mockMvc.perform(get("/public/pat/projects/p-missing")
                        .header("Authorization", "Bearer ok"))
                .andExpect(status().isNotFound());
    }

    @Test
    void getProjectDetailsReturnsPayloadWhenAuthorized() throws Exception {
        when(privateAccessTokenService.authenticateBearerToken("Bearer ok"))
                .thenReturn(Optional.of(new PrivateAccessTokenService.PrivateAccessTokenAuthContext(
                        "tok-1",
                        "user-1",
                        "ws-1",
                        List.of(PrivateAccessTokenService.SCOPE_XML_READ)
                )));

        PatProjectDto.ProjectDetailResponse detail = new PatProjectDto.ProjectDetailResponse(
                "p-1",
                "ws-1",
                "Project A",
                "Training data",
                List.of("train"),
                1,
                LocalDateTime.now(),
                LocalDateTime.now(),
                List.of(new PatProjectDto.PageDetailResponse(
                        "page-1",
                        "0001",
                        null,
                        List.of(),
                        List.of("orig.jpg", "bin.png"),
                        List.of("ocr"),
                        LocalDateTime.now(),
                        LocalDateTime.now(),
                        List.of(new PatProjectDto.ImageFileResponse(
                                "img-1",
                                "0001.orig.jpg",
                                "orig.jpg",
                                "0001",
                                "image/jpeg",
                                1234L
                        )),
                        List.of()
                ))
        );

        when(patProjectReadService.getProjectDetail(eq("ws-1"), eq("p-1")))
                .thenReturn(Optional.of(detail));

        mockMvc.perform(get("/public/pat/projects/p-1")
                        .header("Authorization", "Bearer ok"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("p-1"))
                .andExpect(jsonPath("$.pages[0].id").value("page-1"))
                .andExpect(jsonPath("$.pages[0].imageVariants[0]").value("orig.jpg"));
    }
}
