package com.wally.customersupport.conversation.application.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.lenient;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.wally.customersupport.conversation.application.port.out.ConversationMemory;
import com.wally.customersupport.conversation.application.port.out.ConversationRepository;
import com.wally.customersupport.conversation.application.port.out.MessageRepository;
import com.wally.customersupport.conversation.application.port.out.OutboxRepository;
import com.wally.customersupport.conversation.application.port.out.ProcessingAttemptRepository;
import com.wally.customersupport.conversation.domain.model.Channel;
import com.wally.customersupport.conversation.domain.model.Conversation;
import com.wally.customersupport.conversation.domain.model.ConversationExecutionResult;
import com.wally.customersupport.conversation.domain.model.ConversationStatus;
import com.wally.customersupport.conversation.domain.model.Message;
import com.wally.customersupport.conversation.domain.model.MessageDirection;
import com.wally.customersupport.conversation.domain.model.MessageType;
import com.wally.customersupport.conversation.domain.model.ProcessingAttempt;
import com.wally.customersupport.conversation.domain.model.ProcessingAttemptStatus;
import com.wally.customersupport.shared.infrastructure.config.InboundProcessingProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class InboundMessageProcessingServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-30T12:00:00Z");

    @Mock
    private ConversationRepository conversationRepository;
    @Mock
    private ConversationMemory conversationMemory;
    @Mock
    private MessageRepository messageRepository;
    @Mock
    private ProcessingAttemptRepository processingAttemptRepository;
    @Mock
    private OutboxRepository outboxRepository;
    @Mock
    private ConversationOrchestrator conversationOrchestrator;
    @Mock
    private ConversationSummaryService conversationSummaryService;
    @Mock
    private CustomerPreferenceService customerPreferenceService;
    @Mock
    private ExplicitPreferenceCaptureService explicitPreferenceCaptureService;
    @Mock
    private OptOutDetector optOutDetector;
    @Mock
    private OptInDetector optInDetector;
    @Mock
    private ContactSuppressionService contactSuppressionService;
    @Mock
    private HumanFollowUpTaskService humanFollowUpTaskService;

    private InboundMessageProcessingService service;
    private Conversation conversation;
    private Message message;
    private ProcessingAttempt attempt;

    @BeforeEach
    void setUp() {
        service = new InboundMessageProcessingService(
                conversationRepository,
                conversationMemory,
                messageRepository,
                processingAttemptRepository,
                outboxRepository,
                conversationOrchestrator,
                conversationSummaryService,
                customerPreferenceService,
                explicitPreferenceCaptureService,
                optOutDetector,
                optInDetector,
                contactSuppressionService,
                humanFollowUpTaskService,
                new InboundProcessingProperties(1000, 20, 3, Duration.ofMinutes(5), Duration.ofSeconds(30)),
                Clock.fixed(NOW, ZoneOffset.UTC));
        UUID conversationId = UUID.randomUUID();
        conversation = new Conversation(
                conversationId, Channel.TELEGRAM, "chat-1", "customer-1",
                ConversationStatus.OPEN, NOW, NOW);
        message = new Message(
                UUID.randomUUID(), conversationId, Channel.TELEGRAM, "message-1",
                MessageDirection.INBOUND, MessageType.TEXT, "¿Qué venden?", NOW, NOW);
        attempt = new ProcessingAttempt(
                UUID.randomUUID(), message.id(), ProcessingAttemptStatus.PROCESSING, 1,
                null, NOW, NOW, NOW, NOW);
        when(messageRepository.findById(message.id())).thenReturn(Optional.of(message));
        when(conversationRepository.findById(conversation.id())).thenReturn(Optional.of(conversation));
        lenient().when(conversationMemory.load(conversation.id(), conversation.id().toString()))
                .thenReturn(Optional.empty());
        lenient().when(messageRepository.findRecentBodies(conversation.id(), 20)).thenReturn(List.of(message.body()));
        lenient().when(conversationSummaryService.summaryForContext(any())).thenReturn(null);
        lenient().when(conversationSummaryService.appendAndMaybeSummarize(any(), anyString(), any()))
                .thenAnswer(invocation -> invocation.getArgument(0));
        lenient().when(explicitPreferenceCaptureService.capture(anyString(), anyString(), any()))
                .thenReturn(ExplicitPreferenceCaptureService.CaptureResult.notDetected());
        lenient().when(optOutDetector.isOptOut(anyString())).thenReturn(false);
        lenient().when(optInDetector.isOptIn(anyString())).thenReturn(false);
        lenient().when(contactSuppressionService.isSuppressed(any(), anyString())).thenReturn(false);
        lenient().when(customerPreferenceService.findForContext(anyString(), any())).thenReturn(List.of());
        lenient().when(conversationOrchestrator.replyForDetailed(any())).thenReturn(
                new ConversationExecutionResult(
                        "wcs-agent-runtime-v1", "GENERAL_SUPPORT", "REPLIED", "Respuesta segura", null, 1));
    }

    @Test
    void processesOutsideTheWebhookAndMarksTheAttemptWithTheOutbox() {
        service.process(attempt);

        verify(conversationOrchestrator).replyForDetailed(any());
        verify(outboxRepository).save(any());
        verify(processingAttemptRepository).markCompleted(attempt.id(), NOW);
    }

    @Test
    void emitsTerminalFallbackAfterTheLastAttemptWithoutCallingTheLlm() {
        service.fail(attempt, "BedrockTimeout", true);

        verify(outboxRepository).save(any());
        verify(processingAttemptRepository).markFailed(
                attempt.id(), "BedrockTimeout", NOW.plusSeconds(30), true, NOW);
        verify(conversationOrchestrator, never()).replyForDetailed(any());
    }

    @Test
    void optsOutBeforeCallingTheOrchestratorAndClearsConversationState() {
        when(optOutDetector.isOptOut(message.body())).thenReturn(true);

        service.process(attempt);

        verify(contactSuppressionService).suppress(
                conversation.channel(), conversation.externalCustomerId(), message.id(), NOW);
        verify(conversationMemory).clear(conversation.id(), conversation.id().toString());
        verify(customerPreferenceService).clearConversation(conversation.id(), conversation.id().toString());
        verify(conversationOrchestrator, never()).replyForDetailed(any());
        verify(outboxRepository, never()).save(any());
        verify(processingAttemptRepository).markCompleted(attempt.id(), NOW);
    }

    @Test
    void reactivatesAndResetsContextBeforeCallingTheOrchestrator() {
        when(optInDetector.isOptIn(message.body())).thenReturn(true);
        when(contactSuppressionService.reactivate(
                conversation.channel(), conversation.externalCustomerId(), NOW)).thenReturn(true);

        service.process(attempt);

        verify(contactSuppressionService).reactivate(
                conversation.channel(), conversation.externalCustomerId(), NOW);
        verify(conversationMemory).clear(conversation.id(), conversation.id().toString());
        verify(customerPreferenceService).clearConversation(conversation.id(), conversation.id().toString());
        verify(conversationMemory).save(org.mockito.ArgumentMatchers.argThat(state ->
                state.conversationId().equals(conversation.id())
                        && state.recentMessages().isEmpty()
                        && state.summary() == null));
        verify(outboxRepository).save(any());
        verify(conversationOrchestrator, never()).replyForDetailed(any());
        verify(processingAttemptRepository).markCompleted(attempt.id(), NOW);
    }

    @Test
    void suppressesRegularMessagesUntilExplicitReactivation() {
        when(contactSuppressionService.isSuppressed(
                conversation.channel(), conversation.externalCustomerId())).thenReturn(true);

        service.process(attempt);

        verify(conversationOrchestrator, never()).replyForDetailed(any());
        verify(outboxRepository, never()).save(any());
        verify(processingAttemptRepository).markCompleted(attempt.id(), NOW);
    }
}
