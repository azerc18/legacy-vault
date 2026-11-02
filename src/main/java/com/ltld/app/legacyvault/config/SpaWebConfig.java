package com.ltld.app.legacyvault.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.resource.PathResourceResolver;

import java.io.IOException;

/**
 * Phục vụ FE (React build) từ classpath:/static/ và SPA fallback:
 * - File tĩnh tồn tại (/assets/*.js, favicon...) -> trả file.
 * - /api/** không khớp controller -> null -> 404 JSON như cũ.
 * - Đường dẫn còn lại (/vaults/123, /login...) -> index.html để React Router xử lý.
 * Khi dev local (static rỗng) không có index.html -> null, app vẫn chạy bình thường.
 */
@Configuration
public class SpaWebConfig implements WebMvcConfigurer {

    private static final Resource INDEX_HTML = new ClassPathResource("/static/index.html");

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/**")
                .addResourceLocations("classpath:/static/")
                .resourceChain(true)
                .addResolver(new SpaFallbackResolver());
    }

    private static class SpaFallbackResolver extends PathResourceResolver {
        @Override
        protected Resource getResource(String resourcePath, Resource location) throws IOException {
            Resource requested = location.createRelative(resourcePath);
            if (requested.exists() && requested.isReadable()) {
                return requested;
            }
            if (resourcePath.startsWith("api/")) {
                return null;
            }
            return INDEX_HTML.exists() ? INDEX_HTML : null;
        }
    }
}