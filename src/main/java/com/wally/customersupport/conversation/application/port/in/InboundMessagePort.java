package com.wally.customersupport.conversation.application.port.in;

import com.wally.customersupport.conversation.domain.model.InboundMessageCommand;
import com.wally.customersupport.conversation.domain.model.InboundMessageResult;

public interface InboundMessagePort {

    InboundMessageResult accept(InboundMessageCommand command);
}
