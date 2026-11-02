package de.uniwue.zpd.dachs.larex.backend.config.security;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.util.List;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtClientValidatorTest {

    private final JwtClientValidator validator = new JwtClientValidator("larex-backend", List.of("larex-frontend"));

    @Test
    void acceptsTokensIssuedToTheFrontend() {
        assertThat(validator.validate(jwt(claims -> claims.claim("azp", "larex-frontend"))).hasErrors()).isFalse();
    }

    @Test
    void acceptsTokensWhoseAudienceNamesLarex() {
        assertThat(validator.validate(jwt(claims -> claims
                .claim("azp", "some-tool")
                .audience(List.of("account", "larex-backend")))).hasErrors()).isFalse();
    }

    @Test
    void rejectsTokensIssuedToOtherRealmClients() {
        assertThat(validator.validate(jwt(claims -> claims
                .claim("azp", "other-app")
                .audience(List.of("account")))).hasErrors()).isTrue();
        assertThat(validator.validate(jwt(claims -> { })).hasErrors()).isTrue();
    }

    @Test
    void requiresAtLeastOneAllowedClient() {
        assertThatThrownBy(() -> new KeycloakResourceProperties("larex-backend", List.of(" ")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private Jwt jwt(Consumer<Jwt.Builder> customizer) {
        Jwt.Builder builder = Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .subject("user-1")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(60));
        customizer.accept(builder);
        return builder.build();
    }
}
