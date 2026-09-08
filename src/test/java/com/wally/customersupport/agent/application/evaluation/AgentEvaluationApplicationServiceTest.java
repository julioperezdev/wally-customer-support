package com.wally.customersupport.agent.application.evaluation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.wally.customersupport.agent.application.port.out.AgentEvaluationRunRepository;
import com.wally.customersupport.agent.domain.model.AgentEvaluationExecution;
import com.wally.customersupport.agent.domain.model.AgentEvaluationExecutionMetadata;
import com.wally.customersupport.agent.domain.model.AgentEvaluationScenario;
import com.wally.customersupport.conversation.application.service.DeterministicResponseHumanizer;
import org.junit.jupiter.api.Test;

class AgentEvaluationApplicationServiceTest {

    private static final Instant NOW = Instant.parse("2026-09-07T23:00:00Z");

    private final DeterministicResponseHumanizer humanizer = new DeterministicResponseHumanizer();
    private final AgentEvaluationApplicationService service = new AgentEvaluationApplicationService(
            new AgentEvaluationDatasetCatalog(List.of(new CatalogResponseEvaluationDatasetProvider())),
            new AgentEvaluationRunner(new ResponsePolicyEvaluator()),
            new PassthroughAgentEvaluationRunRepository(),
            Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void executesKnownDatasetAndReturnsSanitizedRunMetrics() {
        AgentEvaluationRun run = service.execute(request(), this::executeScenario);

        assertThat(run.runId()).isNotNull();
        assertThat(run.datasetVersion()).isEqualTo(CatalogResponseEvaluationDataset.VERSION);
        assertThat(run.agentId()).isEqualTo("catalog-specialist");
        assertThat(run.agentVersion()).isEqualTo("v1");
        assertThat(run.provider()).isEqualTo("mock");
        assertThat(run.modelId()).isEqualTo("deterministic-v1");
        assertThat(run.startedAt()).isEqualTo(NOW);
        assertThat(run.completedAt()).isEqualTo(NOW);
        assertThat(run.durationMs()).isZero();
        assertThat(run.suiteResult().passRate()).isEqualTo(1.0);
        assertThat(run.toString()).doesNotContain("Remera NullPointer");
    }

    @Test
    void rejectsUnknownDatasetWithoutRunningExecutor() {
        assertThatThrownBy(() -> service.execute(
                new AgentEvaluationRunRequest("catalog-response-v9", "agent", "v1", "mock", "model"),
                scenario -> {
                    throw new AssertionError("executor must not run");
                }))
                .isInstanceOf(UnknownAgentEvaluationDatasetException.class)
                .hasMessage("evaluation dataset is not registered: catalog-response-v9");
    }

    @Test
    void propagatesExecutorFailureWithoutChangingTheApplicationContract() {
        assertThatThrownBy(() -> service.execute(request(), scenario -> {
            throw new IllegalStateException("evaluation failed");
        }))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("evaluation failed");
    }

    private AgentEvaluationRunRequest request() {
        return new AgentEvaluationRunRequest(
                CatalogResponseEvaluationDataset.VERSION,
                "catalog-specialist",
                "v1",
                "mock",
                "deterministic-v1");
    }

    private AgentEvaluationExecution executeScenario(AgentEvaluationScenario scenario) {
        return new AgentEvaluationExecution(
                humanizer.humanize(scenario.request()),
                new AgentEvaluationExecutionMetadata(
                        "catalog-specialist",
                        "v1",
                        "mock",
                        "deterministic-v1",
                        0,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null));
    }

    private static final class PassthroughAgentEvaluationRunRepository
            implements AgentEvaluationRunRepository {

        @Override
        public AgentEvaluationRun save(AgentEvaluationRun run) {
            return run;
        }

        @Override
        public Optional<AgentEvaluationRun> findById(UUID runId) {
            return Optional.empty();
        }
    }
}
