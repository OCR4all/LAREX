package de.uniwue.zpd.dachs.larex.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "larex.notifications.bridge")
public class NotificationBridgeProperties {

    private boolean enabled = true;
    private String url = "http://frontend:3000/api/notifications/broadcast";
    private String secret = "larex-notification-bridge-dev-secret";

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getSecret() {
        return secret;
    }

    public void setSecret(String secret) {
        this.secret = secret;
    }

    public boolean isConfigured() {
        return hasText(url) && hasText(secret);
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
