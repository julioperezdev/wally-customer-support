package com.wally.customersupport.conversation.application.service;

import java.util.LinkedHashMap;
import java.util.Map;

import com.wally.customersupport.catalog.application.service.CatalogResponseFormatter;
import com.wally.customersupport.conversation.application.port.out.ResponseHumanizer;
import com.wally.customersupport.conversation.domain.model.ResponseHumanizationRequest;
import com.wally.customersupport.conversation.domain.model.ResponseHumanizationResult;
import com.wally.customersupport.shared.infrastructure.observability.StructuredEventLog;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Safe baseline response policy. It renders only facts already validated by a
 * use-case and is intentionally replaceable by a future Bedrock adapter.
 */
@Component
@Slf4j
public class DeterministicResponseHumanizer implements ResponseHumanizer {

    public static final String POLICY_ID = "deterministic-response-humanizer";
    public static final String POLICY_VERSION = "v1";
    private static final String SAFE_FALLBACK = "No pude preparar una respuesta segura para esa consulta. "
            + "Podés volver a intentarlo o pedir ayuda a un agente.";

    @Override
    public ResponseHumanizationResult humanize(ResponseHumanizationRequest request) {
        long startedAt = System.nanoTime();
        if (request == null) {
            return fallback("INVALID_INPUT", null, startedAt);
        }

        try {
            String text = CatalogResponseFormatter.render(request.catalogResult());
            ResponseHumanizationResult result = ResponseHumanizationResult.applied(
                    text,
                    POLICY_ID,
                    POLICY_VERSION);
            logEvent(request, result, startedAt);
            return result;
        } catch (RuntimeException exception) {
            Map<String, Object> fields = baseFields(request, startedAt);
            fields.put("outcome", ResponseHumanizationResult.Outcome.FALLBACK.name());
            fields.put("fallbackReason", "RENDERING_FAILED");
            fields.put("errorType", exception.getClass().getSimpleName());
            StructuredEventLog.warn(log, "RESPONSE_POLICY_FALLBACK", fields);
            return ResponseHumanizationResult.fallback(
                    SAFE_FALLBACK,
                    POLICY_ID,
                    POLICY_VERSION,
                    "RENDERING_FAILED");
        }
    }

    private ResponseHumanizationResult fallback(String reason, ResponseHumanizationRequest request, long startedAt) {
        ResponseHumanizationResult result = ResponseHumanizationResult.fallback(
                SAFE_FALLBACK,
                POLICY_ID,
                POLICY_VERSION,
                reason);
        if (request == null) {
            Map<String, Object> fields = new LinkedHashMap<>();
            fields.put("policyId", POLICY_ID);
            fields.put("policyVersion", POLICY_VERSION);
            fields.put("outcome", result.outcome().name());
            fields.put("fallbackReason", reason);
            fields.put("durationMs", elapsedMillis(startedAt));
            StructuredEventLog.warn(log, "RESPONSE_POLICY_FALLBACK", fields);
        } else {
            logEvent(request, result, startedAt);
        }
        return result;
    }

    private void logEvent(
            ResponseHumanizationRequest request,
            ResponseHumanizationResult result,
            long startedAt) {
        Map<String, Object> fields = baseFields(request, startedAt);
        fields.put("outcome", result.outcome().name());
        if (result.fallbackReason() != null) {
            fields.put("fallbackReason", result.fallbackReason());
        }
        StructuredEventLog.info(log, "RESPONSE_POLICY_APPLIED", fields);
    }

    private static Map<String, Object> baseFields(
            ResponseHumanizationRequest request,
            long startedAt) {
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("useCase", request.useCase());
        fields.put("channel", request.channel().name());
        fields.put("policyId", POLICY_ID);
        fields.put("policyVersion", POLICY_VERSION);
        fields.put("resultStatus", request.catalogResult().status().name());
        fields.put("resultCount", request.catalogResult().resultCount());
        fields.put("durationMs", elapsedMillis(startedAt));
        return fields;
    }

    private static long elapsedMillis(long startedAt) {
        return (System.nanoTime() - startedAt) / 1_000_000;
    }
}
