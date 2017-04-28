package com.web.config;

import java.io.File;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;

import org.opencv.core.Core;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.ViewResolver;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurerAdapter;
import org.springframework.web.servlet.view.InternalResourceViewResolver;

/**
 * Basic MVC Configuration for the folder configuration and system library.
 * 
 */
@Configuration
@ComponentScan(basePackages = "com.web")
@EnableWebMvc
public class MvcConfiguration extends WebMvcConfigurerAdapter {

	/*
	 * Systemlibrary for openCV 
	 */
	static {
		System.loadLibrary(Core.NATIVE_LIBRARY_NAME);

		try {
            String path = getPath();
            if(new File(path).exists()){
            	System.load(path);
            }
		} catch (UnsupportedEncodingException e1) {
            // TODO Auto-generated catch block
            e1.printStackTrace();
        }
}


	
	@Bean
	public ViewResolver getViewResolver() {
		InternalResourceViewResolver resolver = new InternalResourceViewResolver();
		resolver.setPrefix("/WEB-INF/views/");
		resolver.setSuffix(".jsp");
		return resolver;
	}

	@Override
	public void addResourceHandlers(ResourceHandlerRegistry registry) {
		registry.addResourceHandler("/resources/**").addResourceLocations("/resources/");
	}

    public static String getPath() throws UnsupportedEncodingException {
        String path = new MvcConfiguration().getClass().getClassLoader().getResource("").getPath();
        String fullPath = URLDecoder.decode(path, "UTF-8");
        String pathArr[] = fullPath.split("/classes/");
        fullPath = pathArr[0] + "/lib/opencv.dll";

        return fullPath;
    }
}
