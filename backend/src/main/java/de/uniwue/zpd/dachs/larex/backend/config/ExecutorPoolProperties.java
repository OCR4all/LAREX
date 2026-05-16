package de.uniwue.zpd.dachs.larex.backend.config;

import jakarta.validation.constraints.Min;

public class ExecutorPoolProperties {

    @Min(1)
    private int corePoolSize;

    @Min(1)
    private int maxPoolSize;

    @Min(0)
    private int queueCapacity;

    public ExecutorPoolProperties() {
    }

    public ExecutorPoolProperties(int corePoolSize, int maxPoolSize, int queueCapacity) {
        this.corePoolSize = corePoolSize;
        this.maxPoolSize = maxPoolSize;
        this.queueCapacity = queueCapacity;
    }

    public int getCorePoolSize() {
        return corePoolSize;
    }

    public void setCorePoolSize(int corePoolSize) {
        this.corePoolSize = corePoolSize;
    }

    public int getMaxPoolSize() {
        return maxPoolSize;
    }

    public void setMaxPoolSize(int maxPoolSize) {
        this.maxPoolSize = maxPoolSize;
    }

    public int getQueueCapacity() {
        return queueCapacity;
    }

    public void setQueueCapacity(int queueCapacity) {
        this.queueCapacity = queueCapacity;
    }
}
