package com.wally.customersupport.backoffice.application.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
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

    @Autowired
    public BackofficeAccessService(
            @Value("${wcs.backoffice.enabled:false}") boolean enabled,
            @Value("${wcs.backoffice.local-mode-enabled:false}") boolean localModeEnabled,
            @Value("${spring.profiles.active:${spring.profiles.default:prod}}") String activeProfile) {
        this.enabled = enabled;
        this.localModeEnabled = localModeEnabled;
        this.activeProfile = activeProfile;
    }

    public BackofficeAccessService(boolean enabled, boolean localModeEnabled) {
        this(enabled, localModeEnabled, "local");
    }

    public Decision authorize(String capability) {
        if (!enabled) {
            return new Decision(false, 404, "BACKOFFICE_DISABLED");
        }
        if (!localModeEnabled || !isLocalProfile()) {
            return new Decision(false, 403, "BACKOFFICE_AUTHORIZATION_REQUIRED");
        }
        return new Decision(true, 200, capability);
    }

    private boolean isLocalProfile() {
        return "local".equalsIgnoreCase(activeProfile) || "test".equalsIgnoreCase(activeProfile);
    }

    public record Decision(boolean authorized, int status, String reason) {
    }
}
