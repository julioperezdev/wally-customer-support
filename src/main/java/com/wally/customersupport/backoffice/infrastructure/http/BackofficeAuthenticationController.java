package com.wally.customersupport.backoffice.infrastructure.http;

import java.util.Comparator;
import java.util.List;
import java.util.Map;

import com.wally.customersupport.backoffice.application.port.out.BackofficeIdentityProvider;
import com.wally.customersupport.backoffice.application.service.BackofficeAuthenticationException;
import com.wally.customersupport.backoffice.application.service.BackofficeAuthenticationService;
import com.wally.customersupport.backoffice.infrastructure.config.BackofficeAuthenticationProperties;
import com.wally.customersupport.backoffice.infrastructure.security.BackofficeCookieBearerTokenResolver;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/auth")
@RequiredArgsConstructor
@Slf4j
public class BackofficeAuthenticationController {

    private final BackofficeAuthenticationService authenticationService;
    private final BackofficeAuthenticationProperties properties;

    @PostMapping("/login")
    public ResponseEntity<?> login(
            @RequestBody(required = false) LoginRequest request,
            HttpServletResponse response) {
        try {
            BackofficeIdentityProvider.AuthenticationResult result = authenticationService.login(
                    request == null ? null : request.username(),
                    request == null ? null : request.password());
            writeSessionCookies(response, result, true);
            return ResponseEntity.ok(new SessionResponse("AUTHENTICATED"));
        } catch (BackofficeAuthenticationException exception) {
            return error(exception);
        }
    }

    @PostMapping("/refresh")
    public ResponseEntity<?> refresh(
            HttpServletRequest request,
            HttpServletResponse response) {
        try {
            BackofficeIdentityProvider.AuthenticationResult result = authenticationService.refresh(
                    cookie(request, properties.refreshCookieName()));
            writeSessionCookies(response, result, false);
            return ResponseEntity.ok(new SessionResponse("REFRESHED"));
        } catch (BackofficeAuthenticationException exception) {
            clearSessionCookies(response);
            return error(exception);
        }
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletRequest request, HttpServletResponse response) {
        String accessToken = new BackofficeCookieBearerTokenResolver().resolve(request);
        authenticationService.logout(accessToken);
        clearSessionCookies(response);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/me")
    public ResponseEntity<?> me(Authentication authentication) {
        if (!(authentication instanceof JwtAuthenticationToken jwtAuthentication)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("code", "AUTHENTICATION_REQUIRED"));
        }
        var token = jwtAuthentication.getToken();
        List<String> capabilities = authentication.getAuthorities().stream()
                .map(authority -> authority.getAuthority())
                .filter(authority -> authority.startsWith("SCOPE_"))
                .map(authority -> authority.substring("SCOPE_".length()))
                .sorted(Comparator.naturalOrder())
                .toList();
        return ResponseEntity.ok(new MeResponse(
                token.getSubject(),
                token.getClaimAsString("username"),
                token.getClaimAsStringList("cognito:groups"),
                capabilities));
    }

    private void writeSessionCookies(
            HttpServletResponse response,
            BackofficeIdentityProvider.AuthenticationResult result,
            boolean includeRefreshToken) {
        long maxAge = result.accessTokenExpiresInSeconds() > 0
                ? result.accessTokenExpiresInSeconds()
                : 3600;
        response.addHeader(HttpServletResponseHeader.SET_COOKIE,
                sessionCookie(properties.accessCookieName(), result.accessToken(), maxAge).toString());
        if (includeRefreshToken && result.refreshToken() != null && !result.refreshToken().isBlank()) {
            response.addHeader(HttpServletResponseHeader.SET_COOKIE,
                    sessionCookie(properties.refreshCookieName(), result.refreshToken(),
                            properties.normalizedRefreshCookieMaxAgeSeconds()).toString());
        }
    }

    private void clearSessionCookies(HttpServletResponse response) {
        response.addHeader(HttpServletResponseHeader.SET_COOKIE,
                sessionCookie(properties.accessCookieName(), "", 0).toString());
        response.addHeader(HttpServletResponseHeader.SET_COOKIE,
                sessionCookie(properties.refreshCookieName(), "", 0).toString());
    }

    private ResponseCookie sessionCookie(String name, String value, long maxAgeSeconds) {
        return ResponseCookie.from(name, value == null ? "" : value)
                .httpOnly(true)
                .secure(properties.secureCookies())
                .sameSite(properties.normalizedSameSite())
                .path("/")
                .maxAge(maxAgeSeconds)
                .build();
    }

    private static String cookie(HttpServletRequest request, String name) {
        if (request.getCookies() == null) {
            return null;
        }
        for (var cookie : request.getCookies()) {
            if (name.equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return null;
    }

    private static ResponseEntity<Map<String, Object>> error(BackofficeAuthenticationException exception) {
        return switch (exception.reason()) {
            case DISABLED -> ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(Map.of("code", "BACKOFFICE_AUTH_DISABLED"));
            case INVALID_CREDENTIALS -> ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("code", "INVALID_CREDENTIALS"));
            case INVALID_SESSION -> ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("code", "INVALID_SESSION"));
            case CHALLENGE_REQUIRED -> ResponseEntity.status(HttpStatus.PRECONDITION_REQUIRED)
                    .body(Map.of("code", "COGNITO_CHALLENGE_REQUIRED", "challenge", exception.challengeName()));
            case PROVIDER_UNAVAILABLE -> ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(Map.of("code", "AUTH_PROVIDER_UNAVAILABLE"));
        };
    }

    public record LoginRequest(String username, String password) {
    }

    public record SessionResponse(String status) {
    }

    public record MeResponse(
            String subject,
            String username,
            List<String> groups,
            List<String> capabilities) {
        public MeResponse {
            groups = groups == null ? List.of() : groups.stream().sorted().toList();
            capabilities = capabilities == null ? List.of() : capabilities.stream().toList();
        }
    }

    private static final class HttpServletResponseHeader {
        private static final String SET_COOKIE = "Set-Cookie";

        private HttpServletResponseHeader() {
        }
    }
}
