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

        Instant now = clock.instant();
        HumanFollowUpTask task = HumanFollowUpTask.open(
                conversation.id(),
                sourceMessage.id(),
                definition.reason(),
                definition.priority(),
                now.plus(DEFAULT_SLA),
                now);
        HumanFollowUpTask persisted = repository.saveIfAbsent(task);
        StructuredEventLog.info(log, "HUMAN_FOLLOW_UP_TASK_READY", java.util.Map.of(
                "operation", "conversation.human_follow_up.create",
                "result", persisted.id().equals(task.id()) ? "CREATED" : "ALREADY_EXISTS",
                "reason", persisted.reason(),
                "priority", persisted.priority().name(),
                "correlationId", conversation.id()));
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
