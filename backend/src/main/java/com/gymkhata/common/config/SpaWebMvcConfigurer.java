package com.gymkhata.common.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.resource.PathResourceResolver;

import java.io.IOException;

/**
 * Serves the built React PWA from classpath:/static/ in a single container.
 * Forwards client-side SPA routes (e.g. /app/**, /g/**, /login) to /index.html
 * while letting API and actuator paths pass through.
 */
@Configuration
public class SpaWebMvcConfigurer implements WebMvcConfigurer {

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/**")
                .addResourceLocations("classpath:/static/")
                .resourceChain(true)
                .addResolver(new PathResourceResolver() {
                    @Override
                    protected Resource getResource(String resourcePath, Resource location) throws IOException {
                        Resource requested = new ClassPathResource("/static/" + resourcePath);
                        if (requested.exists() && requested.isReadable()) {
                            return requested;
                        }
                        if (resourcePath.startsWith("api") || resourcePath.startsWith("actuator") || resourcePath.startsWith("swagger") || resourcePath.startsWith("api-docs")) {
                            return null;
                        }
                        Resource indexHtml = new ClassPathResource("/static/index.html");
                        return indexHtml.exists() && indexHtml.isReadable() ? indexHtml : null;
                    }
                });
    }
}
