package com.wally.customersupport.agent.application.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import com.wally.customersupport.agent.application.port.out.AgentShadowExecutor;
import com.wally.customersupport.agent.application.port.out.AgentTrafficComparisonEventPublisher;
import com.wally.customersupport.agent.application.shadow.AgentShadowExecutionRequest;
import com.wally.customersupport.agent.application.shadow.AgentShadowExecutionResult;
import com.wally.customersupport.agent.application.shadow.AgentTrafficComparisonEvent;
import com.wally.customersupport.agent.application.shadow.AgentTrafficMode;
import com.wally.customersupport.agent.application.shadow.AgentTrafficRoutingDecision;
import com.wally.customersupport.agent.application.shadow.AgentTrafficRoutingPolicy;
import com.wally.customersupport.agent.application.shadow.AgentTrafficRoutingRequest;
import com.wally.customersupport.conversation.domain.model.ConversationContext;
import com.wally.customersupport.shared.infrastructure.config.AgentRuntimeProperties;
import com.wally.customersupport.shared.infrastructure.observability.RequestObservabilityFilter;
import com.wally.customersupport.shared.infrastructure.observability.StructuredEventLog;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;

/**
 * Runs a candidate behind a fail-safe shadow boundary. It never returns a
 * candidate response and never participates in outbound message creation.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AgentShadowRuntimeService {

    private final AgentShadowExecutor executor;
    private final AgentTrafficComparisonEventPublisher eventPublisher;
    private final AgentTrafficRoutingPolicy routingPolicy;
    private final AgentRuntimeProperties runtimeProperties;

    public AgentShadowExecutionResult executeIfEnabled(
            AgentRuntimeDefinitionResolution definitionResolution,
            ConversationContext context,
            String useCase) {
        if (!runtimeProperties.shadowEnabled()) {
            return AgentShadowExecutionResult.disabled("CONFIG_DISABLED");
        }
        if (definitionResolution == null || !definitionResolution.isActive()) {
            return AgentShadowExecutionResult.skipped("ACTIVE_DEFINITION_UNAVAILABLE");
        }
        if (context == null || context.channel() == null || context.conversationId() == null) {
            return AgentShadowExecutionResult.skipped("CONTEXT_NOT_EXECUTABLE");
        }

        AgentRuntimeDefinition definition = definitionResolution.definition();
        String pseudonymizedConversationId = pseudonymize(context.conversationId());
        AgentTrafficRoutingDecision routing = routingPolicy.decide(
                new AgentTrafficRoutingRequest(AgentTrafficMode.SHADOW, 100, pseudonymizedConversationId));
        if (!routing.candidateSelected()) {
            return AgentShadowExecutionResult.skipped("SHADOW_BUCKET_NOT_SELECTED");
        }

        long startedAt = System.nanoTime();
        AgentShadowExecutionResult result = executeWithTimeout(
                new AgentShadowExecutionRequest(definition, context, useCase),
                definition);
        result = enforceLimits(result, definition);
        publishEvidence(definition, context, useCase, pseudonymizedConversationId, routing, result);
        StructuredEventLog.info(log, "AGENT_SHADOW_EXECUTION_COMPLETED", fields(
                definition, context, useCase, result, startedAt));
        return result;
    }

    private AgentShadowExecutionResult executeWithTimeout(
            AgentShadowExecutionRequest request,
            AgentRuntimeDefinition definition) {
        long timeoutMs = Math.max(1, Math.min(
                runtimeProperties.effectiveShadowTimeout().toMillis(),
                definition.timeout().toMillis()));
        CompletableFuture<AgentShadowExecutionResult> future = CompletableFuture.supplyAsync(
                () -> executor.execute(request));
        long startedAt = System.nanoTime();
        try {
            AgentShadowExecutionResult result = future.get(timeoutMs, TimeUnit.MILLISECONDS);
            return result == null
                    ? AgentShadowExecutionResult.failed(elapsedMillis(startedAt), "NULL_EXECUTOR_RESULT")
                    : result;
        } catch (TimeoutException exception) {
            future.cancel(true);
            return AgentShadowExecutionResult.failed(elapsedMillis(startedAt), "SHADOW_TIMEOUT");
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return AgentShadowExecutionResult.failed(elapsedMillis(startedAt), "SHADOW_INTERRUPTED");
        } catch (ExecutionException exception) {
            return AgentShadowExecutionResult.failed(elapsedMillis(startedAt), "SHADOW_EXECUTOR_FAILED");
        }
    }

    private AgentShadowExecutionResult enforceLimits(
            AgentShadowExecutionResult result,
            AgentRuntimeDefinition definition) {
        if (result.totalTokens() != null
                && result.totalTokens() > (long) definition.maxInputTokens() + definition.maxOutputTokens()) {
            return result.withLimitOutcome("SHADOW_TOKEN_LIMIT");
        }
        if (result.estimatedCostUsd() != null
                && result.estimatedCostUsd().compareTo(definition.budgetLimitUsd()) > 0) {
            return result.withLimitOutcome("SHADOW_BUDGET_LIMIT");
        }
        return result;
    }

    private void publishEvidence(
            AgentRuntimeDefinition definition,
            ConversationContext context,
            String useCase,
            String pseudonymizedConversationId,
            AgentTrafficRoutingDecision routing,
            AgentShadowExecutionResult result) {
        try {
            eventPublisher.publish(new AgentTrafficComparisonEvent(
                    MDC.get(RequestObservabilityFilter.REQUEST_ID_MDC_KEY),
                    pseudonymizedConversationId,
                    context.channel().name(),
                    useCase,
                    definition.agentId(),
                    definition.agentVersion(),
                    definition.modelProvider(),
                    definition.modelId(),
                    routing.mode(),
                    result.outcome(),
                    result.latencyMs(),
                    result.inputTokens(),
                    result.outputTokens(),
                    result.totalTokens(),
                    result.estimatedCostUsd(),
                    result.fallbackReason(),
                    routing.candidateResponsePublished()));
        } catch (RuntimeException exception) {
            StructuredEventLog.warn(log, "AGENT_SHADOW_EVIDENCE_FAILED", Map.of(
                    "agentId", definition.agentId(),
                    "agentVersion", definition.agentVersion(),
                    "errorType", exception.getClass().getSimpleName()));
        }
    }

    private static Map<String, Object> fields(
            AgentRuntimeDefinition definition,
            ConversationContext context,
            String useCase,
            AgentShadowExecutionResult result,
            long startedAt) {
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("agentId", definition.agentId());
        fields.put("agentVersion", definition.agentVersion());
        fields.put("modelProvider", definition.modelProvider());
        fields.put("model", definition.modelId());
        fields.put("channel", context.channel().name());
        fields.put("useCase", useCase);
        fields.put("outcome", result.outcome());
        fields.put("fallbackReason", result.fallbackReason());
        fields.put("latencyMs", result.latencyMs());
        fields.put("durationMs", elapsedMillis(startedAt));
        fields.put("candidateResponsePublished", false);
        return fields;
    }

    private static String pseudonymize(UUID conversationId) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(conversationId.toString().getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder(digest.length * 2);
            for (byte value : digest) {
                result.append(String.format("%02x", value));
            }
            return result.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is required", exception);
        }
    }

    private static long elapsedMillis(long startedAt) {
        return (System.nanoTime() - startedAt) / 1_000_000;
    }
}
