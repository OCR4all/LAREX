package de.uniwue.zpd.dachs.larex.backend.dto;

public enum AdminUserIdentitySource {
    LOCAL,
    LDAP,
    /** Signs in through a Keycloak-brokered identity provider (OIDC or SAML). */
    IDENTITY_PROVIDER,
    SERVICE_ACCOUNT
}
