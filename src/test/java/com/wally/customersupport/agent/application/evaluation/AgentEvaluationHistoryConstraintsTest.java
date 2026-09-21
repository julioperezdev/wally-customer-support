package com.wally.customersupport.agent.application.evaluation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import java.time.Instant;

import org.junit.jupiter.api.Test;

class AgentEvaluationHistoryConstraintsTest {

    @Test
    void rejectsAnUnboundedPageSizeAndAnInvalidDateRange() {
        assertThatThrownBy(() -> new AgentEvaluationHistoryPageRequest(0, 101))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("pageSize");

        Instant from = Instant.parse("2026-09-08T10:00:00Z");
        Instant to = from.minusSeconds(1);
        assertThatThrownBy(() -> new AgentEvaluationHistoryFilter(
                null, null, null, null, null, from, to))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("completedFrom");
    }

}
