package de.uniwue.zpd.dachs.larex.backend.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import jakarta.annotation.PostConstruct;
import java.nio.file.Path;

@Configuration
@EnableConfigurationProperties(ProfileImageProperties.class)
public class StaticResourceConfig implements WebMvcConfigurer {

    private static final Logger logger = LoggerFactory.getLogger(StaticResourceConfig.class);

    private final ProfileImageProperties profileImageProperties;
    private Path profileImagesPath;

    public StaticResourceConfig(ProfileImageProperties profileImageProperties) {
        this.profileImageProperties = profileImageProperties;
    }

    @PostConstruct
    private void initializeResourcePaths() {
        try {
            profileImagesPath = profileImageProperties.getPath().toAbsolutePath().normalize();
            logger.info("Profile image resource directory resolved to: {}", profileImagesPath);
        } catch (Exception e) {
            logger.error("Failed to resolve profile image resource directory: {}", profileImageProperties.getPath(), e);
        }
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // Serve profile images
        registry.addResourceHandler("/api/profile/images/**")
                .addResourceLocations(profileImagesPath.toUri().toString())
                .setCachePeriod(86400); // Cache for 24 hours

        // Note: Project files are now served via FileController for better security
    }
}
