package com.wally.customersupport.backoffice.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.wally.customersupport.conversation.application.port.out.ConversationRepository;
import com.wally.customersupport.conversation.application.port.out.HumanFollowUpTaskRepository;
import com.wally.customersupport.conversation.application.port.out.MessageRepository;
import com.wally.customersupport.conversation.domain.model.Channel;
import com.wally.customersupport.conversation.domain.model.Conversation;
import com.wally.customersupport.conversation.domain.model.ConversationStatus;
import com.wally.customersupport.conversation.domain.model.HumanFollowUpPriority;
import com.wally.customersupport.conversation.domain.model.HumanFollowUpStatus;
import com.wally.customersupport.conversation.domain.model.HumanFollowUpTask;
import org.junit.jupiter.api.Test;

class BackofficeHumanFollowUpQueryServiceTest {

    @Test
    void returnsChannelAndRedactedBoundedContext() {
        UUID conversationId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();
        Instant now = Instant.parse("2026-09-09T00:00:00Z");
        HumanFollowUpTask task = new HumanFollowUpTask(
                taskId,
                conversationId,
                UUID.randomUUID(),
                "HUMAN_REQUEST",
                HumanFollowUpPriority.HIGH,
                HumanFollowUpStatus.OPEN,
                now.plusSeconds(3600),
                now,
                now,
                null);
        ConversationRepository conversations = mock(ConversationRepository.class);
        HumanFollowUpTaskRepository followUps = mock(HumanFollowUpTaskRepository.class);
        MessageRepository messages = mock(MessageRepository.class);
        when(followUps.findOpen(10)).thenReturn(List.of(task));
        when(conversations.findById(conversationId)).thenReturn(java.util.Optional.of(new Conversation(
                conversationId, Channel.TELEGRAM, "conversation", "customer", ConversationStatus.OPEN, now, now)));
        when(messages.findRecentBodies(conversationId, 3)).thenReturn(List.of(
                "Escribime a +54 11 5555 4444 o a julio@example.com por favor"));

        var result = new BackofficeHumanFollowUpQueryService(followUps, conversations, messages).findOpen(10);

        assertThat(result).singleElement().satisfies(view -> {
            assertThat(view.channel()).isEqualTo("TELEGRAM");
            assertThat(view.contextPreview()).singleElement()
                    .isEqualTo("Escribime a [CONTACT_REDACTED] o a [EMAIL_REDACTED] por favor");
        });
    }
}
