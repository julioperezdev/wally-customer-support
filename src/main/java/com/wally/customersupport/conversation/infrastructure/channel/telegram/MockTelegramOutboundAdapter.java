package com.wally.customersupport.conversation.infrastructure.channel.telegram;

import com.wally.customersupport.conversation.application.port.out.OutboundMessagePort;
import com.wally.customersupport.conversation.domain.model.Channel;
import com.wally.customersupport.conversation.domain.model.OutboundMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "wcs.telegram.enabled", havingValue = "true")
@ConditionalOnProperty(name = "wcs.telegram.adapter", havingValue = "mock")
@Slf4j
public class MockTelegramOutboundAdapter implements OutboundMessagePort {

    @Override
    public Channel channel() {
        return Channel.TELEGRAM;
    }

    @Override
    public void send(OutboundMessage message) {
        log.info("Mock Telegram outbound message dispatched: type={}", message.deliveryType());
    }
}
