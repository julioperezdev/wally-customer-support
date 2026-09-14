package com.wally.customersupport.backoffice.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

class BackofficeCookieBearerTokenResolverTest {

    @Test
    void readsTheConfiguredAccessCookieName() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(new Cookie("custom_access", "access-token"));

        assertThat(new BackofficeCookieBearerTokenResolver("custom_access").resolve(request))
                .isEqualTo("access-token");
    }

    @Test
    void prefersAnExplicitAuthorizationHeaderOverTheSessionCookie() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer header-token");
        request.setCookies(new Cookie("custom_access", "cookie-token"));

        assertThat(new BackofficeCookieBearerTokenResolver("custom_access").resolve(request))
                .isEqualTo("header-token");
    }
}
