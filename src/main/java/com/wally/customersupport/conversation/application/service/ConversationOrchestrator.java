package com.wally.customersupport.conversation.application.service;

import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import com.wally.customersupport.agent.application.service.AgentActivationKey;
import com.wally.customersupport.agent.application.service.AgentActivationResolution;
import com.wally.customersupport.agent.application.service.AgentActivationResolver;
import com.wally.customersupport.agent.application.service.CatalogSpecialistExecutionRequest;
import com.wally.customersupport.agent.application.service.CatalogSpecialistExecutionResult;
import com.wally.customersupport.agent.application.service.CatalogSpecialistExecutor;
import com.wally.customersupport.agent.application.service.AgentRuntimeDefinitionResolution;
import com.wally.customersupport.agent.application.service.AgentRuntimeDefinitionResolver;
import com.wally.customersupport.agent.application.service.AgentShadowRuntimeService;
import com.wally.customersupport.agent.application.service.AgentExecutionTraceRecorder;
import com.wally.customersupport.catalog.application.service.CatalogConversationService;
import com.wally.customersupport.catalog.application.service.CatalogResponseFormatter;
import com.wally.customersupport.catalog.application.service.CatalogQueryParser;
import com.wally.customersupport.catalog.application.service.CatalogSearchResult;
import com.wally.customersupport.catalog.domain.model.CatalogQuery;
import com.wally.customersupport.cart.application.port.in.CartConversationHandler;
import com.wally.customersupport.cart.application.service.CartCommandParser;
import com.wally.customersupport.conversation.application.port.out.ConversationIntentClassifier;
import com.wally.customersupport.conversation.application.port.out.PurchaseLinkCreator;
import com.wally.customersupport.knowledge.application.port.out.KnowledgeRetriever;
import com.wally.customersupport.conversation.application.port.out.LlmClient;
import com.wally.customersupport.conversation.application.port.out.ResponseHumanizer;
import com.wally.customersupport.support.application.service.SupportConfigurationQueryService;
import com.wally.customersupport.support.domain.model.BusinessHour;
import com.wally.customersupport.conversation.domain.model.ConversationContext;
import com.wally.customersupport.conversation.domain.model.ConversationExecutionPlan;
import com.wally.customersupport.conversation.domain.model.ConversationExecutionResult;
import com.wally.customersupport.conversation.domain.model.ConversationIntent;
import com.wally.customersupport.conversation.domain.model.ConversationIntentDecision;
import com.wally.customersupport.conversation.domain.model.ResponseHumanizationRequest;
import com.wally.customersupport.conversation.domain.model.ResponseHumanizationResult;
import com.wally.customersupport.knowledge.domain.model.KnowledgeChunk;
import com.wally.customersupport.knowledge.domain.model.KnowledgeQuery;
import com.wally.customersupport.support.domain.model.SupportPolicy;
import com.wally.customersupport.shared.infrastructure.config.AgentRuntimeProperties;
import com.wally.customersupport.shared.infrastructure.config.RagProperties;
import com.wally.customersupport.shared.infrastructure.observability.ActorKeyGenerator;
import com.wally.customersupport.shared.infrastructure.observability.StructuredEventLog;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class ConversationOrchestrator {

    private static final String GREETING = "Hola, ¿cómo te puedo ayudar?";
    private static final String LOW_CONFIDENCE = "No estoy seguro de haber entendido tu consulta. "
            + "Podés preguntarme por productos, stock, horarios, envíos o cambios.";
    private static final String SAFE_FALLBACK = "No pude interpretar la consulta. "
            + "Podés preguntarme por productos, stock, horarios o políticas de la tienda.";
    private static final String HUMAN_HANDOFF = "Entiendo. Un agente revisará tu consulta con el contexto "
            + "de esta conversación dentro de las próximas 24 horas.";
    private static final String PURCHASE_VARIANT_REQUIRED = "Para generar el link de pago necesito una única "
            + "variante. Indicame el producto, talle y color que querés comprar.";
    private static final String PURCHASE_VARIANT_UNAVAILABLE = "Esa variante no tiene stock disponible en este momento. "
            + "Si querés, puedo mostrarte otras opciones.";
    private static final String PURCHASE_LINK_UNAVAILABLE = "No pude generar el link de pago en este momento. "
            + "Tu pedido no fue confirmado; intentá nuevamente en unos minutos.";
    private static final String PURCHASE_DEFERRED = "Entendido, no hay problema. No genero ningún pedido. "
            + "Cuando quieras comprarla, avisame y te preparo el link de pago.";
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");
    private static final List<String> DAY_NAMES = List.of(
            "lunes", "martes", "miércoles", "jueves", "viernes", "sábado", "domingo");

    private final ConversationIntentClassifier intentClassifier;
    private final CatalogConversationService catalogConversationService;
    private final SupportConfigurationQueryService supportConfigurationQueryService;
    private final KnowledgeRetriever knowledgeRetriever;
    private final LlmClient llmClient;
    private final RagProperties ragProperties;
    private final ConversationExecutionPlanFactory executionPlanFactory;
    private final AgentActivationResolver agentActivationResolver;
    private final AgentRuntimeDefinitionResolver agentRuntimeDefinitionResolver;
    private final AgentRuntimeProperties agentRuntimeProperties;
    private final CatalogSpecialistExecutor catalogSpecialistExecutor;
    private final ResponseHumanizer responseHumanizer;
    private final AgentShadowRuntimeService agentShadowRuntimeService;
    private final ActorKeyGenerator actorKeyGenerator;
    private final AgentExecutionTraceRecorder agentExecutionTraceRecorder;
    private final PurchaseLinkCreator purchaseLinkCreator;
    private final CartConversationHandler cartConversationHandler;
    private final ConversationRoutingService conversationRoutingService;

    @Autowired
    public ConversationOrchestrator(
            ConversationIntentClassifier intentClassifier,
            CatalogConversationService catalogConversationService,
            SupportConfigurationQueryService supportConfigurationQueryService,
            KnowledgeRetriever knowledgeRetriever,
            LlmClient llmClient,
            RagProperties ragProperties,
            ConversationExecutionPlanFactory executionPlanFactory,
            AgentActivationResolver agentActivationResolver,
            AgentRuntimeDefinitionResolver agentRuntimeDefinitionResolver,
            AgentRuntimeProperties agentRuntimeProperties,
            CatalogSpecialistExecutor catalogSpecialistExecutor,
            ResponseHumanizer responseHumanizer,
            AgentShadowRuntimeService agentShadowRuntimeService,
            ActorKeyGenerator actorKeyGenerator,
            PurchaseLinkCreator purchaseLinkCreator,
            CartConversationHandler cartConversationHandler) {
        this(
                intentClassifier,
                catalogConversationService,
                supportConfigurationQueryService,
                knowledgeRetriever,
                llmClient,
                ragProperties,
                executionPlanFactory,
                agentActivationResolver,
                agentRuntimeDefinitionResolver,
                agentRuntimeProperties,
                catalogSpecialistExecutor,
                responseHumanizer,
                agentShadowRuntimeService,
                actorKeyGenerator,
                new AgentExecutionTraceRecorder(),
                purchaseLinkCreator,
                cartConversationHandler);
    }

    public ConversationOrchestrator(
            ConversationIntentClassifier intentClassifier,
            CatalogConversationService catalogConversationService,
            SupportConfigurationQueryService supportConfigurationQueryService,
            KnowledgeRetriever knowledgeRetriever,
            LlmClient llmClient,
            RagProperties ragProperties,
            ConversationExecutionPlanFactory executionPlanFactory,
            AgentActivationResolver agentActivationResolver,
            AgentRuntimeDefinitionResolver agentRuntimeDefinitionResolver,
            AgentRuntimeProperties agentRuntimeProperties,
            CatalogSpecialistExecutor catalogSpecialistExecutor,
            ResponseHumanizer responseHumanizer,
            AgentShadowRuntimeService agentShadowRuntimeService,
            ActorKeyGenerator actorKeyGenerator,
            PurchaseLinkCreator purchaseLinkCreator) {
        this(
                intentClassifier,
                catalogConversationService,
                supportConfigurationQueryService,
                knowledgeRetriever,
                llmClient,
                ragProperties,
                executionPlanFactory,
                agentActivationResolver,
                agentRuntimeDefinitionResolver,
                agentRuntimeProperties,
                catalogSpecialistExecutor,
                responseHumanizer,
                agentShadowRuntimeService,
                actorKeyGenerator,
                new AgentExecutionTraceRecorder(),
                purchaseLinkCreator,
                message -> Optional.empty());
    }

    public ConversationOrchestrator(
            ConversationIntentClassifier intentClassifier,
            CatalogConversationService catalogConversationService,
            SupportConfigurationQueryService supportConfigurationQueryService,
            KnowledgeRetriever knowledgeRetriever,
            LlmClient llmClient,
            RagProperties ragProperties,
            ConversationExecutionPlanFactory executionPlanFactory,
            AgentActivationResolver agentActivationResolver,
            AgentRuntimeDefinitionResolver agentRuntimeDefinitionResolver,
            AgentRuntimeProperties agentRuntimeProperties,
            CatalogSpecialistExecutor catalogSpecialistExecutor,
            ResponseHumanizer responseHumanizer,
            AgentShadowRuntimeService agentShadowRuntimeService,
            ActorKeyGenerator actorKeyGenerator,
            AgentExecutionTraceRecorder agentExecutionTraceRecorder,
            PurchaseLinkCreator purchaseLinkCreator,
            CartConversationHandler cartConversationHandler) {
        this.intentClassifier = intentClassifier;
        this.catalogConversationService = catalogConversationService;
        this.supportConfigurationQueryService = supportConfigurationQueryService;
        this.knowledgeRetriever = knowledgeRetriever;
        this.llmClient = llmClient;
        this.ragProperties = ragProperties;
        this.executionPlanFactory = executionPlanFactory;
        this.agentActivationResolver = agentActivationResolver;
        this.agentRuntimeDefinitionResolver = agentRuntimeDefinitionResolver;
        this.agentRuntimeProperties = agentRuntimeProperties;
        this.catalogSpecialistExecutor = catalogSpecialistExecutor;
        this.responseHumanizer = responseHumanizer;
        this.agentShadowRuntimeService = agentShadowRuntimeService;
        this.actorKeyGenerator = actorKeyGenerator;
        this.agentExecutionTraceRecorder = agentExecutionTraceRecorder;
        this.purchaseLinkCreator = purchaseLinkCreator;
        this.cartConversationHandler = cartConversationHandler;
        this.conversationRoutingService = new ConversationRoutingService(intentClassifier);
    }

    public ConversationOrchestrator(
            ConversationIntentClassifier intentClassifier,
            CatalogConversationService catalogConversationService,
            SupportConfigurationQueryService supportConfigurationQueryService,
            KnowledgeRetriever knowledgeRetriever,
            LlmClient llmClient,
            RagProperties ragProperties,
            ConversationExecutionPlanFactory executionPlanFactory,
            AgentActivationResolver agentActivationResolver,
            AgentRuntimeDefinitionResolver agentRuntimeDefinitionResolver,
            AgentRuntimeProperties agentRuntimeProperties,
            CatalogSpecialistExecutor catalogSpecialistExecutor,
            ResponseHumanizer responseHumanizer,
            AgentShadowRuntimeService agentShadowRuntimeService,
            ActorKeyGenerator actorKeyGenerator,
            AgentExecutionTraceRecorder agentExecutionTraceRecorder,
            PurchaseLinkCreator purchaseLinkCreator) {
        this(
                intentClassifier,
                catalogConversationService,
                supportConfigurationQueryService,
                knowledgeRetriever,
                llmClient,
                ragProperties,
                executionPlanFactory,
                agentActivationResolver,
                agentRuntimeDefinitionResolver,
                agentRuntimeProperties,
                catalogSpecialistExecutor,
                responseHumanizer,
                agentShadowRuntimeService,
                actorKeyGenerator,
                agentExecutionTraceRecorder,
                purchaseLinkCreator,
                message -> Optional.empty());
    }

    public ConversationOrchestrator(
            ConversationIntentClassifier intentClassifier,
            CatalogConversationService catalogConversationService,
            SupportConfigurationQueryService supportConfigurationQueryService,
            KnowledgeRetriever knowledgeRetriever,
            LlmClient llmClient,
            RagProperties ragProperties,
            ConversationExecutionPlanFactory executionPlanFactory,
            AgentActivationResolver agentActivationResolver,
            AgentRuntimeDefinitionResolver agentRuntimeDefinitionResolver,
            AgentRuntimeProperties agentRuntimeProperties,
            CatalogSpecialistExecutor catalogSpecialistExecutor,
            ResponseHumanizer responseHumanizer,
            AgentShadowRuntimeService agentShadowRuntimeService,
            ActorKeyGenerator actorKeyGenerator) {
        this(
                intentClassifier,
                catalogConversationService,
                supportConfigurationQueryService,
                knowledgeRetriever,
                llmClient,
                ragProperties,
                executionPlanFactory,
                agentActivationResolver,
                agentRuntimeDefinitionResolver,
                agentRuntimeProperties,
                catalogSpecialistExecutor,
                responseHumanizer,
                agentShadowRuntimeService,
                actorKeyGenerator,
                new AgentExecutionTraceRecorder(),
                request -> Optional.empty());
    }

    public String replyFor(ConversationContext context) {
        return replyForDetailed(context).response();
    }

    public ConversationExecutionResult replyForDetailed(ConversationContext context) {
        long startedAt = System.nanoTime();
        if (context == null || context.latestMessage() == null || context.latestMessage().isBlank()) {
            return completeQuery(
                    context,
                    executionPlanFactory.safeFallback("INVALID_INPUT"),
                    SAFE_FALLBACK,
                    startedAt);
        }
        Optional<CartConversationHandler.Response> cartResponse = handleCartCommand(context);
        if (cartResponse.isPresent()) {
            return executePlan(
                    context,
                    executionPlanFactory.cart(),
                    null,
                    startedAt,
                    RenderedResponse.text(cartResponse.get().text()));
        }
        if (CatalogQueryParser.isPurchaseDeferral(context.latestMessage())) {
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
                    "durationMs", elapsedMillis(startedAt)));
            return executePlan(
                    context,
                    executionPlanFactory.classificationFailure("CLASSIFICATION_FAILED"),
                    startedAt);
        }
        ConversationIntentDecision rawDecision = routingResult.rawDecision();
        ConversationIntentDecision decision = routingResult.decision();
        Optional<CartConversationHandler.Response> structuredCartResponse = handleStructuredCartCommand(context, decision);
        if (structuredCartResponse.isPresent()) {
            return executePlan(
                    context,
                    executionPlanFactory.cart(),
                    decision,
                    startedAt,
                    RenderedResponse.text(structuredCartResponse.get().text()));
        }
        Map<String, Object> classifiedFields = new LinkedHashMap<>();
        classifiedFields.put("intent", decision.intent().name());
        classifiedFields.put("action", decision.action().name());
        classifiedFields.put("confidence", decision.confidence());
        classifiedFields.put("confidenceBucket", confidenceBucket(decision.confidence()));
        classifiedFields.put("rawIntent", rawDecision.intent().name());
        classifiedFields.put("rawAction", rawDecision.action().name());
        classifiedFields.put("rawConfidence", rawDecision.confidence());
        classifiedFields.put("deterministicNormalization", routingResult.normalized());
        classifiedFields.put("routingStrategy", routingResult.strategy());
        classifiedFields.put("resolvedEntityCount", routingResult.resolvedFields().size());
        classifiedFields.put("resolvedEntityTypes", routingResult.resolvedFields());
        classifiedFields.put("routingMissingParameterCount", routingResult.missingFields().size());
        classifiedFields.put("historyMessageCount", context.recentMessages().size());
        addCatalogQueryFields(classifiedFields, "catalogQuery", decision.catalogQuery());
        classifiedFields.put("missingParameterCount", decision.missingParameters().size());
        classifiedFields.put("durationMs", elapsedMillis(startedAt));
        addConversationIdentity(classifiedFields, context);
        StructuredEventLog.info(log, "INTENT_CLASSIFIED", classifiedFields);
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
            RenderedResponse preparedResponse) {
        AgentActivationKey activationKey = resolveActivationKey(context, plan);
        AgentActivationResolution activation = resolveActivation(activationKey, plan);
        AgentRuntimeDefinitionResolution definition = resolveDefinition(activationKey);
        AgentRuntimeDefinitionResolution validatedDefinition =
                agentRuntimeDefinitionResolver.validateForExecution(definition, plan);
        // Preserve compatibility with pure-test adapters that do not expose
        // the optional validation operation yet.
        if (validatedDefinition != null) {
            definition = validatedDefinition;
        }
        Map<String, Object> routeFields = new LinkedHashMap<>();
        routeFields.put("workflowVersion", plan.workflowVersion());
        routeFields.put("useCase", plan.useCase());
        routeFields.put("action", plan.action().name());
        routeFields.put("stepCount", plan.stepCount());
        routeFields.put("maxSteps", plan.maxSteps());
        routeFields.put("fallbackAllowed", plan.fallbackAllowed());
        addConversationIdentity(routeFields, context);
        addActivationFields(routeFields, activation);
        addDefinitionFields(routeFields, definition);
        if (decision != null) {
            routeFields.put("intent", decision.intent().name());
            routeFields.put("confidence", decision.confidence());
        }
        StructuredEventLog.info(log, "AGENT_ROUTED", routeFields);
        Map<String, Object> startedFields = new LinkedHashMap<>();
        startedFields.put("workflowVersion", plan.workflowVersion());
        startedFields.put("useCase", plan.useCase());
        startedFields.put("stepCount", plan.stepCount());
        addConversationIdentity(startedFields, context);
        if (activation != null) {
            addActivationFields(startedFields, activation);
        }
        addDefinitionFields(startedFields, definition);
        StructuredEventLog.info(log, "AGENT_EXECUTION_STARTED", startedFields);

        ConversationExecutionResult result;
        try {
            RenderedResponse rendered = preparedResponse != null
                    ? preparedResponse
                    : isCatalogShippingComposite(context, decision)
                    ? RenderedResponse.text(executeCatalogShippingComposite(context, decision))
                    : switch (plan.action()) {
                case DIRECT_RESPONSE -> RenderedResponse.text(GREETING);
                case CART -> cartConversationHandler.handle(context)
                        .map(response -> RenderedResponse.text(response.text()))
                        .orElse(RenderedResponse.text(SAFE_FALLBACK));
                case PURCHASE_DEFERRED -> RenderedResponse.text(PURCHASE_DEFERRED);
                case CATALOG_SEARCH -> executeCatalogSearch(context, decision, definition);
                case PURCHASE_LINK -> executePurchaseLink(context, decision);
                case BUSINESS_HOURS -> RenderedResponse.text(formatBusinessHours());
                case POLICY_QUERY -> RenderedResponse.text(formatPolicy(decision == null ? null : decision.policyKey()));
                case HUMAN_HANDOFF -> RenderedResponse.text(HUMAN_HANDOFF);
                case GENERAL_SUPPORT -> RenderedResponse.text(safeGeneralSupport(context, definition));
                case LOW_CONFIDENCE -> RenderedResponse.text(LOW_CONFIDENCE);
                case SAFE_FALLBACK -> RenderedResponse.text(SAFE_FALLBACK);
            };
            String reply = rendered.text();
            result = SAFE_FALLBACK.equals(reply)
                    ? ConversationExecutionResult.fallback(
                            plan,
                            reply,
                            plan.fallbackReason() == null ? "SAFE_GENERAL_SUPPORT_FALLBACK" : plan.fallbackReason())
                    : ConversationExecutionResult.completed(plan, reply, rendered.mediaReference());
        } catch (RuntimeException exception) {
            Map<String, Object> fields = new LinkedHashMap<>();
            fields.put("workflowVersion", plan.workflowVersion());
            fields.put("useCase", plan.useCase());
            fields.put("errorType", exception.getClass().getSimpleName());
            fields.put("durationMs", elapsedMillis(startedAt));
            addConversationIdentity(fields, context);
            StructuredEventLog.warn(log, "AGENT_EXECUTION_FAILED", fields);
            ConversationExecutionPlan fallback = executionPlanFactory.safeFallback("EXECUTION_FAILED");
            result = ConversationExecutionResult.fallback(fallback, SAFE_FALLBACK, "EXECUTION_FAILED");
        }
        runShadowSafely(definition, context, plan.useCase(), decision, result.response());
        return completeQuery(context, result, startedAt, definition);
    }

    private Optional<CartConversationHandler.Response> handleCartCommand(ConversationContext context) {
        if (!cartConversationHandler.recognizes(context.latestMessage())
                && !CatalogQueryParser.isPurchaseDeferral(context.latestMessage())) {
            return Optional.empty();
        }
        try {
            // Keep explicit product, variant and quantity data deterministic
            // even when this legacy fast path handles the command before the
            // structured router is invoked.
            return cartConversationHandler.handle(context, CartCommandParser.parse(context.latestMessage()));
        } catch (RuntimeException exception) {
            StructuredEventLog.warn(log, "CONVERSATIONAL_CART_FAILED", Map.of(
                    "errorType", exception.getClass().getSimpleName()));
            return Optional.of(new CartConversationHandler.Response(SAFE_FALLBACK));
        }
    }

    private Optional<CartConversationHandler.Response> handleStructuredCartCommand(
            ConversationContext context,
            ConversationIntentDecision decision) {
        if (decision == null
                || !decision.action().isCartOperation()
                || !executionPlanFactory.isConfident(decision.confidence())) {
            return Optional.empty();
        }
        if (!decision.missingParameters().isEmpty()) {
            return Optional.of(new CartConversationHandler.Response(
                    "Para continuar necesito estos datos: "
                            + String.join(", ", decision.missingParameters()) + "."));
        }
        CartCommandParser.Action action = switch (decision.action()) {
            case ADD_TO_CART -> CartCommandParser.Action.ADD;
            case VIEW_CART -> CartCommandParser.Action.VIEW;
            case REMOVE_FROM_CART -> CartCommandParser.Action.REMOVE;
            case CLEAR_CART -> CartCommandParser.Action.CLEAR;
            case REVIEW_CHECKOUT -> CartCommandParser.Action.REVIEW_CHECKOUT;
            case CONFIRM_CHECKOUT -> CartCommandParser.Action.CONFIRM;
            case CANCEL_CHECKOUT -> CartCommandParser.Action.CANCEL_CHECKOUT;
            default -> null;
        };
        if (action == null) {
            return Optional.empty();
        }
        CartCommandParser.Command deterministicCommand = CartCommandParser.parse(context.latestMessage());
        CatalogQuery effectiveQuery = decision.catalogQuery();
        int effectiveQuantity = decision.quantity();
        if ((action == CartCommandParser.Action.ADD || action == CartCommandParser.Action.REMOVE)
                && deterministicCommand.action() == action
                && deterministicCommand.query() != null
                && !deterministicCommand.query().isEmpty()
                && deterministicCommand.query().hasPrimarySelector()) {
            // The router decides which allow-listed operation to execute, but
            // explicit product, variant and quantity words must remain
            // deterministic. This protects cart mutations when the model
            // returns the right action with an empty or incomplete query.
            effectiveQuery = deterministicCommand.query();
            effectiveQuantity = deterministicCommand.quantity();
        }
        try {
            return cartConversationHandler.handle(
                    context,
                    new CartCommandParser.Command(action, effectiveQuery, effectiveQuantity));
        } catch (RuntimeException exception) {
            StructuredEventLog.warn(log, "CONVERSATIONAL_CART_FAILED", Map.of(
                    "errorType", exception.getClass().getSimpleName(),
                    "action", decision.action().name()));
            return Optional.of(new CartConversationHandler.Response(SAFE_FALLBACK));
        }
    }

    private boolean isCatalogShippingComposite(
            ConversationContext context,
            ConversationIntentDecision decision) {
        if (context == null
                || CatalogQueryParser.isPurchaseRequest(context.latestMessage())
                || !CatalogQueryParser.isShippingQuestion(context.latestMessage())) {
            return false;
        }
        return resolveCatalogQuery(context, decision)
                .map(query -> !query.isEmpty())
                .orElse(false);
    }

    private String executeCatalogShippingComposite(
            ConversationContext context,
            ConversationIntentDecision decision) {
        CatalogQuery query = resolveCatalogQuery(context, decision).orElseThrow();
        String catalogReply = catalogConversationService
                .replyFor(query, context.recentMessages(), context.latestMessage())
                .orElse(LOW_CONFIDENCE);
        String shippingReply = formatPolicy("shipping");
        StructuredEventLog.info(log, "INTENT_COMPOSED", Map.of(
                "primaryIntent", decision.intent().name(),
                "secondaryIntent", "POLICY_QUERY",
                "components", "CATALOG_SEARCH+SHIPPING",
                "result", "COMPOSED"));
        return catalogReply + "\n\n" + shippingReply;
    }

    private java.util.Optional<CatalogQuery> resolveCatalogQuery(
            ConversationContext context,
            ConversationIntentDecision decision) {
        if (decision != null && decision.catalogQuery() != null && !decision.catalogQuery().isEmpty()) {
            return java.util.Optional.of(decision.catalogQuery());
        }
        return CatalogQueryParser.parseConversation(context.recentMessages(), context.latestMessage())
                .filter(query -> !query.isEmpty())
                .or(() -> CatalogQueryParser.parse(context.latestMessage())
                        .filter(query -> !query.isEmpty()));
    }

    private void runShadowSafely(
            AgentRuntimeDefinitionResolution definition,
            ConversationContext context,
            String useCase,
            ConversationIntentDecision decision,
            String activeResponse) {
        try {
            agentShadowRuntimeService.executeIfEnabled(
                    definition,
                    context,
                    useCase,
                    decision == null ? null : decision.catalogQuery(),
                    activeResponse);
        } catch (RuntimeException exception) {
            // Candidate evidence must never break the active customer response.
            StructuredEventLog.warn(log, "AGENT_SHADOW_EXECUTION_FAILED", Map.of(
                    "useCase", useCase,
                    "errorType", exception.getClass().getSimpleName()));
        }
    }

    private RenderedResponse executeCatalogSearch(
            ConversationContext context,
            ConversationIntentDecision decision,
            AgentRuntimeDefinitionResolution definition) {
        if (definition != null && definition.isActive()) {
            CatalogSpecialistExecutionResult specialistResult = catalogSpecialistExecutor.execute(
                    new CatalogSpecialistExecutionRequest(
                            definition.definition(),
                            decision == null ? null : decision.catalogQuery(),
                            context.recentMessages(),
                            context.latestMessage()));
            if (specialistResult.executed()) {
                logCatalogSearchOutcome(context, decision, specialistResult.result(),
                        "agent-specialist", specialistResult.durationMs());
                return humanizeCatalogResult(context, specialistResult.result());
            }
        }
        long searchStartedAt = System.nanoTime();
        Optional<CatalogSearchResult> result = catalogConversationService.search(
                        decision == null ? null : decision.catalogQuery(),
                        context.recentMessages(),
                        context.latestMessage());
        result.ifPresentOrElse(
                catalogResult -> logCatalogSearchOutcome(
                        context, decision, catalogResult, "deterministic-catalog",
                        elapsedMillis(searchStartedAt)),
                () -> logCatalogSearchOutcome(
                        context, decision, null, "deterministic-catalog",
                        elapsedMillis(searchStartedAt)));
        return result
                .map(catalogResult -> humanizeCatalogResult(context, catalogResult))
                .orElseGet(() -> RenderedResponse.text(LOW_CONFIDENCE));
    }

    private RenderedResponse humanizeCatalogResult(
            ConversationContext context,
            CatalogSearchResult catalogResult) {
        if (context == null || context.channel() == null) {
            return RenderedResponse.catalog(CatalogResponseFormatter.render(catalogResult), catalogResult);
        }
        ResponseHumanizationResult humanized = responseHumanizer.humanize(
                new ResponseHumanizationRequest("CATALOG_SEARCH", context.channel(), catalogResult));
        if (humanized == null || humanized.text() == null || humanized.text().isBlank()) {
            return RenderedResponse.catalog(CatalogResponseFormatter.render(catalogResult), catalogResult);
        }
        return RenderedResponse.catalog(humanized.text(), catalogResult);
    }

    private void logCatalogSearchOutcome(
            ConversationContext context,
            ConversationIntentDecision decision,
            CatalogSearchResult result,
            String source,
            Long executionDurationMs) {
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("source", source);
        fields.put("resultStatus", result == null ? "NO_RESULT" : result.status().name());
        fields.put("resultCount", result == null ? 0 : result.resultCount());
        fields.put("imageCount", result == null ? 0 : result.images().size());
        fields.put("followUpKind", result == null ? null : result.followUpKind().name());
        fields.put("resultReason", result == null ? "NO_RESULT" : result.reason());
        fields.put("executionDurationMs", executionDurationMs);
        fields.put("queryType", queryType(result));
        fields.put("contextualContinuation", context != null
                && CatalogQueryParser.isContextualContinuation(context.latestMessage()));
        fields.put("cheaperContinuation", context != null
                && CatalogQueryParser.isCheaperContinuation(context.latestMessage()));
        fields.put("relativeCheaperContinuation", context != null
                && CatalogQueryParser.isRelativeCheaperContinuation(context.latestMessage()));
        fields.put("explicitPriceFilter", decision != null
                && decision.catalogQuery() != null
                && (decision.catalogQuery().minPrice() != null
                || decision.catalogQuery().maxPrice() != null));
        fields.put("warmthSelection", decision != null
                && CatalogQueryParser.isWarmthSelection(decision.catalogQuery()));
        addCatalogQueryFields(fields, "query", decision == null ? null : decision.catalogQuery());
        addConversationIdentity(fields, context);
        StructuredEventLog.info(log, "CATALOG_SEARCH_COMPLETED", fields);
    }

    private static String queryType(CatalogSearchResult result) {
        if (result == null) {
            return "CATALOG_SEARCH";
        }
        if (result.status() == CatalogSearchResult.Status.UNSUPPORTED_CATEGORY) {
            return "UNSUPPORTED_CATALOG";
        }
        return switch (result.followUpKind()) {
            case AVAILABILITY -> "STOCK_QUERY";
            case PRICE -> "PRICE_QUERY";
            case SIZE -> "SIZE_QUERY";
            case COLOR -> "COLOR_QUERY";
            case NONE -> "CATALOG_SEARCH";
        };
    }

    private RenderedResponse executePurchaseLink(
            ConversationContext context,
            ConversationIntentDecision decision) {
        Optional<CatalogQuery> query = CatalogQueryParser.parsePurchaseConversation(
                context.recentMessages(), context.latestMessage())
                .or(() -> decision == null
                        ? Optional.empty()
                        : Optional.ofNullable(decision.catalogQuery()))
                .filter(candidate -> !candidate.isEmpty());
        if (query.isEmpty()) {
            logPurchaseOutcome(context, "VARIANT_REQUIRED");
            return RenderedResponse.text(PURCHASE_VARIANT_REQUIRED);
        }

        Optional<CatalogSearchResult> searchResult = catalogConversationService.search(
                query.get(), context.recentMessages(), context.latestMessage());
        if (searchResult.isEmpty()
                || searchResult.get().status() != CatalogSearchResult.Status.MATCHED
                || searchResult.get().facts().size() != 1) {
            logPurchaseOutcome(context, "VARIANT_NOT_UNIQUE");
            return RenderedResponse.text(PURCHASE_VARIANT_REQUIRED);
        }

        var fact = searchResult.get().facts().getFirst();
        int quantity = CatalogQueryParser.purchaseQuantity(context.latestMessage());
        if (!fact.available() || quantity > fact.stock()) {
            logPurchaseOutcome(context, "INSUFFICIENT_STOCK");
            return RenderedResponse.text(PURCHASE_VARIANT_UNAVAILABLE);
        }

        String idempotencyKey = "purchase-" + sha256(
                context.conversationId() + "|" + fact.sku() + "|" + quantity);
        Optional<PurchaseLinkCreator.PurchaseLink> purchaseLink = purchaseLinkCreator.create(
                new PurchaseLinkCreator.CreatePurchaseLinkRequest(
                        context.conversationId(),
                        customerReference(context),
                        fact.sku(),
                        quantity,
                        idempotencyKey));
        if (purchaseLink.isEmpty()) {
            logPurchaseOutcome(context, "LINK_UNAVAILABLE");
            return RenderedResponse.text(PURCHASE_LINK_UNAVAILABLE);
        }

        PurchaseLinkCreator.PurchaseLink link = purchaseLink.get();
        logPurchaseOutcome(context, "LINK_CREATED");
        return RenderedResponse.text(String.format(
                Locale.ROOT,
                "Listo. Preparé tu pedido de %d %s (%s), por un total de %s %s.\n"
                        + "Podés completar el pago acá: %s",
                link.quantity(), link.productName(), link.sku(), link.total(), link.currency(), link.checkoutUrl()));
    }

    private void logPurchaseOutcome(ConversationContext context, String result) {
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("operation", "conversation.purchase-link");
        fields.put("result", result);
        addConversationIdentity(fields, context);
        StructuredEventLog.info(log, "CONVERSATIONAL_PURCHASE_LINK", fields);
    }

    private static String customerReference(ConversationContext context) {
        String channel = context.channel() == null ? "unknown" : context.channel().name().toLowerCase(Locale.ROOT);
        return channel + ":" + context.externalCustomerId();
    }

    private static String sha256(String value) {
        try {
            return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }

    private record RenderedResponse(String text, String mediaReference) {

        private static RenderedResponse text(String text) {
            return new RenderedResponse(text, null);
        }

        private static RenderedResponse catalog(String text, CatalogSearchResult result) {
            return new RenderedResponse(text, result == null ? null : result.singleImageReference().orElse(null));
        }
    }

    private AgentActivationKey resolveActivationKey(
            ConversationContext context,
            ConversationExecutionPlan plan) {
        if (!agentRuntimeProperties.activationEnabled()) {
            StructuredEventLog.info(log, "AGENT_ACTIVATION_RESOLUTION_SKIPPED", Map.of(
                    "useCase", plan.useCase(),
                    "reason", "CONFIG_DISABLED"));
            return null;
        }
        if (context == null || context.channel() == null) {
            StructuredEventLog.warn(log, "AGENT_ACTIVATION_FALLBACK", Map.of(
                    "useCase", plan.useCase(),
                    "reason", "CHANNEL_UNAVAILABLE"));
            return null;
        }

        String channel = context.channel().name().toLowerCase(Locale.ROOT);
        return new AgentActivationKey(
                plan.steps().getFirst().owner(),
                agentRuntimeProperties.effectiveEnvironment(),
                channel,
                plan.useCase());
    }

    private AgentActivationResolution resolveActivation(
            AgentActivationKey key,
            ConversationExecutionPlan plan) {
        if (key == null) {
            return null;
        }
        AgentActivationResolution resolution = agentActivationResolver.resolve(key);
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("useCase", plan.useCase());
        fields.put("channel", key.channel());
        addActivationFields(fields, resolution);
        StructuredEventLog.info(log, "AGENT_ACTIVATION_RESOLVED", fields);
        return resolution;
    }

    private AgentRuntimeDefinitionResolution resolveDefinition(AgentActivationKey key) {
        if (key == null) {
            return null;
        }
        return agentRuntimeDefinitionResolver.resolve(key);
    }

    private static void addActivationFields(
            Map<String, Object> fields,
            AgentActivationResolution activation) {
        if (activation == null) {
            return;
        }
        fields.put("activationStatus", activation.status().name());
        fields.put("activationReason", activation.reason().name());
        if (activation.isActive()) {
            fields.put("agentId", activation.agentId());
            fields.put("agentVersion", activation.agentVersion());
        }
    }

    private static void addDefinitionFields(
            Map<String, Object> fields,
            AgentRuntimeDefinitionResolution definition) {
        if (definition == null) {
            return;
        }
        fields.put("definitionStatus", definition.status().name());
        fields.put("definitionReason", definition.reason().name());
        if (definition.isActive()) {
            fields.put("agentId", definition.definition().agentId());
            fields.put("agentVersion", definition.definition().agentVersion());
            fields.put("modelProvider", definition.definition().modelProvider());
            fields.put("model", definition.definition().modelId());
            fields.put("promptVersion", definition.definition().systemPromptVersion());
            fields.put("promptHash", definition.definition().systemPromptHash());
            fields.put("inputSchemaVersion", definition.definition().inputSchemaVersion());
            fields.put("outputSchemaVersion", definition.definition().outputSchemaVersion());
            fields.put("maxInputTokens", definition.definition().maxInputTokens());
            fields.put("maxOutputTokens", definition.definition().maxOutputTokens());
            fields.put("timeoutMs", definition.definition().timeout().toMillis());
        }
    }

    private String safeGeneralSupport(
            ConversationContext context,
            AgentRuntimeDefinitionResolution definitionResolution) {
        try {
            List<KnowledgeChunk> knowledge = knowledgeRetriever.retrieve(new KnowledgeQuery(
                    context.latestMessage(),
                    context.conversationId(),
                    Math.max(1, ragProperties.maxResults())));
            ConversationContext groundedContext = new ConversationContext(
                    context.conversationId(),
                    context.externalCustomerId(),
                    context.latestMessage(),
                    context.recentMessages(),
                    knowledge,
                    context.conversationSummary(),
                    context.preferences(),
                    context.channel(),
                    context.selection());
            if (definitionResolution != null && definitionResolution.isActive()) {
                return llmClient.generateReply(groundedContext, definitionResolution.definition());
            }
            return llmClient.generateReply(groundedContext);
        } catch (RuntimeException exception) {
            Map<String, Object> fields = new LinkedHashMap<>();
            fields.put("errorType", exception.getClass().getSimpleName());
            addCorrelationId(fields, context);
            StructuredEventLog.warn(log, "GENERAL_SUPPORT_FAILED", fields);
            return SAFE_FALLBACK;
        }
    }

    private ConversationExecutionResult completeQuery(
            ConversationContext context,
            ConversationExecutionPlan plan,
            String reply,
            long startedAt) {
        return completeQuery(
                context,
                ConversationExecutionResult.completed(plan, reply),
                startedAt);
    }

    private ConversationExecutionResult completeQuery(
            ConversationContext context,
            ConversationExecutionResult result,
            long startedAt) {
        return completeQuery(context, result, startedAt, null);
    }

    private ConversationExecutionResult completeQuery(
            ConversationContext context,
            ConversationExecutionResult result,
            long startedAt,
            AgentRuntimeDefinitionResolution definitionResolution) {
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("queryType", result.useCase());
        fields.put("outcome", result.outcome());
        fields.put("workflowVersion", result.workflowVersion());
        fields.put("executionStepCount", result.stepCount());
        fields.put("responseGenerated", result.response() != null && !result.response().isBlank());
        fields.put("mediaRequested", result.mediaReference() != null);
        if (result.fallbackReason() != null) {
            fields.put("fallbackReason", result.fallbackReason());
        }
        fields.put("durationMs", elapsedMillis(startedAt));
        addConversationIdentity(fields, context);
        StructuredEventLog.info(log, "AGENT_EXECUTION_COMPLETED", fields);
        StructuredEventLog.info(log, "CONVERSATION_QUERY_COMPLETED", fields);
        agentExecutionTraceRecorder.record(
                context,
                result,
                definitionResolution == null || !definitionResolution.isActive()
                        ? null : definitionResolution.definition(),
                agentRuntimeProperties.effectiveEnvironment(),
                elapsedMillis(startedAt));
        return result;
    }

    private static void addCorrelationId(Map<String, Object> fields, ConversationContext context) {
        if (context != null && context.conversationId() != null) {
            fields.put("correlationId", context.conversationId());
        }
    }

    private void addConversationIdentity(Map<String, Object> fields, ConversationContext context) {
        if (context == null) {
            return;
        }
        if (context.channel() != null) {
            fields.put("channel", context.channel().name());
        }
        addCorrelationId(fields, context);
        actorKeyGenerator.generate(context.channel(), context.externalCustomerId())
                .ifPresent(actorKey -> fields.put("actorKey", actorKey));
    }

    private static long elapsedMillis(long startedAt) {
        return (System.nanoTime() - startedAt) / 1_000_000;
    }

    private static void addCatalogQueryFields(
            Map<String, Object> fields,
            String prefix,
            CatalogQuery query) {
        if (query == null) {
            fields.put(prefix + "Present", false);
            fields.put(prefix + "FilterCount", 0);
            fields.put(prefix + "Filters", List.of());
            return;
        }
        fields.put(prefix + "Present", !query.isEmpty());
        fields.put(prefix + "FilterCount", query.presentFieldCount());
        fields.put(prefix + "Filters", query.presentFieldNames());
        if (query.productType() != null) {
            fields.put(prefix + "ProductType", query.productType());
        }
    }

    private static String confidenceBucket(double confidence) {
        if (confidence < 0.5) {
            return "<0.50";
        }
        if (confidence < 0.65) {
            return "0.50-0.64";
        }
        if (confidence < 0.8) {
            return "0.65-0.79";
        }
        if (confidence < 0.95) {
            return "0.80-0.94";
        }
        return ">=0.95";
    }

    private String formatBusinessHours() {
        List<BusinessHour> hours = supportConfigurationQueryService.businessHours();
        if (hours.isEmpty()) {
            return "No tengo horarios publicados. Un agente puede confirmarlos por vos.";
        }

        StringBuilder response = new StringBuilder("Nuestro horario de atención es:\n");
        for (BusinessHour hour : hours) {
            String day = hour.dayOfWeek() >= 1 && hour.dayOfWeek() <= DAY_NAMES.size()
                    ? DAY_NAMES.get(hour.dayOfWeek() - 1)
                    : "día " + hour.dayOfWeek();
            response.append("- ").append(capitalize(day)).append(": ");
            if (hour.closed()) {
                response.append("cerrado");
            } else {
                response.append(hour.opensAt().format(TIME_FORMATTER))
                        .append(" a ")
                        .append(hour.closesAt().format(TIME_FORMATTER));
            }
            response.append("\n");
        }
        response.append("Zona horaria: ")
                .append(hours.getFirst().timezone().getId());
        return response.toString();
    }

    private String formatPolicy(String policyKey) {
        if (policyKey == null || policyKey.isBlank()) {
            return LOW_CONFIDENCE;
        }
        return supportConfigurationQueryService.activePolicy(policyKey)
                .map(this::formatPolicy)
                .orElse("No tengo una política publicada para esa consulta. "
                        + "Un agente puede confirmarla por vos.");
    }

    private String formatPolicy(SupportPolicy policy) {
        return policy.title() + ": " + policy.content();
    }

    private static String capitalize(String value) {
        return value.substring(0, 1).toUpperCase(Locale.ROOT) + value.substring(1);
    }
}
