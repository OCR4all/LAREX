package de.uniwue.zpd.dachs.larex.backend.config.auth;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AuthProvisioningPropertiesTest {

    @Test
    @SuppressWarnings("deprecation")
    void legacyLdapManagedModeIsTreatedAsExternallyManaged() {
        AuthProvisioningProperties properties = new AuthProvisioningProperties();
        properties.setUserProvisioningMode(UserProvisioningMode.LDAP_MANAGED);

        assertEquals(UserProvisioningMode.EXTERNALLY_MANAGED, properties.getUserProvisioningMode());
    }
}
