package com.wally.customersupport.conversation.application.service;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

import com.wally.customersupport.conversation.application.port.in.InboundMessagePort;
import com.wally.customersupport.conversation.application.port.out.ConversationRepository;
import com.wally.customersupport.conversation.application.port.out.MessageRepository;
import com.wally.customersupport.conversation.application.port.out.ProcessingAttemptRepository;
import com.wally.customersupport.conversation.domain.model.Conversation;
import com.wally.customersupport.conversation.domain.model.InboundMessageCommand;
import com.wally.customersupport.conversation.domain.model.InboundMessageResult;
import com.wally.customersupport.conversation.domain.model.Message;
import com.wally.customersupport.conversation.domain.model.MessageDirection;
import com.wally.customersupport.conversation.domain.model.MessageType;
import com.wally.customersupport.conversation.domain.model.MessageWriteResult;
import com.wally.customersupport.conversation.domain.model.ProcessingAttempt;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class InboundMessageApplicationService implements InboundMessagePort {

    private final ConversationRepository conversationRepository;
    private final MessageRepository messageRepository;
    private final ProcessingAttemptRepository processingAttemptRepository;
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
        Conversation conversation = conversationRepository.findOrCreate(
                command.channel(),
                command.externalConversationId(),
                command.externalCustomerId(),
                now);
        MessageWriteResult writeResult = messageRepository.saveIfAbsent(new Message(
                UUID.randomUUID(),
                conversation.id(),
                command.channel(),
                command.externalMessageId(),
                MessageDirection.INBOUND,
                MessageType.TEXT,
                command.body(),
                command.occurredAt() == null ? now : command.occurredAt(),
                now));

        if (!writeResult.created()) {
            return InboundMessageResult.duplicate();
        }
        processingAttemptRepository.save(ProcessingAttempt.pending(writeResult.message().id(), now));
        return InboundMessageResult.accepted();
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
