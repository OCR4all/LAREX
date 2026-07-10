package de.uniwue.zpd.dachs.larex.backend.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

class AsyncExecutorPropertiesTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(TestConfig.class);

    @Test
    void bindsExecutorPoolsFromExistingPropertyNames() {
        contextRunner
                .withPropertyValues(
                        "larex.async.default.core-pool-size=3",
                        "larex.async.default.max-pool-size=6",
                        "larex.async.default.queue-capacity=300",
                        "larex.upload.chunk-size-bytes=2048",
                        "larex.upload.temp-directory=/tmp/upload-temp",
                        "larex.upload.session-timeout-hours=12",
                        "larex.upload.batch-size=20",
                        "larex.upload.max-concurrent-sessions=30",
                        "larex.upload.async.core-pool-size=4",
                        "larex.upload.async.max-pool-size=8",
                        "larex.upload.async.queue-capacity=400",
                        "larex.upload.index-async.core-pool-size=2",
                        "larex.upload.index-async.max-pool-size=5",
                        "larex.upload.index-async.queue-capacity=500",
                        "larex.upload.indexing.stale-threshold-ms=30000",
                        "larex.import.enabled=false",
                        "larex.import.allowed-paths=/tmp/import-a,/tmp/import-b",
                        "larex.import.max-scan-depth=12",
                        "larex.import.max-files-per-job=12000",
                        "larex.import.async.core-pool-size=5",
                        "larex.import.async.max-pool-size=6",
                        "larex.import.async.queue-capacity=600",
                        "larex.iiif.download-min-interval-ms=75",
                        "larex.iiif.max-image-download-bytes=104857600",
                        "larex.iiif.preview-async.core-pool-size=2",
                        "larex.iiif.preview-async.max-pool-size=3",
                        "larex.iiif.preview-async.queue-capacity=80",
                        "larex.iiif.download-async.core-pool-size=3",
                        "larex.iiif.download-async.max-pool-size=4",
                        "larex.iiif.download-async.queue-capacity=40",
                        "larex.annotation.read-cache.maximum-size=123",
                        "larex.annotation.read-cache.expire-after-access-minutes=20",
                        "larex.annotation.post-save.core-pool-size=6",
                        "larex.annotation.post-save.max-pool-size=7",
                        "larex.annotation.post-save.queue-capacity=700",
                        "larex.storage.default-quota-bytes=42",
                        "larex.storage.quota-enforcement-enabled=false",
                        "larex.storage.quota-warning-threshold=75.5",
                        "larex.storage.quota-refresh-debounce-ms=2500",
                        "larex.storage.quota-refresh.pool-size=9"
                )
                .run(context -> {
                    assertExecutorPool(context.getBean(AsyncExecutorProperties.class).getDefault(), 3, 6, 300);
                    UploadProperties uploadProperties = context.getBean(UploadProperties.class);
                    assertThat(uploadProperties.getChunkSizeBytes()).isEqualTo(2048);
                    assertThat(uploadProperties.getTempDirectory()).hasToString("/tmp/upload-temp");
                    assertThat(uploadProperties.getSessionTimeoutHours()).isEqualTo(12);
                    assertThat(uploadProperties.getBatchSize()).isEqualTo(20);
                    assertThat(uploadProperties.getMaxConcurrentSessions()).isEqualTo(30);
                    assertThat(uploadProperties.getIndexing().getStaleThresholdMs()).isEqualTo(30000);
                    assertExecutorPool(uploadProperties.getAsync(), 4, 8, 400);
                    assertExecutorPool(uploadProperties.getIndexAsync(), 2, 5, 500);
                    ImportProperties importProperties = context.getBean(ImportProperties.class);
                    assertThat(importProperties.isEnabled()).isFalse();
                    assertThat(importProperties.getAllowedPaths()).containsExactly("/tmp/import-a", "/tmp/import-b");
                    assertThat(importProperties.getMaxScanDepth()).isEqualTo(12);
                    assertThat(importProperties.getMaxFilesPerJob()).isEqualTo(12000);
                    assertExecutorPool(importProperties.getAsync(), 5, 6, 600);
                    IiifProperties iiifProperties = context.getBean(IiifProperties.class);
                    assertThat(iiifProperties.getDownloadMinIntervalMs()).isEqualTo(75);
                    assertThat(iiifProperties.getMaxImageDownloadBytes()).isEqualTo(104857600);
                    assertExecutorPool(iiifProperties.getPreviewAsync(), 2, 3, 80);
                    assertExecutorPool(iiifProperties.getDownloadAsync(), 3, 4, 40);
                    AnnotationProperties annotationProperties = context.getBean(AnnotationProperties.class);
                    assertThat(annotationProperties.getReadCache().getMaximumSize()).isEqualTo(123);
                    assertThat(annotationProperties.getReadCache().getExpireAfterAccessMinutes()).isEqualTo(20);
                    assertExecutorPool(annotationProperties.getPostSave(), 6, 7, 700);
                    StorageProperties storageProperties = context.getBean(StorageProperties.class);
                    assertThat(storageProperties.getDefaultQuotaBytes()).isEqualTo(42);
                    assertThat(storageProperties.isQuotaEnforcementEnabled()).isFalse();
                    assertThat(storageProperties.getQuotaWarningThreshold()).isEqualTo(75.5);
                    assertThat(storageProperties.getQuotaRefreshDebounceMs()).isEqualTo(2500);
                    assertThat(storageProperties.getQuotaRefresh().getPoolSize()).isEqualTo(9);
                });
    }

    @Test
    void keepsImportExecutorDefaultsWhenNoPropertiesAreConfigured() {
        contextRunner.run(context -> {
            assertExecutorPool(context.getBean(ImportProperties.class).getAsync(), 1, 2, 10);
            IiifProperties iiifProperties = context.getBean(IiifProperties.class);
            assertThat(iiifProperties.getDownloadMinIntervalMs()).isEqualTo(100);
            assertThat(iiifProperties.getMaxImageDownloadBytes()).isEqualTo(536870912);
            assertExecutorPool(iiifProperties.getPreviewAsync(), 2, 4, 100);
            assertExecutorPool(iiifProperties.getDownloadAsync(), 1, 2, 50);
        });
    }

    private void assertExecutorPool(ExecutorPoolProperties pool,
                                    int corePoolSize,
                                    int maxPoolSize,
                                    int queueCapacity) {
        assertThat(pool.getCorePoolSize()).isEqualTo(corePoolSize);
        assertThat(pool.getMaxPoolSize()).isEqualTo(maxPoolSize);
        assertThat(pool.getQueueCapacity()).isEqualTo(queueCapacity);
    }

    @Configuration
    @EnableConfigurationProperties({
            AsyncExecutorProperties.class,
            UploadProperties.class,
            ImportProperties.class,
            IiifProperties.class,
            AnnotationProperties.class,
            StorageProperties.class
    })
    static class TestConfig {
    }
}
