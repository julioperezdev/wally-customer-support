package com.wally.customersupport.conversation.application.tool;

import java.util.Objects;

import com.wally.customersupport.conversation.application.service.HumanFollowUpTaskService;
import com.wally.customersupport.conversation.domain.model.Conversation;
import com.wally.customersupport.conversation.domain.model.HumanFollowUpPriority;
import com.wally.customersupport.conversation.domain.model.Message;
import com.wally.customersupport.shared.infrastructure.observability.StructuredEventLog;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/** Idempotent handoff adapter with sanitized context metadata. */
@Component
@Slf4j
public final class HumanHandoffTool implements WcsTool<HumanHandoffTool.Input, HumanHandoffTool.Result> {

    public static final String NAME = WcsToolContractCatalog.HUMAN_HANDOFF;
    public static final WcsToolDescriptor DESCRIPTOR = WcsToolContractCatalog.find(NAME).orElseThrow();

    private final HumanFollowUpTaskService taskService;

    public HumanHandoffTool(HumanFollowUpTaskService taskService) {
        this.taskService = Objects.requireNonNull(taskService, "taskService");
    }

    @Override
    public WcsToolDescriptor descriptor() {
        return DESCRIPTOR;
    }

    @Override
    public Class<Input> inputType() {
        return Input.class;
    }

    @Override
    public Result execute(Input input) {
        Objects.requireNonNull(input, "input");
        var creation = taskService.createExplicitResult(
                input.conversation(),
                input.sourceMessage(),
                input.reason().name(),
                input.priority());
        Result result = new Result(
                creation.created() ? Status.CREATED : Status.REUSED,
                creation.task().priority(),
                input.contextAvailable());
        StructuredEventLog.info(log, "WCS_TOOL_EXECUTED", java.util.Map.of(
                "tool", NAME,
                "status", result.status().name(),
                "priority", result.priority().name(),
                "contextIncluded", result.contextIncluded()));
        return result;
    }

    public record Input(
            Conversation conversation,
            Message sourceMessage,
            Reason reason,
            HumanFollowUpPriority priority,
            boolean contextAvailable) {

        public Input {
            conversation = Objects.requireNonNull(conversation, "conversation");
            sourceMessage = Objects.requireNonNull(sourceMessage, "sourceMessage");
            reason = Objects.requireNonNull(reason, "reason");
            priority = Objects.requireNonNull(priority, "priority");
        }
    }

    public enum Reason {
        CUSTOMER_REQUEST,
        LOW_CONFIDENCE,
        PAYMENT_ISSUE,
        OTHER
    }

    public enum Status {
        CREATED,
        REUSED,
        REJECTED
    }

    public record Result(Status status, HumanFollowUpPriority priority, boolean contextIncluded) {

        public Result {
            status = Objects.requireNonNull(status, "status");
            priority = Objects.requireNonNull(priority, "priority");
        }
    }
}
