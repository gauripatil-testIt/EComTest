package com.ecomtest.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final String baseUrl;
    private final String storageDir;

    public WebConfig(@Value("${app.product-images.base-url}") String baseUrl,
                      @Value("${app.product-images.storage-dir}") String storageDir) {
        this.baseUrl = baseUrl;
        this.storageDir = storageDir;
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        String pattern = (baseUrl.endsWith("/") ? baseUrl : baseUrl + "/") + "**";
        String location = "file:" + (storageDir.endsWith("/") ? storageDir : storageDir + "/");
        registry.addResourceHandler(pattern)
                .addResourceLocations(location);
    }
}
