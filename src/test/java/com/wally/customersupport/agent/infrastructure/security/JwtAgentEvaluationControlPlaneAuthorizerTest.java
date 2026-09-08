package com.wally.customersupport.agent.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import com.wally.customersupport.agent.application.evaluation.AgentEvaluationControlPlaneAccessRequest;
import com.wally.customersupport.agent.application.service.AgentEvaluationControlPlaneAccessService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

class JwtAgentEvaluationControlPlaneAuthorizerTest {

    private static final String ACTOR = "synthetic-operator";
    private static final AgentEvaluationControlPlaneAccessRequest REQUEST =
            new AgentEvaluationControlPlaneAccessRequest(
                    ACTOR,
                    "prod",
                    AgentEvaluationControlPlaneAccessService.EVALUATION_READ_CAPABILITY);

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void authorizesValidatedJwtWithMatchingSubjectAndScope() {
        setAuthentication(jwtAuthentication(ACTOR, true));

        assertThat(new JwtAgentEvaluationControlPlaneAuthorizer().authorize(REQUEST)).isTrue();
    }

    @Test
    void rejectsJwtWithoutReadScopeOrWithAnotherSubject() {
        setAuthentication(jwtAuthentication(ACTOR, false));
        assertThat(new JwtAgentEvaluationControlPlaneAuthorizer().authorize(REQUEST)).isFalse();

        setAuthentication(jwtAuthentication("another-operator", true));
        assertThat(new JwtAgentEvaluationControlPlaneAuthorizer().authorize(REQUEST)).isFalse();
    }

    @Test
    void rejectsNonJwtAuthentication() {
        SecurityContextHolder.getContext().setAuthentication(
                new TestingAuthenticationToken(ACTOR, null, "SCOPE_agent-evaluation.read"));

        assertThat(new JwtAgentEvaluationControlPlaneAuthorizer().authorize(REQUEST)).isFalse();
    }

    private static void setAuthentication(JwtAuthenticationToken authentication) {
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }

    private static JwtAuthenticationToken jwtAuthentication(String subject, boolean readScope) {
        Jwt jwt = new Jwt(
                "synthetic-token",
                Instant.parse("2026-09-08T00:00:00Z"),
                Instant.parse("2026-09-08T01:00:00Z"),
                Map.of("alg", "none"),
                Map.of("sub", subject, "iss", "https://issuer.example.test"));
        List<SimpleGrantedAuthority> authorities = readScope
                ? List.of(new SimpleGrantedAuthority("SCOPE_agent-evaluation.read"))
                : List.of();
        return new JwtAuthenticationToken(jwt, authorities);
    }
}
