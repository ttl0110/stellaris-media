package com.ltt.stellaris.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    private final StellarisProperties properties;

    public WebMvcConfig(StellarisProperties properties) {
        this.properties = properties;
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // Serve thumbnail cache directory
        if (properties.getThumbnail().getCachePath() != null) {
            String thumbnailPath = properties.getThumbnail().getCachePath().replace("\\", "/");
            if (!thumbnailPath.endsWith("/")) {
                thumbnailPath += "/";
            }
            registry.addResourceHandler("/thumbnails/**")
                    .addResourceLocations("file:" + thumbnailPath);
        }

        // WebJars resources
        registry.addResourceHandler("/webjars/**")
                .addResourceLocations("classpath:/META-INF/resources/webjars/");
    }
}
