package com.wally.customersupport.application.service;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.wally.customersupport.application.port.in.InboundMessagePort;
import com.wally.customersupport.application.port.out.ConversationRepository;
import com.wally.customersupport.application.port.out.MessageRepository;
import com.wally.customersupport.application.port.out.OutboxRepository;
import com.wally.customersupport.application.port.out.ProcessingAttemptRepository;
import com.wally.customersupport.domain.model.Conversation;
import com.wally.customersupport.domain.model.ConversationContext;
import com.wally.customersupport.domain.model.ConversationStatus;
import com.wally.customersupport.domain.model.InboundMessageCommand;
import com.wally.customersupport.domain.model.InboundMessageResult;
import com.wally.customersupport.domain.model.Message;
import com.wally.customersupport.domain.model.MessageDirection;
import com.wally.customersupport.domain.model.MessageType;
import com.wally.customersupport.domain.model.OutboxMessage;
import com.wally.customersupport.domain.model.OutboundMessage;
import com.wally.customersupport.domain.model.ProcessingAttempt;
import com.wally.customersupport.domain.model.ProcessingAttemptStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class InboundMessageApplicationService implements InboundMessagePort {

    private final ConversationRepository conversationRepository;
    private final MessageRepository messageRepository;
    private final ProcessingAttemptRepository processingAttemptRepository;
    private final OutboxRepository outboxRepository;
    private final ConversationOrchestrator conversationOrchestrator;
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

        List<String> recentMessages = messageRepository.findRecentBodies(conversation.id(), 20);
        String reply = conversationOrchestrator.replyFor(new ConversationContext(
                    conversation.id(),
                    conversation.externalCustomerId(),
                    command.body(),
                    recentMessages,
                    List.of()));

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

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
