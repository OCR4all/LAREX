package de.uniwue.zpd.dachs.larex.backend.config;

import jakarta.validation.constraints.Min;

public class SchedulerPoolProperties {

    @Min(1)
    private int poolSize;

    public SchedulerPoolProperties() {
    }

    public SchedulerPoolProperties(int poolSize) {
        this.poolSize = poolSize;
    }

    public int getPoolSize() {
        return poolSize;
    }

    public void setPoolSize(int poolSize) {
        this.poolSize = poolSize;
    }
}
