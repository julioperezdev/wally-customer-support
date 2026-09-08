package com.wally.customersupport.agent.infrastructure.security;

import java.util.Objects;

import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

/** Shared Spring Security adapter logic for scoped evaluation capabilities. */
final class JwtAgentEvaluationSecuritySupport {

    private JwtAgentEvaluationSecuritySupport() {
    }

    static boolean authorize(String actorId, String capability) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (!(authentication instanceof JwtAuthenticationToken jwtAuthentication)
                || !jwtAuthentication.isAuthenticated()) {
            return false;
        }
        if (!hasRequiredAuthority(jwtAuthentication, capability)) {
            return false;
        }
        return Objects.equals(jwtAuthentication.getName(), actorId);
    }

    private static boolean hasRequiredAuthority(
            AbstractAuthenticationToken authentication,
            String capability) {
        String requiredAuthority = "SCOPE_" + capability;
        return authentication.getAuthorities().stream()
                .anyMatch(authority -> requiredAuthority.equals(authority.getAuthority()));
    }
}
