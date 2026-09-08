package com.wally.customersupport.conversation.application.service;

import java.util.List;

import com.wally.customersupport.conversation.domain.model.ConversationExecutionAction;
import com.wally.customersupport.conversation.domain.model.ConversationExecutionPlan;
import com.wally.customersupport.conversation.domain.model.ConversationExecutionStep;
import com.wally.customersupport.conversation.domain.model.ConversationIntentDecision;
import com.wally.customersupport.shared.infrastructure.config.ConversationGuardrailProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class ConversationExecutionPlanFactory {

    public static final double MIN_CONFIDENCE = 0.65;
    private static final int MAX_STEPS = 3;
    private final double minConfidence;

    public ConversationExecutionPlanFactory() {
        this.minConfidence = MIN_CONFIDENCE;
    }

    @Autowired
    public ConversationExecutionPlanFactory(ConversationGuardrailProperties properties) {
        this.minConfidence = properties.effectiveMinIntentConfidence();
    }

    public ConversationExecutionPlan create(ConversationIntentDecision decision) {
        if (decision == null) {
            return safeFallback("NULL_DECISION");
        }
        if (decision.confidence() < minConfidence) {
            return lowConfidence("LOW_CONFIDENCE");
        }

        return switch (decision.intent()) {
            case GREETING -> plan(
                    "GREETING",
                    ConversationExecutionAction.DIRECT_RESPONSE,
                    List.of(step("greeting", "response-humanizer", "direct-response")),
                    null);
            case CATALOG_SEARCH -> plan(
                    "CATALOG_SEARCH",
                    ConversationExecutionAction.CATALOG_SEARCH,
                    List.of(step("catalog-search", "catalog-specialist", "catalog-query")),
                    null);
            case BUSINESS_HOURS -> plan(
                    "BUSINESS_HOURS",
                    ConversationExecutionAction.BUSINESS_HOURS,
                    List.of(step("business-hours", "knowledge-specialist", "business-hours")),
                    null);
            case POLICY_QUERY -> plan(
                    "POLICY_QUERY",
                    ConversationExecutionAction.POLICY_QUERY,
                    List.of(step("policy-query", "knowledge-specialist", "policy-query")),
                    null);
            case HUMAN_HANDOFF -> plan(
                    "HUMAN_HANDOFF",
                    ConversationExecutionAction.HUMAN_HANDOFF,
                    List.of(step("human-handoff", "support-safety", "human-handoff")),
                    null);
            case GENERAL_SUPPORT -> generalSupport(null);
            case UNKNOWN -> safeFallback("UNKNOWN_INTENT");
        };
    }

    public ConversationExecutionPlan classificationFailure(String reason) {
        return generalSupport(reason == null ? "CLASSIFICATION_FAILED" : reason);
    }

    public ConversationExecutionPlan lowConfidence(String reason) {
        return plan(
                "LOW_CONFIDENCE",
                ConversationExecutionAction.LOW_CONFIDENCE,
                List.of(step("low-confidence", "support-safety", "safe-fallback")),
                reason);
    }

    public ConversationExecutionPlan safeFallback(String reason) {
        return plan(
                "SAFE_FALLBACK",
                ConversationExecutionAction.SAFE_FALLBACK,
                List.of(step("safe-fallback", "support-safety", "safe-fallback")),
                reason);
    }

    private ConversationExecutionPlan generalSupport(String reason) {
        return plan(
                "GENERAL_SUPPORT",
                ConversationExecutionAction.GENERAL_SUPPORT,
                List.of(
                        step("knowledge-retrieval", "knowledge-specialist", "knowledge-retrieval"),
                        step("response-generation", "response-humanizer", "response-generation")),
                reason);
    }

    private ConversationExecutionPlan plan(
            String useCase,
            ConversationExecutionAction action,
            List<ConversationExecutionStep> steps,
            String fallbackReason) {
        return new ConversationExecutionPlan(
                ConversationExecutionPlan.CURRENT_WORKFLOW_VERSION,
                useCase,
                action,
                steps,
                MAX_STEPS,
                fallbackReason != null,
                fallbackReason);
    }

    private static ConversationExecutionStep step(String stepId, String owner, String capability) {
        return new ConversationExecutionStep(stepId, owner, capability);
    }
}
