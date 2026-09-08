package com.wally.customersupport.agent.application.service;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

import com.wally.customersupport.agent.application.activation.AgentActivationActionCommand;
import com.wally.customersupport.agent.application.activation.AgentActivationCommand;
import com.wally.customersupport.agent.application.activation.AgentActivationMutationReason;
import com.wally.customersupport.agent.application.activation.AgentActivationMutationResult;
import com.wally.customersupport.agent.application.activation.AgentActivationMutationStatus;
import com.wally.customersupport.agent.application.port.out.AgentActivationCommandGuard;
import com.wally.customersupport.agent.application.port.out.AgentRegistryRepository;
import com.wally.customersupport.agent.domain.model.AgentActivation;
import com.wally.customersupport.agent.domain.model.AgentActivationPolicy;
import com.wally.customersupport.agent.domain.model.AgentVersion;
import com.wally.customersupport.shared.infrastructure.observability.StructuredEventLog;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Provider-neutral write boundary for controlled registry activation actions. */
@Service
@RequiredArgsConstructor
@Slf4j
public class AgentActivationCommandService {

    private final AgentEvaluationControlPlaneAccessService accessService;
    private final AgentRegistryRepository registryRepository;
    private final AgentActivationCommandGuard commandGuard;
    private final AgentActivationPolicy activationPolicy;

    @Transactional
    public AgentActivationMutationResult activate(
            AgentActivationCommand command,
            String actorId,
            String idempotencyKey,
            boolean writeEnabled) {
        Objects.requireNonNull(command, "command");
        AgentActivationMutationResult gate = authorize(
                actorId, command.environment(), writeEnabled, "activate", command.agentId());
        if (gate != null) {
            return gate;
        }
        try {
            AgentVersion version = registryRepository.findVersion(command.agentId(), command.agentVersion())
                    .orElse(null);
            if (version == null) {
                return outcome(AgentActivationMutationStatus.INVALID,
                        AgentActivationMutationReason.VERSION_NOT_FOUND, command, null);
            }
            AgentActivation current = registryRepository.findLatestActivation(
                    command.agentId(), command.environment(), command.channel(), command.useCase()).orElse(null);
            AgentActivation activation = activationPolicy.activate(
                    version,
                    command.toDomainRequest(),
                    current == null ? null : current.agentVersion(),
                    actorId,
                    Instant.now());
            if (!claim(idempotencyKey, command.agentId(), "activate")) {
                return outcome(AgentActivationMutationStatus.ALREADY_PROCESSED,
                        AgentActivationMutationReason.IDEMPOTENCY_ALREADY_CLAIMED, command, null);
            }
            AgentActivation saved = registryRepository.saveActivation(activation);
            return outcome(AgentActivationMutationStatus.ACTIVATED,
                    AgentActivationMutationReason.ACTIVATION_PERSISTED, command, saved);
        } catch (IllegalArgumentException | IllegalStateException exception) {
            return outcome(AgentActivationMutationStatus.INVALID,
                    AgentActivationMutationReason.INVALID_REQUEST, command, null);
        } catch (RuntimeException exception) {
            return outcome(AgentActivationMutationStatus.FAILED,
                    AgentActivationMutationReason.REGISTRY_FAILED, command, null);
        }
    }

    @Transactional
    public AgentActivationMutationResult killSwitch(
            AgentActivationActionCommand command,
            String actorId,
            String idempotencyKey,
            boolean writeEnabled) {
        Objects.requireNonNull(command, "command");
        AgentActivationMutationResult gate = authorize(
                actorId, command.environment(), writeEnabled, "kill_switch", command.agentId());
        if (gate != null) {
            return gate;
        }
        try {
            AgentActivation current = currentActivation(command);
            if (current == null) {
                return actionOutcome(AgentActivationMutationStatus.INVALID,
                        AgentActivationMutationReason.ACTIVATION_NOT_FOUND, command, null);
            }
            if (!claim(idempotencyKey, command.agentId(), "kill_switch")) {
                return actionOutcome(AgentActivationMutationStatus.ALREADY_PROCESSED,
                        AgentActivationMutationReason.IDEMPOTENCY_ALREADY_CLAIMED, command, null);
            }
            AgentActivation saved = registryRepository.saveActivation(
                    activationPolicy.killSwitch(current, actorId, Instant.now()));
            return actionOutcome(AgentActivationMutationStatus.KILL_SWITCHED,
                    AgentActivationMutationReason.KILL_SWITCH_PERSISTED, command, saved);
        } catch (IllegalArgumentException | IllegalStateException exception) {
            return actionOutcome(AgentActivationMutationStatus.INVALID,
                    AgentActivationMutationReason.INVALID_REQUEST, command, null);
        } catch (RuntimeException exception) {
            return actionOutcome(AgentActivationMutationStatus.FAILED,
                    AgentActivationMutationReason.REGISTRY_FAILED, command, null);
        }
    }

    @Transactional
    public AgentActivationMutationResult rollback(
            AgentActivationActionCommand command,
            String actorId,
            String idempotencyKey,
            boolean writeEnabled) {
        Objects.requireNonNull(command, "command");
        AgentActivationMutationResult gate = authorize(
                actorId, command.environment(), writeEnabled, "rollback", command.agentId());
        if (gate != null) {
            return gate;
        }
        try {
            AgentActivation current = currentActivation(command);
            if (current == null) {
                return actionOutcome(AgentActivationMutationStatus.INVALID,
                        AgentActivationMutationReason.ACTIVATION_NOT_FOUND, command, null);
            }
            if (current.previousVersion() == null) {
                return actionOutcome(AgentActivationMutationStatus.INVALID,
                        AgentActivationMutationReason.PREVIOUS_VERSION_NOT_FOUND, command, null);
            }
            AgentVersion previous = registryRepository.findVersion(command.agentId(), current.previousVersion())
                    .orElse(null);
            if (previous == null) {
                return actionOutcome(AgentActivationMutationStatus.INVALID,
                        AgentActivationMutationReason.PREVIOUS_VERSION_NOT_FOUND, command, null);
            }
            if (!claim(idempotencyKey, command.agentId(), "rollback")) {
                return actionOutcome(AgentActivationMutationStatus.ALREADY_PROCESSED,
                        AgentActivationMutationReason.IDEMPOTENCY_ALREADY_CLAIMED, command, null);
            }
            AgentActivation saved = registryRepository.saveActivation(
                    activationPolicy.rollback(current, previous, actorId, Instant.now()));
            return actionOutcome(AgentActivationMutationStatus.ROLLED_BACK,
                    AgentActivationMutationReason.ROLLBACK_PERSISTED, command, saved);
        } catch (IllegalArgumentException | IllegalStateException exception) {
            return actionOutcome(AgentActivationMutationStatus.INVALID,
                    AgentActivationMutationReason.INVALID_REQUEST, command, null);
        } catch (RuntimeException exception) {
            return actionOutcome(AgentActivationMutationStatus.FAILED,
                    AgentActivationMutationReason.REGISTRY_FAILED, command, null);
        }
    }

    private AgentActivationMutationResult authorize(
            String actorId,
            String environment,
            boolean writeEnabled,
            String operation,
            String agentId) {
        if (!writeEnabled) {
            log("AGENT_REGISTRY_MUTATION_DISABLED", operation, agentId,
                    AgentActivationMutationStatus.DISABLED, AgentActivationMutationReason.FEATURE_DISABLED);
            return emptyOutcome(AgentActivationMutationStatus.DISABLED,
                    AgentActivationMutationReason.FEATURE_DISABLED, agentId);
        }
        if (!accessService.authorizeRegistryWrite(actorId, environment).authorized()) {
            log("AGENT_REGISTRY_MUTATION_DENIED", operation, agentId,
                    AgentActivationMutationStatus.DENIED, AgentActivationMutationReason.AUTHORIZATION_DENIED);
            return emptyOutcome(AgentActivationMutationStatus.DENIED,
                    AgentActivationMutationReason.AUTHORIZATION_DENIED, agentId);
        }
        return null;
    }

    private boolean claim(String key, String agentId, String operation) {
        try {
            return commandGuard.tryAcquire(key);
        } catch (RuntimeException exception) {
            log("AGENT_REGISTRY_MUTATION_FAILED", operation, agentId,
                    AgentActivationMutationStatus.FAILED, AgentActivationMutationReason.IDEMPOTENCY_GUARD_FAILED);
            throw exception;
        }
    }

    private AgentActivation currentActivation(AgentActivationActionCommand command) {
        return registryRepository.findLatestActivation(
                command.agentId(), command.environment(), command.channel(), command.useCase()).orElse(null);
    }

    private static AgentActivationMutationResult outcome(
            AgentActivationMutationStatus status,
            AgentActivationMutationReason reason,
            AgentActivationCommand command,
            AgentActivation activation) {
        return new AgentActivationMutationResult(
                status,
                reason,
                command.agentId(),
                activation == null ? command.agentVersion() : activation.agentVersion(),
                command.environment(),
                command.channel(),
                command.useCase(),
                activation == null ? null : activation.activatedAt());
    }

    private static AgentActivationMutationResult actionOutcome(
            AgentActivationMutationStatus status,
            AgentActivationMutationReason reason,
            AgentActivationActionCommand command,
            AgentActivation activation) {
        return new AgentActivationMutationResult(
                status,
                reason,
                command.agentId(),
                activation == null ? null : activation.agentVersion(),
                command.environment(),
                command.channel(),
                command.useCase(),
                activation == null ? null : activation.activatedAt());
    }

    private static AgentActivationMutationResult emptyOutcome(
            AgentActivationMutationStatus status,
            AgentActivationMutationReason reason,
            String agentId) {
        return new AgentActivationMutationResult(status, reason, agentId, null, null, null, null, null);
    }

    private void log(
            String event,
            String operation,
            String agentId,
            AgentActivationMutationStatus status,
            AgentActivationMutationReason reason) {
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("operation", operation);
        fields.put("agentId", agentId);
        fields.put("status", status.name());
        fields.put("reason", reason.name());
        StructuredEventLog.info(log, event, fields);
    }
}
