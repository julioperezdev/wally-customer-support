package com.wally.customersupport.backoffice.infrastructure.security;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver;
import org.springframework.security.oauth2.server.resource.web.DefaultBearerTokenResolver;

/** Reads the access token from the HttpOnly session cookie or a technical bearer header. */
public final class BackofficeCookieBearerTokenResolver implements BearerTokenResolver {

    public static final String ACCESS_COOKIE_NAME = "wcs_backoffice_access";

    private final BearerTokenResolver headerResolver = new DefaultBearerTokenResolver();
    private final String accessCookieName;

    public BackofficeCookieBearerTokenResolver() {
        this(ACCESS_COOKIE_NAME);
    }

    public BackofficeCookieBearerTokenResolver(String accessCookieName) {
        if (accessCookieName == null || accessCookieName.isBlank()) {
            throw new IllegalArgumentException("accessCookieName must not be blank");
        }
        this.accessCookieName = accessCookieName;
    }

    @Override
    public String resolve(HttpServletRequest request) {
        String headerToken = headerResolver.resolve(request);
        if (headerToken != null && !headerToken.isBlank()) {
            return headerToken;
        }
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        for (Cookie cookie : cookies) {
            if (accessCookieName.equals(cookie.getName())
                    && cookie.getValue() != null
                    && !cookie.getValue().isBlank()) {
                return cookie.getValue();
            }
        }
        return null;
    }
}
