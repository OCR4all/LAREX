package de.uniwue.zpd.dachs.larex.backend.config.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "keycloak")
public record KeycloakResourceProperties(String resourceClientId) {

    public KeycloakResourceProperties {
        if (resourceClientId == null || resourceClientId.isBlank()) {
            throw new IllegalArgumentException("keycloak.resource-client-id must be configured");
        }
    }
}
