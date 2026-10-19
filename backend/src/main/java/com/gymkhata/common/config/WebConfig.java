package com.gymkhata.common.config;

import com.gymkhata.tenant.TenantStatusInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@EnableAsync
public class WebConfig implements WebMvcConfigurer {

    private final TenantStatusInterceptor tenantStatusInterceptor;

    public WebConfig(TenantStatusInterceptor tenantStatusInterceptor) {
        this.tenantStatusInterceptor = tenantStatusInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // Suspended gym owners cannot use tenant APIs: 403 ACCOUNT_SUSPENDED
        registry.addInterceptor(tenantStatusInterceptor)
                .addPathPatterns("/api/v1/**")
                .excludePathPatterns("/api/v1/auth/**", "/api/v1/config/**", "/api/v1/pay/**", "/api/v1/admin/**");
    }
}
