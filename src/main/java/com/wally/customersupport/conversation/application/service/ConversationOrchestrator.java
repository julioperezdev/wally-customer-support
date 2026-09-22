package com.wally.customersupport.conversation.application.service;

import java.util.Map;
import java.util.Optional;

import com.wally.customersupport.agent.application.service.AgentExecutionBoundary;
import com.wally.customersupport.agent.application.service.AgentRuntimeDefinitionResolution;
import com.wally.customersupport.cart.application.port.in.CartConversationHandler;
import com.wally.customersupport.conversation.domain.model.ConversationContext;
import com.wally.customersupport.conversation.domain.model.ConversationExecutionPlan;
import com.wally.customersupport.conversation.domain.model.ConversationExecutionResult;
import com.wally.customersupport.conversation.domain.model.ConversationIntentDecision;
import com.wally.customersupport.shared.infrastructure.observability.StructuredEventLog;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class ConversationOrchestrator {

    private static final String GREETING = "Hola, ¿cómo te puedo ayudar?";
    private static final String SAFE_FALLBACK = "No pude interpretar la consulta. "
            + "Podés preguntarme por productos, stock, horarios o políticas de la tienda.";
    private static final String HUMAN_HANDOFF = "Entiendo. Un agente revisará tu consulta con el contexto "
            + "de esta conversación dentro de las próximas 24 horas.";
    private static final String PURCHASE_DEFERRED = "Entendido, no hay problema. No genero ningún pedido. "
            + "Cuando quieras comprarla, avisame y te preparo el link de pago.";
    private final CatalogConversationUseCase catalogConversationUseCase;
    private final CatalogPolicyCompositionUseCase catalogPolicyCompositionUseCase;
    private final ConversationSupportUseCase supportUseCase;
    private final ConversationCartUseCase cartUseCase;
    private final ConversationPurchaseUseCase purchaseUseCase;
    private final ConversationExecutionPlanFactory executionPlanFactory;
    private final AgentExecutionBoundary agentExecutionBoundary;
    private final ConversationExecutionTelemetry telemetry;
    private final ConversationRoutingService conversationRoutingService;

    @Autowired
    public ConversationOrchestrator(
            ConversationRoutingService conversationRoutingService,
            CatalogConversationUseCase catalogConversationUseCase,
            CatalogPolicyCompositionUseCase catalogPolicyCompositionUseCase,
            ConversationSupportUseCase supportUseCase,
            ConversationCartUseCase cartUseCase,
            ConversationPurchaseUseCase purchaseUseCase,
            ConversationExecutionPlanFactory executionPlanFactory,
            AgentExecutionBoundary agentExecutionBoundary,
            ConversationExecutionTelemetry telemetry) {
        this.conversationRoutingService = conversationRoutingService;
        this.catalogConversationUseCase = catalogConversationUseCase;
        this.catalogPolicyCompositionUseCase = catalogPolicyCompositionUseCase;
        this.supportUseCase = supportUseCase;
        this.cartUseCase = cartUseCase;
        this.purchaseUseCase = purchaseUseCase;
        this.executionPlanFactory = executionPlanFactory;
        this.agentExecutionBoundary = agentExecutionBoundary;
        this.telemetry = telemetry;
    }

    public String replyFor(ConversationContext context) {
        return replyForDetailed(context).response();
    }

    public ConversationExecutionResult replyForDetailed(ConversationContext context) {
        long startedAt = System.nanoTime();
        if (context == null || context.latestMessage() == null || context.latestMessage().isBlank()) {
            ConversationExecutionPlan plan = executionPlanFactory.safeFallback("INVALID_INPUT");
            return telemetry.complete(
                    context,
                    ConversationExecutionResult.completed(plan, SAFE_FALLBACK),
                    startedAt,
                    null);
        }
        Optional<CartConversationHandler.Response> cartResponse = cartUseCase.handleBeforeRouting(context);
        if (cartResponse.isPresent()) {
            return executePlan(
                    context,
                    executionPlanFactory.cart(),
                    null,
                    startedAt,
                    ConversationRenderedResponse.text(cartResponse.get().text()));
        }
        if (purchaseUseCase.isDeferral(context.latestMessage())) {
            return executePlan(
                    context,
                    executionPlanFactory.purchaseDeferred(),
                    startedAt);
        }

        ConversationRoutingService.RoutingResult routingResult;
        try {
            routingResult = conversationRoutingService.route(context);
        } catch (RuntimeException exception) {
            StructuredEventLog.warn(log, "INTENT_CLASSIFICATION_FAILED", Map.of(
                    "errorType", exception.getClass().getSimpleName(),
                    "durationMs", telemetry.elapsedMillis(startedAt)));
            return executePlan(
                    context,
                    executionPlanFactory.classificationFailure("CLASSIFICATION_FAILED"),
                    startedAt);
        }
        ConversationIntentDecision decision = routingResult.decision();
        Optional<CartConversationHandler.Response> structuredCartResponse = cartUseCase.handleRouted(context, decision);
        if (structuredCartResponse.isPresent()) {
            return executePlan(
                    context,
                    executionPlanFactory.cart(),
                    decision,
                    startedAt,
                    ConversationRenderedResponse.text(structuredCartResponse.get().text()));
        }
        telemetry.logIntentClassified(context, routingResult, decision, startedAt);
        return executePlan(context, executionPlanFactory.create(decision), decision, startedAt);
    }

    private ConversationExecutionResult executePlan(
            ConversationContext context,
            ConversationExecutionPlan plan,
            long startedAt) {
        return executePlan(context, plan, null, startedAt);
    }

    private ConversationExecutionResult executePlan(
            ConversationContext context,
            ConversationExecutionPlan plan,
            ConversationIntentDecision decision,
            long startedAt) {
        return executePlan(context, plan, decision, startedAt, null);
    }

    private ConversationExecutionResult executePlan(
            ConversationContext context,
            ConversationExecutionPlan plan,
            ConversationIntentDecision decision,
            long startedAt,
            ConversationRenderedResponse preparedResponse) {
        AgentExecutionBoundary.Resolution boundary = agentExecutionBoundary.resolve(context, plan);
        AgentRuntimeDefinitionResolution definition = boundary.definition();
        telemetry.logAgentRouted(context, plan, decision, boundary.activation(), definition);
        telemetry.logAgentExecutionStarted(context, plan, boundary.activation(), definition);

        ConversationExecutionResult result;
        try {
            ConversationRenderedResponse rendered = preparedResponse != null
                    ? preparedResponse
                    : catalogPolicyCompositionUseCase.compose(context, decision, definition)
                            .orElseGet(() -> switch (plan.action()) {
                                case DIRECT_RESPONSE -> ConversationRenderedResponse.text(GREETING);
                                case CART -> cartUseCase.execute(context);
                                case PURCHASE_DEFERRED -> ConversationRenderedResponse.text(PURCHASE_DEFERRED);
                                case CATALOG_SEARCH -> executeCatalogSearch(context, decision, definition);
                                case PURCHASE_LINK -> purchaseUseCase.createLink(context, decision);
                                case BUSINESS_HOURS -> ConversationRenderedResponse.text(supportUseCase.businessHours());
                                case POLICY_QUERY -> ConversationRenderedResponse.text(
                                        supportUseCase.policy(decision == null ? null : decision.policyKey()));
                                case HUMAN_HANDOFF -> ConversationRenderedResponse.text(HUMAN_HANDOFF);
                                case GENERAL_SUPPORT -> ConversationRenderedResponse.text(
                                        supportUseCase.generalSupport(context, definition));
                                case LOW_CONFIDENCE -> catalogConversationUseCase.lowConfidenceResponse();
                                case SAFE_FALLBACK -> ConversationRenderedResponse.text(SAFE_FALLBACK);
                            });
            String reply = rendered.text();
            result = SAFE_FALLBACK.equals(reply)
                    ? ConversationExecutionResult.fallback(
                            plan,
                            reply,
                            plan.fallbackReason() == null ? "SAFE_GENERAL_SUPPORT_FALLBACK" : plan.fallbackReason())
                    : ConversationExecutionResult.completed(
                            plan,
                            reply,
                            rendered.mediaReference(),
                            rendered.workingMemory());
        } catch (RuntimeException exception) {
            telemetry.logAgentExecutionFailed(context, plan, exception, startedAt);
            ConversationExecutionPlan fallback = executionPlanFactory.safeFallback("EXECUTION_FAILED");
            result = ConversationExecutionResult.fallback(fallback, SAFE_FALLBACK, "EXECUTION_FAILED");
        }
        agentExecutionBoundary.executeShadowSafely(
                boundary, context, plan.useCase(), decision, result.response());
        return telemetry.complete(context, result, startedAt, definition);
    }

    private ConversationRenderedResponse executeCatalogSearch(
            ConversationContext context,
            ConversationIntentDecision decision,
            AgentRuntimeDefinitionResolution definition) {
        return catalogConversationUseCase
                .searchResponse(context, decision, definition)
                .orElseGet(catalogConversationUseCase::lowConfidenceResponse);
    }

}
