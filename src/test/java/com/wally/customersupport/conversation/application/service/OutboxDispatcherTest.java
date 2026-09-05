package com.wally.customersupport.conversation.application.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import com.wally.customersupport.conversation.application.port.out.OutboxRepository;
import com.wally.customersupport.conversation.application.port.out.OutboundMessagePort;
import com.wally.customersupport.conversation.domain.model.OutboxMessage;
import com.wally.customersupport.conversation.domain.model.OutboundMessage;
import com.wally.customersupport.conversation.domain.model.Channel;
import com.wally.customersupport.shared.infrastructure.config.OutboxProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OutboxDispatcherTest {

    private static final Instant NOW = Instant.parse("2026-08-30T12:00:00Z");

    @Mock
    private OutboxRepository outboxRepository;
    @Mock
    private OutboundMessagePort outboundMessagePort;

    @Test
    void retriesTransientOutboundFailureBeforeTheConfiguredLimit() {
        OutboxMessage message = OutboxMessage.pendingReply(
                OutboundMessage.text(Channel.WHATSAPP, UUID.randomUUID(), "synthetic-recipient", "Hola"), NOW);
        when(outboxRepository.findDue(NOW, 50)).thenReturn(List.of(message));
        when(outboundMessagePort.channel()).thenReturn(Channel.WHATSAPP);
        doThrow(new IllegalStateException("synthetic failure"))
                .when(outboundMessagePort).send(message.message());

        new OutboxDispatcher(
                outboxRepository,
                List.of(outboundMessagePort),
                new OutboxProperties(5000, 3),
                Clock.fixed(NOW, ZoneOffset.UTC))
                .dispatchDueMessages();

        verify(outboxRepository).markProcessing(message.id());
        verify(outboxRepository).markFailed(
                eq(message.id()), eq("synthetic failure"), eq(NOW.plusSeconds(30)), eq(false));
    }

    @Test
    void routesTelegramMessageToTelegramAdapter() {
        OutboundMessagePort telegramPort = org.mockito.Mockito.mock(OutboundMessagePort.class);
        OutboxMessage message = OutboxMessage.pendingReply(
                OutboundMessage.text(Channel.TELEGRAM, UUID.randomUUID(), "synthetic-chat", "Hola"), NOW);
        when(outboxRepository.findDue(NOW, 50)).thenReturn(List.of(message));
        when(outboundMessagePort.channel()).thenReturn(Channel.WHATSAPP);
        when(telegramPort.channel()).thenReturn(Channel.TELEGRAM);

        new OutboxDispatcher(
                outboxRepository,
                List.of(outboundMessagePort, telegramPort),
                new OutboxProperties(5000, 3),
                Clock.fixed(NOW, ZoneOffset.UTC))
                .dispatchDueMessages();

        verify(telegramPort).send(message.message());
    }
}
