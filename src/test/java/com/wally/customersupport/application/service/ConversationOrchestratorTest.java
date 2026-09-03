package com.wally.customersupport.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.wally.customersupport.application.port.out.ConversationIntentClassifier;
import com.wally.customersupport.application.port.out.KnowledgeRetriever;
import com.wally.customersupport.application.port.out.LlmClient;
import com.wally.customersupport.domain.model.BusinessHour;
import com.wally.customersupport.domain.model.CatalogQuery;
import com.wally.customersupport.domain.model.ConversationContext;
import com.wally.customersupport.domain.model.ConversationIntent;
import com.wally.customersupport.domain.model.ConversationIntentDecision;
import com.wally.customersupport.domain.model.KnowledgeChunk;
import com.wally.customersupport.domain.model.SupportPolicy;
import com.wally.customersupport.infrastructure.config.RagProperties;
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
                new RagProperties("mock", 5, null, null));
        context = new ConversationContext(
                UUID.randomUUID(), "customer-1", "consulta", List.of("consulta"), List.of());
    }

    @Test
    void routesCatalogIntentWithStructuredFilters() {
        CatalogQuery query = new CatalogQuery("camiseta", null, "M", "negro");
        when(intentClassifier.classify("consulta"))
                .thenReturn(new ConversationIntentDecision(ConversationIntent.CATALOG_SEARCH, 0.95, query, null));
        when(catalogConversationService.replyFor(query)).thenReturn(Optional.of("resultado del catálogo"));

        assertEquals("resultado del catálogo", orchestrator.replyFor(context));

        verify(catalogConversationService).replyFor(query);
        verify(knowledgeRetriever, never()).retrieve(any());
        verify(llmClient, never()).generateReply(any());
    }

    @Test
    void routesBusinessHoursToDatabaseUseCase() {
        when(intentClassifier.classify("consulta"))
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
        when(intentClassifier.classify("consulta"))
                .thenReturn(new ConversationIntentDecision(ConversationIntent.POLICY_QUERY, 0.97, null, "shipping"));
        when(supportConfigurationQueryService.activePolicy("shipping"))
                .thenReturn(Optional.of(new SupportPolicy(
                        UUID.randomUUID(), "shipping", "Envíos", "Contenido autorizado", true, true, 1,
                        Instant.parse("2026-09-01T12:00:00Z"))));

        assertEquals("Envíos: Contenido autorizado", orchestrator.replyFor(context));
    }

    @Test
    void usesKnowledgeAndLlmOnlyForGeneralSupport() {
        when(intentClassifier.classify("consulta"))
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
        when(intentClassifier.classify("consulta"))
                .thenReturn(new ConversationIntentDecision(ConversationIntent.CATALOG_SEARCH, 0.40, null, null));

        assertEquals("No estoy seguro de haber entendido tu consulta. Podés preguntarme por productos, stock, "
                + "horarios, envíos o cambios.", orchestrator.replyFor(context));

        verify(catalogConversationService, never()).replyFor(any(CatalogQuery.class));
        verify(knowledgeRetriever, never()).retrieve(any());
        verify(llmClient, never()).generateReply(any());
    }
}
