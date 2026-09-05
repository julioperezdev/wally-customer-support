package com.wally.customersupport.conversation.infrastructure.channel.whatsapp.mock;

import com.wally.customersupport.conversation.application.port.out.OutboundMessagePort;
import com.wally.customersupport.conversation.domain.model.Channel;
import com.wally.customersupport.conversation.domain.model.OutboundMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "wcs.whatsapp.adapter", havingValue = "mock", matchIfMissing = true)
public class MockWhatsAppOutboundAdapter implements OutboundMessagePort {

    private static final Logger LOGGER = LoggerFactory.getLogger(MockWhatsAppOutboundAdapter.class);

    @Override
    public Channel channel() {
        return Channel.WHATSAPP;
    }

    @Override
    public void send(OutboundMessage message) {
        LOGGER.info("Mock WhatsApp outbound message dispatched: type={}", message.deliveryType());
    }
}
