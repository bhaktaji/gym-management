package com.gymkhata.common.config;

import org.springframework.boot.autoconfigure.web.servlet.WebMvcRegistrations;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Serves the built single-page app (frontend/dist copied into
 * src/main/resources/static by scripts/build-all.ps1). Unknown non-API paths
 * fall back to index.html so client-side routes work on a hard refresh.
 */
@Configuration
public class SpaConfig implements WebMvcConfigurer {

    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
        registry.addViewController("/{path:^(?!api|actuator|swagger-ui|api-docs)[^\\.]*}").setViewName("forward:/index.html");
        registry.addViewController("/{path:^(?!api|actuator|swagger-ui|api-docs)[^\\.]*}/**").setViewName("forward:/index.html");
    }
}
