package com.wally.customersupport.agent.infrastructure.security;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;

/**
 * Maps Cognito groups and resource-server scopes to WCS capability authorities.
 *
 * <p>The application keeps endpoint authorization provider-neutral. Cognito
 * groups represent roles for human operators, while direct scopes remain
 * available for service clients. Cognito custom scopes use the
 * {@code wcs-backoffice/<capability>} form and are normalized to the canonical
 * WCS {@code backoffice.<capability>} capability.</p>
 */
public final class CognitoJwtAuthenticationConverter
        implements Converter<Jwt, AbstractAuthenticationToken> {

    private static final String SCOPE_PREFIX = "SCOPE_";
    private static final String COGNITO_SCOPE_PREFIX = "wcs-backoffice/";

    private static final Set<String> ALL_CAPABILITIES = Set.of(
            "backoffice.catalog.read",
            "backoffice.catalog.write",
            "backoffice.catalog.media.write",
            "backoffice.orders.read",
            "backoffice.orders.write",
            "backoffice.human-follow-up.read",
            "backoffice.human-follow-up.write",
            "agent-evaluation.read",
            "agent-evaluation.execute",
            "agent-registry.read",
            "agent-registry.write",
            "feature-flags.read",
            "feature-flags.write");

    private static final Map<String, Set<String>> ROLE_CAPABILITIES = Map.of(
            "store-viewer", Set.of(
                    "backoffice.catalog.read",
                    "backoffice.orders.read",
                    "backoffice.human-follow-up.read",
                    "agent-evaluation.read",
                    "agent-registry.read",
                    "feature-flags.read"),
            "store-operator", Set.of(
                    "backoffice.catalog.read",
                    "backoffice.catalog.write",
                    "backoffice.catalog.media.write",
                    "backoffice.orders.read",
                    "backoffice.human-follow-up.read",
                    "backoffice.human-follow-up.write",
                    "agent-evaluation.read",
                    "agent-registry.read",
                    "feature-flags.read"),
            "order-operator", Set.of(
                    "backoffice.catalog.read",
                    "backoffice.orders.read",
                    "backoffice.orders.write"),
            "agent-operator", Set.of(
                    "agent-evaluation.read",
                    "agent-evaluation.execute",
                    "agent-registry.read",
                    "agent-registry.write",
                    "feature-flags.read",
                    "feature-flags.write"),
            "admin", ALL_CAPABILITIES);

    private final JwtAuthenticationConverter delegate;

    public CognitoJwtAuthenticationConverter() {
        JwtGrantedAuthoritiesConverter defaultScopes = new JwtGrantedAuthoritiesConverter();
        this.delegate = new JwtAuthenticationConverter();
        this.delegate.setJwtGrantedAuthoritiesConverter(jwt -> {
            Set<GrantedAuthority> authorities = new LinkedHashSet<>(defaultScopes.convert(jwt));
            addNormalizedScopes(jwt, authorities);
            addGroupCapabilities(jwt, authorities);
            return authorities;
        });
    }

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        return delegate.convert(jwt);
    }

    private static void addNormalizedScopes(Jwt jwt, Set<GrantedAuthority> authorities) {
        for (String scope : scopeClaims(jwt)) {
            if (scope.startsWith(COGNITO_SCOPE_PREFIX)) {
                String capability = scope.substring(COGNITO_SCOPE_PREFIX.length());
                authorities.add(new SimpleGrantedAuthority(
                        SCOPE_PREFIX + canonicalCapability(capability.replace('/', '.'))));
            }
        }
    }

    private static String canonicalCapability(String capability) {
        if (capability.startsWith("catalog.")
                || capability.startsWith("orders.")
                || capability.startsWith("human-follow-up.")) {
            return "backoffice." + capability;
        }
        return capability;
    }

    private static void addGroupCapabilities(Jwt jwt, Set<GrantedAuthority> authorities) {
        List<String> groups = jwt.getClaimAsStringList("cognito:groups");
        if (groups == null) {
            return;
        }
        for (String group : groups) {
            ROLE_CAPABILITIES.getOrDefault(group, Set.of()).stream()
                    .map(capability -> new SimpleGrantedAuthority(SCOPE_PREFIX + capability))
                    .forEach(authorities::add);
        }
    }

    private static List<String> scopeClaims(Jwt jwt) {
        String scope = jwt.getClaimAsString("scope");
        if (scope == null || scope.isBlank()) {
            return List.of();
        }
        return List.of(scope.split("\\s+"));
    }

    static Set<String> capabilitiesForGroup(String group) {
        return ROLE_CAPABILITIES.getOrDefault(group, Set.of());
    }

    static Set<String> allCapabilities() {
        return ALL_CAPABILITIES;
    }
}
