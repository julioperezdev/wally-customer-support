package com.wally.customersupport.agent.domain.model;

import java.util.List;
import java.util.Objects;

import com.wally.customersupport.conversation.domain.model.Channel;
import com.wally.customersupport.conversation.domain.model.ConversationContext;
import com.wally.customersupport.conversation.domain.model.ResponseHumanizationRequest;
import com.wally.customersupport.conversation.domain.model.ResponseHumanizationResult;

/** Synthetic, versioned input and expectations for one agent evaluation. */
public record AgentEvaluationScenario(
        String scenarioId,
        String datasetVersion,
        String useCase,
        Channel channel,
        ResponseHumanizationRequest request,
        ResponseHumanizationResult.Outcome expectedOutcome,
        List<String> requiredTextFragments,
        List<String> forbiddenTextFragments,
        String expectedIntent,
        List<String> expectedEntityTypes,
        String expectedToolName,
        Boolean expectedGrounded,
        ConversationContext routingContext,
        String expectedAction,
        Integer expectedQuantity) {

    /** Backwards-compatible routing scenario contract without quantity expectations. */
    public AgentEvaluationScenario(
            String scenarioId,
            String datasetVersion,
            String useCase,
            Channel channel,
            ResponseHumanizationRequest request,
            ResponseHumanizationResult.Outcome expectedOutcome,
            List<String> requiredTextFragments,
            List<String> forbiddenTextFragments,
            String expectedIntent,
            List<String> expectedEntityTypes,
            String expectedToolName,
            Boolean expectedGrounded,
            ConversationContext routingContext,
            String expectedAction) {
        this(scenarioId, datasetVersion, useCase, channel, request, expectedOutcome, requiredTextFragments,
                forbiddenTextFragments, expectedIntent, expectedEntityTypes, expectedToolName, expectedGrounded,
                routingContext, expectedAction, null);
    }

    /** Backwards-compatible scenario contract without routing/tool or RAG oracles. */
    public AgentEvaluationScenario(
            String scenarioId,
            String datasetVersion,
            String useCase,
            Channel channel,
            ResponseHumanizationRequest request,
            ResponseHumanizationResult.Outcome expectedOutcome,
            List<String> requiredTextFragments,
            List<String> forbiddenTextFragments) {
        this(scenarioId, datasetVersion, useCase, channel, request, expectedOutcome,
                requiredTextFragments, forbiddenTextFragments, null, null, null, null, null, null);
    }

    /** Backwards-compatible contract for scenarios with quality expectations but no routing oracle. */
    public AgentEvaluationScenario(
            String scenarioId,
            String datasetVersion,
            String useCase,
            Channel channel,
            ResponseHumanizationRequest request,
            ResponseHumanizationResult.Outcome expectedOutcome,
            List<String> requiredTextFragments,
            List<String> forbiddenTextFragments,
            String expectedIntent,
            List<String> expectedEntityTypes,
            String expectedToolName,
            Boolean expectedGrounded) {
        this(scenarioId, datasetVersion, useCase, channel, request, expectedOutcome,
                requiredTextFragments, forbiddenTextFragments, expectedIntent, expectedEntityTypes,
                expectedToolName, expectedGrounded, null, null);
    }

    public AgentEvaluationScenario {
        scenarioId = required(scenarioId, "scenarioId");
        datasetVersion = required(datasetVersion, "datasetVersion");
        useCase = required(useCase, "useCase");
        channel = Objects.requireNonNull(channel, "channel");
        expectedOutcome = Objects.requireNonNull(expectedOutcome, "expectedOutcome");
        requiredTextFragments = normalizeFragments(requiredTextFragments);
        forbiddenTextFragments = normalizeFragments(forbiddenTextFragments);
        expectedIntent = normalizeOptional(expectedIntent);
        expectedEntityTypes = normalizeOptionalFragments(expectedEntityTypes);
        expectedToolName = normalizeOptional(expectedToolName);
        expectedAction = normalizeOptional(expectedAction);
        if (expectedQuantity != null && (expectedQuantity < 1 || expectedQuantity > 100)) {
            throw new IllegalArgumentException("expectedQuantity must be between 1 and 100");
        }
        if (request == null && routingContext == null
                && expectedOutcome != ResponseHumanizationResult.Outcome.FALLBACK) {
            throw new IllegalArgumentException("only fallback scenarios may omit the request");
        }
        if (routingContext != null && (expectedIntent == null || expectedAction == null
                || expectedEntityTypes == null)) {
            throw new IllegalArgumentException(
                    "routing scenarios require expected intent, action, and extracted entities");
        }
        if (routingContext != null && request != null) {
            throw new IllegalArgumentException("a scenario cannot evaluate routing and response humanization together");
        }
    }

    private static List<String> normalizeFragments(List<String> fragments) {
        if (fragments == null) {
            return List.of();
        }
        return fragments.stream()
                .filter(Objects::nonNull)
                .map(String::strip)
                .filter(value -> !value.isBlank())
                .distinct()
                .toList();
    }

    private static List<String> normalizeOptionalFragments(List<String> fragments) {
        return fragments == null ? null : normalizeFragments(fragments);
    }

    private static String normalizeOptional(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.strip();
        return normalized.isBlank() ? null : normalized;
    }

    private static String required(String value, String field) {
        String normalized = Objects.requireNonNull(value, field).strip();
        if (normalized.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return normalized;
    }
}
