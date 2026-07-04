package com.lumentale.wiki.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.CacheControl;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.TimeUnit;

/**
 * Web layer config: CORS, the /data/** asset handler, and HTTP caching.
 *
 * The dataset is static post-seed, so EVERY /api response is cacheable. The
 * /data/** handler serves the filesystem leg of the hybrid asset strategy
 * (see {@link com.lumentale.wiki.common.AssetResolver}); GUIDs the filesystem
 * doesn't have are resolved against the DB manifest by the resolver instead.
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Value("${lumentale.data-dir:data/assets}")
    private String dataDir;

    @Value("${lumentale.cors-origins:http://localhost:5174,http://127.0.0.1:5174}")
    private String[] corsOrigins;

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOrigins(corsOrigins)
                .allowedMethods("GET", "OPTIONS");
        registry.addMapping("/data/**").allowedOrigins("*");
    }

    /**
     * All /api responses derive purely from the seeded data, which never changes
     * between boots — safe to cache for an hour. Set in preHandle, before the body
     * is committed. Error responses opt out: the {@code ApiExceptionHandler}
     * overrides this with {@code no-store}.
     */
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new HandlerInterceptor() {
            @Override
            public boolean preHandle(HttpServletRequest req, HttpServletResponse res, Object handler) {
                res.setHeader("Cache-Control", "public, max-age=3600");
                return true;
            }
        }).addPathPatterns("/api/**");
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        Path absolute = Paths.get(dataDir).toAbsolutePath().normalize();
        // Content-addressed by stable GUID path → the file at a URL never changes,
        // so browsers may cache for a year and skip revalidation (immutable).
        registry.addResourceHandler("/data/**")
                .addResourceLocations(absolute.toUri().toString())
                .setCacheControl(CacheControl.maxAge(365, TimeUnit.DAYS).cachePublic().immutable());
    }
}
