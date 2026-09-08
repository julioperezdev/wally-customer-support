package com.wally.customersupport.conversation.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;

import com.wally.customersupport.conversation.domain.model.ConversationExecutionAction;
import com.wally.customersupport.conversation.domain.model.ConversationExecutionPlan;
import com.wally.customersupport.conversation.domain.model.ConversationExecutionStep;
import com.wally.customersupport.conversation.domain.model.ConversationIntent;
import com.wally.customersupport.conversation.domain.model.ConversationIntentDecision;
import com.wally.customersupport.shared.infrastructure.config.ConversationGuardrailProperties;
import org.junit.jupiter.api.Test;

class ConversationExecutionPlanFactoryTest {

    private final ConversationExecutionPlanFactory factory = new ConversationExecutionPlanFactory();

    @Test
    void mapsCatalogIntentToAValidatedCatalogPlan() {
        ConversationExecutionPlan plan = factory.create(
                new ConversationIntentDecision(ConversationIntent.CATALOG_SEARCH, 0.95, null, null));

        assertEquals(ConversationExecutionAction.CATALOG_SEARCH, plan.action());
        assertEquals("CATALOG_SEARCH", plan.useCase());
        assertEquals(1, plan.stepCount());
        assertEquals("catalog-specialist", plan.steps().getFirst().owner());
    }

    @Test
    void mapsLowConfidenceToTheSafetyFallback() {
        ConversationExecutionPlan plan = factory.create(
                new ConversationIntentDecision(ConversationIntent.CATALOG_SEARCH, 0.40, null, null));

        assertEquals(ConversationExecutionAction.LOW_CONFIDENCE, plan.action());
        assertEquals("LOW_CONFIDENCE", plan.fallbackReason());
    }

    @Test
    void usesConfiguredConfidenceGuardrail() {
        ConversationExecutionPlanFactory configuredFactory = new ConversationExecutionPlanFactory(
                new ConversationGuardrailProperties(0.90));

        ConversationExecutionPlan plan = configuredFactory.create(
                new ConversationIntentDecision(ConversationIntent.CATALOG_SEARCH, 0.80, null, null));

        assertEquals(ConversationExecutionAction.LOW_CONFIDENCE, plan.action());
        assertEquals("LOW_CONFIDENCE", plan.fallbackReason());
    }

    @Test
    void mapsGeneralSupportToBoundedKnowledgeAndResponseSteps() {
        ConversationExecutionPlan plan = factory.create(
                new ConversationIntentDecision(ConversationIntent.GENERAL_SUPPORT, 0.90, null, null));

        assertEquals(ConversationExecutionAction.GENERAL_SUPPORT, plan.action());
        assertEquals(2, plan.stepCount());
        assertEquals(3, plan.maxSteps());
    }

    @Test
    void rejectsPlansThatExceedTheExecutionLimit() {
        List<ConversationExecutionStep> steps = List.of(
                new ConversationExecutionStep("one", "catalog-specialist", "catalog-query"),
                new ConversationExecutionStep("two", "catalog-specialist", "catalog-query"),
                new ConversationExecutionStep("three", "catalog-specialist", "catalog-query"),
                new ConversationExecutionStep("four", "catalog-specialist", "catalog-query"));

        assertThrows(IllegalArgumentException.class, () -> new ConversationExecutionPlan(
                ConversationExecutionPlan.CURRENT_WORKFLOW_VERSION,
                "INVALID",
                ConversationExecutionAction.SAFE_FALLBACK,
                steps,
                4,
                true,
                "INVALID_PLAN"));
    }

    @Test
    void rejectsUnsupportedCapabilities() {
        assertThrows(IllegalArgumentException.class, () -> new ConversationExecutionPlan(
                ConversationExecutionPlan.CURRENT_WORKFLOW_VERSION,
                "INVALID",
                ConversationExecutionAction.SAFE_FALLBACK,
                List.of(new ConversationExecutionStep("unknown", "support-safety", "sql")),
                1,
                true,
                "INVALID_PLAN"));
    }
}
