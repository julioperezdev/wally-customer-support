package com.wally.customersupport.conversation.application.service;

import java.util.List;

import com.wally.customersupport.conversation.domain.model.ConversationExecutionAction;
import com.wally.customersupport.conversation.domain.model.ConversationExecutionPlan;
import com.wally.customersupport.conversation.domain.model.ConversationExecutionStep;
import com.wally.customersupport.conversation.domain.model.ConversationIntentDecision;
import com.wally.customersupport.conversation.application.tool.WcsToolContractCatalog;
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
                    List.of(toolStep(
                            "catalog-search",
                            "catalog-specialist",
                            "catalog-query",
                            WcsToolContractCatalog.CATALOG_SEARCH)),
                    null);
            case PURCHASE_LINK -> plan(
                    "PURCHASE_LINK",
                    ConversationExecutionAction.PURCHASE_LINK,
                    List.of(toolStep(
                            "purchase-link",
                            "checkout-specialist",
                            "payment-link",
                            WcsToolContractCatalog.CHECKOUT_CREATE)),
                    null);
            case BUSINESS_HOURS -> plan(
                    "BUSINESS_HOURS",
                    ConversationExecutionAction.BUSINESS_HOURS,
                    List.of(toolStep(
                            "business-hours",
                            "knowledge-specialist",
                            "business-hours",
                            WcsToolContractCatalog.KNOWLEDGE_RETRIEVE)),
                    null);
            case POLICY_QUERY -> plan(
                    "POLICY_QUERY",
                    ConversationExecutionAction.POLICY_QUERY,
                    List.of(toolStep(
                            "policy-query",
                            "knowledge-specialist",
                            "policy-query",
                            WcsToolContractCatalog.KNOWLEDGE_RETRIEVE)),
                    null);
            case HUMAN_HANDOFF -> plan(
                    "HUMAN_HANDOFF",
                    ConversationExecutionAction.HUMAN_HANDOFF,
                    List.of(toolStep(
                            "human-handoff",
                            "support-safety",
                            "human-handoff",
                            WcsToolContractCatalog.HUMAN_HANDOFF)),
                    null);
            case GENERAL_SUPPORT -> generalSupport(null);
            case UNKNOWN -> safeFallback("UNKNOWN_INTENT");
        };
    }

    public boolean isConfident(double confidence) {
        return Double.isFinite(confidence) && confidence >= minConfidence;
    }

    public ConversationExecutionPlan classificationFailure(String reason) {
        return generalSupport(reason == null ? "CLASSIFICATION_FAILED" : reason);
    }

    public ConversationExecutionPlan cart() {
        return plan(
                "CART",
                ConversationExecutionAction.CART,
                List.of(toolStep(
                        "cart-management",
                        "checkout-specialist",
                        "cart-management",
                        WcsToolContractCatalog.CART_MANAGE)),
                null);
    }

    public ConversationExecutionPlan purchaseDeferred() {
        return plan(
                "PURCHASE_DEFERRED",
                ConversationExecutionAction.PURCHASE_DEFERRED,
                List.of(step("purchase-deferred", "checkout-specialist", "direct-response")),
                null);
    }

    public ConversationExecutionPlan lowConfidence(String reason) {
        return plan(
                "LOW_CONFIDENCE",
                ConversationExecutionAction.LOW_CONFIDENCE,
                List.of(toolStep(
                        "low-confidence",
                        "support-safety",
                        "safe-fallback",
                        WcsToolContractCatalog.SAFE_FALLBACK)),
                reason);
    }

    public ConversationExecutionPlan safeFallback(String reason) {
        return plan(
                "SAFE_FALLBACK",
                ConversationExecutionAction.SAFE_FALLBACK,
                List.of(toolStep(
                        "safe-fallback",
                        "support-safety",
                        "safe-fallback",
                        WcsToolContractCatalog.SAFE_FALLBACK)),
                reason);
    }

    private ConversationExecutionPlan generalSupport(String reason) {
        return plan(
                "GENERAL_SUPPORT",
                ConversationExecutionAction.GENERAL_SUPPORT,
                List.of(
                        toolStep(
                                "knowledge-retrieval",
                                "knowledge-specialist",
                                "knowledge-retrieval",
                                WcsToolContractCatalog.KNOWLEDGE_RETRIEVE),
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

    private static ConversationExecutionStep toolStep(
            String stepId,
            String owner,
            String capability,
            String toolName) {
        var contract = WcsToolContractCatalog.find(toolName)
                .orElseThrow(() -> new IllegalStateException("Unknown WCS tool contract: " + toolName));
        return new ConversationExecutionStep(
                stepId,
                owner,
                capability,
                toolName,
                contract.inputSchemaVersion(),
                contract.outputSchemaVersion());
    }
}
