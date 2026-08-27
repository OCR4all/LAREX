package de.uniwue.zpd.dachs.larex.backend.config;

import jakarta.validation.Valid;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "larex.async")
public class AsyncExecutorProperties {

    private boolean waitForTasksToCompleteOnShutdown = true;

    @Valid
    private ExecutorPoolProperties defaultExecutor = new ExecutorPoolProperties(2, 4, 100);

    public boolean isWaitForTasksToCompleteOnShutdown() {
        return waitForTasksToCompleteOnShutdown;
    }

    public void setWaitForTasksToCompleteOnShutdown(boolean waitForTasksToCompleteOnShutdown) {
        this.waitForTasksToCompleteOnShutdown = waitForTasksToCompleteOnShutdown;
    }

    public ExecutorPoolProperties getDefault() {
        return defaultExecutor;
    }

    public void setDefault(ExecutorPoolProperties defaultExecutor) {
        this.defaultExecutor = defaultExecutor;
    }
}
