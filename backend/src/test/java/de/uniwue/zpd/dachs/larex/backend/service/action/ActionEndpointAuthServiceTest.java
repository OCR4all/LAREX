package de.uniwue.zpd.dachs.larex.backend.service.action;

import de.uniwue.zpd.dachs.larex.backend.config.ActionProperties;
import de.uniwue.zpd.dachs.larex.backend.dto.action.ActionDefinitionDocument;
import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ActionEndpointAuthServiceTest {

    private static final Clock FIXED_CLOCK = Clock.fixed(Instant.parse("2026-05-17T15:00:00Z"), ZoneOffset.UTC);

    @Test
    void resolvesSecretFromTypedProperties() {
        ActionProperties properties = new ActionProperties();
        properties.setEndpointSecrets(Map.of("kraken-segmentation-v1", "shared-secret"));
        ActionEndpointAuthService service = new ActionEndpointAuthService(properties, new MockEnvironment(), FIXED_CLOCK);

        Map<String, String> headers = service.buildDispatchHeaders(
                new ActionDefinitionDocument.EndpointAuth("hmac", "kraken-segmentation-v1"),
                "kraken-segmentation",
                "run-1",
                URI.create("http://processor:9000/dispatch"),
                "nonce-1",
                "{}"
        );

        assertThat(headers).containsEntry("X-LAREX-Action-Auth", "hmac-sha256;v=1");
        assertThat(headers).containsKey("X-LAREX-Action-Signature");
    }

    @Test
    void resolvesSecretFromSpringStyleEnvironmentAlias() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("LAREX_ACTIONS_ENDPOINT_SECRETS_KRAKEN_SEGMENTATION_V1", "shared-secret");
        ActionEndpointAuthService service = new ActionEndpointAuthService(new ActionProperties(), environment, FIXED_CLOCK);

        assertThat(service.hasSecret("kraken-segmentation-v1")).isTrue();
    }

    @Test
    void resolvesSecretFromDocumentedEnvironmentAlias() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("LAREX_ACTION_ENDPOINT_SECRET_KRAKEN_SEGMENTATION_V1", "shared-secret");
        ActionEndpointAuthService service = new ActionEndpointAuthService(new ActionProperties(), environment, FIXED_CLOCK);

        assertThat(service.hasSecret("kraken-segmentation-v1")).isTrue();
    }

    @Test
    void resolvesDatabaseSecretBeforeEnvironmentFallback() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("LAREX_ACTION_ENDPOINT_SECRET_KRAKEN_SEGMENTATION_V1", "env-secret");
        ActionEndpointSecretService secretService = mock(ActionEndpointSecretService.class);
        when(secretService.resolveDbSecretForUse("kraken-segmentation-v1")).thenReturn("db-secret");
        ActionEndpointAuthService service = new ActionEndpointAuthService(
                new ActionProperties(),
                environment,
                secretService,
                FIXED_CLOCK
        );
        ActionEndpointAuthService envOnlyService = new ActionEndpointAuthService(new ActionProperties(), environment, FIXED_CLOCK);

        Map<String, String> dbHeaders = service.buildDispatchHeaders(
                new ActionDefinitionDocument.EndpointAuth("hmac", "kraken-segmentation-v1"),
                "kraken-segmentation",
                "run-1",
                URI.create("http://processor:9000/dispatch"),
                "nonce-1",
                "{}"
        );
        Map<String, String> envHeaders = envOnlyService.buildDispatchHeaders(
                new ActionDefinitionDocument.EndpointAuth("hmac", "kraken-segmentation-v1"),
                "kraken-segmentation",
                "run-1",
                URI.create("http://processor:9000/dispatch"),
                "nonce-1",
                "{}"
        );

        assertThat(dbHeaders.get("X-LAREX-Action-Signature"))
                .isNotEqualTo(envHeaders.get("X-LAREX-Action-Signature"));
        verify(secretService).resolveDbSecretForUse("kraken-segmentation-v1");
    }

    @Test
    void hasSecretAcceptsDatabaseSecret() {
        ActionEndpointSecretService secretService = mock(ActionEndpointSecretService.class);
        when(secretService.hasDbSecret("kraken-segmentation-v1")).thenReturn(true);
        ActionEndpointAuthService service = new ActionEndpointAuthService(
                new ActionProperties(),
                new MockEnvironment(),
                secretService,
                FIXED_CLOCK
        );

        assertThat(service.hasSecret("kraken-segmentation-v1")).isTrue();
    }
}
