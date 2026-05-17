package de.uniwue.zpd.dachs.larex.backend.config.security;

import org.springframework.core.convert.converter.Converter;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Extracts roles from the Keycloak JWT and maps them to Spring Security's GrantedAuthority.
 * Extracts both realm roles (for global admin) and client roles.
 */
@Component
public class JwtAuthConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    private final JwtGrantedAuthoritiesConverter jwtGrantedAuthoritiesConverter = new JwtGrantedAuthoritiesConverter();
    private final String resourceClientId;

    public JwtAuthConverter(KeycloakResourceProperties keycloakResourceProperties) {
        this.resourceClientId = keycloakResourceProperties.resourceClientId();
    }

    @Override
    public AbstractAuthenticationToken convert(@NonNull Jwt jwt) {
        Collection<GrantedAuthority> authorities = Stream.of(
                jwtGrantedAuthoritiesConverter.convert(jwt).stream(),
                extractRealmRoles(jwt).stream(),
                extractResourceRoles(jwt).stream()
        ).flatMap(s -> s).collect(Collectors.toSet());

        String principalName = jwt.getSubject();
        return new JwtAuthenticationToken(jwt, authorities, principalName);
    }

    private Collection<? extends GrantedAuthority> extractRealmRoles(Jwt jwt) {
        Map<String, Object> realmAccess = jwt.getClaimAsMap("realm_access");
        if (realmAccess != null && realmAccess.get("roles") instanceof Collection) {
            @SuppressWarnings("unchecked")
            Collection<String> roles = (Collection<String>) realmAccess.get("roles");
            return roles.stream()
                    .map(role -> new SimpleGrantedAuthority("ROLE_" + role))
                    .collect(Collectors.toSet());
        }
        return Collections.emptySet();
    }

    private Collection<? extends GrantedAuthority> extractResourceRoles(Jwt jwt) {
        Map<String, Object> resourceAccess = jwt.getClaimAsMap("resource_access");
        if (resourceAccess != null && resourceAccess.get(resourceClientId) instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> resource = (Map<String, Object>) resourceAccess.get(resourceClientId);
            if (resource.get("roles") instanceof Collection) {
                @SuppressWarnings("unchecked")
                Collection<String> roles = (Collection<String>) resource.get("roles");
                return roles.stream()
                        .map(role -> new SimpleGrantedAuthority("ROLE_" + role))
                        .collect(Collectors.toSet());
            }
        }
        return Collections.emptySet();
    }
}
