package de.uniwue.zpd.dachs.larex.backend.config;

import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "larex.scheduling")
public class SchedulingProperties {

    @Min(1)
    private int defaultPoolSize = 4;

    @Min(1)
    private int coordinationPoolSize = 2;

    public int getDefaultPoolSize() {
        return defaultPoolSize;
    }

    public void setDefaultPoolSize(int defaultPoolSize) {
        this.defaultPoolSize = defaultPoolSize;
    }

    public int getCoordinationPoolSize() {
        return coordinationPoolSize;
    }

    public void setCoordinationPoolSize(int coordinationPoolSize) {
        this.coordinationPoolSize = coordinationPoolSize;
    }
}
