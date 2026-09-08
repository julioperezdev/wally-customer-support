package com.wally.customersupport.agent.application.service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

import com.wally.customersupport.agent.application.activation.AgentActivationPreflightCommand;
import com.wally.customersupport.agent.application.activation.AgentActivationPreflightCheck;
import com.wally.customersupport.agent.application.activation.AgentActivationPreflightCheckStatus;
import com.wally.customersupport.agent.application.activation.AgentActivationPreflightResult;
import com.wally.customersupport.agent.application.activation.AgentActivationPreflightStatus;
import com.wally.customersupport.agent.application.evaluation.AgentEvaluationControlPlaneAccessDecision;
import com.wally.customersupport.agent.application.port.out.AgentRegistryRepository;
import com.wally.customersupport.agent.domain.model.AgentActivation;
import com.wally.customersupport.agent.domain.model.AgentActivationPolicy;
import com.wally.customersupport.agent.domain.model.AgentVersion;
import com.wally.customersupport.shared.infrastructure.observability.StructuredEventLog;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Validates an activation request without claiming an idempotency key or
 * writing an activation. The same domain policy used by the write path is
 * exercised so the UI cannot report a false-ready activation.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AgentActivationPreflightService {

    private final AgentEvaluationControlPlaneAccessService accessService;
    private final AgentRegistryRepository registryRepository;
    private final AgentActivationPolicy activationPolicy;

    @Transactional(readOnly = true)
    public AgentActivationPreflightResult check(AgentActivationPreflightCommand command, String actorId) {
        AgentEvaluationControlPlaneAccessDecision decision = accessService.authorizeRegistry(
                actorId, command.environment());
        List<AgentActivationPreflightCheck> checks = new ArrayList<>();
        checks.add(check(
                "AUTHORIZATION",
                decision.authorized() ? AgentActivationPreflightCheckStatus.PASS
                        : AgentActivationPreflightCheckStatus.FAIL,
                decision.authorized() ? "Actor autorizado para consultar el preflight"
                        : "Actor no autorizado para este ambiente"));
        if (!decision.authorized()) {
            return result(command, AgentActivationPreflightStatus.DENIED, false, checks);
        }

        AgentVersion version = registryRepository.findVersion(command.agentId(), command.agentVersion())
                .orElse(null);
        checks.add(check(
                "VERSION_EXISTS",
                version == null ? AgentActivationPreflightCheckStatus.FAIL : AgentActivationPreflightCheckStatus.PASS,
                version == null ? "La versión solicitada no existe" : "La versión existe en el registry"));
        if (version == null) {
            return result(command, AgentActivationPreflightStatus.BLOCKED, false, checks);
        }

        boolean approved = version.canBeActivated();
        checks.add(check(
                "VERSION_APPROVED",
                approved ? AgentActivationPreflightCheckStatus.PASS : AgentActivationPreflightCheckStatus.FAIL,
                approved ? "La versión está aprobada" : "La versión no está aprobada para activación"));

        AgentActivation current = registryRepository.findLatestActivation(
                command.agentId(), command.environment(), command.channel(), command.useCase()).orElse(null);
        checks.add(check(
                "CURRENT_ACTIVATION",
                current == null ? AgentActivationPreflightCheckStatus.WARN : AgentActivationPreflightCheckStatus.PASS,
                current == null ? "No existe una activación previa para este scope"
                        : "Existe una activación previa; se conservará para rollback"));

        if (isBlank(command.approvalReference()) || isBlank(command.operationalApprovalReference())) {
            checks.add(check("APPROVAL_REFERENCES", AgentActivationPreflightCheckStatus.FAIL,
                    "Faltan referencias de aprobación requeridas"));
        } else {
            checks.add(check("APPROVAL_REFERENCES", AgentActivationPreflightCheckStatus.PASS,
                    "Las referencias de aprobación están presentes"));
        }

        try {
            activationPolicy.activate(
                    version,
                    command.toDomainRequest(),
                    current == null ? null : current.agentVersion(),
                    actorId,
                    Instant.now());
            checks.add(check("ACTIVATION_POLICY", AgentActivationPreflightCheckStatus.PASS,
                    "La solicitud cumple la política de activación"));
        } catch (IllegalArgumentException | IllegalStateException exception) {
            checks.add(check("ACTIVATION_POLICY", AgentActivationPreflightCheckStatus.FAIL,
                    "La solicitud no cumple la política de activación"));
        }

        boolean blocked = checks.stream().anyMatch(check -> check.status() == AgentActivationPreflightCheckStatus.FAIL);
        AgentActivationPreflightStatus status = blocked
                ? AgentActivationPreflightStatus.BLOCKED
                : AgentActivationPreflightStatus.READY;
        return result(command, status, !blocked, checks);
    }

    private AgentActivationPreflightResult result(
            AgentActivationPreflightCommand command,
            AgentActivationPreflightStatus status,
            boolean canActivate,
            List<AgentActivationPreflightCheck> checks) {
        AgentActivationPreflightResult result = new AgentActivationPreflightResult(
                status,
                canActivate,
                command.agentId(),
                command.agentVersion(),
                command.environment(),
                command.channel(),
                command.useCase(),
                checks);
        LinkedHashMap<String, Object> fields = new LinkedHashMap<>();
        fields.put("agentId", command.agentId());
        fields.put("agentVersion", command.agentVersion());
        fields.put("environment", command.environment());
        fields.put("channel", command.channel());
        fields.put("useCase", command.useCase());
        fields.put("status", status.name());
        fields.put("canActivate", canActivate);
        fields.put("failedChecks", checks.stream()
                .filter(check -> check.status() == AgentActivationPreflightCheckStatus.FAIL)
                .count());
        StructuredEventLog.info(log, "AGENT_REGISTRY_PREFLIGHT_COMPLETED", fields);
        return result;
    }

    private static AgentActivationPreflightCheck check(
            String code,
            AgentActivationPreflightCheckStatus status,
            String message) {
        return new AgentActivationPreflightCheck(code, status, message);
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
