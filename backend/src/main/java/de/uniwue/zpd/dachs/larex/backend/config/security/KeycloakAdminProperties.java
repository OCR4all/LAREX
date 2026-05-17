package de.uniwue.zpd.dachs.larex.backend.config.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "keycloak.admin")
public record KeycloakAdminProperties(
        String serverUrl,
        String realm,
        String clientId,
        String clientSecret,
        ActionEmail actionEmail
) {

    private static final String DEFAULT_REALM = "larex-dev";
    private static final String DEFAULT_ACTION_EMAIL_CLIENT_ID = "larex-frontend";
    private static final String DEFAULT_ACTION_EMAIL_REDIRECT_URI = "http://larex.localhost/auth/keycloak";
    private static final int DEFAULT_ACTION_EMAIL_LIFESPAN_SECONDS = 43_200;

    public KeycloakAdminProperties {
        realm = defaultIfMissing(realm, DEFAULT_REALM);
        actionEmail = actionEmail == null ? new ActionEmail(null, null, null) : actionEmail;
    }

    private static String defaultIfMissing(String value, String fallback) {
        return value == null ? fallback : value;
    }

    public record ActionEmail(
            String clientId,
            String redirectUri,
            Integer lifespanSeconds
    ) {

        public ActionEmail {
            clientId = defaultIfMissing(clientId, DEFAULT_ACTION_EMAIL_CLIENT_ID);
            redirectUri = defaultIfMissing(redirectUri, DEFAULT_ACTION_EMAIL_REDIRECT_URI);
            lifespanSeconds = lifespanSeconds == null ? DEFAULT_ACTION_EMAIL_LIFESPAN_SECONDS : lifespanSeconds;
        }
    }
}
