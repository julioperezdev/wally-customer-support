package com.wally.customersupport.conversation.application.service;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

import com.wally.customersupport.conversation.application.port.out.ConversationMemory;
import com.wally.customersupport.conversation.application.port.out.ConversationRepository;
import com.wally.customersupport.conversation.application.port.out.MessageRepository;
import com.wally.customersupport.conversation.application.port.out.OutboxRepository;
import com.wally.customersupport.conversation.application.port.out.ProcessingAttemptRepository;
import com.wally.customersupport.conversation.domain.model.ConversationContext;
import com.wally.customersupport.conversation.domain.model.ConversationExecutionResult;
import com.wally.customersupport.conversation.domain.model.ConversationState;
import com.wally.customersupport.conversation.domain.model.Message;
import com.wally.customersupport.conversation.domain.model.OutboxMessage;
import com.wally.customersupport.conversation.domain.model.OutboundMessage;
import com.wally.customersupport.conversation.domain.model.ProcessingAttempt;
import com.wally.customersupport.shared.infrastructure.config.InboundProcessingProperties;
import com.wally.customersupport.shared.infrastructure.observability.StructuredEventLog;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class InboundMessageProcessingService {

    private static final String TERMINAL_FALLBACK =
            "No pude procesar tu consulta en este momento. Un agente revisará tu mensaje.";

    private final ConversationRepository conversationRepository;
    private final ConversationMemory conversationMemory;
    private final MessageRepository messageRepository;
    private final ProcessingAttemptRepository processingAttemptRepository;
    private final OutboxRepository outboxRepository;
    private final ConversationOrchestrator conversationOrchestrator;
    private final ConversationSummaryService conversationSummaryService;
    private final CustomerPreferenceService customerPreferenceService;
    private final ExplicitPreferenceCaptureService explicitPreferenceCaptureService;
    private final OptOutDetector optOutDetector;
    private final ContactSuppressionService contactSuppressionService;
    private final HumanFollowUpTaskService humanFollowUpTaskService;
    private final InboundProcessingProperties properties;
    private final Clock clock;

    @Transactional
    public void process(ProcessingAttempt attempt) {
        Message inboundMessage = messageRepository.findById(attempt.messageId())
                .orElseThrow(() -> new IllegalStateException("Inbound message was not found"));
        var conversation = conversationRepository.findById(inboundMessage.conversationId())
                .orElseThrow(() -> new IllegalStateException("Conversation was not found"));
        Instant now = clock.instant();
        String actorId = conversation.id().toString();

        if (optOutDetector.isOptOut(inboundMessage.body())) {
            contactSuppressionService.suppress(
                    conversation.channel(),
                    conversation.externalCustomerId(),
                    inboundMessage.id(),
                    now);
            conversationMemory.clear(conversation.id(), actorId);
            customerPreferenceService.clearConversation(conversation.id(), actorId);
            StructuredEventLog.info(log, "MESSAGE_OPTED_OUT", java.util.Map.of(
                    "operation", "conversation.opt_out",
                    "result", "SUPPRESSED",
                    "channel", conversation.channel().name(),
                    "correlationId", conversation.id()));
            processingAttemptRepository.markCompleted(attempt.id(), now);
            return;
        }

        if (contactSuppressionService.isSuppressed(
                conversation.channel(), conversation.externalCustomerId())) {
            StructuredEventLog.info(log, "MESSAGE_SUPPRESSED", java.util.Map.of(
                    "operation", "conversation.opt_out.guard",
                    "result", "DO_NOT_CONTACT",
                    "channel", conversation.channel().name(),
                    "correlationId", conversation.id()));
            processingAttemptRepository.markCompleted(attempt.id(), now);
            return;
        }

        ConversationState conversationState = loadConversationState(conversation.id(), actorId, now);
        conversationSummaryService.recordContextPrepared(conversationState);
        ExplicitPreferenceCaptureService.CaptureResult preferenceCapture =
                explicitPreferenceCaptureService.capture(actorId, inboundMessage.body(), now);
        var preferences = customerPreferenceService.findForContext(conversation.id().toString(), conversation.id());
        ConversationExecutionResult executionResult = null;
        String reply;
        if (preferenceCapture.shouldAcknowledge()) {
            reply = preferenceReply(preferenceCapture);
        } else {
            executionResult = conversationOrchestrator.replyForDetailed(new ConversationContext(
                        conversation.id(),
                        conversation.externalCustomerId(),
                        inboundMessage.body(),
                        conversationState.recentMessages(),
                        List.of(),
                        conversationSummaryService.summaryForContext(conversationState),
                        preferences,
                        inboundMessage.channel()));
            reply = executionResult.response();
        }

        saveConversationMemory(conversationSummaryService.appendAndMaybeSummarize(
                conversationState,
                inboundMessage.body(),
                now));

        humanFollowUpTaskService.createIfRequired(conversation, inboundMessage, executionResult);

        if (!isBlank(reply)) {
            outboxRepository.save(OutboxMessage.pendingReply(
                    OutboundMessage.text(
                            conversation.channel(),
                            conversation.id(),
                            conversation.externalCustomerId(),
                            reply),
                    now));
        }
        processingAttemptRepository.markCompleted(attempt.id(), now);
    }

    @Transactional
    public void fail(ProcessingAttempt attempt, String error, boolean exhausted) {
        Instant now = clock.instant();
        if (exhausted) {
            messageRepository.findById(attempt.messageId()).ifPresent(message ->
                    conversationRepository.findById(message.conversationId()).ifPresent(conversation ->
                            outboxRepository.save(OutboxMessage.pendingReply(
                                    OutboundMessage.text(
                                            conversation.channel(),
                                            conversation.id(),
                                            conversation.externalCustomerId(),
                                            TERMINAL_FALLBACK),
                                    now))));
        }
        processingAttemptRepository.markFailed(
                attempt.id(),
                sanitizeError(error),
                now.plus(properties.retryDelay()),
                exhausted,
                now);
    }

    private ConversationState loadConversationState(
            java.util.UUID conversationId,
            String actorId,
            Instant now) {
        try {
            return conversationMemory.load(conversationId, actorId)
                    .orElseGet(() -> new ConversationState(
                            conversationId,
                            actorId,
                            messageRepository.findRecentBodies(conversationId, 20),
                            now));
        } catch (RuntimeException exception) {
            StructuredEventLog.warn(log, "MEMORY_STATE_LOAD_FAILED", java.util.Map.of(
                    "errorType", exception.getClass().getSimpleName(),
                    "correlationId", conversationId));
            return new ConversationState(
                    conversationId,
                    actorId,
                    messageRepository.findRecentBodies(conversationId, 20),
                    now);
        }
    }

    private void saveConversationMemory(ConversationState state) {
        try {
            conversationMemory.save(state);
        } catch (RuntimeException exception) {
            StructuredEventLog.warn(log, "MEMORY_STATE_SAVE_FAILED", java.util.Map.of(
                    "errorType", exception.getClass().getSimpleName(),
                    "correlationId", state.conversationId()));
        }
    }

    private static String preferenceReply(ExplicitPreferenceCaptureService.CaptureResult result) {
        if (result.status() == ExplicitPreferenceCaptureService.Status.SAVED) {
            return "Perfecto, voy a tener en cuenta que preferís el " + result.color() + ".";
        }
        return "Puedo recordar como preferencia estos colores: negro, blanco, gris, azul, rojo, "
                + "verde, amarillo, rosa o violeta.";
    }

    private static String sanitizeError(String error) {
        if (error == null || error.isBlank()) {
            return "ProcessingException";
        }
        return error.length() > 1000 ? error.substring(0, 1000) : error;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
