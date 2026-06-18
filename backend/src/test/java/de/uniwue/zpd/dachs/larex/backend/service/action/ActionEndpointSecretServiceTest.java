package de.uniwue.zpd.dachs.larex.backend.service.action;

import de.uniwue.zpd.dachs.larex.backend.config.ActionProperties;
import de.uniwue.zpd.dachs.larex.backend.config.security.GlobalAdminService;
import de.uniwue.zpd.dachs.larex.backend.dto.action.ActionDto;
import de.uniwue.zpd.dachs.larex.backend.entity.ActionEndpointSecret;
import de.uniwue.zpd.dachs.larex.backend.entity.ActionProcessorDefinition;
import de.uniwue.zpd.dachs.larex.backend.repository.action.ActionEndpointSecretRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.action.ActionProcessorDefinitionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.env.MockEnvironment;
import tools.jackson.databind.ObjectMapper;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ActionEndpointSecretServiceTest {

    private static final Clock FIXED_CLOCK = Clock.fixed(Instant.parse("2026-05-17T15:00:00Z"), ZoneOffset.UTC);

    private ActionEndpointSecretRepository secretRepository;
    private ActionProcessorDefinitionRepository definitionRepository;
    private ActionProperties properties;
    private MockEnvironment environment;
    private ActionEndpointSecretService service;

    @BeforeEach
    void setUp() {
        secretRepository = mock(ActionEndpointSecretRepository.class);
        definitionRepository = mock(ActionProcessorDefinitionRepository.class);
        GlobalAdminService globalAdminService = mock(GlobalAdminService.class);
        when(globalAdminService.isGlobalAdmin()).thenReturn(true);
        properties = new ActionProperties();
        properties.setEndpointSecretEncryptionKey("test-encryption-key-with-enough-entropy");
        environment = new MockEnvironment();
        when(secretRepository.save(any(ActionEndpointSecret.class))).thenAnswer(invocation -> invocation.getArgument(0));
        service = new ActionEndpointSecretService(
                secretRepository,
                definitionRepository,
                globalAdminService,
                properties,
                new ObjectMapper(),
                environment,
                FIXED_CLOCK
        );
    }

    @Test
    void createReturnsPlaintextOnceButListDoesNotExposeIt() {
        when(secretRepository.existsByRef("kraken-segmentation-v1")).thenReturn(false);

        ActionDto.EndpointSecretRevealResponse created = service.createSecret(
                new ActionDto.EndpointSecretRequest("kraken_segmentation_v1", "Kraken", "Segmentation"),
                "admin-1"
        );
        ActionEndpointSecret saved = savedSecret();
        when(secretRepository.findAllByOrderByRefAsc()).thenReturn(List.of(saved));

        assertThat(created.secret().ref()).isEqualTo("kraken-segmentation-v1");
        assertThat(created.plaintext()).hasSize(64);
        assertThat(saved.getEncryptedSecret()).doesNotContain(created.plaintext());
        assertThat(service.listSecrets())
                .singleElement()
                .satisfies(response -> {
                    assertThat(response.ref()).isEqualTo("kraken-segmentation-v1");
                    assertThat(response.displayName()).isEqualTo("Kraken");
                });
    }

    @Test
    void resolveForUseDecryptsAndUpdatesLastUsedAt() {
        when(secretRepository.existsByRef("processor-v1")).thenReturn(false);
        String plaintext = service.createSecret(
                new ActionDto.EndpointSecretRequest("processor-v1", null, null),
                "admin-1"
        ).plaintext();
        ActionEndpointSecret saved = savedSecret();
        when(secretRepository.findByRef("processor-v1")).thenReturn(Optional.of(saved));

        assertThat(service.resolveDbSecretForUse("PROCESSOR_V1")).isEqualTo(plaintext);
        assertThat(saved.getLastUsedAt()).isEqualTo(java.time.LocalDateTime.now(FIXED_CLOCK));
    }

    @Test
    void rotateReturnsNewPlaintextAndStoresEncryptedValue() {
        when(secretRepository.existsByRef("processor-v1")).thenReturn(false);
        String original = service.createSecret(
                new ActionDto.EndpointSecretRequest("processor-v1", null, null),
                "admin-1"
        ).plaintext();
        ActionEndpointSecret saved = savedSecret();
        saved.setId("secret-1");
        when(secretRepository.findById("secret-1")).thenReturn(Optional.of(saved));

        ActionDto.EndpointSecretRevealResponse rotated = service.rotateSecret("secret-1");

        assertThat(rotated.plaintext()).hasSize(64).isNotEqualTo(original);
        assertThat(saved.getEncryptedSecret()).doesNotContain(rotated.plaintext());
        assertThat(saved.getRotatedAt()).isEqualTo(java.time.LocalDateTime.now(FIXED_CLOCK));
    }

    @Test
    void deleteRejectsReferencedSecret() {
        ActionEndpointSecret secret = new ActionEndpointSecret();
        secret.setId("secret-1");
        secret.setRef("processor-v1");
        when(secretRepository.findById("secret-1")).thenReturn(Optional.of(secret));
        ActionProcessorDefinition definition = new ActionProcessorDefinition();
        definition.setProcessorKey("processor");
        definition.setParsedJson("""
                {"version":1,"id":"processor","name":"Processor","endpoint":{"url":"https://processor.example/dispatch","auth":{"type":"hmac","secretRef":"processor-v1"}}}
                """);
        when(definitionRepository.findAllByOrderByNameAsc()).thenReturn(List.of(definition));

        assertThatThrownBy(() -> service.deleteSecret("secret-1"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("still referenced")
                .hasMessageContaining("processor");
    }

    @Test
    void deleteRemovesUnreferencedSecret() {
        ActionEndpointSecret secret = new ActionEndpointSecret();
        secret.setId("secret-1");
        secret.setRef("processor-v1");
        when(secretRepository.findById("secret-1")).thenReturn(Optional.of(secret));
        when(definitionRepository.findAllByOrderByNameAsc()).thenReturn(List.of());

        service.deleteSecret("secret-1");

        verify(secretRepository).delete(secret);
    }

    @Test
    void listIncludesConfiguredEnvFallbackWithoutPlaintext() {
        properties.setEndpointSecrets(java.util.Map.of("bundled-processor-v1", "fallback-secret"));
        when(secretRepository.findAllByOrderByRefAsc()).thenReturn(List.of());
        when(definitionRepository.findAllByOrderByNameAsc()).thenReturn(List.of());

        assertThat(service.listSecrets())
                .singleElement()
                .satisfies(response -> {
                    assertThat(response.ref()).isEqualTo("bundled-processor-v1");
                    assertThat(response.source()).isEqualTo("ENV_FALLBACK");
                    assertThat(response.id()).isNull();
                });
    }

    private ActionEndpointSecret savedSecret() {
        ArgumentCaptor<ActionEndpointSecret> captor = ArgumentCaptor.forClass(ActionEndpointSecret.class);
        verify(secretRepository, atLeastOnce()).save(captor.capture());
        return captor.getValue();
    }
}
