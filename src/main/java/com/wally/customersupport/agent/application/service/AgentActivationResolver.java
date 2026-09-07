package com.wally.customersupport.agent.application.service;

import java.util.Objects;

import com.wally.customersupport.agent.application.port.out.AgentRegistryRepository;
import com.wally.customersupport.agent.domain.model.AgentActivation;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AgentActivationResolver {

    private final AgentRegistryRepository registry;

    public AgentActivationResolution resolve(AgentActivationKey key) {
        Objects.requireNonNull(key, "key");
        try {
            return registry.findLatestActivation(
                            key.agentId(),
                            key.environment(),
                            key.channel(),
                            key.useCase())
                    .map(this::resolveLatest)
                    .orElseGet(() -> AgentActivationResolution.fallback(AgentResolutionReason.NOT_CONFIGURED));
        } catch (RuntimeException exception) {
            return AgentActivationResolution.fallback(AgentResolutionReason.REGISTRY_UNAVAILABLE);
        }
    }

    private AgentActivationResolution resolveLatest(AgentActivation activation) {
        if (activation.killSwitch()) {
            return AgentActivationResolution.fallback(AgentResolutionReason.KILL_SWITCH);
        }
        if (!activation.enabled()) {
            return AgentActivationResolution.fallback(AgentResolutionReason.DISABLED);
        }
        return AgentActivationResolution.active(activation.agentId(), activation.agentVersion());
    }
}
