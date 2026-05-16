package de.uniwue.zpd.dachs.larex.backend.config;

import jakarta.validation.Valid;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "larex.storage")
public class StorageAsyncProperties {

    @Valid
    private SchedulerPoolProperties quotaRefresh = new SchedulerPoolProperties(1);

    public SchedulerPoolProperties getQuotaRefresh() {
        return quotaRefresh;
    }

    public void setQuotaRefresh(SchedulerPoolProperties quotaRefresh) {
        this.quotaRefresh = quotaRefresh;
    }
}
