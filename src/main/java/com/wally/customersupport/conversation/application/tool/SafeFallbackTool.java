package com.wally.customersupport.conversation.application.tool;

import java.util.Objects;

import com.wally.customersupport.shared.infrastructure.observability.StructuredEventLog;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/** Deterministic safety boundary for invalid, uncertain, or unavailable work. */
@Component
@Slf4j
public final class SafeFallbackTool implements WcsTool<SafeFallbackTool.Input, SafeFallbackTool.Result> {

    public static final String NAME = WcsToolContractCatalog.SAFE_FALLBACK;
    public static final WcsToolDescriptor DESCRIPTOR = WcsToolContractCatalog.find(NAME).orElseThrow();

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
        boolean handoffSuggested = switch (input.reason()) {
            case LOW_CONFIDENCE, TOOL_UNAVAILABLE, EXECUTION_FAILED -> true;
            case UNKNOWN_INTENT, INVALID_TOOL_INPUT -> false;
        };
        Result result = new Result(Status.FALLBACK, handoffSuggested);
        StructuredEventLog.info(log, "WCS_TOOL_EXECUTED", java.util.Map.of(
                "tool", NAME,
                "reason", input.reason().name(),
                "handoffSuggested", handoffSuggested));
        return result;
    }

    public record Input(Reason reason) {

        public Input {
            reason = Objects.requireNonNull(reason, "reason");
        }
    }

    public enum Reason {
        UNKNOWN_INTENT,
        LOW_CONFIDENCE,
        INVALID_TOOL_INPUT,
        TOOL_UNAVAILABLE,
        EXECUTION_FAILED
    }

    public record Result(Status status, boolean handoffSuggested) {

        public Result {
            status = Objects.requireNonNull(status, "status");
        }
    }

    public enum Status {
        FALLBACK
    }
}
