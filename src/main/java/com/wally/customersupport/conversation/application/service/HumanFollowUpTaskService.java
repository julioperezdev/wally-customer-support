package com.wally.customersupport.conversation.application.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

import com.wally.customersupport.conversation.application.port.out.HumanFollowUpTaskRepository;
import com.wally.customersupport.conversation.domain.model.Conversation;
import com.wally.customersupport.conversation.domain.model.ConversationExecutionResult;
import com.wally.customersupport.conversation.domain.model.HumanFollowUpPriority;
import com.wally.customersupport.conversation.domain.model.HumanFollowUpTask;
import com.wally.customersupport.conversation.domain.model.Message;
import com.wally.customersupport.shared.infrastructure.observability.StructuredEventLog;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class HumanFollowUpTaskService {

    private static final Duration DEFAULT_SLA = Duration.ofHours(24);

    private final HumanFollowUpTaskRepository repository;
    private final Clock clock;

    public void createIfRequired(
            Conversation conversation,
            Message sourceMessage,
            ConversationExecutionResult result) {
        if (conversation == null || sourceMessage == null || result == null) {
            return;
        }
        FollowUpDefinition definition = definitionFor(result.outcome());
        if (definition == null) {
            return;
        }

        createExplicitResult(conversation, sourceMessage, definition.reason(), definition.priority());
    }

    public HumanFollowUpTask createExplicit(
            Conversation conversation,
            Message sourceMessage,
            String reason,
            HumanFollowUpPriority priority) {
        return createExplicitResult(conversation, sourceMessage, reason, priority).task();
    }

    public CreationResult createExplicitResult(
            Conversation conversation,
            Message sourceMessage,
            String reason,
            HumanFollowUpPriority priority) {
        if (conversation == null || sourceMessage == null) {
            throw new IllegalArgumentException("conversation and sourceMessage are required");
        }
        if (reason == null || reason.isBlank() || priority == null) {
            throw new IllegalArgumentException("reason and priority are required");
        }

        Instant now = clock.instant();
        HumanFollowUpTask task = HumanFollowUpTask.open(
                conversation.id(),
                sourceMessage.id(),
                reason,
                priority,
                now.plus(DEFAULT_SLA),
                now);
        HumanFollowUpTask persisted = repository.saveIfAbsent(task);
        boolean created = persisted.id().equals(task.id());
        StructuredEventLog.info(log, "HUMAN_FOLLOW_UP_TASK_READY", java.util.Map.of(
                "operation", "conversation.human_follow_up.create",
                "result", created ? "CREATED" : "ALREADY_EXISTS",
                "reason", persisted.reason(),
                "priority", persisted.priority().name(),
                "correlationId", conversation.id()));
        return new CreationResult(persisted, created);
    }

    public record CreationResult(HumanFollowUpTask task, boolean created) {
    }

    private static FollowUpDefinition definitionFor(String outcome) {
        return switch (outcome) {
            case "HANDOFF" -> new FollowUpDefinition("HUMAN_REQUEST", HumanFollowUpPriority.HIGH);
            case "LOW_CONFIDENCE" -> new FollowUpDefinition("LOW_CONFIDENCE", HumanFollowUpPriority.NORMAL);
            case "FALLBACK" -> new FollowUpDefinition("UNRESOLVED", HumanFollowUpPriority.NORMAL);
            default -> null;
        };
    }

    private record FollowUpDefinition(String reason, HumanFollowUpPriority priority) {
    }
}
