package de.uniwue;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.servlet.support.SpringBootServletInitializer;

/**
 * Main Spring Boot Application class for LAREX.
 * This replaces the traditional web.xml configuration and enables embedded Tomcat deployment.
 */
@SpringBootApplication
public class LarexApplication extends SpringBootServletInitializer {

    /*
     * System library for openCV
     */
    static {
        nu.pattern.OpenCV.loadLocally();
    }

    @Override
    protected SpringApplicationBuilder configure(SpringApplicationBuilder application) {
        return application.sources(LarexApplication.class);
    }

    public static void main(String[] args) {
        SpringApplication.run(LarexApplication.class, args);
    }
}
