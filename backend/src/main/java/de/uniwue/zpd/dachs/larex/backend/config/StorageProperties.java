package de.uniwue.zpd.dachs.larex.backend.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "larex.storage")
public class StorageProperties {

    @Min(1)
    private long defaultQuotaBytes = 1073741824L;

    private boolean quotaEnforcementEnabled = true;

    @DecimalMin("0.0")
    private double quotaWarningThreshold = 80.0;

    @Min(1)
    private long quotaRefreshDebounceMs = 1500;

    @Valid
    private SchedulerPoolProperties quotaRefresh = new SchedulerPoolProperties(1);

    public long getDefaultQuotaBytes() {
        return defaultQuotaBytes;
    }

    public void setDefaultQuotaBytes(long defaultQuotaBytes) {
        this.defaultQuotaBytes = defaultQuotaBytes;
    }

    public boolean isQuotaEnforcementEnabled() {
        return quotaEnforcementEnabled;
    }

    public void setQuotaEnforcementEnabled(boolean quotaEnforcementEnabled) {
        this.quotaEnforcementEnabled = quotaEnforcementEnabled;
    }

    public double getQuotaWarningThreshold() {
        return quotaWarningThreshold;
    }

    public void setQuotaWarningThreshold(double quotaWarningThreshold) {
        this.quotaWarningThreshold = quotaWarningThreshold;
    }

    public long getQuotaRefreshDebounceMs() {
        return quotaRefreshDebounceMs;
    }

    public void setQuotaRefreshDebounceMs(long quotaRefreshDebounceMs) {
        this.quotaRefreshDebounceMs = quotaRefreshDebounceMs;
    }

    public SchedulerPoolProperties getQuotaRefresh() {
        return quotaRefresh;
    }

    public void setQuotaRefresh(SchedulerPoolProperties quotaRefresh) {
        this.quotaRefresh = quotaRefresh;
    }
}
