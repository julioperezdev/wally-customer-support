package com.wally.customersupport;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.sql.DataSource;
import com.wally.customersupport.agent.application.evaluation.AgentEvaluationApplicationService;
import com.wally.customersupport.agent.application.evaluation.AgentEvaluationHistoryFilter;
import com.wally.customersupport.agent.application.evaluation.AgentEvaluationHistoryPageRequest;
import com.wally.customersupport.agent.application.evaluation.AgentEvaluationRun;
import com.wally.customersupport.agent.application.evaluation.AgentEvaluationRunRequest;
import com.wally.customersupport.agent.application.evaluation.CatalogResponseEvaluationDataset;
import com.wally.customersupport.agent.application.port.out.AgentEvaluationRunRepository;
import com.wally.customersupport.agent.application.service.AgentEvaluationHistoryQueryService;
import com.wally.customersupport.agent.application.service.AgentEvaluationComparisonApplicationService;
import com.wally.customersupport.agent.application.service.AgentEvaluationEvidenceExportApplicationService;
import com.wally.customersupport.agent.domain.model.AgentEvaluationExecution;
import com.wally.customersupport.agent.domain.model.AgentEvaluationExecutionMetadata;
import com.wally.customersupport.agent.domain.model.AgentEvaluationResult;
import com.wally.customersupport.agent.domain.model.AgentEvaluationScenario;
import com.wally.customersupport.agent.domain.model.AgentEvaluationSuiteResult;
import com.wally.customersupport.catalog.application.service.CatalogConversationService;
import com.wally.customersupport.catalog.application.service.CatalogQueryService;
import com.wally.customersupport.catalog.domain.model.CatalogQuery;
import com.wally.customersupport.conversation.application.port.out.ConversationMemory;
import com.wally.customersupport.conversation.application.port.in.InboundMessagePort;
import com.wally.customersupport.conversation.application.port.out.MessageRepository;
import com.wally.customersupport.conversation.application.port.out.ProcessingAttemptRepository;
import com.wally.customersupport.conversation.application.service.ConversationOrchestrator;
import com.wally.customersupport.conversation.application.service.DeterministicResponseHumanizer;
import com.wally.customersupport.conversation.application.service.ExplicitPreferenceCaptureService;
import com.wally.customersupport.conversation.application.service.CustomerPreferenceService;
import com.wally.customersupport.conversation.application.port.out.ConversationRepository;
import com.wally.customersupport.conversation.domain.model.Channel;
import com.wally.customersupport.conversation.domain.model.Conversation;
import com.wally.customersupport.conversation.domain.model.ConversationMemoryConflictException;
import com.wally.customersupport.conversation.domain.model.ConversationMemoryOwnershipException;
import com.wally.customersupport.conversation.domain.model.ConversationState;
import com.wally.customersupport.conversation.domain.model.ConversationSummary;
import com.wally.customersupport.conversation.domain.model.ConversationStatus;
import com.wally.customersupport.conversation.domain.model.InboundMessageCommand;
import com.wally.customersupport.conversation.domain.model.InboundMessageResult;
import com.wally.customersupport.conversation.domain.model.Message;
import com.wally.customersupport.conversation.domain.model.MessageDirection;
import com.wally.customersupport.conversation.domain.model.MessageType;
import com.wally.customersupport.conversation.domain.model.ProcessingAttempt;
import com.wally.customersupport.conversation.infrastructure.repository.postgres.ConversationMemoryJpaEntity;
import com.wally.customersupport.conversation.infrastructure.repository.postgres.SpringDataConversationMemoryRepository;
import com.wally.customersupport.support.application.service.SupportConfigurationQueryService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
class WallyCustomerSupportApplicationIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16")
            .withDatabaseName("wcs_test")
            .withUsername("wcs")
            .withPassword("test");

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    private DataSource dataSource;

    @Autowired
    private CatalogQueryService catalogQueryService;

    @Autowired
    private CatalogConversationService catalogConversationService;

    @Autowired
    private ConversationOrchestrator conversationOrchestrator;

    @Autowired
    private ConversationRepository conversationRepository;

    @Autowired
    private InboundMessagePort inboundMessagePort;

    @Autowired
    private ProcessingAttemptRepository processingAttemptRepository;

    @Autowired
    private MessageRepository messageRepository;

    @Autowired
    private ConversationMemory conversationMemory;

    @Autowired
    private CustomerPreferenceService customerPreferenceService;

    @Autowired
    private ExplicitPreferenceCaptureService explicitPreferenceCaptureService;

    @Autowired
    private SpringDataConversationMemoryRepository conversationMemoryRepository;

    @Autowired
    private SupportConfigurationQueryService supportConfigurationQueryService;

    @Autowired
    private AgentEvaluationApplicationService agentEvaluationApplicationService;

    @Autowired
    private AgentEvaluationRunRepository agentEvaluationRunRepository;

    @Autowired
    private AgentEvaluationHistoryQueryService agentEvaluationHistoryQueryService;

    @Autowired
    private AgentEvaluationComparisonApplicationService agentEvaluationComparisonApplicationService;

    @Autowired
    private AgentEvaluationEvidenceExportApplicationService agentEvaluationEvidenceExportApplicationService;

    @Test
    void startsWithFlywayAndTestAdapters() {
        assertNotNull(dataSource);
    }

    @Test
    void persistsInboundAsDurablePendingWorkAndPreservesConversationOrder() {
        String conversationKey = "queue-chat-" + UUID.randomUUID();
        Instant now = Instant.now();
        InboundMessageCommand first = new InboundMessageCommand(
                Channel.TELEGRAM, "queue-message-1-" + UUID.randomUUID(), conversationKey,
                "queue-customer", "primer mensaje", now);
        InboundMessageCommand second = new InboundMessageCommand(
                Channel.TELEGRAM, "queue-message-2-" + UUID.randomUUID(), conversationKey,
                "queue-customer", "segundo mensaje", now.plusMillis(1));

        assertEquals(InboundMessageResult.Result.ACCEPTED, inboundMessagePort.accept(first).result());
        assertEquals(InboundMessageResult.Result.ACCEPTED, inboundMessagePort.accept(second).result());

        List<ProcessingAttempt> due = processingAttemptRepository.findDue(
                now.plusSeconds(1), 20, Duration.ofMinutes(5));
        assertEquals(1, due.size());
        assertTrue(messageRepository.findById(due.getFirst().messageId()).isPresent());
        assertTrue(processingAttemptRepository.claim(due.getFirst().id(), now.plusSeconds(1)));

        assertTrue(processingAttemptRepository.findDue(
                now.plusSeconds(1), 20, Duration.ofMinutes(5)).isEmpty());
        processingAttemptRepository.markCompleted(due.getFirst().id(), now.plusSeconds(2));

        assertEquals(1, processingAttemptRepository.findDue(
                now.plusSeconds(3), 20, Duration.ofMinutes(5)).size());
    }

    @Test
    void loadsOnlyInboundMessagesWhenRebuildingCatalogContext() {
        Instant now = Instant.now();
        UUID conversationId = UUID.randomUUID();
        conversationRepository.save(new Conversation(
                conversationId,
                Channel.TELEGRAM,
                "context-chat-" + conversationId,
                "context-customer",
                ConversationStatus.OPEN,
                now,
                now));

        messageRepository.save(new Message(
                UUID.randomUUID(), conversationId, Channel.TELEGRAM, "inbound-old-" + conversationId,
                MessageDirection.INBOUND, MessageType.TEXT, "Mejor un buzo", now, now));
        messageRepository.save(new Message(
                UUID.randomUUID(), conversationId, Channel.TELEGRAM, "outbound-old-" + conversationId,
                MessageDirection.OUTBOUND, MessageType.TEXT, "Encontré estos productos", now.plusSeconds(1), now));
        messageRepository.save(new Message(
                UUID.randomUUID(), conversationId, Channel.TELEGRAM, "inbound-new-" + conversationId,
                MessageDirection.INBOUND, MessageType.TEXT, "¿Qué opciones tienen?", now.plusSeconds(2), now));

        assertEquals(
                List.of("¿Qué opciones tienen?", "Mejor un buzo"),
                messageRepository.findRecentBodies(conversationId, 20));
    }

    @Test
    void loadsDemoCatalogWithDeterministicVariantData() {
        var products = catalogQueryService.search(new CatalogQuery("NullPointer", null, "M", "Negro"));

        assertEquals(1, products.size());
        assertEquals("Remera NullPointer", products.getFirst().name());
        assertEquals("remera", products.getFirst().productType());
        assertEquals("RP-REM-NP-NEG-M", products.getFirst().variants().getFirst().sku());
        assertEquals("ARS", products.getFirst().variants().getFirst().currency());
        assertEquals(12, products.getFirst().variants().getFirst().stock());
        assertTrue(products.getFirst().demo());
    }

    @Test
    void loadsDemoBusinessHoursAndVersionedPolicy() {
        var hours = supportConfigurationQueryService.businessHours();
        var policy = supportConfigurationQueryService.activePolicy("returns");

        assertEquals(7, hours.size());
        assertTrue(hours.getLast().closed());
        assertTrue(policy.isPresent());
        assertEquals(1, policy.get().version());
        assertTrue(policy.get().demo());
    }

    @Test
    void answersAConversationCatalogQueryUsingTheDemoDatabase() {
        String reply = catalogConversationService
                .replyFor("¿Tienen remera negra talle M?")
                .orElseThrow();

        assertTrue(reply.contains("Remera NullPointer"));
        assertTrue(reply.contains("18.900,00 ARS"));
        assertTrue(reply.contains("stock disponible: 12"));
    }

    @Test
    void answersGeneralCatalogListingFromPostgresWithAnApplicationLimit() {
        String reply = catalogConversationService
                .replyFor("¿Qué productos tienen?")
                .orElseThrow();

        assertTrue(reply.startsWith("Encontré estos productos:"));
        assertTrue(reply.contains("Remera NullPointer"));
        assertTrue(reply.contains("Buzo Spring Boot"));
        assertTrue(reply.contains("Campera Deploy Friday"));
    }

    @Test
    void appliesMaximumPriceTogetherWithCatalogFilters() {
        String reply = catalogConversationService
                .replyFor("Busco una remera negra talle M que cueste menos de 20.000 pesos")
                .orElseThrow();

        assertTrue(reply.contains("Remera NullPointer"));
        assertTrue(reply.contains("18.900,00 ARS"));
        assertTrue(!reply.contains("Buzo Spring Boot"));
    }

    @Test
    void resolvesCatalogFollowUpThroughTheCommonConversationOrchestrator() {
        UUID conversationId = UUID.randomUUID();
        String initialMessage = "Busco una remera negra talle M";

        String reply = conversationOrchestrator.replyFor(new com.wally.customersupport.conversation.domain.model.ConversationContext(
                conversationId,
                "synthetic-customer",
                "¿Está disponible?",
                List.of(initialMessage),
                List.of(),
                null,
                List.of(),
                Channel.TELEGRAM));

        assertTrue(reply.contains("está disponible"));
        assertTrue(reply.contains("RP-REM-NP-NEG-M"));
        assertTrue(reply.contains("12 unidades"));
    }

    @Test
    void persistsConversationMemoryWithVersionAndOwnershipInPostgres() {
        Instant now = Instant.now();
        UUID conversationId = UUID.randomUUID();
        conversationRepository.save(new Conversation(
                conversationId,
                Channel.TELEGRAM,
                "memory-chat-" + conversationId,
                "telegram-user",
                ConversationStatus.OPEN,
                now,
                now));

        ConversationState initial = conversationMemory.save(new ConversationState(
                conversationId, "actor-1", List.of("buzo", "negro"), now));

        assertEquals(0L, initial.version());
        assertEquals(initial, conversationMemory.load(conversationId, "actor-1").orElseThrow());
        assertTrue(conversationMemory.load(conversationId, "actor-2").isEmpty());
        assertThrows(ConversationMemoryOwnershipException.class, () -> conversationMemory.save(
                new ConversationState(conversationId, "actor-2", List.of("no debe mezclarse"), now)));

        ConversationState updated = conversationMemory.save(new ConversationState(
                conversationId, "actor-1", List.of("buzo", "negro", "talle M"), now.plusSeconds(1), initial.version()));
        assertEquals(1L, updated.version());

        assertThrows(ConversationMemoryConflictException.class, () -> conversationMemory.save(
                new ConversationState(conversationId, "actor-1", List.of("estado obsoleto"), now, initial.version())));

        conversationMemory.clear(conversationId, "actor-1");
        assertTrue(conversationMemory.load(conversationId, "actor-1").isEmpty());
    }

    @Test
    void removesExpiredConversationMemoryFromPostgresOnLoad() {
        Instant expiredAt = Instant.now().minus(Duration.ofDays(2));
        UUID conversationId = UUID.randomUUID();
        conversationRepository.save(new Conversation(
                conversationId,
                Channel.WHATSAPP,
                "expired-conversation-" + conversationId,
                "customer",
                ConversationStatus.OPEN,
                expiredAt,
                expiredAt));
        conversationMemoryRepository.saveAndFlush(new ConversationMemoryJpaEntity(
                new ConversationState(conversationId, "actor-expired", List.of("contexto"), expiredAt)));

        assertTrue(conversationMemory.load(conversationId, "actor-expired").isEmpty());
        assertTrue(conversationMemoryRepository.findById(conversationId).isEmpty());
    }

    @Test
    void persistsAndLoadsVersionedConversationSummary() {
        Instant now = Instant.now();
        UUID conversationId = UUID.randomUUID();
        conversationRepository.save(new Conversation(
                conversationId,
                Channel.TELEGRAM,
                "summary-chat-" + conversationId,
                "telegram-user",
                ConversationStatus.OPEN,
                now,
                now));
        ConversationSummary summary = new ConversationSummary(
                "El cliente busca un buzo negro y todavía no confirmó talle.",
                1L,
                8,
                now.truncatedTo(ChronoUnit.MICROS));

        ConversationState saved = conversationMemory.save(new ConversationState(
                conversationId,
                "actor-summary",
                List.of("¿Qué talles tienen?"),
                now,
                0L,
                summary));

        ConversationState loaded = conversationMemory.load(conversationId, "actor-summary").orElseThrow();
        assertEquals(summary, loaded.summary());
        assertEquals(saved.version(), loaded.version());
    }

    @Test
    void persistsAndReplacesExplicitCustomerPreferenceInPostgres() {
        String actorId = "preference-actor-" + UUID.randomUUID();
        Instant now = Instant.now();

        assertTrue(customerPreferenceService.recordExplicitColor(actorId, "Negro", now).isPresent());
        assertTrue(customerPreferenceService.recordExplicitColor(actorId, "azul", now.plusSeconds(1)).isPresent());

        var preferences = customerPreferenceService.findForContext(actorId, null);

        assertEquals(1, preferences.size());
        assertEquals("preferred_color", preferences.getFirst().key());
        assertEquals("azul", preferences.getFirst().value());

        customerPreferenceService.clearActor(actorId);
        assertTrue(customerPreferenceService.findForContext(actorId, null).isEmpty());
    }

    @Test
    void capturesExplicitPreferenceInCommonInboundContract() {
        String actorId = "capture-actor-" + UUID.randomUUID();
        Instant now = Instant.now();

        var saved = explicitPreferenceCaptureService.capture(actorId, "Prefiero el negro", now);
        var incidental = explicitPreferenceCaptureService.capture(actorId, "Busco una remera negra", now);

        assertEquals(ExplicitPreferenceCaptureService.Status.SAVED, saved.status());
        assertEquals("negro", saved.color());
        assertEquals(ExplicitPreferenceCaptureService.Status.NOT_DETECTED, incidental.status());
        assertEquals("negro", customerPreferenceService.findForContext(actorId, null).getFirst().value());
    }

    @Test
    void persistsAndLoadsSanitizedEvaluationRunWithScenarioMetadata() {
        AgentEvaluationRun run = agentEvaluationApplicationService.execute(
                new AgentEvaluationRunRequest(
                        CatalogResponseEvaluationDataset.VERSION,
                        "catalog-specialist",
                        "v1",
                        "mock",
                        "deterministic-v1"),
                this::executeEvaluationScenario);

        AgentEvaluationRun loaded = agentEvaluationRunRepository.findById(run.runId()).orElseThrow();

        assertEquals(run.runId(), loaded.runId());
        assertEquals(5, loaded.suiteResult().totalScenarios());
        assertEquals(5, loaded.suiteResult().passedScenarios());
        assertEquals(1.0, loaded.suiteResult().passRate());
        assertEquals("deterministic-v1",
                loaded.suiteResult().scenarioResults().getFirst().executionMetadata().modelId());
        assertTrue(loaded.toString().contains("catalog-response-v1"));
        assertTrue(!loaded.toString().contains("Remera NullPointer"));

        assertThrows(IllegalStateException.class, () -> agentEvaluationRunRepository.save(run));
    }

    @Test
    void queriesEvaluationHistoryWithFiltersStablePaginationAndSanitizedDetail() {
        String datasetVersion = "history-dataset-" + UUID.randomUUID();
        String agentId = "history-agent-" + UUID.randomUUID();
        Instant completedAt = Instant.parse("2026-09-08T00:00:00Z");
        AgentEvaluationRun first = evaluationRun(
                UUID.fromString("00000000-0000-0000-0000-000000000001"),
                datasetVersion,
                agentId,
                completedAt);
        AgentEvaluationRun second = evaluationRun(
                UUID.fromString("00000000-0000-0000-0000-000000000002"),
                datasetVersion,
                agentId,
                completedAt);
        AgentEvaluationRun excluded = evaluationRun(
                UUID.fromString("00000000-0000-0000-0000-000000000003"),
                "other-dataset-" + UUID.randomUUID(),
                agentId,
                completedAt);
        agentEvaluationRunRepository.save(first);
        agentEvaluationRunRepository.save(second);
        agentEvaluationRunRepository.save(excluded);

        AgentEvaluationHistoryFilter filter = new AgentEvaluationHistoryFilter(
                datasetVersion,
                agentId,
                "v1",
                "mock",
                "history-model",
                completedAt.minusSeconds(1),
                completedAt.plusSeconds(1));
        var firstPage = agentEvaluationHistoryQueryService.search(
                filter,
                new AgentEvaluationHistoryPageRequest(0, 1));
        var secondPage = agentEvaluationHistoryQueryService.search(
                filter,
                new AgentEvaluationHistoryPageRequest(1, 1));

        assertEquals(2, firstPage.totalElements());
        assertEquals(2, firstPage.totalPages());
        assertEquals(first.runId(), firstPage.items().getFirst().runId());
        assertTrue(firstPage.hasNext());
        assertEquals(second.runId(), secondPage.items().getFirst().runId());
        assertTrue(secondPage.isLast());

        assertTrue(agentEvaluationHistoryQueryService.findById(first.runId()).isPresent());
        assertTrue(agentEvaluationHistoryQueryService.findById(UUID.randomUUID()).isEmpty());
        assertTrue(firstPage.toString().contains(datasetVersion));
        assertTrue(!firstPage.toString().contains("Remera NullPointer"));
    }

    @Test
    void comparesPersistedEvaluationRunsWithoutReturningResponseContent() {
        String datasetVersion = "comparison-dataset-" + UUID.randomUUID();
        Instant completedAt = Instant.parse("2026-09-08T01:00:00Z");
        UUID baselineId = UUID.randomUUID();
        UUID candidateId = UUID.randomUUID();
        agentEvaluationRunRepository.save(evaluationRun(
                baselineId,
                datasetVersion,
                "comparison-agent",
                completedAt,
                new AgentEvaluationExecutionMetadata(
                        "comparison-agent", "v1", "mock", "model-v1", 10, 20L,
                        null, null, 100, new BigDecimal("0.10"), "test-pricing-v1")));
        agentEvaluationRunRepository.save(evaluationRun(
                candidateId,
                datasetVersion,
                "comparison-agent",
                completedAt,
                new AgentEvaluationExecutionMetadata(
                        "comparison-agent", "v2", "mock", "model-v2", 10, 25L,
                        null, null, 150, new BigDecimal("0.15"), "test-pricing-v1")));

        var comparison = agentEvaluationComparisonApplicationService
                .compare(baselineId, candidateId)
                .orElseThrow();

        assertEquals(datasetVersion, comparison.datasetVersion());
        assertEquals(50, comparison.metricDelta().totalTokensDelta().orElseThrow());
        assertEquals(5, comparison.metricDelta().providerLatencyMsDelta().orElseThrow());
        assertEquals(new BigDecimal("0.05"), comparison.metricDelta().estimatedCostUsdDelta().orElseThrow());
        assertEquals(1, comparison.scenarios().size());
        assertTrue(!comparison.toString().contains("Remera NullPointer"));
    }

    @Test
    void exportsPersistedComparisonWithAStableSanitizedSchema() {
        String datasetVersion = "export-dataset-" + UUID.randomUUID();
        Instant completedAt = Instant.parse("2026-09-08T02:00:00Z");
        UUID baselineId = UUID.randomUUID();
        UUID candidateId = UUID.randomUUID();
        agentEvaluationRunRepository.save(evaluationRun(
                baselineId, datasetVersion, "export-agent", completedAt,
                new AgentEvaluationExecutionMetadata(
                        "export-agent", "v1", "mock", "model-v1", 10, 20L,
                        null, null, 100, new BigDecimal("0.10"), "test-pricing-v1")));
        agentEvaluationRunRepository.save(evaluationRun(
                candidateId, datasetVersion, "export-agent", completedAt, null));

        var export = agentEvaluationEvidenceExportApplicationService
                .export(baselineId, candidateId)
                .orElseThrow();

        assertEquals("wcs.agent-evaluation-evidence.v1", export.schemaVersion());
        assertEquals(baselineId, export.comparison().baselineRunId());
        assertEquals(candidateId, export.comparison().candidateRunId());
        assertTrue(export.comparison().metricDelta().totalTokensDelta().isEmpty());
        assertTrue(!export.toString().contains("Remera NullPointer"));
    }

    private AgentEvaluationRun evaluationRun(
            UUID runId,
            String datasetVersion,
            String agentId,
            Instant completedAt) {
        return evaluationRun(runId, datasetVersion, agentId, completedAt, null);
    }

    private AgentEvaluationRun evaluationRun(
            UUID runId,
            String datasetVersion,
            String agentId,
            Instant completedAt,
            AgentEvaluationExecutionMetadata metadata) {
        AgentEvaluationResult scenario = new AgentEvaluationResult(
                "scenario-1", datasetVersion, true, 1.0, List.of(), metadata);
        return new AgentEvaluationRun(
                runId,
                datasetVersion,
                agentId,
                "v1",
                "mock",
                "history-model",
                completedAt.minusMillis(5),
                completedAt,
                5,
                new AgentEvaluationSuiteResult(
                        datasetVersion,
                        List.of(scenario),
                        1,
                        1,
                        0,
                        1.0,
                        1.0,
                        Map.of()));
    }

    private AgentEvaluationExecution executeEvaluationScenario(AgentEvaluationScenario scenario) {
        DeterministicResponseHumanizer humanizer = new DeterministicResponseHumanizer();
        return new AgentEvaluationExecution(
                humanizer.humanize(scenario.request()),
                new AgentEvaluationExecutionMetadata(
                        "catalog-specialist",
                        "v1",
                        "mock",
                        "deterministic-v1",
                        17,
                        9L,
                        null,
                        null,
                        null,
                        null,
                        "test-pricing-v1"));
    }
}
