package com.wally.customersupport.application.port.in;

import com.wally.customersupport.domain.model.InboundMessageCommand;
import com.wally.customersupport.domain.model.InboundMessageResult;

public interface InboundMessagePort {

    InboundMessageResult accept(InboundMessageCommand command);
}
