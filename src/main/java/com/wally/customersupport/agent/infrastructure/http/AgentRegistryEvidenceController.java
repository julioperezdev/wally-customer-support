package com.wally.customersupport.agent.infrastructure.http;

import java.security.Principal;
import java.util.List;

import com.wally.customersupport.agent.application.evaluation.AgentEvaluationControlPlaneAccessDecision;
import com.wally.customersupport.agent.application.registry.AgentExecutionTraceView;
import com.wally.customersupport.agent.application.registry.AgentRegistryAuditView;
import com.wally.customersupport.agent.application.service.AgentEvaluationControlPlaneAccessService;
import com.wally.customersupport.agent.application.service.AgentRegistryEvidenceQueryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Read-only audit and runtime evidence endpoints for the agent platform. */
@RestController
@RequestMapping("/internal/agent-registry")
@RequiredArgsConstructor
@Slf4j
public class AgentRegistryEvidenceController {

    private final AgentEvaluationControlPlaneAccessService accessService;
    private final AgentRegistryEvidenceQueryService evidenceQueryService;

    @GetMapping("/audit")
    public ResponseEntity<?> audit(
            Principal principal,
            @RequestParam(required = false) String agentId,
            @RequestParam(defaultValue = "50") int limit) {
        AgentEvaluationControlPlaneAccessDecision decision = accessService.authorizeRegistry(actor(principal));
        if (!decision.authorized()) {
            return forbidden();
        }
        return ResponseEntity.ok(evidenceQueryService.audit(agentId, bounded(limit)));
    }

    @GetMapping("/executions")
    public ResponseEntity<?> executions(
            Principal principal,
            @RequestParam(required = false) String agentId,
            @RequestParam(required = false) String useCase,
            @RequestParam(defaultValue = "100") int limit) {
        AgentEvaluationControlPlaneAccessDecision decision = accessService.authorizeRegistry(actor(principal));
        if (!decision.authorized()) {
            return forbidden();
        }
        return ResponseEntity.ok(evidenceQueryService.executions(agentId, useCase, bounded(limit)));
    }

    private static String actor(Principal principal) {
        return principal == null ? null : principal.getName();
    }

    private static int bounded(int limit) {
        return Math.max(1, Math.min(limit, 200));
    }

    private static ResponseEntity<List<?>> forbidden() {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(List.of());
    }
}
