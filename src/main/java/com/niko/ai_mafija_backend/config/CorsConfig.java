package com.niko.ai_mafija_backend.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.Arrays;

@Configuration
public class CorsConfig implements WebMvcConfigurer {

    private final String[] allowedOrigins;

    public CorsConfig(
            @Value("${app.cors.allowed-origins:http://localhost:5173}") String[] allowedOrigins) {
        // APP_CORS_ALLOWED_ORIGINS: više domena odvojenih zarezom, razmaci se ignoriraju
        this.allowedOrigins = Arrays.stream(allowedOrigins).map(String::trim).toArray(String[]::new);
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOrigins(allowedOrigins)
                .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
                .allowedHeaders("Content-Type", "Accept", "Origin", "X-Player-Id", "X-Player-Token")
                .allowCredentials(false)
                .maxAge(3600);
    }
}
