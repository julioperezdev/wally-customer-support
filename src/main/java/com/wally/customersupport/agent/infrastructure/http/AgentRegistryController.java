package com.wally.customersupport.agent.infrastructure.http;

import java.security.Principal;
import java.util.List;
import java.util.Map;

import com.wally.customersupport.agent.application.evaluation.AgentEvaluationControlPlaneAccessDecision;
import com.wally.customersupport.agent.application.registry.AgentRegistryAgentView;
import com.wally.customersupport.agent.application.registry.AgentRegistryQuery;
import com.wally.customersupport.agent.application.service.AgentEvaluationControlPlaneAccessService;
import com.wally.customersupport.agent.application.service.AgentRegistryQueryService;
import com.wally.customersupport.shared.infrastructure.observability.StructuredEventLog;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Read-only HTTP facade for sanitized agent versions and activation history. */
@RestController
@RequestMapping("/internal/agent-registry")
@RequiredArgsConstructor
@Slf4j
public class AgentRegistryController {

    private final AgentEvaluationControlPlaneAccessService accessService;
    private final AgentRegistryQueryService queryService;

    @GetMapping("/agents")
    public ResponseEntity<?> searchAgents(
            Principal principal,
            @RequestParam(required = false) String agentId,
            @RequestParam(required = false) String environment,
            @RequestParam(required = false) String channel,
            @RequestParam(required = false) String useCase,
            @RequestParam(defaultValue = "50") int limit) {
        long startedAt = System.nanoTime();
        AgentEvaluationControlPlaneAccessDecision decision = accessService.authorizeRegistry(actorId(principal));
        Map<String, Object> fields = Map.of(
                "operation", "list_agents",
                "capability", AgentEvaluationControlPlaneAccessService.REGISTRY_READ_CAPABILITY,
                "result", decision.status().name(),
                "reason", decision.reason().name(),
                "durationMs", elapsedMillis(startedAt));
        if (!decision.authorized()) {
            StructuredEventLog.warn(log, "AGENT_REGISTRY_ACCESS_DENIED", fields);
            return forbidden();
        }

        AgentRegistryQuery query = new AgentRegistryQuery(agentId, environment, channel, useCase, limit);
        List<AgentRegistryAgentView> agents = queryService.search(query);
        StructuredEventLog.info(log, "AGENT_REGISTRY_ACCESS_GRANTED", fields);
        return ResponseEntity.ok(agents);
    }

    private static String actorId(Principal principal) {
        return principal == null ? null : principal.getName();
    }

    private static ResponseEntity<AgentEvaluationControlPlaneError> forbidden() {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(new AgentEvaluationControlPlaneError("ACCESS_DENIED"));
    }

    private static long elapsedMillis(long startedAt) {
        return (System.nanoTime() - startedAt) / 1_000_000;
    }
}
