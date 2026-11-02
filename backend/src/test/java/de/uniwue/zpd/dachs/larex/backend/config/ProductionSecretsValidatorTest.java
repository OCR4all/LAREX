package de.uniwue.zpd.dachs.larex.backend.config;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProductionSecretsValidatorTest {

    private static final String STRONG = "Qm4cQ9v1r8yN3kZpX2sW7tB6uH0jL5eA9fD3gK8mP1";

    @Test
    void acceptsGeneratedSecrets() {
        assertThatCode(() -> new ProductionSecretsValidator(validEnvironment()).afterPropertiesSet())
                .doesNotThrowAnyException();
    }

    @Test
    void rejectsPlaceholderSecrets() {
        MockEnvironment environment = validEnvironment()
                .withProperty("keycloak.admin.client-secret", "PLEASE_CHANGE_IN_PRODUCTION")
                .withProperty("larex.notifications.bridge.secret", "larex-notification-bridge-dev-secret");

        assertThatThrownBy(() -> new ProductionSecretsValidator(environment).afterPropertiesSet())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("KEYCLOAK_ADMIN_CLIENT_SECRET")
                .hasMessageContaining("LAREX_NOTIFICATIONS_BRIDGE_SECRET");
    }

    @Test
    void rejectsShortSharedSecrets() {
        MockEnvironment environment = validEnvironment()
                .withProperty("larex.actions.endpoint-secret-encryption-key", "short-key");

        assertThatThrownBy(() -> new ProductionSecretsValidator(environment).afterPropertiesSet())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("LAREX_ACTION_ENDPOINT_SECRET_ENCRYPTION_KEY");
    }

    private MockEnvironment validEnvironment() {
        return new MockEnvironment()
                .withProperty("spring.datasource.password", "x8KfP2mQ7rT4vW9yZ3bN6cD1")
                .withProperty("keycloak.admin.client-secret", STRONG)
                .withProperty("larex.notifications.bridge.secret", STRONG)
                .withProperty("larex.actions.endpoint-secret-encryption-key", STRONG);
    }
}
