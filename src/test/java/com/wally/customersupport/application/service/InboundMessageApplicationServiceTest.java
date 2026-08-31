package com.wally.customersupport.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.wally.customersupport.application.port.out.ConversationRepository;
import com.wally.customersupport.application.port.out.KnowledgeRetriever;
import com.wally.customersupport.application.port.out.LlmClient;
import com.wally.customersupport.application.port.out.MessageRepository;
import com.wally.customersupport.application.port.out.OutboxRepository;
import com.wally.customersupport.application.port.out.ProcessingAttemptRepository;
import com.wally.customersupport.domain.model.Channel;
import com.wally.customersupport.domain.model.Conversation;
import com.wally.customersupport.domain.model.ConversationStatus;
import com.wally.customersupport.domain.model.InboundMessageCommand;
import com.wally.customersupport.domain.model.InboundMessageResult;
import com.wally.customersupport.domain.model.KnowledgeChunk;
import com.wally.customersupport.domain.model.Message;
import com.wally.customersupport.domain.model.MessageDirection;
import com.wally.customersupport.domain.model.MessageType;
import com.wally.customersupport.domain.model.OutboxMessage;
import com.wally.customersupport.domain.model.ProcessingAttempt;
import com.wally.customersupport.infrastructure.config.RagProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class InboundMessageApplicationServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-30T12:00:00Z");

    @Mock
    private ConversationRepository conversationRepository;
    @Mock
    private MessageRepository messageRepository;
    @Mock
    private ProcessingAttemptRepository processingAttemptRepository;
    @Mock
    private OutboxRepository outboxRepository;
    @Mock
    private KnowledgeRetriever knowledgeRetriever;
    @Mock
    private LlmClient llmClient;

    private InboundMessageApplicationService service;
    private Conversation conversation;
    private InboundMessageCommand command;

    @BeforeEach
    void setUp() {
        service = new InboundMessageApplicationService(
                conversationRepository,
                messageRepository,
                processingAttemptRepository,
                outboxRepository,
                knowledgeRetriever,
                llmClient,
                new RagProperties("mock", 5),
                Clock.fixed(NOW, ZoneOffset.UTC));
        conversation = new Conversation(
                UUID.randomUUID(), Channel.WHATSAPP, "conversation-1", "customer-1",
                ConversationStatus.OPEN, NOW, NOW);
        command = new InboundMessageCommand(
                Channel.WHATSAPP, "message-1", "conversation-1", "customer-1", "Necesito ayuda", NOW);
    }

    @Test
    void persistsInboundMessageAndQueuesReplyBehindOutbox() {
        Message message = new Message(
                UUID.randomUUID(), conversation.id(), "message-1", MessageDirection.INBOUND,
                MessageType.TEXT, command.body(), NOW, NOW);
        when(messageRepository.existsByExternalMessageId("message-1")).thenReturn(false);
        when(conversationRepository.findByChannelAndExternalConversationId(Channel.WHATSAPP, "conversation-1"))
                .thenReturn(Optional.of(conversation));
        when(messageRepository.save(any(Message.class))).thenReturn(message);
        when(messageRepository.findRecentBodies(conversation.id(), 20)).thenReturn(List.of(command.body()));
        when(knowledgeRetriever.retrieve(any())).thenReturn(List.of(new KnowledgeChunk("policy", 0.9, "source-1")));
        when(llmClient.generateReply(any())).thenReturn("Respuesta segura");

        InboundMessageResult result = service.accept(command);

        assertEquals(InboundMessageResult.Result.ACCEPTED, result.result());
        verify(messageRepository).save(any(Message.class));
        verify(processingAttemptRepository).save(any(ProcessingAttempt.class));
        verify(outboxRepository).save(any(OutboxMessage.class));
    }

    @Test
    void returnsDuplicateWithoutCallingAiOrSendingAnotherReply() {
        when(messageRepository.existsByExternalMessageId("message-1")).thenReturn(true);

        InboundMessageResult result = service.accept(command);

        assertEquals(InboundMessageResult.Result.DUPLICATE, result.result());
        verify(conversationRepository, never()).save(any());
        verify(messageRepository, times(1)).existsByExternalMessageId("message-1");
        verify(knowledgeRetriever, never()).retrieve(any());
        verify(llmClient, never()).generateReply(any());
        verify(outboxRepository, never()).save(any());
    }
}
