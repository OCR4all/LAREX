package de.uniwue.zpd.dachs.larex.backend.config.auth;

public enum UserProvisioningMode {
    LOCAL,
    EXTERNALLY_MANAGED,
    /** Accepted so existing deployments keep working; normalized to {@link #EXTERNALLY_MANAGED}. */
    @Deprecated
    LDAP_MANAGED
}
