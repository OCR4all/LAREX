package de.uniwue.zpd.dachs.larex.backend.config.auth;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "larex.auth")
public class AuthProvisioningProperties {

    private UserProvisioningMode userProvisioningMode = UserProvisioningMode.LOCAL;

    public UserProvisioningMode getUserProvisioningMode() {
        return userProvisioningMode;
    }

    public void setUserProvisioningMode(UserProvisioningMode userProvisioningMode) {
        this.userProvisioningMode = userProvisioningMode;
    }
}
