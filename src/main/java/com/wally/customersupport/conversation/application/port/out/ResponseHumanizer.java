package com.wally.customersupport.conversation.application.port.out;

import com.wally.customersupport.conversation.domain.model.ResponseHumanizationRequest;
import com.wally.customersupport.conversation.domain.model.ResponseHumanizationResult;

/**
 * Presents validated use-case results without becoming a source of business
 * facts.
 */
public interface ResponseHumanizer {

    ResponseHumanizationResult humanize(ResponseHumanizationRequest request);
}
