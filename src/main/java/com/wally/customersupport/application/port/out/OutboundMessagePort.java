package com.wally.customersupport.application.port.out;

import com.wally.customersupport.domain.model.OutboundMessage;

public interface OutboundMessagePort {

    void send(OutboundMessage message);
}
