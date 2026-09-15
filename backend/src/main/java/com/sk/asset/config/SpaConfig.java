package com.sk.asset.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.resource.PathResourceResolver;

import java.io.IOException;

/**
 * SPA history 路由 fallback：
 * 前端使用 history 模式（URL 无 #），刷新/直链非根路径时服务端须回退到 index.html，
 * 否则 404。API 路径（/api/**）不参与 fallback。
 */
@Configuration
public class SpaConfig implements WebMvcConfigurer {

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/**")
            .addResourceLocations("classpath:/static/")
            .resourceChain(true)
            .addResolver(new PathResourceResolver() {
                @Override
                protected Resource getResource(String resourcePath, Resource location) throws IOException {
                    Resource requested = location.createRelative(resourcePath);
                    if (requested.exists() && requested.isReadable()) {
                        return requested;
                    }
                    // API 交给控制器，其余前端路由一律回退 index.html
                    if (resourcePath.startsWith("api/")) {
                        return null;
                    }
                    return new ClassPathResource("/static/index.html");
                }
            });
    }
}
