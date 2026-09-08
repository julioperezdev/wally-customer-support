package com.wally.customersupport.conversation.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import com.wally.customersupport.conversation.application.port.out.ConversationRepository;
import com.wally.customersupport.conversation.application.port.out.MessageRepository;
import com.wally.customersupport.conversation.application.port.out.ProcessingAttemptRepository;
import com.wally.customersupport.conversation.domain.model.Channel;
import com.wally.customersupport.conversation.domain.model.Conversation;
import com.wally.customersupport.conversation.domain.model.ConversationStatus;
import com.wally.customersupport.conversation.domain.model.InboundMessageCommand;
import com.wally.customersupport.conversation.domain.model.InboundMessageResult;
import com.wally.customersupport.conversation.domain.model.Message;
import com.wally.customersupport.conversation.domain.model.MessageDirection;
import com.wally.customersupport.conversation.domain.model.MessageType;
import com.wally.customersupport.conversation.domain.model.MessageWriteResult;
import com.wally.customersupport.conversation.domain.model.ProcessingAttempt;
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

    private InboundMessageApplicationService service;
    private Conversation conversation;
    private InboundMessageCommand command;
    private Message persistedMessage;

    @BeforeEach
    void setUp() {
        service = new InboundMessageApplicationService(
                conversationRepository,
                messageRepository,
                processingAttemptRepository,
                Clock.fixed(NOW, ZoneOffset.UTC));
        conversation = new Conversation(
                UUID.randomUUID(), Channel.WHATSAPP, "conversation-1", "customer-1",
                ConversationStatus.OPEN, NOW, NOW);
        command = new InboundMessageCommand(
                Channel.WHATSAPP, "message-1", "conversation-1", "customer-1", "Necesito ayuda", NOW);
        persistedMessage = new Message(
                UUID.randomUUID(), conversation.id(), Channel.WHATSAPP, "message-1", MessageDirection.INBOUND,
                MessageType.TEXT, command.body(), NOW, NOW);
    }

    @Test
    void persistsInboundMessageAndQueuesDurableProcessingAttempt() {
        when(messageRepository.existsByExternalMessageId(Channel.WHATSAPP, "message-1")).thenReturn(false);
        when(conversationRepository.findOrCreate(
                Channel.WHATSAPP, "conversation-1", "customer-1", NOW)).thenReturn(conversation);
        when(messageRepository.saveIfAbsent(any(Message.class)))
                .thenReturn(new MessageWriteResult(persistedMessage, true));

        InboundMessageResult result = service.accept(command);

        assertEquals(InboundMessageResult.Result.ACCEPTED, result.result());
        verify(messageRepository).saveIfAbsent(any(Message.class));
        verify(processingAttemptRepository).save(any(ProcessingAttempt.class));
    }

    @Test
    void returnsDuplicateWithoutCreatingAProcessingAttempt() {
        when(messageRepository.existsByExternalMessageId(Channel.WHATSAPP, "message-1")).thenReturn(true);

        InboundMessageResult result = service.accept(command);

        assertEquals(InboundMessageResult.Result.DUPLICATE, result.result());
        verify(conversationRepository, never()).findOrCreate(any(), any(), any(), any());
        verify(processingAttemptRepository, never()).save(any());
    }

    @Test
    void treatsDatabaseConflictAsDuplicateWithoutCreatingAnotherJob() {
        when(messageRepository.existsByExternalMessageId(Channel.WHATSAPP, "message-1")).thenReturn(false);
        when(conversationRepository.findOrCreate(
                Channel.WHATSAPP, "conversation-1", "customer-1", NOW)).thenReturn(conversation);
        when(messageRepository.saveIfAbsent(any(Message.class)))
                .thenReturn(new MessageWriteResult(persistedMessage, false));

        InboundMessageResult result = service.accept(command);

        assertEquals(InboundMessageResult.Result.DUPLICATE, result.result());
        verify(processingAttemptRepository, never()).save(any());
    }

    @Test
    void ignoresIncompleteMessagesWithoutTouchingPersistence() {
        InboundMessageResult result = service.accept(new InboundMessageCommand(
                Channel.TELEGRAM, "", "chat-1", "user-1", "Hola", NOW));

        assertEquals(InboundMessageResult.Result.IGNORED, result.result());
        verify(messageRepository, never()).existsByExternalMessageId(any(), any());
    }
}
