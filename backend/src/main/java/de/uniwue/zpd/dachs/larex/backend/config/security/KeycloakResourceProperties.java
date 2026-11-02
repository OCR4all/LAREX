package de.uniwue.zpd.dachs.larex.backend.config.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * @param resourceClientId    client whose roles are mapped to authorities
 * @param allowedTokenClients clients whose access tokens the API accepts (the frontend BFF)
 */
@ConfigurationProperties(prefix = "keycloak")
public record KeycloakResourceProperties(String resourceClientId, List<String> allowedTokenClients) {

    public KeycloakResourceProperties {
        if (resourceClientId == null || resourceClientId.isBlank()) {
            throw new IllegalArgumentException("keycloak.resource-client-id must be configured");
        }
        allowedTokenClients = allowedTokenClients == null
                ? List.of()
                : allowedTokenClients.stream().map(String::trim).filter(client -> !client.isEmpty()).toList();
        if (allowedTokenClients.isEmpty()) {
            throw new IllegalArgumentException("keycloak.allowed-token-clients must be configured");
        }
    }
}
