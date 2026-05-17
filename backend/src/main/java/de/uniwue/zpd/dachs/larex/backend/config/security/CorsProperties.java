package de.uniwue.zpd.dachs.larex.backend.config.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "cors")
public record CorsProperties(String allowedOrigin) {

    public CorsProperties {
        if (allowedOrigin == null || allowedOrigin.isBlank()) {
            throw new IllegalArgumentException("cors.allowed-origin must be configured");
        }
    }
}
