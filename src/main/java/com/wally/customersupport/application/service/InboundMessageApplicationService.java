package com.wally.customersupport.application.service;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.wally.customersupport.application.port.in.InboundMessagePort;
import com.wally.customersupport.application.port.out.ConversationRepository;
import com.wally.customersupport.application.port.out.KnowledgeRetriever;
import com.wally.customersupport.application.port.out.LlmClient;
import com.wally.customersupport.application.port.out.MessageRepository;
import com.wally.customersupport.application.port.out.OutboxRepository;
import com.wally.customersupport.application.port.out.ProcessingAttemptRepository;
import com.wally.customersupport.domain.model.Conversation;
import com.wally.customersupport.domain.model.ConversationContext;
import com.wally.customersupport.domain.model.ConversationStatus;
import com.wally.customersupport.domain.model.InboundMessageCommand;
import com.wally.customersupport.domain.model.InboundMessageResult;
import com.wally.customersupport.domain.model.KnowledgeChunk;
import com.wally.customersupport.domain.model.KnowledgeQuery;
import com.wally.customersupport.domain.model.Message;
import com.wally.customersupport.domain.model.MessageDirection;
import com.wally.customersupport.domain.model.MessageType;
import com.wally.customersupport.domain.model.OutboxMessage;
import com.wally.customersupport.domain.model.OutboundMessage;
import com.wally.customersupport.domain.model.ProcessingAttempt;
import com.wally.customersupport.domain.model.ProcessingAttemptStatus;
import com.wally.customersupport.infrastructure.config.RagProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InboundMessageApplicationService implements InboundMessagePort {

    private final ConversationRepository conversationRepository;
    private final MessageRepository messageRepository;
    private final ProcessingAttemptRepository processingAttemptRepository;
    private final OutboxRepository outboxRepository;
    private final KnowledgeRetriever knowledgeRetriever;
    private final LlmClient llmClient;
    private final RagProperties ragProperties;
    private final Clock clock;

    @Autowired
    public InboundMessageApplicationService(
            ConversationRepository conversationRepository,
            MessageRepository messageRepository,
            ProcessingAttemptRepository processingAttemptRepository,
            OutboxRepository outboxRepository,
            KnowledgeRetriever knowledgeRetriever,
            LlmClient llmClient,
            RagProperties ragProperties) {
        this(
                conversationRepository,
                messageRepository,
                processingAttemptRepository,
                outboxRepository,
                knowledgeRetriever,
                llmClient,
                ragProperties,
                Clock.systemUTC());
    }

    InboundMessageApplicationService(
            ConversationRepository conversationRepository,
            MessageRepository messageRepository,
            ProcessingAttemptRepository processingAttemptRepository,
            OutboxRepository outboxRepository,
            KnowledgeRetriever knowledgeRetriever,
            LlmClient llmClient,
            RagProperties ragProperties,
            Clock clock) {
        this.conversationRepository = conversationRepository;
        this.messageRepository = messageRepository;
        this.processingAttemptRepository = processingAttemptRepository;
        this.outboxRepository = outboxRepository;
        this.knowledgeRetriever = knowledgeRetriever;
        this.llmClient = llmClient;
        this.ragProperties = ragProperties;
        this.clock = clock;
    }

    @Override
    @Transactional
    public InboundMessageResult accept(InboundMessageCommand command) {
        if (isBlank(command.externalMessageId())
                || isBlank(command.externalConversationId())
                || isBlank(command.customerWaId())
                || isBlank(command.body())) {
            return InboundMessageResult.ignored();
        }
        if (messageRepository.existsByExternalMessageId(command.externalMessageId())) {
            return InboundMessageResult.duplicate();
        }

        Instant now = clock.instant();
        Conversation conversation = conversationRepository
                .findByChannelAndExternalConversationId(command.channel(), command.externalConversationId())
                .orElseGet(() -> conversationRepository.save(new Conversation(
                        UUID.randomUUID(),
                        command.channel(),
                        command.externalConversationId(),
                        command.customerWaId(),
                        ConversationStatus.OPEN,
                        now,
                        now)));

        Message inboundMessage = messageRepository.save(new Message(
                UUID.randomUUID(),
                conversation.id(),
                command.externalMessageId(),
                MessageDirection.INBOUND,
                MessageType.TEXT,
                command.body(),
                command.occurredAt() == null ? now : command.occurredAt(),
                now));

        List<String> recentMessages = messageRepository.findRecentBodies(conversation.id(), 20);
        List<KnowledgeChunk> knowledge = knowledgeRetriever.retrieve(new KnowledgeQuery(
                command.body(),
                conversation.id(),
                Math.max(1, ragProperties.maxResults())));
        String reply = llmClient.generateReply(new ConversationContext(
                conversation.id(),
                conversation.customerWaId(),
                command.body(),
                recentMessages,
                knowledge));

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
                    OutboundMessage.text(conversation.id(), conversation.customerWaId(), reply),
                    now));
        }
        return InboundMessageResult.accepted();
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
