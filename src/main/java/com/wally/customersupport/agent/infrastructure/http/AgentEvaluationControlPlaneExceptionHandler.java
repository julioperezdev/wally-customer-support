package com.wally.customersupport.agent.infrastructure.http;

import com.wally.customersupport.agent.application.service.IncompatibleEvaluationDatasetException;
import com.wally.customersupport.shared.infrastructure.observability.StructuredEventLog;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Prevents internal validation and provider errors from leaking through the API. */
@RestControllerAdvice(assignableTypes = AgentEvaluationControlPlaneController.class)
@Slf4j
public class AgentEvaluationControlPlaneExceptionHandler {

    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<AgentEvaluationControlPlaneError> invalidRequest() {
        return ResponseEntity.badRequest()
                .body(new AgentEvaluationControlPlaneError("INVALID_REQUEST"));
    }

    @ExceptionHandler({MethodArgumentTypeMismatchException.class, MissingServletRequestParameterException.class})
    ResponseEntity<AgentEvaluationControlPlaneError> malformedRequest() {
        return ResponseEntity.badRequest()
                .body(new AgentEvaluationControlPlaneError("INVALID_REQUEST"));
    }

    @ExceptionHandler(IncompatibleEvaluationDatasetException.class)
    ResponseEntity<AgentEvaluationControlPlaneError> incompatibleDataset() {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new AgentEvaluationControlPlaneError("INCOMPATIBLE_DATASET"));
    }

    @ExceptionHandler(RuntimeException.class)
    ResponseEntity<AgentEvaluationControlPlaneError> unexpectedFailure() {
        StructuredEventLog.warn(log, "AGENT_EVALUATION_CONTROL_PLANE_FAILED", java.util.Map.of(
                "result", "ERROR"));
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new AgentEvaluationControlPlaneError("INTERNAL_ERROR"));
    }
}
