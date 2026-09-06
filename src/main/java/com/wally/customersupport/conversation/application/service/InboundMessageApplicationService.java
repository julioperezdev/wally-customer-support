package com.wally.customersupport.conversation.application.service;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.wally.customersupport.conversation.application.port.in.InboundMessagePort;
import com.wally.customersupport.conversation.application.port.out.ConversationMemory;
import com.wally.customersupport.conversation.application.port.out.ConversationRepository;
import com.wally.customersupport.conversation.application.port.out.MessageRepository;
import com.wally.customersupport.conversation.application.port.out.OutboxRepository;
import com.wally.customersupport.conversation.application.port.out.ProcessingAttemptRepository;
import com.wally.customersupport.conversation.domain.model.Conversation;
import com.wally.customersupport.conversation.domain.model.ConversationContext;
import com.wally.customersupport.conversation.domain.model.ConversationState;
import com.wally.customersupport.conversation.domain.model.ConversationStatus;
import com.wally.customersupport.conversation.domain.model.InboundMessageCommand;
import com.wally.customersupport.conversation.domain.model.InboundMessageResult;
import com.wally.customersupport.conversation.domain.model.Message;
import com.wally.customersupport.conversation.domain.model.MessageDirection;
import com.wally.customersupport.conversation.domain.model.MessageType;
import com.wally.customersupport.conversation.domain.model.OutboxMessage;
import com.wally.customersupport.conversation.domain.model.OutboundMessage;
import com.wally.customersupport.conversation.domain.model.ProcessingAttempt;
import com.wally.customersupport.conversation.domain.model.ProcessingAttemptStatus;
import com.wally.customersupport.shared.infrastructure.observability.StructuredEventLog;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class InboundMessageApplicationService implements InboundMessagePort {

    private final ConversationRepository conversationRepository;
    private final ConversationMemory conversationMemory;
    private final MessageRepository messageRepository;
    private final ProcessingAttemptRepository processingAttemptRepository;
    private final OutboxRepository outboxRepository;
    private final ConversationOrchestrator conversationOrchestrator;
    private final ConversationSummaryService conversationSummaryService;
    private final Clock clock;

    @Override
    @Transactional
    public InboundMessageResult accept(InboundMessageCommand command) {
        if (isBlank(command.externalMessageId())
                || isBlank(command.externalConversationId())
                || isBlank(command.externalCustomerId())
                || isBlank(command.body())) {
            return InboundMessageResult.ignored();
        }
        if (messageRepository.existsByExternalMessageId(command.channel(), command.externalMessageId())) {
            return InboundMessageResult.duplicate();
        }

        Instant now = clock.instant();
        Conversation conversation = conversationRepository
                .findByChannelAndExternalConversationId(command.channel(), command.externalConversationId())
                .orElseGet(() -> conversationRepository.save(new Conversation(
                        UUID.randomUUID(),
                        command.channel(),
                        command.externalConversationId(),
                        command.externalCustomerId(),
                        ConversationStatus.OPEN,
                        now,
                        now)));

        Message inboundMessage = messageRepository.save(new Message(
                UUID.randomUUID(),
                conversation.id(),
                command.channel(),
                command.externalMessageId(),
                MessageDirection.INBOUND,
                MessageType.TEXT,
                command.body(),
                command.occurredAt() == null ? now : command.occurredAt(),
                now));

        String actorId = conversation.id().toString();
        ConversationState conversationState = loadConversationState(conversation.id(), actorId, now);
        conversationSummaryService.recordContextPrepared(conversationState);
        String reply = conversationOrchestrator.replyFor(new ConversationContext(
                    conversation.id(),
                    conversation.externalCustomerId(),
                    command.body(),
                    conversationState.recentMessages(),
                    List.of(),
                    conversationSummaryService.summaryForContext(conversationState)));

        saveConversationMemory(conversationSummaryService.appendAndMaybeSummarize(
                conversationState,
                command.body(),
                now));

        processingAttemptRepository.save(new ProcessingAttempt(
                UUID.randomUUID(),
                inboundMessage.id(),
                ProcessingAttemptStatus.COMPLETED,
                1,
                null,
                now,
                now));

        if (!isBlank(reply)) {
            outboxRepository.save(OutboxMessage.pendingReply(
                    OutboundMessage.text(
                            conversation.channel(),
                            conversation.id(),
                            conversation.externalCustomerId(),
                            reply),
                    now));
        }
        return InboundMessageResult.accepted();
    }

    private ConversationState loadConversationState(UUID conversationId, String actorId, Instant now) {
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

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
