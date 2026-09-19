package com.wally.customersupport.conversation.application.tool;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.UUID;

import com.wally.customersupport.conversation.application.service.HumanFollowUpTaskService;
import com.wally.customersupport.conversation.domain.model.Channel;
import com.wally.customersupport.conversation.domain.model.Conversation;
import com.wally.customersupport.conversation.domain.model.ConversationStatus;
import com.wally.customersupport.conversation.domain.model.HumanFollowUpPriority;
import com.wally.customersupport.conversation.domain.model.HumanFollowUpTask;
import com.wally.customersupport.conversation.domain.model.Message;
import com.wally.customersupport.conversation.domain.model.MessageDirection;
import com.wally.customersupport.conversation.domain.model.MessageType;
import org.junit.jupiter.api.Test;

class HumanHandoffToolTest {

    private static final Instant NOW = Instant.parse("2026-09-19T01:00:00Z");

    @Test
    void reportsCreatedAndReusedHandoffsWithoutLoggingConversationContent() {
        HumanFollowUpTaskService service = mock(HumanFollowUpTaskService.class);
        Conversation conversation = new Conversation(
                UUID.randomUUID(), Channel.TELEGRAM, "chat-1", "customer-1",
                ConversationStatus.OPEN, NOW, NOW);
        Message message = new Message(
                UUID.randomUUID(), conversation.id(), Channel.TELEGRAM, "message-1",
                MessageDirection.INBOUND, MessageType.TEXT, "necesito ayuda", NOW, NOW);
        HumanFollowUpTask task = HumanFollowUpTask.open(
                conversation.id(), message.id(), "CUSTOMER_REQUEST", HumanFollowUpPriority.HIGH,
                NOW.plusSeconds(3600), NOW);
        when(service.createExplicitResult(
                conversation, message, "CUSTOMER_REQUEST", HumanFollowUpPriority.HIGH))
                .thenReturn(new HumanFollowUpTaskService.CreationResult(task, true))
                .thenReturn(new HumanFollowUpTaskService.CreationResult(task, false));
        HumanHandoffTool tool = new HumanHandoffTool(service);

        HumanHandoffTool.Result first = tool.execute(new HumanHandoffTool.Input(
                conversation, message, HumanHandoffTool.Reason.CUSTOMER_REQUEST,
                HumanFollowUpPriority.HIGH, true));
        HumanHandoffTool.Result second = tool.execute(new HumanHandoffTool.Input(
                conversation, message, HumanHandoffTool.Reason.CUSTOMER_REQUEST,
                HumanFollowUpPriority.HIGH, true));

        assertThat(first.status()).isEqualTo(HumanHandoffTool.Status.CREATED);
        assertThat(second.status()).isEqualTo(HumanHandoffTool.Status.REUSED);
        assertThat(first.contextIncluded()).isTrue();
    }
}
