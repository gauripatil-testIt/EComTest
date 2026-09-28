package com.ecomtest.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Value("${app.upload.dir}")
    private String uploadDir;

    @Value("${app.upload.base-url}")
    private String uploadBaseUrl;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        String location = uploadDir.endsWith("/") ? uploadDir : uploadDir + "/";
        String pattern = uploadBaseUrl.endsWith("/") ? uploadBaseUrl + "**" : uploadBaseUrl + "/**";
        registry.addResourceHandler(pattern)
                .addResourceLocations("file:" + location);
    }
}
