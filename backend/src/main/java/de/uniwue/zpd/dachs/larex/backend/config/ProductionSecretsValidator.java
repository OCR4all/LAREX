package de.uniwue.zpd.dachs.larex.backend.config;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Refuses to start a production backend with publicly known placeholder or development secrets.
 * Run {@code scripts/operations/bootstrap-env-secrets.sh prod} to generate real values.
 */
@Component
@Profile("prod")
public class ProductionSecretsValidator implements InitializingBean {

    static final Set<String> KNOWN_PLACEHOLDERS = Set.of(
            "change-me",
            "please_change_in_production",
            "replace-with-random-32-plus-character-secret",
            "change-me-initial-admin",
            "larex_dev_pw",
            "admin",
            "larex-notification-bridge-dev-secret",
            "dev-action-endpoint-secret-encryption-key-change-me"
    );

    private static final int MIN_SHARED_SECRET_LENGTH = 32;

    private final Environment environment;

    public ProductionSecretsValidator(Environment environment) {
        this.environment = environment;
    }

    @Override
    public void afterPropertiesSet() {
        List<String> problems = new ArrayList<>();
        requireNonPlaceholder(problems, "spring.datasource.password", "POSTGRES_PASSWORD", false, 0);
        requireNonPlaceholder(problems, "keycloak.admin.client-secret", "KEYCLOAK_ADMIN_CLIENT_SECRET", false, 0);
        requireNonPlaceholder(problems, "larex.notifications.bridge.secret", "LAREX_NOTIFICATIONS_BRIDGE_SECRET",
                false, MIN_SHARED_SECRET_LENGTH);
        requireNonPlaceholder(problems, "larex.actions.endpoint-secret-encryption-key",
                "LAREX_ACTION_ENDPOINT_SECRET_ENCRYPTION_KEY", true, MIN_SHARED_SECRET_LENGTH);
        if (!problems.isEmpty()) {
            throw new IllegalStateException("Insecure production configuration: " + String.join("; ", problems)
                    + ". Run scripts/operations/bootstrap-env-secrets.sh prod to generate secrets.");
        }
    }

    private void requireNonPlaceholder(List<String> problems,
                                       String property,
                                       String envName,
                                       boolean optional,
                                       int minLength) {
        String value = environment.getProperty(property);
        if (value == null || value.isBlank()) {
            if (!optional) {
                problems.add(envName + " is not set");
            }
            return;
        }
        if (isPlaceholder(value)) {
            problems.add(envName + " still uses a placeholder or development value");
        } else if (value.length() < minLength) {
            problems.add(envName + " must be at least " + minLength + " characters");
        }
    }

    static boolean isPlaceholder(String value) {
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        return KNOWN_PLACEHOLDERS.contains(normalized) || normalized.contains("change-me");
    }
}
