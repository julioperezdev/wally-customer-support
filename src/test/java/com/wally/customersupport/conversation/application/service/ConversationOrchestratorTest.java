package com.wally.customersupport.conversation.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.Duration;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import com.wally.customersupport.agent.application.service.AgentActivationKey;
import com.wally.customersupport.agent.application.service.AgentActivationResolution;
import com.wally.customersupport.agent.application.service.AgentActivationResolver;
import com.wally.customersupport.agent.application.service.AgentDefinitionResolutionReason;
import com.wally.customersupport.agent.application.service.AgentRuntimeDefinition;
import com.wally.customersupport.agent.application.service.AgentRuntimeDefinitionResolution;
import com.wally.customersupport.agent.application.service.AgentRuntimeDefinitionResolver;
import com.wally.customersupport.agent.application.service.AgentShadowRuntimeService;
import com.wally.customersupport.agent.application.service.CatalogSpecialistExecutionResult;
import com.wally.customersupport.conversation.application.port.out.ConversationIntentClassifier;
import com.wally.customersupport.conversation.application.port.out.ResponseHumanizer;
import com.wally.customersupport.catalog.application.service.CatalogFact;
import com.wally.customersupport.catalog.application.service.CatalogConversationService;
import com.wally.customersupport.catalog.application.service.CatalogSearchResult;
import com.wally.customersupport.support.application.service.SupportConfigurationQueryService;
import com.wally.customersupport.knowledge.application.port.out.KnowledgeRetriever;
import com.wally.customersupport.conversation.application.port.out.LlmClient;
import com.wally.customersupport.support.domain.model.BusinessHour;
import com.wally.customersupport.catalog.domain.model.CatalogQuery;
import com.wally.customersupport.agent.domain.model.AgentInferenceParameters;
import com.wally.customersupport.conversation.domain.model.ConversationContext;
import com.wally.customersupport.conversation.domain.model.Channel;
import com.wally.customersupport.conversation.domain.model.ConversationIntent;
import com.wally.customersupport.conversation.domain.model.ConversationIntentDecision;
import com.wally.customersupport.conversation.domain.model.ResponseHumanizationResult;
import com.wally.customersupport.knowledge.domain.model.KnowledgeChunk;
import com.wally.customersupport.support.domain.model.SupportPolicy;
import com.wally.customersupport.shared.infrastructure.config.AgentRuntimeProperties;
import com.wally.customersupport.shared.infrastructure.config.RagProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ConversationOrchestratorTest {

    @Mock
    private ConversationIntentClassifier intentClassifier;
    @Mock
    private CatalogConversationService catalogConversationService;
    @Mock
    private SupportConfigurationQueryService supportConfigurationQueryService;
    @Mock
    private KnowledgeRetriever knowledgeRetriever;
    @Mock
    private LlmClient llmClient;
    @Mock
    private AgentActivationResolver agentActivationResolver;
    @Mock
    private AgentRuntimeDefinitionResolver agentRuntimeDefinitionResolver;
    @Mock
    private com.wally.customersupport.agent.application.service.CatalogSpecialistExecutor catalogSpecialistExecutor;
    @Mock
    private ResponseHumanizer responseHumanizer;
    @Mock
    private AgentShadowRuntimeService agentShadowRuntimeService;

    private ConversationOrchestrator orchestrator;
    private ConversationContext context;

    @BeforeEach
    void setUp() {
        orchestrator = new ConversationOrchestrator(
                intentClassifier,
                catalogConversationService,
                supportConfigurationQueryService,
                knowledgeRetriever,
                llmClient,
                new RagProperties("mock", 5, null, null),
                new ConversationExecutionPlanFactory(),
                agentActivationResolver,
                agentRuntimeDefinitionResolver,
                new AgentRuntimeProperties(false, "prod", false, Duration.ofSeconds(5), "noop", "test", 0),
                catalogSpecialistExecutor,
                responseHumanizer,
                agentShadowRuntimeService);
        context = new ConversationContext(
                UUID.randomUUID(), "customer-1", "consulta", List.of("consulta"), List.of());
    }

    @Test
    void keepsCurrentExecutionWhenActivationGateIsDisabled() {
        when(intentClassifier.classify(any(ConversationContext.class)))
                .thenReturn(new ConversationIntentDecision(ConversationIntent.BUSINESS_HOURS, 0.98, null, null));
        when(supportConfigurationQueryService.businessHours()).thenReturn(List.of(
                new BusinessHour(UUID.randomUUID(), 1, LocalTime.of(9, 0), LocalTime.of(18, 0), false,
                        ZoneId.of("America/Argentina/Buenos_Aires"), true, true, 1)));

        assertEquals("Nuestro horario de atención es:\n"
                + "- Lunes: 09:00 a 18:00\n"
                + "Zona horaria: America/Argentina/Buenos_Aires", orchestrator.replyFor(context));

        verify(agentActivationResolver, never()).resolve(any(AgentActivationKey.class));
    }

    @Test
    void resolvesActiveAgentWithoutChangingTheDeterministicResponse() {
        ConversationContext channelContext = new ConversationContext(
                context.conversationId(),
                context.externalCustomerId(),
                context.latestMessage(),
                context.recentMessages(),
                context.knowledge(),
                context.conversationSummary(),
                context.preferences(),
                Channel.TELEGRAM);
        ConversationOrchestrator enabledOrchestrator = new ConversationOrchestrator(
                intentClassifier,
                catalogConversationService,
                supportConfigurationQueryService,
                knowledgeRetriever,
                llmClient,
                new RagProperties("mock", 5, null, null),
                new ConversationExecutionPlanFactory(),
                agentActivationResolver,
                agentRuntimeDefinitionResolver,
                new AgentRuntimeProperties(true, "prod", false, Duration.ofSeconds(5), "noop", "test", 0),
                catalogSpecialistExecutor,
                responseHumanizer,
                agentShadowRuntimeService);
        when(intentClassifier.classify(any(ConversationContext.class)))
                .thenReturn(new ConversationIntentDecision(ConversationIntent.GREETING, 0.98, null, null));
        when(agentActivationResolver.resolve(new AgentActivationKey(
                "response-humanizer", "prod", "telegram", "GREETING")))
                .thenReturn(AgentActivationResolution.active("response-humanizer", 1));

        assertEquals("Hola, ¿cómo te puedo ayudar?", enabledOrchestrator.replyFor(channelContext));
        verify(agentActivationResolver).resolve(new AgentActivationKey(
                "response-humanizer", "prod", "telegram", "GREETING"));
        verify(agentRuntimeDefinitionResolver).resolve(new AgentActivationKey(
                "response-humanizer", "prod", "telegram", "GREETING"));
    }

    @Test
    void keepsCurrentResponseWhenDefinitionResolutionFallsBack() {
        ConversationContext channelContext = new ConversationContext(
                context.conversationId(),
                context.externalCustomerId(),
                context.latestMessage(),
                context.recentMessages(),
                context.knowledge(),
                context.conversationSummary(),
                context.preferences(),
                Channel.TELEGRAM);
        ConversationOrchestrator enabledOrchestrator = new ConversationOrchestrator(
                intentClassifier,
                catalogConversationService,
                supportConfigurationQueryService,
                knowledgeRetriever,
                llmClient,
                new RagProperties("mock", 5, null, null),
                new ConversationExecutionPlanFactory(),
                agentActivationResolver,
                agentRuntimeDefinitionResolver,
                new AgentRuntimeProperties(true, "prod", false, Duration.ofSeconds(5), "noop", "test", 0),
                catalogSpecialistExecutor,
                responseHumanizer,
                agentShadowRuntimeService);
        AgentActivationKey key = new AgentActivationKey(
                "response-humanizer", "prod", "telegram", "GREETING");
        when(intentClassifier.classify(any(ConversationContext.class)))
                .thenReturn(new ConversationIntentDecision(ConversationIntent.GREETING, 0.98, null, null));
        when(agentActivationResolver.resolve(key))
                .thenReturn(AgentActivationResolution.active("response-humanizer", 1));
        when(agentRuntimeDefinitionResolver.resolve(key))
                .thenReturn(AgentRuntimeDefinitionResolution.fallback(
                        AgentDefinitionResolutionReason.VERSION_NOT_FOUND));

        assertEquals("Hola, ¿cómo te puedo ayudar?", enabledOrchestrator.replyFor(channelContext));
        verify(agentRuntimeDefinitionResolver).resolve(key);
    }

    @Test
    void preservesWhatsAppChannelInTheClassifierContext() {
        ConversationContext channelContext = new ConversationContext(
                context.conversationId(),
                context.externalCustomerId(),
                context.latestMessage(),
                context.recentMessages(),
                context.knowledge(),
                context.conversationSummary(),
                context.preferences(),
                Channel.WHATSAPP);
        when(intentClassifier.classify(any(ConversationContext.class)))
                .thenReturn(new ConversationIntentDecision(ConversationIntent.GREETING, 0.98, null, null));

        assertEquals("Hola, ¿cómo te puedo ayudar?", orchestrator.replyFor(channelContext));
        verify(intentClassifier).classify(argThat((ConversationContext candidate) ->
                candidate.channel() == Channel.WHATSAPP));
    }

    @Test
    void routesCatalogIntentWithStructuredFilters() {
        CatalogQuery query = new CatalogQuery("camiseta", null, "M", "negro");
        when(intentClassifier.classify(any(ConversationContext.class)))
                .thenReturn(new ConversationIntentDecision(ConversationIntent.CATALOG_SEARCH, 0.95, query, null));
        when(catalogConversationService.replyFor(query, context.recentMessages(), context.latestMessage()))
                .thenReturn(Optional.of("resultado del catálogo"));

        assertEquals("resultado del catálogo", orchestrator.replyFor(context));

        verify(catalogConversationService).replyFor(query, context.recentMessages(), context.latestMessage());
        verify(knowledgeRetriever, never()).retrieve(any());
        verify(llmClient, never()).generateReply(any());
    }

    @Test
    void routesActiveCatalogDefinitionThroughTheTypedSpecialistBoundary() {
        ConversationContext channelContext = new ConversationContext(
                context.conversationId(),
                context.externalCustomerId(),
                context.latestMessage(),
                context.recentMessages(),
                context.knowledge(),
                context.conversationSummary(),
                context.preferences(),
                Channel.TELEGRAM);
        ConversationOrchestrator enabledOrchestrator = new ConversationOrchestrator(
                intentClassifier,
                catalogConversationService,
                supportConfigurationQueryService,
                knowledgeRetriever,
                llmClient,
                new RagProperties("mock", 5, null, null),
                new ConversationExecutionPlanFactory(),
                agentActivationResolver,
                agentRuntimeDefinitionResolver,
                new AgentRuntimeProperties(true, "prod", false, Duration.ofSeconds(5), "noop", "test", 0),
                catalogSpecialistExecutor,
                responseHumanizer,
                agentShadowRuntimeService);
        CatalogQuery query = new CatalogQuery("nullpointer", null, "M", "negro");
        AgentActivationKey key = new AgentActivationKey(
                "catalog-specialist", "prod", "telegram", "CATALOG_SEARCH");
        when(intentClassifier.classify(any(ConversationContext.class)))
                .thenReturn(new ConversationIntentDecision(ConversationIntent.CATALOG_SEARCH, 0.95, query, null));
        when(agentActivationResolver.resolve(key))
                .thenReturn(AgentActivationResolution.active("catalog-specialist", 2));
        when(agentRuntimeDefinitionResolver.resolve(key))
                .thenReturn(AgentRuntimeDefinitionResolution.active(catalogDefinition()));
        when(catalogSpecialistExecutor.execute(any()))
                .thenReturn(new CatalogSpecialistExecutionResult(
                        CatalogSpecialistExecutionResult.Status.EXECUTED,
                        "EXECUTED",
                        new CatalogSearchResult(
                                CatalogSearchResult.Status.MATCHED,
                                List.of(new CatalogFact(
                                        "Remera NullPointer",
                                        "RP-REM-NP-NEG-M",
                                        "M",
                                        "Negro",
                                        new BigDecimal("18900.00"),
                                        "ARS",
                                        12)),
                                null,
                                CatalogSearchResult.FollowUpKind.NONE,
                                "MATCHED"),
                        1));
        when(responseHumanizer.humanize(any()))
                .thenReturn(ResponseHumanizationResult.applied(
                        "Encontré estos productos:\n"
                                + "- Remera NullPointer — Negro, talle M — 18.900,00 ARS — stock disponible: 12 "
                                + "(SKU: RP-REM-NP-NEG-M)",
                        "deterministic-response-humanizer",
                        "v1"));

        assertEquals("Encontré estos productos:\n"
                + "- Remera NullPointer — Negro, talle M — 18.900,00 ARS — stock disponible: 12 "
                + "(SKU: RP-REM-NP-NEG-M)", enabledOrchestrator.replyFor(channelContext));

        verify(catalogSpecialistExecutor).execute(any());
        verify(catalogConversationService, never()).replyFor(
                any(CatalogQuery.class), any(), any());
    }

    @Test
    void routesBusinessHoursToDatabaseUseCase() {
        when(intentClassifier.classify(any(ConversationContext.class)))
                .thenReturn(new ConversationIntentDecision(ConversationIntent.BUSINESS_HOURS, 0.98, null, null));
        when(supportConfigurationQueryService.businessHours()).thenReturn(List.of(
                new BusinessHour(UUID.randomUUID(), 1, LocalTime.of(9, 0), LocalTime.of(18, 0), false,
                        ZoneId.of("America/Argentina/Buenos_Aires"), true, true, 1),
                new BusinessHour(UUID.randomUUID(), 7, null, null, true,
                        ZoneId.of("America/Argentina/Buenos_Aires"), true, true, 1)));

        String reply = orchestrator.replyFor(context);

        assertEquals("Nuestro horario de atención es:\n"
                + "- Lunes: 09:00 a 18:00\n"
                + "- Domingo: cerrado\n"
                + "Zona horaria: America/Argentina/Buenos_Aires", reply);
    }

    @Test
    void routesPolicyIntentToPublishedPolicy() {
        when(intentClassifier.classify(any(ConversationContext.class)))
                .thenReturn(new ConversationIntentDecision(ConversationIntent.POLICY_QUERY, 0.97, null, "shipping"));
        when(supportConfigurationQueryService.activePolicy("shipping"))
                .thenReturn(Optional.of(new SupportPolicy(
                        UUID.randomUUID(), "shipping", "Envíos", "Contenido autorizado", true, true, 1,
                        Instant.parse("2026-09-01T12:00:00Z"))));

        assertEquals("Envíos: Contenido autorizado", orchestrator.replyFor(context));
    }

    @Test
    void composesCatalogPriceAndShippingForAProductFollowUp() {
        ConversationContext compositeContext = new ConversationContext(
                UUID.randomUUID(),
                "customer-1",
                "¿Cuánto cuesta y cómo se hace el envío?",
                List.of("Busco un buzo negro talle L"),
                List.of(),
                null,
                List.of(),
                Channel.TELEGRAM);
        when(intentClassifier.classify(any(ConversationContext.class)))
                .thenReturn(new ConversationIntentDecision(
                        ConversationIntent.POLICY_QUERY, 0.97, null, "shipping"));
        when(catalogConversationService.replyFor(
                argThat(query -> "buzo".equals(query.productType()) && "negro".equals(query.color())
                        && "l".equalsIgnoreCase(query.size())),
                any(),
                any()))
                .thenReturn(Optional.of("El precio actual es 42.900,00 ARS."));
        when(supportConfigurationQueryService.activePolicy("shipping"))
                .thenReturn(Optional.of(new SupportPolicy(
                        UUID.randomUUID(), "shipping", "Envíos", "Se confirma antes de finalizar la compra.",
                        true, true, 1, Instant.parse("2026-09-01T12:00:00Z"))));

        String reply = orchestrator.replyFor(compositeContext);

        assertTrue(reply.contains("42.900,00 ARS"));
        assertTrue(reply.contains("Se confirma antes de finalizar la compra."));
        verify(catalogConversationService).replyFor(any(CatalogQuery.class), any(), any());
        verify(supportConfigurationQueryService).activePolicy("shipping");
    }

    @Test
    void usesKnowledgeAndLlmOnlyForGeneralSupport() {
        when(intentClassifier.classify(any(ConversationContext.class)))
                .thenReturn(new ConversationIntentDecision(ConversationIntent.GENERAL_SUPPORT, 0.90, null, null));
        when(knowledgeRetriever.retrieve(any())).thenReturn(List.of(new KnowledgeChunk("fuente", 0.9, "policy-1")));
        when(llmClient.generateReply(any())).thenReturn("respuesta respaldada");

        assertEquals("respuesta respaldada", orchestrator.replyFor(context));

        verify(knowledgeRetriever).retrieve(any());
        verify(llmClient).generateReply(any());
        verify(catalogConversationService, never()).replyFor(any(CatalogQuery.class));
    }

    @Test
    void doesNotRouteLowConfidenceIntentToAUseCase() {
        when(intentClassifier.classify(any(ConversationContext.class)))
                .thenReturn(new ConversationIntentDecision(ConversationIntent.CATALOG_SEARCH, 0.40, null, null));

        assertEquals("No estoy seguro de haber entendido tu consulta. Podés preguntarme por productos, stock, "
                + "horarios, envíos o cambios.", orchestrator.replyFor(context));

        verify(catalogConversationService, never()).replyFor(any(CatalogQuery.class));
        verify(knowledgeRetriever, never()).retrieve(any());
        verify(llmClient, never()).generateReply(any());
    }

    @Test
    void fallsBackSafelyWhenTheSelectedUseCaseFails() {
        CatalogQuery query = new CatalogQuery("camiseta", null, "M", "negro");
        when(intentClassifier.classify(any(ConversationContext.class)))
                .thenReturn(new ConversationIntentDecision(ConversationIntent.CATALOG_SEARCH, 0.95, query, null));
        when(catalogConversationService.replyFor(query, context.recentMessages(), context.latestMessage()))
                .thenThrow(new IllegalStateException("catalog unavailable"));

        String reply = orchestrator.replyFor(context);

        assertTrue(reply.startsWith("No pude interpretar la consulta."));
    }

    private static AgentRuntimeDefinition catalogDefinition() {
        return new AgentRuntimeDefinition(
                "catalog-specialist",
                2,
                "Catalog specialist",
                "Search products using deterministic catalog tools",
                "bedrock",
                "openai.gpt-oss-20b-1:0",
                AgentInferenceParameters.deterministic(),
                "system-v1",
                "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
                "catalog-input-v1",
                "catalog-output-v1",
                Set.of("catalog.search"),
                Set.of(),
                "conversation-summary-v1",
                "grounded-customer-support-v1",
                Duration.ofSeconds(10),
                2,
                2_000,
                1_000,
                BigDecimal.valueOf(0.05),
                "safe-fallback",
                "catalog-eval-v1");
    }
}
