package de.uniwue.zpd.dachs.larex.backend.config;

import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "larex.actions")
public class ActionProperties {

    private String publicBaseUrl = "";
    private String publicBaseUrlAllowedOrigins = "";
    private boolean publicBaseUrlRequireHttps = true;
    private String endpointAllowedOrigins = "";
    private boolean endpointRequireHttps = true;
    private boolean endpointAllowInsecureLocal = true;
    private Map<String, String> endpointSecrets = new LinkedHashMap<>();
    private Dispatch dispatch = new Dispatch();
    private Timeout timeout = new Timeout();
    private Retention retention = new Retention();
    private Results results = new Results();

    public String getPublicBaseUrl() {
        return publicBaseUrl;
    }

    public void setPublicBaseUrl(String publicBaseUrl) {
        this.publicBaseUrl = publicBaseUrl;
    }

    public String getPublicBaseUrlAllowedOrigins() {
        return publicBaseUrlAllowedOrigins;
    }

    public void setPublicBaseUrlAllowedOrigins(String publicBaseUrlAllowedOrigins) {
        this.publicBaseUrlAllowedOrigins = publicBaseUrlAllowedOrigins;
    }

    public boolean isPublicBaseUrlRequireHttps() {
        return publicBaseUrlRequireHttps;
    }

    public void setPublicBaseUrlRequireHttps(boolean publicBaseUrlRequireHttps) {
        this.publicBaseUrlRequireHttps = publicBaseUrlRequireHttps;
    }

    public String getEndpointAllowedOrigins() {
        return endpointAllowedOrigins;
    }

    public void setEndpointAllowedOrigins(String endpointAllowedOrigins) {
        this.endpointAllowedOrigins = endpointAllowedOrigins;
    }

    public boolean isEndpointRequireHttps() {
        return endpointRequireHttps;
    }

    public void setEndpointRequireHttps(boolean endpointRequireHttps) {
        this.endpointRequireHttps = endpointRequireHttps;
    }

    public boolean isEndpointAllowInsecureLocal() {
        return endpointAllowInsecureLocal;
    }

    public void setEndpointAllowInsecureLocal(boolean endpointAllowInsecureLocal) {
        this.endpointAllowInsecureLocal = endpointAllowInsecureLocal;
    }

    public Map<String, String> getEndpointSecrets() {
        return endpointSecrets;
    }

    public void setEndpointSecrets(Map<String, String> endpointSecrets) {
        this.endpointSecrets = endpointSecrets == null ? new LinkedHashMap<>() : endpointSecrets;
    }

    public Dispatch getDispatch() {
        return dispatch;
    }

    public void setDispatch(Dispatch dispatch) {
        this.dispatch = dispatch;
    }

    public Timeout getTimeout() {
        return timeout;
    }

    public void setTimeout(Timeout timeout) {
        this.timeout = timeout;
    }

    public Retention getRetention() {
        return retention;
    }

    public void setRetention(Retention retention) {
        this.retention = retention;
    }

    public Results getResults() {
        return results;
    }

    public void setResults(Results results) {
        this.results = results;
    }

    public static class Dispatch {
        private int maxAttempts = 3;
        private long retryBackoffMs = 3_000;

        public int getMaxAttempts() {
            return maxAttempts;
        }

        public void setMaxAttempts(int maxAttempts) {
            this.maxAttempts = maxAttempts;
        }

        public long getRetryBackoffMs() {
            return retryBackoffMs;
        }

        public void setRetryBackoffMs(long retryBackoffMs) {
            this.retryBackoffMs = retryBackoffMs;
        }
    }

    public static class Timeout {
        private long dispatchMinutes = 5;
        private long heartbeatMinutes = 30;

        public long getDispatchMinutes() {
            return dispatchMinutes;
        }

        public void setDispatchMinutes(long dispatchMinutes) {
            this.dispatchMinutes = dispatchMinutes;
        }

        public long getHeartbeatMinutes() {
            return heartbeatMinutes;
        }

        public void setHeartbeatMinutes(long heartbeatMinutes) {
            this.heartbeatMinutes = heartbeatMinutes;
        }
    }

    public static class Retention {
        private long terminalDays = 30;

        public long getTerminalDays() {
            return terminalDays;
        }

        public void setTerminalDays(long terminalDays) {
            this.terminalDays = terminalDays;
        }
    }

    public static class Results {
        private int maxFiles = 500;
        private long maxFileBytes = 536_870_912;
        private long maxTotalBytes = 2_147_483_648L;

        public int getMaxFiles() {
            return maxFiles;
        }

        public void setMaxFiles(int maxFiles) {
            this.maxFiles = maxFiles;
        }

        public long getMaxFileBytes() {
            return maxFileBytes;
        }

        public void setMaxFileBytes(long maxFileBytes) {
            this.maxFileBytes = maxFileBytes;
        }

        public long getMaxTotalBytes() {
            return maxTotalBytes;
        }

        public void setMaxTotalBytes(long maxTotalBytes) {
            this.maxTotalBytes = maxTotalBytes;
        }
    }
}
