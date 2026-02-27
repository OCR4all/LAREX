package de.uniwue.zpd.dachs.larex.backend.config.security;

import de.uniwue.zpd.dachs.larex.backend.config.security.KeycloakAdminProperties;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.KeycloakBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class KeycloakConfig {

    private final KeycloakAdminProperties keycloakAdminProperties;

    public KeycloakConfig(KeycloakAdminProperties keycloakAdminProperties) {
        this.keycloakAdminProperties = keycloakAdminProperties;
    }

    @Bean
    public Keycloak keycloakAdmin() {
        return KeycloakBuilder.builder()
                .serverUrl(keycloakAdminProperties.serverUrl())
                .realm(keycloakAdminProperties.realm())
                .grantType("client_credentials")
                .clientId(keycloakAdminProperties.clientId())
                .clientSecret(keycloakAdminProperties.clientSecret())
                .build();
    }
}
