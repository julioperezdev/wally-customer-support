package com.wally.customersupport.conversation.application.port.out;

import java.util.Optional;

/**
 * Resolves an opaque media reference into a short-lived public URL at dispatch
 * time. The application stores the reference, never a provider URL or token.
 */
public interface OutboundMediaUrlResolver {

    Optional<String> resolve(String mediaReference);
}
