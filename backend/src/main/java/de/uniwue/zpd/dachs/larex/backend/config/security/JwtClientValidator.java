package de.uniwue.zpd.dachs.larex.backend.config.security;

import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Accepts only access tokens issued to LAREX clients. Keycloak signs tokens for every client in
 * the realm with the same keys, so issuer validation alone would accept tokens meant for other
 * applications sharing the realm.
 */
public class JwtClientValidator implements OAuth2TokenValidator<Jwt> {

    private static final OAuth2Error INVALID_CLIENT = new OAuth2Error(
            OAuth2ErrorCodes.INVALID_TOKEN,
            "The access token was not issued to a LAREX client",
            null
    );

    private final Set<String> acceptedClients;

    public JwtClientValidator(String resourceClientId, List<String> allowedTokenClients) {
        this.acceptedClients = new HashSet<>(allowedTokenClients);
        this.acceptedClients.add(resourceClientId);
    }

    @Override
    public OAuth2TokenValidatorResult validate(Jwt jwt) {
        String authorizedParty = jwt.getClaimAsString("azp");
        if (authorizedParty != null && acceptedClients.contains(authorizedParty)) {
            return OAuth2TokenValidatorResult.success();
        }
        List<String> audience = jwt.getAudience();
        if (audience != null && audience.stream().anyMatch(acceptedClients::contains)) {
            return OAuth2TokenValidatorResult.success();
        }
        return OAuth2TokenValidatorResult.failure(INVALID_CLIENT);
    }
}
