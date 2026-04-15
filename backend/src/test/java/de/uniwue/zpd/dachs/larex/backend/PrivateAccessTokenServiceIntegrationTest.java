package de.uniwue.zpd.dachs.larex.backend;

import de.uniwue.zpd.dachs.larex.backend.dto.PrivateAccessTokenDto;
import de.uniwue.zpd.dachs.larex.backend.entity.UserPrivateAccessToken;
import de.uniwue.zpd.dachs.larex.backend.repository.user.UserPrivateAccessTokenRepository;
import de.uniwue.zpd.dachs.larex.backend.service.user.PrivateAccessTokenService;
import de.uniwue.zpd.dachs.larex.backend.service.workspace.WorkspaceAccessService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doNothing;

@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = {
        "larex.auth.private-access-tokens.allowed-user-ids=user-1,global-admin",
        "larex.auth.private-access-tokens.default-expiry-days=30",
        "larex.auth.private-access-tokens.max-expiry-days=90",
        "larex.auth.private-access-tokens.max-active-tokens-per-workspace=5"
})
class PrivateAccessTokenServiceIntegrationTest {

    @Autowired
    private PrivateAccessTokenService userMachineTokenService;

    @Autowired
    private UserPrivateAccessTokenRepository userMachineTokenRepository;

    @MockBean
    private WorkspaceAccessService workspaceAccessService;

    @BeforeEach
    void setUp() {
        userMachineTokenRepository.deleteAll();
        doNothing().when(workspaceAccessService).requireWorkspaceAccess("ws-1", "user-1");
        doNothing().when(workspaceAccessService).requireWorkspaceAccess("ws-2", "user-1");
    }

    @Test
    void createListAndRevokeMachineToken() {
        PrivateAccessTokenDto.CreateRequest request = new PrivateAccessTokenDto.CreateRequest(
                "ws-1",
                "slurm-job",
                LocalDateTime.now().plusDays(10),
                List.of(PrivateAccessTokenService.SCOPE_XML_READ, PrivateAccessTokenService.SCOPE_XML_WRITE)
        );

        PrivateAccessTokenDto.CreatedResponse created = userMachineTokenService.createTokenForUser("user-1", request);
        assertNotNull(created.id());
        assertTrue(created.secret().startsWith("lrx_pat_"));

        UserPrivateAccessToken stored = userMachineTokenRepository.findById(created.id()).orElseThrow();
        assertNotEquals(created.secret(), stored.getSecretHash());
        assertTrue(stored.getSecretHash().matches("^[a-f0-9]{64}$"));
        assertNotNull(stored.getSecretPrefix());

        List<PrivateAccessTokenDto.SummaryResponse> summaries = userMachineTokenService.listTokensForUser("user-1");
        assertEquals(1, summaries.size());
        assertEquals("slurm-job", summaries.getFirst().name());
        assertTrue(summaries.getFirst().active());

        userMachineTokenService.revokeTokenForUser("user-1", created.id());

        UserPrivateAccessToken revoked = userMachineTokenRepository.findById(created.id()).orElseThrow();
        assertNotNull(revoked.getRevokedAt());
        assertFalse(userMachineTokenService.authenticateBearerToken("Bearer " + created.secret()).isPresent());
    }

    @Test
    void authenticateBearerTokenUpdatesLastUsedAt() {
        PrivateAccessTokenDto.CreatedResponse created = userMachineTokenService.createTokenForUser(
                "user-1",
                new PrivateAccessTokenDto.CreateRequest(
                        "ws-1",
                        "xml-reader",
                        LocalDateTime.now().plusDays(5),
                        List.of(PrivateAccessTokenService.SCOPE_XML_READ)
                )
        );

        Optional<PrivateAccessTokenService.PrivateAccessTokenAuthContext> contextOpt =
                userMachineTokenService.authenticateBearerToken("Bearer " + created.secret());

        assertTrue(contextOpt.isPresent());
        PrivateAccessTokenService.PrivateAccessTokenAuthContext context = contextOpt.get();
        assertEquals("user-1", context.ownerUserId());
        assertEquals("ws-1", context.workspaceId());
        assertTrue(context.hasScope(PrivateAccessTokenService.SCOPE_XML_READ));
        assertFalse(context.hasScope(PrivateAccessTokenService.SCOPE_XML_WRITE));

        UserPrivateAccessToken stored = userMachineTokenRepository.findById(created.id()).orElseThrow();
        assertNotNull(stored.getLastUsedAt());
    }

    @Test
    void createTokenRejectsUsersOutsideAllowlist() {
        SecurityException thrown = assertThrows(
                SecurityException.class,
                () -> userMachineTokenService.createTokenForUser(
                        "user-2",
                        new PrivateAccessTokenDto.CreateRequest(
                                "ws-1",
                                "forbidden",
                                LocalDateTime.now().plusDays(5),
                                List.of(PrivateAccessTokenService.SCOPE_XML_READ)
                        )
                )
        );

        assertTrue(thrown.getMessage().contains("not enabled"));
    }

    @Test
    void createTokenEnforcesMaxActiveTokensPerWorkspace() {
        for (int i = 1; i <= 5; i++) {
            userMachineTokenService.createTokenForUser(
                    "user-1",
                    new PrivateAccessTokenDto.CreateRequest(
                            "ws-2",
                            "token-" + i,
                            LocalDateTime.now().plusDays(7),
                            List.of(PrivateAccessTokenService.SCOPE_XML_READ)
                    )
            );
        }

        IllegalArgumentException thrown = assertThrows(
                IllegalArgumentException.class,
                () -> userMachineTokenService.createTokenForUser(
                        "user-1",
                        new PrivateAccessTokenDto.CreateRequest(
                                "ws-2",
                                "token-6",
                                LocalDateTime.now().plusDays(7),
                                List.of(PrivateAccessTokenService.SCOPE_XML_READ)
                        )
                )
        );

        assertTrue(thrown.getMessage().contains("Maximum number of active private access tokens"));
    }
}
