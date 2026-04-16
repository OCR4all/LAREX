package de.uniwue.zpd.dachs.larex.backend.config.auth;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "larex.auth")
public class AuthProvisioningProperties {

    private UserProvisioningMode userProvisioningMode = UserProvisioningMode.LOCAL;
    private final PrivateAccessTokens privateAccessTokens = new PrivateAccessTokens();

    public UserProvisioningMode getUserProvisioningMode() {
        return userProvisioningMode;
    }

    public void setUserProvisioningMode(UserProvisioningMode userProvisioningMode) {
        this.userProvisioningMode = userProvisioningMode;
    }

    public PrivateAccessTokens getPrivateAccessTokens() {
        return privateAccessTokens;
    }

    public static class PrivateAccessTokens {

        private int defaultExpiryDays = 30;
        private int maxExpiryDays = 90;
        private int maxActiveTokensPerWorkspace = 5;

        public int getDefaultExpiryDays() {
            return defaultExpiryDays;
        }

        public void setDefaultExpiryDays(int defaultExpiryDays) {
            this.defaultExpiryDays = defaultExpiryDays;
        }

        public int getMaxExpiryDays() {
            return maxExpiryDays;
        }

        public void setMaxExpiryDays(int maxExpiryDays) {
            this.maxExpiryDays = maxExpiryDays;
        }

        public int getMaxActiveTokensPerWorkspace() {
            return maxActiveTokensPerWorkspace;
        }

        public void setMaxActiveTokensPerWorkspace(int maxActiveTokensPerWorkspace) {
            this.maxActiveTokensPerWorkspace = maxActiveTokensPerWorkspace;
        }
    }
}
