package com.wally.customersupport.conversation.application.tool;

/**
 * Internal, provider-neutral capability exposed to an AI orchestrator.
 *
 * <p>A tool is an adapter around an application service. It is not allowed to
 * contain SQL or infer business facts from the customer message.</p>
 */
public interface WcsTool<I, O> {

    WcsToolDescriptor descriptor();

    Class<I> inputType();

    O execute(I input);
}
