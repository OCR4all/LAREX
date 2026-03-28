package de.uniwue.zpd.dachs.larex.backend;

import de.uniwue.zpd.dachs.larex.backend.dto.ErrorEventCaptureRequest;
import de.uniwue.zpd.dachs.larex.backend.exception.StorageQuotaExceededException;
import de.uniwue.zpd.dachs.larex.backend.repository.admin.ErrorEventRepository;
import de.uniwue.zpd.dachs.larex.backend.service.admin.ErrorEventService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, ErrorEventIntegrationTest.TestErrorController.class})
class ErrorEventIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ErrorEventRepository errorEventRepository;

    @Autowired
    private ErrorEventService errorEventService;

    @BeforeEach
    void setUp() {
        errorEventRepository.deleteAll();
    }

    @Test
    @WithMockUser(username = "global-admin", roles = "GLOBAL_ADMIN")
    void unexpectedException_persistsEventAndReturnsErrorId() throws Exception {
        mockMvc.perform(post("/admin/test-errors/unexpected"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.errorId", notNullValue()));

        mockMvc.perform(get("/admin/errors/summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalEvents").value(1))
                .andExpect(jsonPath("$.serverErrors").value(1));
    }

    @Test
    @WithMockUser(username = "worker-1", roles = "USER")
    void storageQuotaException_persistsWorkspaceContext() throws Exception {
        mockMvc.perform(post("/workspaces/ws-123/test-errors/quota"))
                .andExpect(status().isInsufficientStorage())
                .andExpect(jsonPath("$.errorId", notNullValue()))
                .andExpect(jsonPath("$.workspaceId").value("ws-123"));

        mockMvc.perform(get("/admin/errors")
                        .param("workspaceId", "ws-123"))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/admin/errors")
                        .param("workspaceId", "ws-123")
                        .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user("global-admin").roles("GLOBAL_ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].workspaceId").value("ws-123"));
    }

    @Test
    @WithMockUser(username = "worker-1", roles = "USER")
    void validationError_doesNotPersistEvent() throws Exception {
        mockMvc.perform(post("/workspaces/ws-999/test-errors/validation")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorId").isEmpty());

        mockMvc.perform(get("/admin/errors/summary")
                        .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user("global-admin").roles("GLOBAL_ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalEvents").value(0));
    }

    @Test
    @WithMockUser(username = "global-admin", roles = "GLOBAL_ADMIN")
    void adminErrors_canFilterByWorkspaceAndUser() throws Exception {
        errorEventService.capture(new ErrorEventCaptureRequest(
                500,
                null,
                "Internal Server Error",
                "First error",
                "/workspaces/ws-a/projects/p-1",
                "POST",
                IllegalStateException.class.getName(),
                "user-a",
                "alice",
                "ws-a",
                null,
                "stack"
        ));
        errorEventService.capture(new ErrorEventCaptureRequest(
                409,
                "CONFLICT",
                "Data Conflict",
                "Second error",
                "/workspaces/ws-b/projects/p-2",
                "PUT",
                IllegalArgumentException.class.getName(),
                "user-b",
                "bob",
                "ws-b",
                null,
                null
        ));

        mockMvc.perform(get("/admin/errors")
                        .param("workspaceId", "ws-b")
                        .param("userId", "user-b"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.items[0].workspaceId").value("ws-b"))
                .andExpect(jsonPath("$.items[0].userId").value("user-b"));
    }

    @RestController
    @Validated
    static class TestErrorController {

        @PostMapping("/admin/test-errors/unexpected")
        Map<String, String> unexpected() {
            throw new IllegalStateException("Unexpected test failure");
        }

        @PostMapping("/workspaces/{workspaceId}/test-errors/quota")
        Map<String, String> quota(@PathVariable String workspaceId) {
            throw new StorageQuotaExceededException(
                    workspaceId,
                    "upload",
                    100L,
                    90L,
                    120L,
                    0L,
                    0L,
                    100.0
            );
        }

        @PostMapping("/workspaces/{workspaceId}/test-errors/validation")
        Map<String, String> validation(@PathVariable String workspaceId, @Valid @RequestBody TestValidationRequest request) {
            return Map.of("workspaceId", workspaceId, "name", request.name());
        }
    }

    record TestValidationRequest(@NotBlank String name) {
    }
}
