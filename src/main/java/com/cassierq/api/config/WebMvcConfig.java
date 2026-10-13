package com.cassierq.api.config;

import java.nio.file.Path;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@RequiredArgsConstructor
public class WebMvcConfig implements WebMvcConfigurer {

    private final UploadProperties uploadProperties;

    // File di {baseDir}/stores/{kode-toko}/products/{id}/main|gallery/xxx.jpg
    // (lihat FileStorageService) diserve persis di URL /uploads/stores/... yang sama.
    // File lama berpola /uploads/products/{id}/xxx.jpg tetap terlayani.
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        String location = "file:" + Path.of(uploadProperties.baseDir()).toAbsolutePath().normalize() + "/";
        registry.addResourceHandler("/uploads/**").addResourceLocations(location);
    }
}
