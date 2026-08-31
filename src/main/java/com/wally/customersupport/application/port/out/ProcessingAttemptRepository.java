package com.wally.customersupport.application.port.out;

import com.wally.customersupport.domain.model.ProcessingAttempt;

public interface ProcessingAttemptRepository {

    ProcessingAttempt save(ProcessingAttempt attempt);
}
