package com.wally.customersupport.conversation.application.service;

import java.util.Optional;

import com.wally.customersupport.agent.application.service.AgentActivationResolver;
import com.wally.customersupport.agent.application.service.AgentExecutionBoundary;
import com.wally.customersupport.agent.application.service.AgentExecutionTraceRecorder;
import com.wally.customersupport.agent.application.service.AgentRuntimeDefinitionResolver;
import com.wally.customersupport.agent.application.service.AgentShadowRuntimeService;
import com.wally.customersupport.agent.application.service.CatalogSpecialistExecutor;
import com.wally.customersupport.catalog.application.service.CatalogConversationService;
import com.wally.customersupport.cart.application.port.in.CartConversationHandler;
import com.wally.customersupport.conversation.application.port.out.ConversationIntentClassifier;
import com.wally.customersupport.conversation.application.port.out.LlmClient;
import com.wally.customersupport.conversation.application.port.out.PurchaseLinkCreator;
import com.wally.customersupport.conversation.application.port.out.ResponseHumanizer;
import com.wally.customersupport.knowledge.application.port.out.KnowledgeRetriever;
import com.wally.customersupport.shared.infrastructure.config.AgentRuntimeProperties;
import com.wally.customersupport.shared.infrastructure.config.RagProperties;
import com.wally.customersupport.shared.infrastructure.observability.ActorKeyGenerator;
import com.wally.customersupport.support.application.service.SupportConfigurationQueryService;

/**
 * Keeps test wiring concise after the production orchestrator was reduced to
 * one explicit constructor. Compatibility overloads must not leak back into
 * the runtime class just to make unit tests shorter.
 */
final class ConversationOrchestratorTestSupport {

    private ConversationOrchestratorTestSupport() {
    }

    static ConversationOrchestrator create(
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
        return create(
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

    static ConversationOrchestrator create(
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
        return create(
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

    static ConversationOrchestrator create(
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
        ConversationExecutionTelemetry telemetry = new ConversationExecutionTelemetry(
                actorKeyGenerator,
                agentExecutionTraceRecorder,
                agentRuntimeProperties);
        CatalogConversationUseCase catalogUseCase = new CatalogConversationUseCase(
                catalogConversationService,
                catalogSpecialistExecutor,
                responseHumanizer,
                telemetry);
        ConversationSupportUseCase supportUseCase = new ConversationSupportUseCase(
                supportConfigurationQueryService,
                knowledgeRetriever,
                llmClient,
                ragProperties,
                telemetry);
        ConversationCartUseCase cartUseCase = new ConversationCartUseCase(
                cartConversationHandler,
                executionPlanFactory);
        ConversationPurchaseUseCase purchaseUseCase = new ConversationPurchaseUseCase(
                catalogUseCase,
                purchaseLinkCreator,
                telemetry);
        AgentExecutionBoundary agentExecutionBoundary = new AgentExecutionBoundary(
                agentActivationResolver,
                agentRuntimeDefinitionResolver,
                agentRuntimeProperties,
                agentShadowRuntimeService);
        return new ConversationOrchestrator(
                new ConversationRoutingService(intentClassifier),
                catalogUseCase,
                supportUseCase,
                cartUseCase,
                purchaseUseCase,
                executionPlanFactory,
                agentExecutionBoundary,
                telemetry);
    }

    static ConversationOrchestrator create(
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
        return create(
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

    static ConversationOrchestrator create(
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
        return create(
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
                request -> Optional.empty(),
                message -> Optional.empty());
    }
}
