package de.uniwue.zpd.dachs.larex.backend.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class MvcAsyncRequestTimeoutConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withInitializer(new ConfigDataApplicationContextInitializer());

    @Test
    void givesStreamingResponsesAThirtyMinuteDefaultTimeout() {
        contextRunner.run(context -> assertThat(context.getEnvironment()
                .getProperty("spring.mvc.async.request-timeout"))
                .isEqualTo("30m"));
    }

    @Test
    void allowsDeploymentsToOverrideTheStreamingTimeout() {
        contextRunner
                .withPropertyValues("LAREX_ASYNC_REQUEST_TIMEOUT=45m")
                .run(context -> assertThat(context.getEnvironment()
                        .getProperty("spring.mvc.async.request-timeout"))
                        .isEqualTo("45m"));
    }
}
