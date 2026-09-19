package com.wally.customersupport.agent.application.service;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import com.wally.customersupport.agent.application.port.out.AgentRegistryRepository;
import com.wally.customersupport.agent.domain.model.AgentVersion;
import com.wally.customersupport.shared.infrastructure.observability.StructuredEventLog;
import org.springframework.beans.factory.annotation.Autowired;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/** Resolves a validated immutable execution snapshot without executing it. */
@Service
@Slf4j
public class AgentRuntimeDefinitionResolver {

    private final AgentActivationResolver activationResolver;
    private final AgentRegistryRepository registry;
    private final AgentSpecialistRegistry specialistRegistry;

    /** Compatibility constructor for pure application tests. */
    public AgentRuntimeDefinitionResolver(
            AgentActivationResolver activationResolver,
            AgentRegistryRepository registry) {
        this(activationResolver, registry, AgentSpecialistRegistry.defaultRegistry());
    }

    @Autowired
    public AgentRuntimeDefinitionResolver(
            AgentActivationResolver activationResolver,
            AgentRegistryRepository registry,
            AgentSpecialistRegistry specialistRegistry) {
        this.activationResolver = activationResolver;
        this.registry = registry;
        this.specialistRegistry = specialistRegistry;
    }

    public AgentRuntimeDefinitionResolution resolve(AgentActivationKey key) {
        Objects.requireNonNull(key, "key");

        AgentActivationResolution activation = activationResolver.resolve(key);
        if (activation == null) {
            return record(key, AgentRuntimeDefinitionResolution.fallback(
                    AgentDefinitionResolutionReason.INVALID_DEFINITION));
        }
        if (!activation.isActive()) {
            return record(key, AgentRuntimeDefinitionResolution.fallback(
                    mapActivationReason(activation.reason())));
        }

        Optional<AgentVersion> storedVersion;
        try {
            storedVersion = registry.findVersion(activation.agentId(), activation.agentVersion());
        } catch (RuntimeException exception) {
            return record(key, AgentRuntimeDefinitionResolution.fallback(
                    AgentDefinitionResolutionReason.REGISTRY_UNAVAILABLE));
        }
        if (storedVersion == null) {
            return record(key, AgentRuntimeDefinitionResolution.fallback(
                    AgentDefinitionResolutionReason.REGISTRY_UNAVAILABLE));
        }
        if (storedVersion.isEmpty()) {
            return record(key, AgentRuntimeDefinitionResolution.fallback(
                    AgentDefinitionResolutionReason.VERSION_NOT_FOUND));
        }

        AgentVersion version = storedVersion.get();
        try {
            if (!activation.agentId().equals(version.agentId())
                    || activation.agentVersion() != version.version()) {
                return record(key, AgentRuntimeDefinitionResolution.fallback(
                        AgentDefinitionResolutionReason.VERSION_MISMATCH));
            }
            if (!version.canBeActivated()) {
                return record(key, AgentRuntimeDefinitionResolution.fallback(
                        AgentDefinitionResolutionReason.VERSION_NOT_PUBLISHABLE));
            }
            AgentRuntimeDefinition definition = AgentRuntimeDefinition.from(version);
            AgentSpecialistRegistry.Validation validation = specialistRegistry.validateDefinition(definition);
            if (!validation.valid()) {
                Map<String, Object> fields = new LinkedHashMap<>();
                fields.put("agentId", definition.agentId());
                fields.put("agentVersion", definition.agentVersion());
                fields.put("reason", validation.reason());
                fields.put("invalidTools", validation.invalidTools());
                StructuredEventLog.warn(log, "AGENT_SPECIALIST_DEFINITION_REJECTED", fields);
                return record(key, AgentRuntimeDefinitionResolution.fallback(
                        AgentDefinitionResolutionReason.INVALID_DEFINITION));
            }
            return record(key, AgentRuntimeDefinitionResolution.active(definition));
        } catch (RuntimeException exception) {
            return record(key, AgentRuntimeDefinitionResolution.fallback(
                    AgentDefinitionResolutionReason.INVALID_DEFINITION));
        }
    }

    private AgentRuntimeDefinitionResolution record(
            AgentActivationKey key,
            AgentRuntimeDefinitionResolution resolution) {
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("environment", key.environment());
        fields.put("channel", key.channel());
        fields.put("useCase", key.useCase());
        fields.put("definitionStatus", resolution.status().name());
        fields.put("definitionReason", resolution.reason().name());
        if (resolution.isActive()) {
            fields.put("agentId", resolution.definition().agentId());
            fields.put("agentVersion", resolution.definition().agentVersion());
            fields.put("modelProvider", resolution.definition().modelProvider());
            fields.put("model", resolution.definition().modelId());
            StructuredEventLog.info(log, "AGENT_DEFINITION_RESOLVED", fields);
        } else {
            StructuredEventLog.warn(log, "AGENT_DEFINITION_RESOLUTION_FALLBACK", fields);
        }
        return resolution;
    }

    private static AgentDefinitionResolutionReason mapActivationReason(AgentResolutionReason reason) {
        return switch (reason) {
            case ACTIVE -> AgentDefinitionResolutionReason.INVALID_DEFINITION;
            case NOT_CONFIGURED -> AgentDefinitionResolutionReason.NOT_CONFIGURED;
            case DISABLED -> AgentDefinitionResolutionReason.DISABLED;
            case KILL_SWITCH -> AgentDefinitionResolutionReason.KILL_SWITCH;
            case REGISTRY_UNAVAILABLE -> AgentDefinitionResolutionReason.REGISTRY_UNAVAILABLE;
        };
    }
}
