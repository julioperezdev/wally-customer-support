package com.wally.customersupport.conversation.infrastructure.channel.whatsapp.mock;

import com.wally.customersupport.conversation.application.port.out.OutboundMessagePort;
import com.wally.customersupport.conversation.domain.model.Channel;
import com.wally.customersupport.conversation.domain.model.OutboundMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "wcs.whatsapp.adapter", havingValue = "mock", matchIfMissing = true)
@Slf4j
public class MockWhatsAppOutboundAdapter implements OutboundMessagePort {

    @Override
    public Channel channel() {
        return Channel.WHATSAPP;
    }

    @Override
    public void send(OutboundMessage message) {
        log.info("Mock WhatsApp outbound message dispatched: type={}", message.deliveryType());
    }
}
