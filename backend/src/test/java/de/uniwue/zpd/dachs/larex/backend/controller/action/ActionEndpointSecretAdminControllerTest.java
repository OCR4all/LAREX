package de.uniwue.zpd.dachs.larex.backend.controller.action;

import de.uniwue.zpd.dachs.larex.backend.dto.action.ActionDto;
import de.uniwue.zpd.dachs.larex.backend.service.action.ActionEndpointSecretService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class ActionEndpointSecretAdminControllerTest {

    @Mock
    private ActionEndpointSecretService secretService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(new ActionEndpointSecretAdminController(secretService))
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .build();
    }

    @Test
    void listSecretsDoesNotExposePlaintext() throws Exception {
        when(secretService.listSecrets()).thenReturn(List.of(secretResponse()));

        mockMvc.perform(get("/admin/actions/endpoint-secrets"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].ref").value("processor-v1"))
                .andExpect(jsonPath("$[0].envName").value("LAREX_ACTION_ENDPOINT_SECRET_PROCESSOR_V1"))
                .andExpect(jsonPath("$[0].plaintext").doesNotExist())
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("raw-secret"))));
    }

    @Test
    void createSecretRevealsPlaintext() throws Exception {
        when(secretService.createSecret(any(ActionDto.EndpointSecretRequest.class), any()))
                .thenReturn(new ActionDto.EndpointSecretRevealResponse(secretResponse(), "raw-secret"));

        mockMvc.perform(post("/admin/actions/endpoint-secrets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "ref": "processor-v1",
                                  "displayName": "Processor"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.secret.ref").value("processor-v1"))
                .andExpect(jsonPath("$.plaintext").value("raw-secret"));
    }

    @Test
    void rotateSecretRevealsPlaintext() throws Exception {
        when(secretService.rotateSecret("secret-1"))
                .thenReturn(new ActionDto.EndpointSecretRevealResponse(secretResponse(), "rotated-secret"));

        mockMvc.perform(post("/admin/actions/endpoint-secrets/secret-1/rotate"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.secret.id").value("secret-1"))
                .andExpect(jsonPath("$.plaintext").value("rotated-secret"));
    }

    @Test
    void deleteDelegatesToService() throws Exception {
        mockMvc.perform(delete("/admin/actions/endpoint-secrets/secret-1"))
                .andExpect(status().isNoContent());

        verify(secretService).deleteSecret(eq("secret-1"));
    }

    private ActionDto.EndpointSecretResponse secretResponse() {
        return new ActionDto.EndpointSecretResponse(
                "secret-1",
                "processor-v1",
                "LAREX_ACTION_ENDPOINT_SECRET_PROCESSOR_V1",
                "Processor",
                "Dispatch signing",
                "admin-1",
                LocalDateTime.parse("2026-05-17T15:00:00"),
                LocalDateTime.parse("2026-05-17T15:00:00"),
                null,
                null,
                "DATABASE"
        );
    }
}
