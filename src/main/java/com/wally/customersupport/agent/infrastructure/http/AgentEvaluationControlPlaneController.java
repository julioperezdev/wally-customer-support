package com.wally.customersupport.agent.infrastructure.http;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import com.wally.customersupport.agent.application.evaluation.AgentEvaluationControlPlaneAccessDecision;
import com.wally.customersupport.agent.application.evaluation.AgentEvaluationHistoryFilter;
import com.wally.customersupport.agent.application.evaluation.AgentEvaluationHistoryPage;
import com.wally.customersupport.agent.application.evaluation.AgentEvaluationHistoryPageRequest;
import com.wally.customersupport.agent.application.service.AgentEvaluationComparisonApplicationService;
import com.wally.customersupport.agent.application.service.AgentEvaluationControlPlaneAccessService;
import com.wally.customersupport.agent.application.service.AgentEvaluationEvidenceExportApplicationService;
import com.wally.customersupport.agent.application.service.AgentEvaluationHistoryQueryService;
import com.wally.customersupport.shared.infrastructure.observability.StructuredEventLog;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Read-only HTTP facade for sanitized evaluation history and evidence. */
@RestController
@RequestMapping("/internal/agent-evaluations")
@Slf4j
public class AgentEvaluationControlPlaneController {

    public static final String ACTOR_HEADER = "X-WCS-Actor-Id";

    private final AgentEvaluationControlPlaneAccessService accessService;
    private final AgentEvaluationHistoryQueryService historyQueryService;
    private final AgentEvaluationComparisonApplicationService comparisonService;
    private final AgentEvaluationEvidenceExportApplicationService evidenceExportService;

    public AgentEvaluationControlPlaneController(
            AgentEvaluationControlPlaneAccessService accessService,
            AgentEvaluationHistoryQueryService historyQueryService,
            AgentEvaluationComparisonApplicationService comparisonService,
            AgentEvaluationEvidenceExportApplicationService evidenceExportService) {
        this.accessService = accessService;
        this.historyQueryService = historyQueryService;
        this.comparisonService = comparisonService;
        this.evidenceExportService = evidenceExportService;
    }

    @GetMapping("/runs")
    public ResponseEntity<?> searchRuns(
            @RequestHeader(name = ACTOR_HEADER, required = false) String actorId,
            @RequestParam(required = false) String datasetVersion,
            @RequestParam(required = false) String agentId,
            @RequestParam(required = false) String agentVersion,
            @RequestParam(required = false) String provider,
            @RequestParam(required = false) String modelId,
            @RequestParam(required = false) String completedFrom,
            @RequestParam(required = false) String completedTo,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        if (!isAuthorized(actorId, "list_runs")) {
            return forbidden();
        }
        AgentEvaluationHistoryFilter filter = new AgentEvaluationHistoryFilter(
                datasetVersion,
                agentId,
                agentVersion,
                provider,
                modelId,
                parseInstant(completedFrom),
                parseInstant(completedTo));
        AgentEvaluationHistoryPageRequest pageRequest = new AgentEvaluationHistoryPageRequest(page, size);
        return ResponseEntity.ok(historyQueryService.search(filter, pageRequest));
    }

    @GetMapping("/runs/{runId}")
    public ResponseEntity<?> getRun(
            @RequestHeader(name = ACTOR_HEADER, required = false) String actorId,
            @org.springframework.web.bind.annotation.PathVariable UUID runId) {
        if (!isAuthorized(actorId, "get_run")) {
            return forbidden();
        }
        return historyQueryService.findById(runId)
                .<ResponseEntity<?>>map(ResponseEntity::ok)
                .orElseGet(() -> notFound("RUN_NOT_FOUND"));
    }

    @GetMapping("/comparisons")
    public ResponseEntity<?> compareRuns(
            @RequestHeader(name = ACTOR_HEADER, required = false) String actorId,
            @RequestParam UUID baselineRunId,
            @RequestParam UUID candidateRunId) {
        if (!isAuthorized(actorId, "compare_runs")) {
            return forbidden();
        }
        return comparisonService.compare(baselineRunId, candidateRunId)
                .<ResponseEntity<?>>map(ResponseEntity::ok)
                .orElseGet(() -> notFound("RUN_NOT_FOUND"));
    }

    @GetMapping("/evidence")
    public ResponseEntity<?> exportEvidence(
            @RequestHeader(name = ACTOR_HEADER, required = false) String actorId,
            @RequestParam UUID baselineRunId,
            @RequestParam UUID candidateRunId) {
        if (!isAuthorized(actorId, "export_evidence")) {
            return forbidden();
        }
        return evidenceExportService.export(baselineRunId, candidateRunId)
                .<ResponseEntity<?>>map(ResponseEntity::ok)
                .orElseGet(() -> notFound("RUN_NOT_FOUND"));
    }

    private boolean isAuthorized(String actorId, String operation) {
        long startedAt = System.nanoTime();
        AgentEvaluationControlPlaneAccessDecision decision = accessService.authorize(actorId);
        Map<String, Object> fields = Map.of(
                "operation", operation,
                "capability", AgentEvaluationControlPlaneAccessService.EVALUATION_READ_CAPABILITY,
                "result", decision.status().name(),
                "reason", decision.reason().name(),
                "durationMs", elapsedMillis(startedAt));
        if (decision.authorized()) {
            StructuredEventLog.info(log, "AGENT_EVALUATION_CONTROL_PLANE_ACCESS_GRANTED", fields);
            return true;
        }
        StructuredEventLog.warn(log, "AGENT_EVALUATION_CONTROL_PLANE_ACCESS_DENIED", fields);
        return false;
    }

    private static ResponseEntity<AgentEvaluationControlPlaneError> forbidden() {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(new AgentEvaluationControlPlaneError("ACCESS_DENIED"));
    }

    private static ResponseEntity<AgentEvaluationControlPlaneError> notFound(String code) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new AgentEvaluationControlPlaneError(code));
    }

    private static Instant parseInstant(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return Instant.parse(value.strip());
    }

    private static long elapsedMillis(long startedAt) {
        return (System.nanoTime() - startedAt) / 1_000_000;
    }
}
