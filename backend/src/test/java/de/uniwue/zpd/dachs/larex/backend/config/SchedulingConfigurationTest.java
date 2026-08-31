package de.uniwue.zpd.dachs.larex.backend.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

import static org.assertj.core.api.Assertions.assertThat;

class SchedulingConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(SchedulingConfiguration.class);

    @Test
    void createsDefaultAndCoordinationSchedulers() {
        contextRunner
                .withPropertyValues(
                        "larex.scheduling.default-pool-size=3",
                        "larex.scheduling.coordination-pool-size=2"
                )
                .run(context -> {
                    ThreadPoolTaskScheduler defaultScheduler = context.getBean(
                            SchedulingConfiguration.DEFAULT_SCHEDULER,
                            ThreadPoolTaskScheduler.class
                    );
                    ThreadPoolTaskScheduler coordinationScheduler = context.getBean(
                            SchedulingConfiguration.COORDINATION_SCHEDULER,
                            ThreadPoolTaskScheduler.class
                    );

                    assertThat(defaultScheduler.getScheduledThreadPoolExecutor().getCorePoolSize()).isEqualTo(3);
                    assertThat(defaultScheduler.getThreadNamePrefix()).isEqualTo("scheduled-");
                    assertThat(coordinationScheduler.getScheduledThreadPoolExecutor().getCorePoolSize()).isEqualTo(2);
                    assertThat(coordinationScheduler.getThreadNamePrefix()).isEqualTo("coordination-");
                });
    }

    @Test
    void canDisableScheduling() {
        contextRunner
                .withPropertyValues("larex.scheduling.enabled=false")
                .run(context -> {
                    assertThat(context).doesNotHaveBean(SchedulingConfiguration.DEFAULT_SCHEDULER);
                    assertThat(context).doesNotHaveBean(SchedulingConfiguration.COORDINATION_SCHEDULER);
                });
    }
}
