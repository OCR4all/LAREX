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
                        "larex.import.async.core-pool-size=5",
                        "larex.import.async.max-pool-size=6",
                        "larex.import.async.queue-capacity=600",
                        "larex.annotation.post-save.core-pool-size=6",
                        "larex.annotation.post-save.max-pool-size=7",
                        "larex.annotation.post-save.queue-capacity=700",
                        "larex.storage.quota-refresh.pool-size=9"
                )
                .run(context -> {
                    assertExecutorPool(context.getBean(AsyncExecutorProperties.class).getDefault(), 3, 6, 300);
                    assertExecutorPool(context.getBean(UploadAsyncProperties.class).getAsync(), 4, 8, 400);
                    assertExecutorPool(context.getBean(UploadAsyncProperties.class).getIndexAsync(), 2, 5, 500);
                    assertExecutorPool(context.getBean(ImportAsyncProperties.class).getAsync(), 5, 6, 600);
                    assertExecutorPool(context.getBean(AnnotationAsyncProperties.class).getPostSave(), 6, 7, 700);
                    assertThat(context.getBean(StorageAsyncProperties.class).getQuotaRefresh().getPoolSize()).isEqualTo(9);
                });
    }

    @Test
    void keepsImportExecutorDefaultsWhenNoPropertiesAreConfigured() {
        contextRunner.run(context ->
                assertExecutorPool(context.getBean(ImportAsyncProperties.class).getAsync(), 1, 2, 10)
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
            ImportAsyncProperties.class,
            AnnotationAsyncProperties.class,
            StorageAsyncProperties.class
    })
    static class TestConfig {
    }
}
