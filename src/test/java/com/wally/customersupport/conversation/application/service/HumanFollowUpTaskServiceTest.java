package com.wally.customersupport.conversation.application.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import com.wally.customersupport.conversation.application.port.out.HumanFollowUpTaskRepository;
import com.wally.customersupport.conversation.domain.model.Channel;
import com.wally.customersupport.conversation.domain.model.Conversation;
import com.wally.customersupport.conversation.domain.model.ConversationExecutionResult;
import com.wally.customersupport.conversation.domain.model.ConversationStatus;
import com.wally.customersupport.conversation.domain.model.HumanFollowUpPriority;
import com.wally.customersupport.conversation.domain.model.HumanFollowUpTask;
import com.wally.customersupport.conversation.domain.model.Message;
import com.wally.customersupport.conversation.domain.model.MessageDirection;
import com.wally.customersupport.conversation.domain.model.MessageType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class HumanFollowUpTaskServiceTest {

    private static final Instant NOW = Instant.parse("2026-09-08T12:00:00Z");

    @Mock
    private HumanFollowUpTaskRepository repository;

    @Test
    void createsHighPriorityTaskForExplicitHumanRequest() {
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        HumanFollowUpTaskService service = new HumanFollowUpTaskService(repository, clock);
        Conversation conversation = new Conversation(
                UUID.randomUUID(), Channel.TELEGRAM, "chat-1", "customer-1",
                ConversationStatus.OPEN, NOW, NOW);
        Message message = new Message(
                UUID.randomUUID(), conversation.id(), Channel.TELEGRAM, "message-1",
                MessageDirection.INBOUND, MessageType.TEXT, "Quiero hablar con una persona", NOW, NOW);
        ConversationExecutionResult result = new ConversationExecutionResult(
                "wcs-agent-runtime-v1", "HUMAN_HANDOFF", "HANDOFF", "respuesta", null, 1);
        when(repository.saveIfAbsent(any(HumanFollowUpTask.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        service.createIfRequired(conversation, message, result);

        verify(repository).saveIfAbsent(org.mockito.ArgumentMatchers.argThat(task ->
                task.priority() == HumanFollowUpPriority.HIGH
                        && task.reason().equals("HUMAN_REQUEST")
                        && task.dueAt().equals(NOW.plusSeconds(24 * 60 * 60))));
    }

    @Test
    void ignoresSuccessfulAutomatedResponses() {
        HumanFollowUpTaskService service = new HumanFollowUpTaskService(
                repository, Clock.fixed(NOW, ZoneOffset.UTC));
        Conversation conversation = new Conversation(
                UUID.randomUUID(), Channel.TELEGRAM, "chat-1", "customer-1",
                ConversationStatus.OPEN, NOW, NOW);
        Message message = new Message(
                UUID.randomUUID(), conversation.id(), Channel.TELEGRAM, "message-1",
                MessageDirection.INBOUND, MessageType.TEXT, "hola", NOW, NOW);
        ConversationExecutionResult result = new ConversationExecutionResult(
                "wcs-agent-runtime-v1", "GREETING", "REPLIED", "respuesta", null, 1);

        service.createIfRequired(conversation, message, result);

        org.mockito.Mockito.verifyNoInteractions(repository);
    }
}
