package com.wally.customersupport.agent.infrastructure.http;

import java.security.Principal;

import com.wally.customersupport.agent.application.evaluation.AgentEvaluationExecutor;
import com.wally.customersupport.agent.application.evaluation.AgentEvaluationTriggerExecutionRequest;
import com.wally.customersupport.agent.application.evaluation.AgentEvaluationTriggerExecutionResult;
import com.wally.customersupport.agent.application.evaluation.AgentEvaluationTriggerExecutionStatus;
import com.wally.customersupport.agent.application.evaluation.AgentEvaluationTriggerRequest;
import com.wally.customersupport.agent.application.service.AgentEvaluationTriggerAuthorizationService;
import com.wally.customersupport.agent.application.service.AgentEvaluationTriggerExecutionService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Authenticated HTTP facade for starting a bounded evaluation run. */
@RestController
@RequestMapping("/internal/agent-evaluations")
@ConditionalOnProperty(
        name = "wcs.agent-evaluation.trigger.enabled",
        havingValue = "true")
public class AgentEvaluationTriggerController {

    private static final String IDEMPOTENCY_HEADER = "Idempotency-Key";

    private final AgentEvaluationTriggerExecutionService triggerService;
    private final AgentEvaluationExecutor executor;
    private final String environment;

    public AgentEvaluationTriggerController(
            AgentEvaluationTriggerExecutionService triggerService,
            AgentEvaluationExecutor executor,
            @Value("${wcs.agent-evaluation.authorization.allowed-environment:prod}") String environment) {
        this.triggerService = triggerService;
        this.executor = executor;
        this.environment = environment;
    }

    @PostMapping("/runs")
    public ResponseEntity<AgentEvaluationTriggerHttpResponse> trigger(
            Principal principal,
            @RequestHeader(name = IDEMPOTENCY_HEADER, required = false) String idempotencyKey,
            @RequestBody AgentEvaluationTriggerHttpRequest request) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new IllegalArgumentException(IDEMPOTENCY_HEADER + " must not be blank");
        }

        AgentEvaluationTriggerRequest authorizationRequest = new AgentEvaluationTriggerRequest(
                principal == null ? null : principal.getName(),
                environment,
                AgentEvaluationTriggerAuthorizationService.EVALUATION_EXECUTE_CAPABILITY,
                idempotencyKey);
        AgentEvaluationTriggerExecutionResult result = triggerService.execute(
                new AgentEvaluationTriggerExecutionRequest(
                        authorizationRequest,
                        request.toApplicationRequest()),
                executor);
        return response(result);
    }

    private static ResponseEntity<AgentEvaluationTriggerHttpResponse> response(
            AgentEvaluationTriggerExecutionResult result) {
        AgentEvaluationTriggerHttpResponse response = AgentEvaluationTriggerHttpResponse.from(result);
        return switch (result.status()) {
            case COMPLETED -> ResponseEntity.ok(response);
            case ALREADY_PROCESSED -> ResponseEntity.status(HttpStatus.CONFLICT).body(response);
            case DENIED -> ResponseEntity.status(HttpStatus.FORBIDDEN).body(response);
            case FAILED -> ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        };
    }
}
