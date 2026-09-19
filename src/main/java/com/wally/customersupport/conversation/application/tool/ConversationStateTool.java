package com.wally.customersupport.conversation.application.tool;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;

import com.wally.customersupport.conversation.application.service.ConversationSelectionStateService;
import com.wally.customersupport.conversation.domain.model.ConversationState;
import com.wally.customersupport.shared.infrastructure.observability.StructuredEventLog;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/** Typed, persistence-neutral adapter for bounded conversation selection state. */
@Component
@Slf4j
public final class ConversationStateTool implements WcsTool<ConversationStateTool.Input, ConversationStateTool.Result> {

    public static final String NAME = WcsToolContractCatalog.CONVERSATION_STATE;
    public static final WcsToolDescriptor DESCRIPTOR = WcsToolContractCatalog.find(NAME).orElseThrow();

    private final ConversationSelectionStateService stateService;
    private final Clock clock;

    public ConversationStateTool(ConversationSelectionStateService stateService, Clock clock) {
        this.stateService = Objects.requireNonNull(stateService, "stateService");
        this.clock = Objects.requireNonNull(clock, "clock");
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
        Instant now = clock.instant();
        ConversationState next = switch (input.operation()) {
            case READ -> stateService.read(input.current());
            case MERGE_SELECTION -> stateService.mergeSelection(
                    input.current(), input.selectionField(), input.selectionValue(), now);
            case RESET -> stateService.reset(input.current(), now);
        };
        Status status = switch (input.operation()) {
            case READ -> Status.READ;
            case MERGE_SELECTION -> Status.UPDATED;
            case RESET -> Status.RESET;
        };
        Result result = new Result(status, next.version(), next.selection().catalogQuery().presentFieldCount(), next);
        StructuredEventLog.info(log, "WCS_TOOL_EXECUTED", java.util.Map.of(
                "tool", NAME,
                "operation", input.operation().name(),
                "status", status.name(),
                "selectionFieldCount", result.selectionFieldCount()));
        return result;
    }

    public record Input(
            ConversationState current,
            Operation operation,
            String selectionField,
            String selectionValue) {

        public Input {
            current = Objects.requireNonNull(current, "current");
            operation = Objects.requireNonNull(operation, "operation");
            if (operation == Operation.MERGE_SELECTION
                    && (selectionField == null || selectionField.isBlank()
                    || selectionValue == null || selectionValue.isBlank())) {
                throw new IllegalArgumentException("merge selection requires field and value");
            }
            selectionField = normalize(selectionField);
            selectionValue = normalize(selectionValue);
        }
    }

    public enum Operation {
        READ,
        MERGE_SELECTION,
        RESET
    }

    public enum Status {
        READ,
        UPDATED,
        RESET
    }

    public record Result(
            Status status,
            long stateVersion,
            int selectionFieldCount,
            ConversationState nextState) {

        public Result {
            status = Objects.requireNonNull(status, "status");
            nextState = Objects.requireNonNull(nextState, "nextState");
            if (stateVersion < 0 || selectionFieldCount < 0) {
                throw new IllegalArgumentException("state metrics must not be negative");
            }
        }
    }

    private static String normalize(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}
