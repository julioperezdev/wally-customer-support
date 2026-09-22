package com.wally.customersupport.agent.application.service;

import java.time.Instant;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Objects;

import com.wally.customersupport.agent.application.port.out.AgentRegistryCommandGuard;
import com.wally.customersupport.agent.application.port.out.AgentRegistryAuditRepository;
import com.wally.customersupport.agent.application.port.out.AgentRegistryRepository;
import com.wally.customersupport.agent.application.registry.AgentLifecycleTransitionCommand;
import com.wally.customersupport.agent.application.registry.AgentRegistryMutationReason;
import com.wally.customersupport.agent.application.registry.AgentRegistryMutationResult;
import com.wally.customersupport.agent.application.registry.AgentRegistryMutationStatus;
import com.wally.customersupport.agent.application.registry.AgentVersionDraftCommand;
import com.wally.customersupport.agent.domain.model.AgentLifecyclePolicy;
import com.wally.customersupport.agent.domain.model.AgentLifecycleState;
import com.wally.customersupport.agent.domain.model.AgentSemanticVersion;
import com.wally.customersupport.agent.domain.model.AgentVersion;
import com.wally.customersupport.shared.infrastructure.observability.StructuredEventLog;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Protected authoring boundary for immutable agent definitions and lifecycle. */
@Service
@Slf4j
public class AgentRegistryCommandService {

    private final AgentEvaluationControlPlaneAccessService accessService;
    private final AgentRegistryRepository registryRepository;
    private final AgentRegistryCommandGuard commandGuard;
    private final AgentRegistryAuditRepository auditRepository;
    private final AgentLifecyclePolicy lifecyclePolicy;

    private final boolean authoringWriteEnabled;

    public AgentRegistryCommandService(
            AgentEvaluationControlPlaneAccessService accessService,
            AgentRegistryRepository registryRepository,
            AgentRegistryCommandGuard commandGuard,
            AgentLifecyclePolicy lifecyclePolicy,
            @Value("${wcs.agent-registry.authoring-write-enabled:false}") boolean authoringWriteEnabled) {
        this(accessService, registryRepository, commandGuard, lifecyclePolicy,
                new NoOpAgentRegistryAuditRepository(), authoringWriteEnabled);
    }

    @Autowired
    public AgentRegistryCommandService(
            AgentEvaluationControlPlaneAccessService accessService,
            AgentRegistryRepository registryRepository,
            AgentRegistryCommandGuard commandGuard,
            AgentLifecyclePolicy lifecyclePolicy,
            AgentRegistryAuditRepository auditRepository,
            @Value("${wcs.agent-registry.authoring-write-enabled:false}") boolean authoringWriteEnabled) {
        this.accessService = accessService;
        this.registryRepository = registryRepository;
        this.commandGuard = commandGuard;
        this.lifecyclePolicy = lifecyclePolicy;
        this.auditRepository = auditRepository;
        this.authoringWriteEnabled = authoringWriteEnabled;
    }

    @Transactional
    public AgentRegistryMutationResult createDraft(
            AgentVersionDraftCommand command,
            String actorId,
            String idempotencyKey) {
        Objects.requireNonNull(command, "command");
        AgentRegistryMutationResult gate = authorize(actorId, "create_draft", command.agentId());
        if (gate != null) {
            return gate;
        }
        try {
            int version = resolveVersion(command);
            String semanticVersion = resolveSemanticVersion(command);
            if (registryRepository.findVersion(command.agentId(), version).isPresent()) {
                return result(AgentRegistryMutationStatus.CONFLICT,
                        AgentRegistryMutationReason.VERSION_ALREADY_EXISTS,
                        command.agentId(), version, null, null, null);
            }
            if (!claim(idempotencyKey, command.agentId(), "create_draft:" + version)) {
                return result(AgentRegistryMutationStatus.ALREADY_PROCESSED,
                        AgentRegistryMutationReason.IDEMPOTENCY_ALREADY_CLAIMED,
                        command.agentId(), version, null, null, null);
            }
            Instant now = Instant.now();
            AgentVersion draft = command.toDraft(version, semanticVersion, actorId, now);
            AgentVersion saved = registryRepository.saveVersion(draft);
            auditRepository.save(new com.wally.customersupport.agent.domain.model.AgentRegistryAuditEvent(
                    "VERSION_CREATED", saved.agentId(), saved.version(), null, saved.state().name(),
                    null, null, null, actorId, "draft_created", now));
            logMutation("AGENT_VERSION_DRAFT_CREATED", command.agentId(), saved, null, null);
            return result(AgentRegistryMutationStatus.CREATED,
                    AgentRegistryMutationReason.DRAFT_PERSISTED,
                    saved.agentId(), saved.version(), saved.state(), saved.createdAt(), now);
        } catch (IllegalArgumentException | NullPointerException exception) {
            return result(AgentRegistryMutationStatus.INVALID,
                    AgentRegistryMutationReason.INVALID_REQUEST,
                    command.agentId(), command.version(), null, null, null);
        } catch (RuntimeException exception) {
            return result(AgentRegistryMutationStatus.FAILED,
                    AgentRegistryMutationReason.REGISTRY_FAILED,
                    command.agentId(), command.version(), null, null, null);
        }
    }

    @Transactional
    public AgentRegistryMutationResult cloneVersion(
            String agentId,
            int sourceVersion,
            String actorId,
            String idempotencyKey) {
        AgentRegistryMutationResult gate = authorize(actorId, "clone_version", agentId);
        if (gate != null) {
            return gate;
        }
        try {
            AgentVersion source = registryRepository.findVersion(agentId, sourceVersion).orElse(null);
            if (source == null) {
                return result(AgentRegistryMutationStatus.INVALID,
                        AgentRegistryMutationReason.VERSION_NOT_FOUND,
                        agentId, sourceVersion, null, null, null);
            }
            int nextVersion = nextVersion(agentId);
            String nextSemanticVersion = nextSemanticVersion(agentId);
            if (!claim(idempotencyKey, agentId, "clone_version:" + sourceVersion)) {
                return result(AgentRegistryMutationStatus.ALREADY_PROCESSED,
                        AgentRegistryMutationReason.IDEMPOTENCY_ALREADY_CLAIMED,
                        agentId, nextVersion, null, null, null);
            }
            Instant now = Instant.now();
            AgentVersion draft = AgentVersion.draft(
                    source.agentId(),
                    nextVersion,
                    nextSemanticVersion,
                    source.name(),
                    source.purpose(),
                    source.modelProvider(),
                    source.modelId(),
                    source.inferenceParameters(),
                    source.systemPromptVersion(),
                    source.systemPromptHash(),
                    source.inputSchemaVersion(),
                    source.outputSchemaVersion(),
                    source.allowedTools(),
                    source.knowledgeSources(),
                    source.memoryPolicy(),
                    source.responsePolicy(),
                    source.timeout(),
                    source.maxSteps(),
                    source.maxInputTokens(),
                    source.maxOutputTokens(),
                    source.budgetLimitUsd(),
                    source.fallbackAgentId(),
                    source.evaluationSuiteVersion(),
                    source.invocationConfiguration(),
                    actorId,
                    now);
            AgentVersion saved = registryRepository.saveVersion(draft);
            auditRepository.save(new com.wally.customersupport.agent.domain.model.AgentRegistryAuditEvent(
                    "VERSION_CLONED", saved.agentId(), saved.version(), source.state().name(), saved.state().name(),
                    null, null, null, actorId, "version_cloned", now));
            logMutation("AGENT_VERSION_CLONED", agentId, saved, source.state(), sourceVersion);
            return result(AgentRegistryMutationStatus.CREATED,
                    AgentRegistryMutationReason.VERSION_CLONED,
                    saved.agentId(), saved.version(), saved.state(), saved.createdAt(), now);
        } catch (IllegalArgumentException exception) {
            return result(AgentRegistryMutationStatus.INVALID,
                    AgentRegistryMutationReason.INVALID_REQUEST, agentId, sourceVersion, null, null, null);
        } catch (RuntimeException exception) {
            return result(AgentRegistryMutationStatus.CONFLICT,
                    AgentRegistryMutationReason.LIFECYCLE_CHANGED_CONCURRENTLY, agentId, sourceVersion, null, null, null);
        }
    }

    @Transactional
    public AgentRegistryMutationResult transition(
            AgentLifecycleTransitionCommand command,
            String actorId,
            String idempotencyKey) {
        Objects.requireNonNull(command, "command");
        if (requiresPublicationApproval(command.targetState()) && !command.hasApprovalReferences()) {
            return result(AgentRegistryMutationStatus.INVALID,
                    AgentRegistryMutationReason.APPROVAL_REFERENCES_REQUIRED,
                    command.agentId(), command.version(), null, null, null);
        }
        AgentRegistryMutationResult gate = authorizeTransition(actorId, command);
        if (gate != null) {
            return gate;
        }
        try {
            if (!isAuthoringTarget(command.targetState())) {
                return result(AgentRegistryMutationStatus.INVALID,
                        AgentRegistryMutationReason.INVALID_LIFECYCLE_TARGET,
                        command.agentId(), command.version(), null, null, null);
            }
            AgentVersion current = registryRepository.findVersion(command.agentId(), command.version()).orElse(null);
            if (current == null) {
                return result(AgentRegistryMutationStatus.INVALID,
                        AgentRegistryMutationReason.VERSION_NOT_FOUND,
                        command.agentId(), command.version(), null, null, null);
            }
            Instant now = Instant.now();
            AgentVersion transitioned = lifecyclePolicy.transition(
                    current, command.targetState(), actorId, now);
            if (!claim(idempotencyKey, command.agentId(), "transition_lifecycle:" + command.version())) {
                return result(AgentRegistryMutationStatus.ALREADY_PROCESSED,
                        AgentRegistryMutationReason.IDEMPOTENCY_ALREADY_CLAIMED,
                        command.agentId(), command.version(), current.state(), current.createdAt(), null);
            }
            AgentVersion saved = registryRepository.updateLifecycle(
                    current.agentId(),
                    current.version(),
                    current.state(),
                    transitioned.state(),
                    transitioned.approvedBy(),
                    transitioned.approvedAt());
            auditRepository.save(new com.wally.customersupport.agent.domain.model.AgentRegistryAuditEvent(
                    "LIFECYCLE_TRANSITIONED", saved.agentId(), saved.version(), current.state().name(),
                    saved.state().name(), null, null, null, actorId, command.reason(), now));
            logMutation("AGENT_VERSION_LIFECYCLE_TRANSITIONED", command.agentId(), saved,
                    current.state(), null);
            return result(AgentRegistryMutationStatus.TRANSITIONED,
                    AgentRegistryMutationReason.LIFECYCLE_TRANSITIONED,
                    saved.agentId(), saved.version(), saved.state(), saved.createdAt(), now);
        } catch (IllegalArgumentException | IllegalStateException exception) {
            return result(AgentRegistryMutationStatus.INVALID,
                    AgentRegistryMutationReason.INVALID_REQUEST,
                    command.agentId(), command.version(), null, null, null);
        } catch (RuntimeException exception) {
            return result(AgentRegistryMutationStatus.FAILED,
                    AgentRegistryMutationReason.REGISTRY_FAILED,
                    command.agentId(), command.version(), null, null, null);
        }
    }

    private AgentRegistryMutationResult authorize(String actorId, String operation, String agentId) {
        if (!authoringWriteEnabled) {
            log("AGENT_REGISTRY_AUTHORING_DISABLED", operation, agentId,
                    AgentRegistryMutationStatus.DISABLED, AgentRegistryMutationReason.FEATURE_DISABLED);
            return result(AgentRegistryMutationStatus.DISABLED,
                    AgentRegistryMutationReason.FEATURE_DISABLED, agentId, null, null, null, null);
        }
        if (!accessService.authorizeRegistryWrite(actorId).authorized()) {
            log("AGENT_REGISTRY_AUTHORING_DENIED", operation, agentId,
                    AgentRegistryMutationStatus.DENIED, AgentRegistryMutationReason.AUTHORIZATION_DENIED);
            return result(AgentRegistryMutationStatus.DENIED,
                    AgentRegistryMutationReason.AUTHORIZATION_DENIED, agentId, null, null, null, null);
        }
        return null;
    }

    private AgentRegistryMutationResult authorizeTransition(
            String actorId,
            AgentLifecycleTransitionCommand command) {
        AgentRegistryMutationResult gate = authorize(actorId, "transition_lifecycle", command.agentId());
        if (gate != null) {
            return gate;
        }
        if (command.targetState() == AgentLifecycleState.APPROVED
                || command.targetState() == AgentLifecycleState.ACTIVE
                || command.targetState() == AgentLifecycleState.RETIRED) {
            if (!accessService.authorizeRegistryPublish(actorId).authorized()) {
                return result(AgentRegistryMutationStatus.DENIED,
                        AgentRegistryMutationReason.AUTHORIZATION_DENIED,
                        command.agentId(), command.version(), null, null, null);
            }
        }
        return null;
    }

    private boolean claim(String key, String agentId, String operation) {
        try {
            String normalizedKey = Objects.requireNonNull(key, "idempotencyKey").strip();
            if (normalizedKey.isBlank()) {
                throw new IllegalArgumentException("idempotencyKey must not be blank");
            }
            return commandGuard.tryAcquire(operation + ":" + agentId + ":" + normalizedKey);
        } catch (RuntimeException exception) {
            log("AGENT_REGISTRY_AUTHORING_FAILED", operation, agentId,
                    AgentRegistryMutationStatus.FAILED, AgentRegistryMutationReason.IDEMPOTENCY_GUARD_FAILED);
            throw exception;
        }
    }

    private int resolveVersion(AgentVersionDraftCommand command) {
        if (command.version() != null) {
            if (command.version() < 1) {
                throw new IllegalArgumentException("version must be positive");
            }
            return command.version();
        }
        return nextVersion(command.agentId());
    }

    private int nextVersion(String agentId) {
        return registryRepository.findVersions(agentId).stream()
                .mapToInt(AgentVersion::version)
                .max()
                .orElse(0) + 1;
    }

    private String nextSemanticVersion(String agentId) {
        return registryRepository.findVersions(agentId).stream()
                .map(version -> AgentSemanticVersion.parse(version.semanticVersion()))
                .max(AgentSemanticVersion::compareTo)
                .map(AgentSemanticVersion::nextPatch)
                .orElse(new AgentSemanticVersion(1, 0, 0))
                .toString();
    }

    private String resolveSemanticVersion(AgentVersionDraftCommand command) {
        List<AgentVersion> existing = registryRepository.findVersions(command.agentId());
        AgentSemanticVersion latest = existing.stream()
                .map(version -> AgentSemanticVersion.parse(version.semanticVersion()))
                .max(AgentSemanticVersion::compareTo)
                .orElse(null);
        String requestedValue = command.semanticVersion();
        AgentSemanticVersion requested = requestedValue == null || requestedValue.isBlank()
                ? latest == null ? new AgentSemanticVersion(1, 0, 0) : latest.nextPatch()
                : AgentSemanticVersion.parse(requestedValue);
        if (latest == null && !requested.equals(new AgentSemanticVersion(1, 0, 0))) {
            throw new IllegalArgumentException("the first version of an agent must be 1.0.0");
        }
        if (latest != null && requested.compareTo(latest) <= 0) {
            throw new IllegalArgumentException("semanticVersion must be greater than the latest version");
        }
        return requested.toString();
    }

    private static boolean isAuthoringTarget(AgentLifecycleState target) {
        return target == AgentLifecycleState.CANDIDATE
                || target == AgentLifecycleState.EVALUATED
                || target == AgentLifecycleState.APPROVED
                || target == AgentLifecycleState.ACTIVE
                || target == AgentLifecycleState.RETIRED;
    }

    private static boolean requiresPublicationApproval(AgentLifecycleState target) {
        return target == AgentLifecycleState.APPROVED
                || target == AgentLifecycleState.ACTIVE
                || target == AgentLifecycleState.RETIRED;
    }

    private void logMutation(
            String event,
            String agentId,
            AgentVersion version,
            AgentLifecycleState previousState,
            Integer sourceVersion) {
        LinkedHashMap<String, Object> fields = new LinkedHashMap<>();
        fields.put("agentId", agentId);
        fields.put("agentVersion", version.version());
        fields.put("state", version.state().name());
        fields.put("previousState", previousState == null ? null : previousState.name());
        fields.put("sourceVersion", sourceVersion);
        StructuredEventLog.info(log, event, fields);
    }

    private void log(
            String event,
            String operation,
            String agentId,
            AgentRegistryMutationStatus status,
            AgentRegistryMutationReason reason) {
        StructuredEventLog.info(log, event, java.util.Map.of(
                "operation", operation,
                "agentId", agentId,
                "status", status.name(),
                "reason", reason.name()));
    }

    private static AgentRegistryMutationResult result(
            AgentRegistryMutationStatus status,
            AgentRegistryMutationReason reason,
            String agentId,
            Integer version,
            AgentLifecycleState state,
            Instant createdAt,
            Instant changedAt) {
        return new AgentRegistryMutationResult(status, reason, agentId, version, state, createdAt, changedAt);
    }
}
