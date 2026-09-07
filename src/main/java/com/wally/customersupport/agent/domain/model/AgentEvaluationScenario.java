package com.wally.customersupport.agent.domain.model;

import java.util.List;
import java.util.Objects;

import com.wally.customersupport.conversation.domain.model.Channel;
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
        List<String> forbiddenTextFragments) {

    public AgentEvaluationScenario {
        scenarioId = required(scenarioId, "scenarioId");
        datasetVersion = required(datasetVersion, "datasetVersion");
        useCase = required(useCase, "useCase");
        channel = Objects.requireNonNull(channel, "channel");
        expectedOutcome = Objects.requireNonNull(expectedOutcome, "expectedOutcome");
        requiredTextFragments = normalizeFragments(requiredTextFragments);
        forbiddenTextFragments = normalizeFragments(forbiddenTextFragments);
        if (request == null && expectedOutcome != ResponseHumanizationResult.Outcome.FALLBACK) {
            throw new IllegalArgumentException("only fallback scenarios may omit the request");
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

    private static String required(String value, String field) {
        String normalized = Objects.requireNonNull(value, field).strip();
        if (normalized.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return normalized;
    }
}
