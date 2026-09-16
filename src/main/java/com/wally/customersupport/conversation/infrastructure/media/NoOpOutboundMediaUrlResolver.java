package com.wally.customersupport.conversation.infrastructure.media;

import java.util.Optional;

import com.wally.customersupport.conversation.application.port.out.OutboundMediaUrlResolver;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** Keeps media delivery optional while preserving the textual response. */
@Component
@ConditionalOnProperty(
        name = "wcs.backoffice.media.enabled",
        havingValue = "false",
        matchIfMissing = true)
public class NoOpOutboundMediaUrlResolver implements OutboundMediaUrlResolver {

    @Override
    public Optional<String> resolve(String mediaReference) {
        return Optional.empty();
    }
}
