package com.wally.customersupport.agent.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;

class CognitoJwtAuthenticationConverterTest {

    private final CognitoJwtAuthenticationConverter converter = new CognitoJwtAuthenticationConverter();

    @Test
    void mapsCognitoRoleToOnlyItsCapabilities() {
        Authentication authentication = converter.convert(jwt(
                "operator",
                List.of("store-viewer"),
                "openid email"));

        assertThat(authorities(authentication)).contains(
                "SCOPE_backoffice.catalog.read",
                "SCOPE_backoffice.orders.read",
                "SCOPE_agent-evaluation.read");
        assertThat(authorities(authentication)).doesNotContain(
                "SCOPE_backoffice.orders.write",
                "SCOPE_backoffice.catalog.write",
                "SCOPE_agent-registry.write");
    }

    @Test
    void normalizesCognitoResourceServerScopesToCanonicalCapabilities() {
        Authentication authentication = converter.convert(jwt(
                "service-client",
                List.of(),
                "wcs-backoffice/orders.write wcs-backoffice/catalog.read"));

        assertThat(authorities(authentication)).contains(
                "SCOPE_backoffice.orders.write",
                "SCOPE_backoffice.catalog.read");
    }

    @Test
    void preservesProviderNeutralScopes() {
        Authentication authentication = converter.convert(jwt(
                "generic-client",
                List.of(),
                "agent-evaluation.read"));

        assertThat(authorities(authentication)).contains("SCOPE_agent-evaluation.read");
    }

    @Test
    void adminReceivesAllKnownCapabilities() {
        Authentication authentication = converter.convert(jwt(
                "admin",
                List.of("admin"),
                "openid"));

        assertThat(authorities(authentication)).containsAll(
                CognitoJwtAuthenticationConverter.allCapabilities().stream()
                        .map(capability -> "SCOPE_" + capability)
                        .collect(Collectors.toSet()));
    }

    private static Jwt jwt(String subject, List<String> groups, String scope) {
        return Jwt.withTokenValue("synthetic-token")
                .header("alg", "RS256")
                .subject(subject)
                .claim("scope", scope)
                .claim("cognito:groups", groups)
                .issuedAt(Instant.now().minusSeconds(30))
                .expiresAt(Instant.now().plusSeconds(300))
                .build();
    }

    private static Set<String> authorities(Authentication authentication) {
        return authentication.getAuthorities().stream()
                .map(authority -> authority.getAuthority())
                .collect(Collectors.toSet());
    }
}
