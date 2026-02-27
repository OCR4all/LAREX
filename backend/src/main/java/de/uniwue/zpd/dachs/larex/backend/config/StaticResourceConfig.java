package de.uniwue.zpd.dachs.larex.backend.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import jakarta.annotation.PostConstruct;
import java.nio.file.Path;
import java.nio.file.Paths;

@Configuration
public class StaticResourceConfig implements WebMvcConfigurer {

    private static final Logger logger = LoggerFactory.getLogger(StaticResourceConfig.class);

    @Value("${app.upload.profile-images.path:uploads/profile-images}")
    private String profileImagesPath;

    @Value("${file.upload-dir}")
    private String uploadDir;

    @PostConstruct
    private void initializeResourcePaths() {
        // Convert to absolute path to ensure correct resolution
        try {
            Path uploadPath = Paths.get(uploadDir).toAbsolutePath();
            this.uploadDir = uploadPath.toString();
            logger.info("Static resource upload directory resolved to: {}", this.uploadDir);
        } catch (Exception e) {
            logger.error("Failed to resolve upload directory: {}", uploadDir, e);
        }
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // Serve profile images
        registry.addResourceHandler("/api/profile/images/**")
                .addResourceLocations("file:" + profileImagesPath + "/")
                .setCachePeriod(86400); // Cache for 24 hours

        // Note: Project files are now served via FileController for better security
    }
}