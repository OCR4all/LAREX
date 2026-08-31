package de.uniwue.zpd.dachs.larex.backend.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

@Configuration(proxyBeanMethods = false)
@EnableScheduling
@EnableConfigurationProperties(SchedulingProperties.class)
@ConditionalOnProperty(
        prefix = "larex.scheduling",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true
)
public class SchedulingConfiguration {

    public static final String DEFAULT_SCHEDULER = "taskScheduler";
    public static final String COORDINATION_SCHEDULER = "coordinationTaskScheduler";

    private final SchedulingProperties properties;

    public SchedulingConfiguration(SchedulingProperties properties) {
        this.properties = properties;
    }

    @Bean(name = DEFAULT_SCHEDULER)
    public ThreadPoolTaskScheduler taskScheduler() {
        return taskScheduler(properties.getDefaultPoolSize(), "scheduled-");
    }

    @Bean(name = COORDINATION_SCHEDULER)
    public ThreadPoolTaskScheduler coordinationTaskScheduler() {
        return taskScheduler(properties.getCoordinationPoolSize(), "coordination-");
    }

    private ThreadPoolTaskScheduler taskScheduler(int poolSize, String threadNamePrefix) {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(poolSize);
        scheduler.setThreadNamePrefix(threadNamePrefix);
        scheduler.setRemoveOnCancelPolicy(true);
        scheduler.setExecuteExistingDelayedTasksAfterShutdownPolicy(false);
        scheduler.setContinueExistingPeriodicTasksAfterShutdownPolicy(false);
        scheduler.setWaitForTasksToCompleteOnShutdown(false);
        return scheduler;
    }
}
