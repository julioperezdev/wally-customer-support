package com.wally.customersupport.backoffice.application.service;

import java.util.List;
import java.util.Locale;

import com.wally.customersupport.backoffice.application.model.BackofficeHumanFollowUp;
import com.wally.customersupport.conversation.application.port.out.ConversationRepository;
import com.wally.customersupport.conversation.application.port.out.HumanFollowUpTaskRepository;
import com.wally.customersupport.conversation.application.port.out.MessageRepository;
import com.wally.customersupport.conversation.domain.model.HumanFollowUpPriority;
import com.wally.customersupport.conversation.domain.model.HumanFollowUpStatus;
import com.wally.customersupport.conversation.domain.model.HumanFollowUpTask;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class BackofficeHumanFollowUpQueryService {

    private static final int MAX_CONTEXT_MESSAGES = 3;
    private static final int MAX_CONTEXT_CHARACTERS = 180;

    private final HumanFollowUpTaskRepository followUpTaskRepository;
    private final ConversationRepository conversationRepository;
    private final MessageRepository messageRepository;

    @Transactional(readOnly = true)
    public List<BackofficeHumanFollowUp> findOpen(int limit) {
        return followUpTaskRepository.findOpen(Math.min(100, Math.max(1, limit))).stream()
                .map(this::toView)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<BackofficeHumanFollowUp> find(int limit, String status, String priority) {
        int boundedLimit = Math.min(100, Math.max(1, limit));
        List<HumanFollowUpStatus> statuses = statuses(status);
        HumanFollowUpPriority parsedPriority = priority == null || priority.isBlank()
                ? null
                : parsePriority(priority);
        return followUpTaskRepository.findOpen(boundedLimit, statuses.size() == 1 ? statuses.get(0) : null, parsedPriority).stream()
                .map(this::toView)
                .toList();
    }

    private static List<HumanFollowUpStatus> statuses(String status) {
        if (status == null || status.isBlank()) {
            return List.of(HumanFollowUpStatus.OPEN, HumanFollowUpStatus.IN_PROGRESS);
        }
        try {
            return List.of(HumanFollowUpStatus.valueOf(status.strip().toUpperCase(Locale.ROOT)));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("status is invalid", exception);
        }
    }

    private static HumanFollowUpPriority parsePriority(String priority) {
        try {
            return HumanFollowUpPriority.valueOf(priority.strip().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("priority is invalid", exception);
        }
    }

    private BackofficeHumanFollowUp toView(HumanFollowUpTask task) {
        String channel = conversationRepository.findById(task.conversationId())
                .map(conversation -> conversation.channel().name())
                .orElse("UNKNOWN");
        List<String> context = messageRepository.findRecentBodies(task.conversationId(), MAX_CONTEXT_MESSAGES).stream()
                .map(BackofficeHumanFollowUpQueryService::sanitize)
                .toList();
        return new BackofficeHumanFollowUp(
                task.id(),
                task.conversationId(),
                channel,
                task.reason(),
                task.priority().name(),
                task.status().name(),
                task.dueAt(),
                task.assignedTo(),
                context);
    }

    private static String sanitize(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        String sanitized = value
                .replaceAll("(?i)\\b[\\w.%+-]+@[\\w.-]+\\.[a-z]{2,}\\b", "[EMAIL_REDACTED]")
                .replaceAll("(?<!\\w)\\+?[0-9][0-9 .()\\-]{6,}[0-9](?!\\w)", "[CONTACT_REDACTED]")
                .strip();
        return sanitized.length() <= MAX_CONTEXT_CHARACTERS
                ? sanitized
                : sanitized.substring(0, MAX_CONTEXT_CHARACTERS) + "…";
    }
}
