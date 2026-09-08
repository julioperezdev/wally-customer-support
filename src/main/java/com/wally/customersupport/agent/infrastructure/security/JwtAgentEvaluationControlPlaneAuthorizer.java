package com.wally.customersupport.agent.infrastructure.security;

import java.util.Objects;

import com.wally.customersupport.agent.application.evaluation.AgentEvaluationControlPlaneAccessRequest;
import com.wally.customersupport.agent.application.service.AgentEvaluationControlPlaneAccessService;
import com.wally.customersupport.agent.application.port.out.AgentEvaluationControlPlaneAuthorizer;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

/** Spring Security adapter that maps a validated JWT to the control-plane port. */
public class JwtAgentEvaluationControlPlaneAuthorizer implements AgentEvaluationControlPlaneAuthorizer {

    private static final String REQUIRED_AUTHORITY =
            "SCOPE_" + AgentEvaluationControlPlaneAccessService.EVALUATION_READ_CAPABILITY;

    @Override
    public boolean authorize(AgentEvaluationControlPlaneAccessRequest request) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (!(authentication instanceof JwtAuthenticationToken jwtAuthentication)
                || !jwtAuthentication.isAuthenticated()) {
            return false;
        }
        if (!hasRequiredAuthority(jwtAuthentication)) {
            return false;
        }
        return Objects.equals(jwtAuthentication.getName(), request.actorId());
    }

    private static boolean hasRequiredAuthority(AbstractAuthenticationToken authentication) {
        return authentication.getAuthorities().stream()
                .anyMatch(authority -> REQUIRED_AUTHORITY.equals(authority.getAuthority()));
    }
}
