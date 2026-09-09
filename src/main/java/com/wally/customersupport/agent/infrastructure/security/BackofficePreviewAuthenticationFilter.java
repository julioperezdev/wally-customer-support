package com.wally.customersupport.agent.infrastructure.security;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/** Authenticates the temporary read-only backoffice token without logging it. */
public final class BackofficePreviewAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";
    private static final List<SimpleGrantedAuthority> READ_AUTHORITIES = List.of(
            new SimpleGrantedAuthority("SCOPE_agent-evaluation.read"),
            new SimpleGrantedAuthority("SCOPE_agent-registry.read"),
            new SimpleGrantedAuthority("SCOPE_feature-flags.read"),
            new SimpleGrantedAuthority("SCOPE_backoffice.catalog.read"),
            new SimpleGrantedAuthority("SCOPE_backoffice.human-follow-up.read"));

    private final byte[] configuredToken;

    public BackofficePreviewAuthenticationFilter(String configuredToken) {
        if (configuredToken == null || configuredToken.isBlank()) {
            throw new IllegalStateException(
                    "wcs.backoffice.preview.token must be configured when preview mode is enabled");
        }
        this.configuredToken = configuredToken.getBytes(StandardCharsets.UTF_8);
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        String presentedToken = bearerToken(request.getHeader("Authorization"));
        if (presentedToken != null && matches(presentedToken)) {
            SecurityContextHolder.getContext().setAuthentication(
                    new UsernamePasswordAuthenticationToken(
                            "backoffice-preview", null, READ_AUTHORITIES));
        }
        filterChain.doFilter(request, response);
    }

    private boolean matches(String presentedToken) {
        return MessageDigest.isEqual(
                configuredToken,
                presentedToken.getBytes(StandardCharsets.UTF_8));
    }

    private static String bearerToken(String authorization) {
        if (authorization == null || !authorization.startsWith(BEARER_PREFIX)) {
            return null;
        }
        String token = authorization.substring(BEARER_PREFIX.length()).trim();
        return token.isBlank() ? null : token;
    }
}
