package com.wally.customersupport.backoffice.infrastructure.http;

import java.security.Principal;
import java.util.Map;

import com.wally.customersupport.agent.application.evaluation.AgentEvaluationControlPlaneAccessDecision;
import com.wally.customersupport.agent.application.service.AgentEvaluationControlPlaneAccessService;
import com.wally.customersupport.backoffice.application.model.BackofficeAgentMapQuery;
import com.wally.customersupport.backoffice.application.model.BackofficeAgentMapSimulation;
import com.wally.customersupport.backoffice.application.model.BackofficeAgentMapSimulationRequest;
import com.wally.customersupport.backoffice.application.service.BackofficeAgentMapService;
import com.wally.customersupport.shared.infrastructure.observability.StructuredEventLog;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Read-only map and deactivation simulation for the agent platform. */
@RestController
@RequestMapping("/internal/backoffice/agent-map")
@RequiredArgsConstructor
@Slf4j
public class BackofficeAgentMapController {

    private final AgentEvaluationControlPlaneAccessService accessService;
    private final BackofficeAgentMapService mapService;

    @GetMapping
    public ResponseEntity<?> describe(
            Principal principal,
            @RequestParam(defaultValue = "prod") String environment,
            @RequestParam(required = false) String channel,
            @RequestParam(required = false) String useCase,
            @RequestParam(required = false) String agentId) {
        AgentEvaluationControlPlaneAccessDecision decision = accessService.authorizeRegistry(
                actorId(principal), environment);
        if (!decision.authorized()) {
            logAccess("agent_map.read", decision);
            return forbidden();
        }
        try {
            var result = mapService.describe(new BackofficeAgentMapQuery(environment, channel, useCase, agentId));
            StructuredEventLog.info(log, "BACKOFFICE_AGENT_MAP_VIEWED", Map.of(
                    "operation", "agent_map.read",
                    "environment", environment,
                    "useCase", useCase == null ? "all" : useCase,
                    "useCaseCount", result.useCases().size(),
                    "evidenceRunsScanned", result.evidenceRunsScanned()));
            return ResponseEntity.ok(result);
        } catch (IllegalArgumentException exception) {
            return badRequest(exception.getMessage());
        }
    }

    @PostMapping("/simulations")
    public ResponseEntity<?> simulate(
            Principal principal,
            @RequestBody BackofficeAgentMapSimulationRequest request) {
        AgentEvaluationControlPlaneAccessDecision decision = accessService.authorizeRegistry(
                actorId(principal), request.environment());
        if (!decision.authorized()) {
            logAccess("agent_map.simulation", decision);
            return forbidden();
        }
        try {
            BackofficeAgentMapSimulation result = mapService.simulate(request);
            StructuredEventLog.info(log, "BACKOFFICE_AGENT_MAP_SIMULATED", Map.of(
                    "operation", "agent_map.simulation",
                    "environment", request.environment(),
                    "channel", request.channel(),
                    "useCase", request.useCase(),
                    "disabledAgentId", request.disabledAgentId(),
                    "outcome", result.outcome()));
            return ResponseEntity.ok(result);
        } catch (IllegalArgumentException exception) {
            return badRequest(exception.getMessage());
        }
    }

    private void logAccess(String operation, AgentEvaluationControlPlaneAccessDecision decision) {
        StructuredEventLog.warn(log, "BACKOFFICE_AGENT_MAP_ACCESS_DENIED", Map.of(
                "operation", operation,
                "capability", AgentEvaluationControlPlaneAccessService.REGISTRY_READ_CAPABILITY,
                "result", decision.status().name(),
                "reason", decision.reason().name()));
    }

    private static String actorId(Principal principal) {
        return principal == null ? null : principal.getName();
    }

    private static ResponseEntity<Map<String, String>> forbidden() {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("code", "ACCESS_DENIED"));
    }

    private static ResponseEntity<Map<String, String>> badRequest(String message) {
        return ResponseEntity.badRequest().body(Map.of(
                "code", "INVALID_AGENT_MAP_REQUEST",
                "message", message == null ? "invalid request" : message));
    }
}
