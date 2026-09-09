package com.wally.customersupport.backoffice.application.service;

import com.wally.customersupport.agent.application.evaluation.AgentEvaluationControlPlaneAccessDecision;
import com.wally.customersupport.agent.application.service.AgentEvaluationControlPlaneAccessService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Keeps the first operational panel closed by default. Production access must
 * later be connected to the same JWT/role boundary as the control plane.
 */
@Service
public class BackofficeAccessService {

    private final boolean enabled;
    private final boolean localModeEnabled;
    private final String activeProfile;
    private final AgentEvaluationControlPlaneAccessService controlPlaneAccessService;

    @Autowired
    public BackofficeAccessService(
            @Value("${wcs.backoffice.enabled:false}") boolean enabled,
            @Value("${wcs.backoffice.local-mode-enabled:false}") boolean localModeEnabled,
            @Value("${spring.profiles.active:${spring.profiles.default:prod}}") String activeProfile,
            AgentEvaluationControlPlaneAccessService controlPlaneAccessService) {
        this.enabled = enabled;
        this.localModeEnabled = localModeEnabled;
        this.activeProfile = activeProfile;
        this.controlPlaneAccessService = controlPlaneAccessService;
    }

    public BackofficeAccessService(boolean enabled, boolean localModeEnabled) {
        this(enabled, localModeEnabled, "local", null);
    }

    public Decision authorize(String capability) {
        return authorize(capability, null);
    }

    public Decision authorize(String capability, String actorId) {
        if (!enabled) {
            return new Decision(false, 404, "BACKOFFICE_DISABLED");
        }
        if (localModeEnabled && isLocalProfile()) {
            return new Decision(true, 200, capability);
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

    private boolean isLocalProfile() {
        return "local".equalsIgnoreCase(activeProfile) || "test".equalsIgnoreCase(activeProfile);
    }

    public record Decision(boolean authorized, int status, String reason) {
    }
}
