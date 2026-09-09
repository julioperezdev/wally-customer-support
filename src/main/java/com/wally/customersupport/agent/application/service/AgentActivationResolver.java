package com.wally.customersupport.agent.application.service;

import java.util.Objects;

import com.wally.customersupport.agent.application.port.out.AgentRegistryRepository;
import com.wally.customersupport.agent.domain.model.AgentActivation;
import com.wally.customersupport.featureflag.application.FeatureFlagContext;
import com.wally.customersupport.featureflag.application.service.FeatureFlagRuntimeService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class AgentActivationResolver {

    private final AgentRegistryRepository registry;
    private final FeatureFlagRuntimeService featureFlags;

    @Autowired
    public AgentActivationResolver(AgentRegistryRepository registry, FeatureFlagRuntimeService featureFlags) {
        this.registry = registry;
        this.featureFlags = featureFlags;
    }

    /** Compatibility constructor for pure registry tests and callers that do not enable hot flags. */
    public AgentActivationResolver(AgentRegistryRepository registry) {
        this.registry = registry;
        this.featureFlags = null;
    }

    public AgentActivationResolution resolve(AgentActivationKey key) {
        Objects.requireNonNull(key, "key");
        try {
            return registry.findLatestActivation(
                            key.agentId(),
                            key.environment(),
                            key.channel(),
                            key.useCase())
                    .map(activation -> resolveLatest(key, activation))
                    .orElseGet(() -> AgentActivationResolution.fallback(AgentResolutionReason.NOT_CONFIGURED));
        } catch (RuntimeException exception) {
            return AgentActivationResolution.fallback(AgentResolutionReason.REGISTRY_UNAVAILABLE);
        }
    }

    private AgentActivationResolution resolveLatest(AgentActivationKey key, AgentActivation activation) {
        if (activation.killSwitch()) {
            return AgentActivationResolution.fallback(AgentResolutionReason.KILL_SWITCH);
        }
        if (!activation.enabled()) {
            return AgentActivationResolution.fallback(AgentResolutionReason.DISABLED);
        }
        if (featureFlags != null && !featureFlags.isAgentExecutionAllowed(new FeatureFlagContext(
                key.environment(), key.channel(), key.useCase(), key.agentId(), activation.agentVersion()))) {
            return AgentActivationResolution.fallback(AgentResolutionReason.KILL_SWITCH);
        }
        return AgentActivationResolution.active(activation.agentId(), activation.agentVersion());
    }
}
