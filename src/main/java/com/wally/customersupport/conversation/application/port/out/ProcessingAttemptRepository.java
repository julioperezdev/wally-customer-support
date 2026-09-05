package com.wally.customersupport.conversation.application.port.out;

import com.wally.customersupport.conversation.domain.model.ProcessingAttempt;

public interface ProcessingAttemptRepository {

    ProcessingAttempt save(ProcessingAttempt attempt);
}
