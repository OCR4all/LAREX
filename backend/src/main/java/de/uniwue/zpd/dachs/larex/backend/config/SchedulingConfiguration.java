package de.uniwue.zpd.dachs.larex.backend.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration(proxyBeanMethods = false)
@EnableScheduling
@ConditionalOnProperty(
        prefix = "larex.scheduling",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true
)
public class SchedulingConfiguration {
}
