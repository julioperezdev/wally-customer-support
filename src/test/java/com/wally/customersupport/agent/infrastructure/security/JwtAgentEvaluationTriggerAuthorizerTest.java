package com.wally.customersupport.agent.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import com.wally.customersupport.agent.application.evaluation.AgentEvaluationTriggerRequest;
import com.wally.customersupport.agent.application.service.AgentEvaluationTriggerAuthorizationService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

class JwtAgentEvaluationTriggerAuthorizerTest {

    private static final String ACTOR = "evaluation-operator";
    private static final AgentEvaluationTriggerRequest REQUEST = new AgentEvaluationTriggerRequest(
            ACTOR,
            "prod",
            AgentEvaluationTriggerAuthorizationService.EVALUATION_EXECUTE_CAPABILITY,
            "request-001");

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void acceptsOnlyTheExecutionCapabilityForTheAuthenticatedActor() {
        Jwt jwt = Jwt.withTokenValue("synthetic-token")
                .header("alg", "none")
                .subject(ACTOR)
                .build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(
                jwt,
                List.of(new SimpleGrantedAuthority("SCOPE_agent-evaluation.execute"))));

        assertThat(new JwtAgentEvaluationTriggerAuthorizer().authorize(REQUEST)).isTrue();
    }

    @Test
    void rejectsReadOnlyScopeAndActorMismatch() {
        Jwt jwt = Jwt.withTokenValue("synthetic-token")
                .header("alg", "none")
                .subject("another-actor")
                .build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(
                jwt,
                List.of(new SimpleGrantedAuthority("SCOPE_agent-evaluation.read"))));

        assertThat(new JwtAgentEvaluationTriggerAuthorizer().authorize(REQUEST)).isFalse();
    }

    @Test
    void rejectsNonJwtAuthentication() {
        SecurityContextHolder.getContext().setAuthentication(
                new TestingAuthenticationToken(ACTOR, "credentials"));

        assertThat(new JwtAgentEvaluationTriggerAuthorizer().authorize(REQUEST)).isFalse();
    }
}
