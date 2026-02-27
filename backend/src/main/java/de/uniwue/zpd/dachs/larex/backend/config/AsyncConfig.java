package de.uniwue.zpd.dachs.larex.backend.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.ThreadPoolExecutor;

@Configuration
@EnableScheduling
public class AsyncConfig {

    private static final Logger logger = LoggerFactory.getLogger(AsyncConfig.class);

    @Value("${larex.upload.async.core-pool-size:2}")
    private int corePoolSize;

    @Value("${larex.upload.async.max-pool-size:5}")
    private int maxPoolSize;

    @Value("${larex.upload.async.queue-capacity:100}")
    private int queueCapacity;

    @Value("${larex.upload.index-async.core-pool-size:1}")
    private int indexCorePoolSize;

    @Value("${larex.upload.index-async.max-pool-size:2}")
    private int indexMaxPoolSize;

    @Value("${larex.upload.index-async.queue-capacity:200}")
    private int indexQueueCapacity;

    @Value("${larex.async.default.core-pool-size:2}")
    private int defaultCorePoolSize;

    @Value("${larex.async.default.max-pool-size:4}")
    private int defaultMaxPoolSize;

    @Value("${larex.async.default.queue-capacity:100}")
    private int defaultQueueCapacity;

    @Bean(name = "taskExecutor")
    public ThreadPoolTaskExecutor taskExecutor() {
        logger.info("Initializing default async task executor with core pool size: {}, max pool size: {}, queue capacity: {}",
                defaultCorePoolSize, defaultMaxPoolSize, defaultQueueCapacity);

        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(defaultCorePoolSize);
        executor.setMaxPoolSize(defaultMaxPoolSize);
        executor.setQueueCapacity(defaultQueueCapacity);
        executor.setThreadNamePrefix("async-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(60);
        executor.initialize();
        return executor;
    }

    @Bean(name = "uploadTaskExecutor")
    public ThreadPoolTaskExecutor uploadTaskExecutor() {
        logger.info("Initializing upload task executor with core pool size: {}, max pool size: {}, queue capacity: {}",
                corePoolSize, maxPoolSize, queueCapacity);

        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(corePoolSize);
        executor.setMaxPoolSize(maxPoolSize);
        executor.setQueueCapacity(queueCapacity);
        executor.setThreadNamePrefix("upload-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(60);
        executor.initialize();
        return executor;
    }

    @Bean(name = "uploadIndexTaskExecutor")
    public ThreadPoolTaskExecutor uploadIndexTaskExecutor() {
        logger.info("Initializing upload index task executor with core pool size: {}, max pool size: {}, queue capacity: {}",
                indexCorePoolSize, indexMaxPoolSize, indexQueueCapacity);

        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(indexCorePoolSize);
        executor.setMaxPoolSize(indexMaxPoolSize);
        executor.setQueueCapacity(indexQueueCapacity);
        executor.setThreadNamePrefix("upload-index-");
        // Apply backpressure instead of failing uploads when many pages are queued for indexing.
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(120);
        executor.initialize();
        return executor;
    }

    @Bean(name = "importTaskExecutor")
    public ThreadPoolTaskExecutor importTaskExecutor() {
        logger.info("Initializing import task executor");

        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(2);
        executor.setQueueCapacity(10);
        executor.setThreadNamePrefix("import-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(120);
        executor.initialize();
        return executor;
    }
}
