package com.wally.customersupport.agent.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.wally.customersupport.agent.application.evaluation.AgentEvaluationHistoryFilter;
import com.wally.customersupport.agent.application.evaluation.AgentEvaluationHistoryPage;
import com.wally.customersupport.agent.application.evaluation.AgentEvaluationHistoryPageRequest;
import com.wally.customersupport.agent.application.evaluation.AgentEvaluationRunSummary;
import com.wally.customersupport.agent.application.port.out.AgentEvaluationRunRepository;
import org.junit.jupiter.api.Test;

class AgentEvaluationHistoryQueryServiceTest {

    @Test
    void delegatesBoundedSearchToTheRepository() {
        AgentEvaluationRunRepository repository = mock(AgentEvaluationRunRepository.class);
        AgentEvaluationHistoryQueryService service = new AgentEvaluationHistoryQueryService(repository);
        AgentEvaluationHistoryFilter filter = new AgentEvaluationHistoryFilter(
                "catalog-response-v1", "catalog-specialist", "v1", "mock", "deterministic-v1", null, null);
        AgentEvaluationHistoryPageRequest pageRequest = new AgentEvaluationHistoryPageRequest(1, 10);
        AgentEvaluationRunSummary summary = summary();
        AgentEvaluationHistoryPage expected = new AgentEvaluationHistoryPage(List.of(summary), 1, 10, 11, 2);
        when(repository.search(filter, pageRequest)).thenReturn(expected);

        AgentEvaluationHistoryPage actual = service.search(filter, pageRequest);

        assertThat(actual).isSameAs(expected);
        verify(repository).search(filter, pageRequest);
    }

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

    private static AgentEvaluationRunSummary summary() {
        return new AgentEvaluationRunSummary(
                UUID.randomUUID(),
                "catalog-response-v1",
                "catalog-specialist",
                "v1",
                "mock",
                "deterministic-v1",
                Instant.parse("2026-09-08T10:00:00Z"),
                Instant.parse("2026-09-08T10:00:01Z"),
                1,
                1,
                1,
                0,
                1,
                1,
                Map.of());
    }
}
