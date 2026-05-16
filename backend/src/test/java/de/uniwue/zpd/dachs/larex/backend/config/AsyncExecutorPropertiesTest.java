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
                        "larex.upload.async.core-pool-size=4",
                        "larex.upload.async.max-pool-size=8",
                        "larex.upload.async.queue-capacity=400",
                        "larex.upload.index-async.core-pool-size=2",
                        "larex.upload.index-async.max-pool-size=5",
                        "larex.upload.index-async.queue-capacity=500",
                        "larex.import.enabled=false",
                        "larex.import.allowed-paths=/tmp/import-a,/tmp/import-b",
                        "larex.import.max-scan-depth=12",
                        "larex.import.max-files-per-job=12000",
                        "larex.import.async.core-pool-size=5",
                        "larex.import.async.max-pool-size=6",
                        "larex.import.async.queue-capacity=600",
                        "larex.annotation.read-cache.maximum-size=123",
                        "larex.annotation.read-cache.expire-after-access-minutes=20",
                        "larex.annotation.post-save.core-pool-size=6",
                        "larex.annotation.post-save.max-pool-size=7",
                        "larex.annotation.post-save.queue-capacity=700",
                        "larex.storage.quota-refresh.pool-size=9"
                )
                .run(context -> {
                    assertExecutorPool(context.getBean(AsyncExecutorProperties.class).getDefault(), 3, 6, 300);
                    assertExecutorPool(context.getBean(UploadAsyncProperties.class).getAsync(), 4, 8, 400);
                    assertExecutorPool(context.getBean(UploadAsyncProperties.class).getIndexAsync(), 2, 5, 500);
                    ImportProperties importProperties = context.getBean(ImportProperties.class);
                    assertThat(importProperties.isEnabled()).isFalse();
                    assertThat(importProperties.getAllowedPaths()).containsExactly("/tmp/import-a", "/tmp/import-b");
                    assertThat(importProperties.getMaxScanDepth()).isEqualTo(12);
                    assertThat(importProperties.getMaxFilesPerJob()).isEqualTo(12000);
                    assertExecutorPool(importProperties.getAsync(), 5, 6, 600);
                    AnnotationProperties annotationProperties = context.getBean(AnnotationProperties.class);
                    assertThat(annotationProperties.getReadCache().getMaximumSize()).isEqualTo(123);
                    assertThat(annotationProperties.getReadCache().getExpireAfterAccessMinutes()).isEqualTo(20);
                    assertExecutorPool(annotationProperties.getPostSave(), 6, 7, 700);
                    assertThat(context.getBean(StorageAsyncProperties.class).getQuotaRefresh().getPoolSize()).isEqualTo(9);
                });
    }

    @Test
    void keepsImportExecutorDefaultsWhenNoPropertiesAreConfigured() {
        contextRunner.run(context ->
                assertExecutorPool(context.getBean(ImportProperties.class).getAsync(), 1, 2, 10)
        );
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
            UploadAsyncProperties.class,
            ImportProperties.class,
            AnnotationProperties.class,
            StorageAsyncProperties.class
    })
    static class TestConfig {
    }
}
