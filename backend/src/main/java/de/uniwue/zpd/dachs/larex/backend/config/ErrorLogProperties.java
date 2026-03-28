package de.uniwue.zpd.dachs.larex.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "larex.error-log")
public class ErrorLogProperties {

    private int retentionDays = 30;
    private int stackTraceMaxLength = 16000;

    public int getRetentionDays() {
        return retentionDays;
    }

    public void setRetentionDays(int retentionDays) {
        this.retentionDays = retentionDays;
    }

    public int getStackTraceMaxLength() {
        return stackTraceMaxLength;
    }

    public void setStackTraceMaxLength(int stackTraceMaxLength) {
        this.stackTraceMaxLength = stackTraceMaxLength;
    }
}
