package com.wally.customersupport.conversation.application.port.out;

import com.wally.customersupport.conversation.domain.model.OutboundMessage;
import com.wally.customersupport.conversation.domain.model.Channel;

public interface OutboundMessagePort {

    Channel channel();

    void send(OutboundMessage message);
}
