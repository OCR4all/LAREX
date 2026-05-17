package de.uniwue.zpd.dachs.larex.backend.config.security;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties({
        CorsProperties.class,
        KeycloakAdminProperties.class,
        KeycloakResourceProperties.class
})
public class SecurityPropertiesConfiguration {
}
