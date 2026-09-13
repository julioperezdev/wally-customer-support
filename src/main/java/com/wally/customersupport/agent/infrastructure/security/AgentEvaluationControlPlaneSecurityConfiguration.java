package com.wally.customersupport.agent.infrastructure.security;

import java.util.List;

import com.wally.customersupport.agent.application.port.out.AgentEvaluationControlPlaneAuthorizer;
import com.wally.customersupport.agent.application.port.out.AgentEvaluationTriggerAuthorizer;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimNames;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtDecoders;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;

/** Conditional JWT security limited to the internal evaluation control plane. */
@Configuration(proxyBeanMethods = false)
public class AgentEvaluationControlPlaneSecurityConfiguration {

    private static final String SECURITY_ENABLED_PROPERTY =
            "wcs.agent-evaluation.control-plane.security.enabled";
    private static final String ISSUER_URI_PROPERTY =
            "wcs.agent-evaluation.control-plane.security.issuer-uri";
    private static final String AUDIENCE_PROPERTY =
            "wcs.agent-evaluation.control-plane.security.audience";
    private static final String REQUIRED_AUTHORITY = "SCOPE_agent-evaluation.read";
    private static final String REGISTRY_READ_AUTHORITY = "SCOPE_agent-registry.read";
    private static final String REGISTRY_WRITE_AUTHORITY = "SCOPE_agent-registry.write";
    private static final String FEATURE_FLAGS_READ_AUTHORITY = "SCOPE_feature-flags.read";
    private static final String FEATURE_FLAGS_WRITE_AUTHORITY = "SCOPE_feature-flags.write";
    private static final String EXECUTE_AUTHORITY = "SCOPE_agent-evaluation.execute";

    @Bean
    @ConditionalOnProperty(
            name = "wcs.agent-evaluation.control-plane.security.enabled",
            havingValue = "true")
    AgentEvaluationControlPlaneAuthorizer jwtAgentEvaluationControlPlaneAuthorizer() {
        return new JwtAgentEvaluationControlPlaneAuthorizer();
    }

    @Bean
    @ConditionalOnProperty(
            name = "wcs.agent-evaluation.control-plane.security.enabled",
            havingValue = "true")
    AgentEvaluationTriggerAuthorizer jwtAgentEvaluationTriggerAuthorizer() {
        return new JwtAgentEvaluationTriggerAuthorizer();
    }

    @Bean
    @ConditionalOnProperty(name = SECURITY_ENABLED_PROPERTY, havingValue = "true")
    @ConditionalOnMissingBean(JwtDecoder.class)
    JwtDecoder agentEvaluationJwtDecoder(
            @Value("${" + ISSUER_URI_PROPERTY + ":}") String issuerUri,
            @Value("${" + AUDIENCE_PROPERTY + ":}") String audience) {
        String normalizedIssuer = requireText(issuerUri, ISSUER_URI_PROPERTY);
        String normalizedAudience = requireText(audience, AUDIENCE_PROPERTY);
        NimbusJwtDecoder decoder = JwtDecoders.fromIssuerLocation(normalizedIssuer);
        decoder.setJwtValidator(tokenValidator(normalizedIssuer, normalizedAudience));
        return decoder;
    }

    static OAuth2TokenValidator<Jwt> tokenValidator(String issuerUri, String audience) {
        OAuth2TokenValidator<Jwt> standardValidator = JwtValidators.createDefaultWithIssuer(issuerUri);
        OAuth2TokenValidator<Jwt> audienceValidator = new JwtClaimValidator<List<String>>(
                JwtClaimNames.AUD,
                claim -> claim != null && claim.contains(audience));
        return new DelegatingOAuth2TokenValidator<>(standardValidator, audienceValidator);
    }

    @Bean
    @Order(0)
    @ConditionalOnExpression("'${wcs.backoffice.preview.enabled:false}' == 'true'"
            + " && '${wcs.agent-evaluation.control-plane.security.enabled:false}' == 'false'")
    SecurityFilterChain backofficePreviewSecurityFilterChain(
            HttpSecurity http,
            BackofficePreviewAuthenticationFilter previewFilter) throws Exception {
        http
                .securityMatcher("/internal/**")
                .csrf(csrf -> csrf.disable())
                .addFilterBefore(previewFilter, AnonymousAuthenticationFilter.class)
                .exceptionHandling(exceptionHandling -> exceptionHandling
                        .authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(HttpMethod.GET, "/internal/agent-evaluations/**")
                        .hasAuthority(REQUIRED_AUTHORITY)
                        .requestMatchers(HttpMethod.GET, "/internal/agent-registry/**")
                        .hasAuthority(REGISTRY_READ_AUTHORITY)
                        .requestMatchers(HttpMethod.POST, "/internal/agent-registry/activations/preflight")
                        .hasAuthority(REGISTRY_READ_AUTHORITY)
                        .requestMatchers(HttpMethod.GET, "/internal/backoffice/agent-map/**")
                        .hasAuthority(REGISTRY_READ_AUTHORITY)
                        .requestMatchers(HttpMethod.POST, "/internal/backoffice/agent-map/simulations")
                        .hasAuthority(REGISTRY_READ_AUTHORITY)
                        .requestMatchers(HttpMethod.GET, "/internal/backoffice/feature-flags/**")
                        .hasAuthority(FEATURE_FLAGS_READ_AUTHORITY)
                        .requestMatchers(HttpMethod.GET, "/internal/backoffice/catalog/**")
                        .hasAuthority("SCOPE_backoffice.catalog.read")
                        .requestMatchers(HttpMethod.GET, "/internal/backoffice/human-follow-ups/**")
                        .hasAuthority("SCOPE_backoffice.human-follow-up.read")
                        .anyRequest().denyAll());
        return http.build();
    }

    @Bean
    @ConditionalOnExpression("'${wcs.backoffice.preview.enabled:false}' == 'true'"
            + " && '${wcs.agent-evaluation.control-plane.security.enabled:false}' == 'false'")
    BackofficePreviewAuthenticationFilter backofficePreviewAuthenticationFilter(
            @Value("${wcs.backoffice.preview.token:}") String previewToken) {
        return new BackofficePreviewAuthenticationFilter(previewToken);
    }

    @Bean
    @Order(1)
    @ConditionalOnProperty(
            name = "wcs.agent-evaluation.control-plane.security.enabled",
            havingValue = "true")
    SecurityFilterChain agentEvaluationControlPlaneSecurityFilterChain(HttpSecurity http) throws Exception {
        http
                .securityMatcher(
                        "/internal/agent-evaluations/**",
                        "/internal/agent-registry/**",
                        "/internal/backoffice/agent-map/**",
                        "/internal/backoffice/feature-flags/**",
                        "/internal/backoffice/catalog/**",
                        "/internal/backoffice/human-follow-ups/**")
                .csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(HttpMethod.POST, "/internal/agent-evaluations/runs")
                        .hasAuthority(EXECUTE_AUTHORITY)
                        .requestMatchers(HttpMethod.GET, "/internal/agent-evaluations/**")
                        .hasAuthority(REQUIRED_AUTHORITY)
                        .requestMatchers(HttpMethod.GET, "/internal/agent-registry/**")
                        .hasAuthority(REGISTRY_READ_AUTHORITY)
                        .requestMatchers(HttpMethod.POST, "/internal/agent-registry/agents/**")
                        .hasAuthority(REGISTRY_WRITE_AUTHORITY)
                        .requestMatchers(HttpMethod.POST, "/internal/agent-registry/activations/preflight")
                        .hasAuthority(REGISTRY_READ_AUTHORITY)
                        .requestMatchers(HttpMethod.POST, "/internal/agent-registry/activations/**")
                        .hasAuthority(REGISTRY_WRITE_AUTHORITY)
                        .requestMatchers(HttpMethod.GET, "/internal/backoffice/agent-map/**")
                        .hasAuthority(REGISTRY_READ_AUTHORITY)
                        .requestMatchers(HttpMethod.POST, "/internal/backoffice/agent-map/simulations")
                        .hasAuthority(REGISTRY_READ_AUTHORITY)
                        .requestMatchers(HttpMethod.GET, "/internal/backoffice/feature-flags/**")
                        .hasAuthority(FEATURE_FLAGS_READ_AUTHORITY)
                        .requestMatchers(HttpMethod.POST, "/internal/backoffice/feature-flags/**")
                        .hasAuthority(FEATURE_FLAGS_WRITE_AUTHORITY)
                        .requestMatchers(HttpMethod.GET, "/internal/backoffice/catalog/**")
                        .hasAuthority("SCOPE_backoffice.catalog.read")
                        .requestMatchers(HttpMethod.POST, "/internal/backoffice/catalog/variants/*/stock")
                        .hasAuthority("SCOPE_backoffice.catalog.write")
                        .requestMatchers(HttpMethod.POST, "/internal/backoffice/catalog/products/*/image/**")
                        .hasAuthority("SCOPE_backoffice.catalog.media.write")
                        .requestMatchers(HttpMethod.GET, "/internal/backoffice/human-follow-ups/**")
                        .hasAuthority("SCOPE_backoffice.human-follow-up.read")
                        .requestMatchers(HttpMethod.POST, "/internal/backoffice/human-follow-ups/**")
                        .hasAuthority("SCOPE_backoffice.human-follow-up.write")
                        .anyRequest().denyAll())
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(Customizer.withDefaults()));
        return http.build();
    }

    /** Keep existing public webhook and actuator behavior outside the admin boundary. */
    @Bean
    @Order(2)
    @ConditionalOnProperty(
            name = "wcs.agent-evaluation.control-plane.security.enabled",
            havingValue = "true")
    SecurityFilterChain publicEndpointsSecurityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(authorize -> authorize.anyRequest().permitAll());
        return http.build();
    }

    /** Avoid Spring Security's generated catch-all login when the feature is disabled. */
    @Bean
    @Order(2)
    @ConditionalOnProperty(
            name = "wcs.agent-evaluation.control-plane.security.enabled",
            havingValue = "false",
            matchIfMissing = true)
    SecurityFilterChain securityDisabledFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(authorize -> authorize.anyRequest().permitAll());
        return http.build();
    }

    private static String requireText(String value, String property) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(property + " must be configured when control-plane security is enabled");
        }
        return value.strip();
    }
}
