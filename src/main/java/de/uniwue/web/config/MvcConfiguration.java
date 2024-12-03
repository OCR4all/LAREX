package de.uniwue.web.config;

import java.util.concurrent.TimeUnit;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.CacheControl;
import org.springframework.web.multipart.support.StandardServletMultipartResolver;
import org.springframework.web.servlet.ViewResolver;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.view.InternalResourceViewResolver;

/**
 * Basic MVC Configuration for the folder configuration and system library.
 *
 */
@Configuration
@ComponentScan(basePackages = "com.web")
@EnableWebMvc
public class MvcConfiguration implements WebMvcConfigurer {

	/*
	 * System library for openCV
	 */
	static {
		nu.pattern.OpenCV.loadLocally();
	}

	@Bean
	public ViewResolver getViewResolver() {
		InternalResourceViewResolver resolver = new InternalResourceViewResolver();
		resolver.setPrefix("/WEB-INF/views/");
		resolver.setSuffix(".jsp");
		return resolver;
	}

	@Bean(name = "multipartResolver")
	public StandardServletMultipartResolver createMultipartResolver() {
        return new StandardServletMultipartResolver();
	}

	@Override
	public void addResourceHandlers(ResourceHandlerRegistry registry) {
		registry.addResourceHandler("/resources/**").addResourceLocations("/resources/")
		.setCacheControl(CacheControl.maxAge(1, TimeUnit.HOURS).cachePublic());
	}
}
