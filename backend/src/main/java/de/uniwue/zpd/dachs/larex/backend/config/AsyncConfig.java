package de.uniwue.zpd.dachs.larex.backend.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadPoolExecutor;

@Configuration
@EnableConfigurationProperties({
        AsyncExecutorProperties.class,
        UploadProperties.class,
        ImportProperties.class,
        IiifProperties.class,
        AnnotationProperties.class,
        StorageProperties.class,
        ProjectExportProperties.class
})
public class AsyncConfig {

    private static final Logger logger = LoggerFactory.getLogger(AsyncConfig.class);

    private final AsyncExecutorProperties asyncProperties;
    private final UploadProperties uploadProperties;
    private final ImportProperties importProperties;
    private final IiifProperties iiifProperties;
    private final AnnotationProperties annotationProperties;
    private final StorageProperties storageProperties;
    private final BackupProperties backupProperties;
    private final ProjectExportProperties projectExportProperties;

    public AsyncConfig(AsyncExecutorProperties asyncProperties,
                       UploadProperties uploadProperties,
                       ImportProperties importProperties,
                       IiifProperties iiifProperties,
                       AnnotationProperties annotationProperties,
                       StorageProperties storageProperties,
                       BackupProperties backupProperties,
                       ProjectExportProperties projectExportProperties) {
        this.asyncProperties = asyncProperties;
        this.uploadProperties = uploadProperties;
        this.importProperties = importProperties;
        this.iiifProperties = iiifProperties;
        this.annotationProperties = annotationProperties;
        this.storageProperties = storageProperties;
        this.backupProperties = backupProperties;
        this.projectExportProperties = projectExportProperties;
    }

    @Bean(name = "taskExecutor")
    public ThreadPoolTaskExecutor taskExecutor() {
        return taskExecutor("default async", asyncProperties.getDefault(), "async-", 60, null);
    }

    @Bean(name = "uploadTaskExecutor")
    public ThreadPoolTaskExecutor uploadTaskExecutor() {
        return taskExecutor("upload", uploadProperties.getAsync(), "upload-", 60, null);
    }

    @Bean(name = "uploadIndexTaskExecutor")
    public ThreadPoolTaskExecutor uploadIndexTaskExecutor() {
        return taskExecutor(
                "upload index",
                uploadProperties.getIndexAsync(),
                "upload-index-",
                120,
                new ThreadPoolExecutor.CallerRunsPolicy()
        );
    }

    @Bean(name = "importTaskExecutor")
    public ThreadPoolTaskExecutor importTaskExecutor() {
        return taskExecutor("import", importProperties.getAsync(), "import-", 120, null);
    }

    @Bean(name = "backupTaskExecutor")
    public ThreadPoolTaskExecutor backupTaskExecutor() {
        return taskExecutor("backup", backupProperties.getAsync(), "backup-", 120, null);
    }

    @Bean(name = "projectExportTaskExecutor")
    public ThreadPoolTaskExecutor projectExportTaskExecutor() {
        return taskExecutor("project export", projectExportProperties.getAsync(), "project-export-", 120, null);
    }

    @Bean(name = "actionNotificationTaskExecutor")
    public ThreadPoolTaskExecutor actionNotificationTaskExecutor() {
        return taskExecutor("Action notification", asyncProperties.getDefault(), "action-notification-", 30, null);
    }

    @Bean(name = "iiifPreviewTaskExecutor")
    public ThreadPoolTaskExecutor iiifPreviewTaskExecutor() {
        return taskExecutor("IIIF preview", iiifProperties.getPreviewAsync(), "iiif-preview-", 60, null);
    }

    @Bean(name = "iiifDownloadTaskExecutor")
    public ThreadPoolTaskExecutor iiifDownloadTaskExecutor() {
        return taskExecutor("IIIF download", iiifProperties.getDownloadAsync(), "iiif-download-", 120, null);
    }

    @Bean(name = "annotationPostSaveTaskExecutor")
    public ThreadPoolTaskExecutor annotationPostSaveTaskExecutor() {
        return taskExecutor("annotation post-save", annotationProperties.getPostSave(), "annotation-post-save-", 120, null);
    }

    @Bean(name = "quotaRefreshTaskScheduler")
    public ThreadPoolTaskScheduler quotaRefreshTaskScheduler() {
        SchedulerPoolProperties quotaRefresh = storageProperties.getQuotaRefresh();
        logger.info("Initializing quota refresh task scheduler with pool size: {}", quotaRefresh.getPoolSize());

        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(quotaRefresh.getPoolSize());
        scheduler.setThreadNamePrefix("quota-refresh-");
        scheduler.setContinueExistingPeriodicTasksAfterShutdownPolicy(false);
        scheduler.setExecuteExistingDelayedTasksAfterShutdownPolicy(false);
        scheduler.setWaitForTasksToCompleteOnShutdown(false);
        scheduler.initialize();
        return scheduler;
    }

    @Bean(name = "actionResultTaskScheduler")
    public ThreadPoolTaskScheduler actionResultTaskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(1);
        scheduler.setThreadNamePrefix("action-result-");
        scheduler.setExecuteExistingDelayedTasksAfterShutdownPolicy(false);
        scheduler.setWaitForTasksToCompleteOnShutdown(false);
        scheduler.initialize();
        return scheduler;
    }

    @Bean(name = "uploadRealtimeTaskScheduler")
    public ThreadPoolTaskScheduler uploadRealtimeTaskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(uploadProperties.getRealtime().getSchedulerPoolSize());
        scheduler.setThreadNamePrefix("upload-realtime-");
        scheduler.setRemoveOnCancelPolicy(true);
        scheduler.setExecuteExistingDelayedTasksAfterShutdownPolicy(false);
        scheduler.setWaitForTasksToCompleteOnShutdown(false);
        scheduler.initialize();
        return scheduler;
    }

    private ThreadPoolTaskExecutor taskExecutor(String label,
                                                ExecutorPoolProperties pool,
                                                String threadNamePrefix,
                                                int awaitTerminationSeconds,
                                                RejectedExecutionHandler rejectedExecutionHandler) {
        logger.info("Initializing {} task executor with core pool size: {}, max pool size: {}, queue capacity: {}",
                label, pool.getCorePoolSize(), pool.getMaxPoolSize(), pool.getQueueCapacity());

        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(pool.getCorePoolSize());
        executor.setMaxPoolSize(pool.getMaxPoolSize());
        executor.setQueueCapacity(pool.getQueueCapacity());
        executor.setThreadNamePrefix(threadNamePrefix);
        if (rejectedExecutionHandler != null) {
            executor.setRejectedExecutionHandler(rejectedExecutionHandler);
        }
        boolean waitForTasksToCompleteOnShutdown = asyncProperties.isWaitForTasksToCompleteOnShutdown();
        executor.setWaitForTasksToCompleteOnShutdown(waitForTasksToCompleteOnShutdown);
        executor.setAwaitTerminationSeconds(waitForTasksToCompleteOnShutdown ? awaitTerminationSeconds : 0);
        executor.initialize();
        return executor;
    }
}
