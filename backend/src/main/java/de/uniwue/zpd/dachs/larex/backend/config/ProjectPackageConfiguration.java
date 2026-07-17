package de.uniwue.zpd.dachs.larex.backend.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(ProjectPackageProperties.class)
public class ProjectPackageConfiguration {
}
