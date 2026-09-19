package com.wally.customersupport.conversation.application.tool;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class SafeFallbackToolTest {

    @Test
    void suggestsHandoffOnlyForOperationalOrConfidenceFailures() {
        SafeFallbackTool tool = new SafeFallbackTool();

        assertThat(tool.execute(new SafeFallbackTool.Input(SafeFallbackTool.Reason.LOW_CONFIDENCE)))
                .isEqualTo(new SafeFallbackTool.Result(SafeFallbackTool.Status.FALLBACK, true));
        assertThat(tool.execute(new SafeFallbackTool.Input(SafeFallbackTool.Reason.TOOL_UNAVAILABLE)))
                .isEqualTo(new SafeFallbackTool.Result(SafeFallbackTool.Status.FALLBACK, true));
        assertThat(tool.execute(new SafeFallbackTool.Input(SafeFallbackTool.Reason.UNKNOWN_INTENT)))
                .isEqualTo(new SafeFallbackTool.Result(SafeFallbackTool.Status.FALLBACK, false));
    }
}
