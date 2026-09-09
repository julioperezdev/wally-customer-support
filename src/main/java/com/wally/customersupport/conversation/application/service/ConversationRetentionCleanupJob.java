package com.wally.customersupport.conversation.application.service;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "wcs.conversation.retention.enabled", havingValue = "true")
public class ConversationRetentionCleanupJob {

    private final ConversationRetentionCleanupService service;

    @Scheduled(fixedDelayString = "${wcs.conversation.retention.schedule-delay-ms:86400000}")
    public void clean() {
        service.run();
    }
}
