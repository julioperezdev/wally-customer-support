package com.wally.customersupport.backoffice.application.service;

import com.wally.customersupport.agent.application.evaluation.AgentEvaluationControlPlaneAccessDecision;
import com.wally.customersupport.agent.application.service.AgentEvaluationControlPlaneAccessService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/** Applies the same Cognito/JWT capability boundary to all backoffice panels. */
@Service
public class BackofficeAccessService {

    private final boolean enabled;
    private final AgentEvaluationControlPlaneAccessService controlPlaneAccessService;

    @Autowired
    public BackofficeAccessService(
            @Value("${wcs.backoffice.enabled:false}") boolean enabled,
            AgentEvaluationControlPlaneAccessService controlPlaneAccessService) {
        this.enabled = enabled;
        this.controlPlaneAccessService = controlPlaneAccessService;
    }

    public BackofficeAccessService(boolean enabled) {
        this.enabled = enabled;
        this.controlPlaneAccessService = null;
    }

    public Decision authorize(String capability) {
        return authorize(capability, null);
    }

    public Decision authorize(String capability, String actorId) {
        if (!enabled) {
            return new Decision(false, 404, "BACKOFFICE_DISABLED");
        }
        if (controlPlaneAccessService != null) {
            AgentEvaluationControlPlaneAccessDecision decision =
                    controlPlaneAccessService.authorizeBackoffice(actorId, capability);
            if (decision.authorized()) {
                return new Decision(true, 200, capability);
            }
        }
        return new Decision(false, 403, "BACKOFFICE_AUTHORIZATION_REQUIRED");
    }

    public record Decision(boolean authorized, int status, String reason) {
    }
}
