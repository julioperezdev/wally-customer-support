package com.wally.customersupport.backoffice.infrastructure.config;

import java.util.Arrays;
import java.util.List;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/** CORS policy for the browser-owned backoffice session endpoints. */
@Configuration(proxyBeanMethods = false)
@RequiredArgsConstructor
public class BackofficeCorsConfiguration {

    private final BackofficeAuthenticationProperties properties;

    @Bean
    CorsConfigurationSource backofficeCorsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(Arrays.stream(properties.corsAllowedOrigins().split(","))
                .map(String::strip)
                .filter(origin -> !origin.isBlank())
                .toList());
        configuration.setAllowedMethods(List.of("GET", "POST", "OPTIONS"));
        configuration.setAllowedHeaders(List.of(
                "Accept", "Authorization", "Content-Type", "Idempotency-Key", "X-WCS-Actor-Key"));
        configuration.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/internal/**", configuration);
        return source;
    }
}
