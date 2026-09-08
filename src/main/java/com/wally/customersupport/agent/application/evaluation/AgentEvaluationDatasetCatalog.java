package com.wally.customersupport.agent.application.evaluation;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.wally.customersupport.agent.domain.model.AgentEvaluationScenario;
import org.springframework.stereotype.Component;

/** Resolves immutable evaluation datasets by their explicit version. */
@Component
public class AgentEvaluationDatasetCatalog {

    private final Map<String, AgentEvaluationDataset> datasets;

    public AgentEvaluationDatasetCatalog(List<AgentEvaluationDataset> datasets) {
        Objects.requireNonNull(datasets, "datasets");
        this.datasets = datasets.stream()
                .map(dataset -> Objects.requireNonNull(dataset, "datasets must not contain null"))
                .collect(Collectors.toUnmodifiableMap(
                        AgentEvaluationDataset::version,
                        Function.identity(),
                        (left, right) -> {
                            throw new IllegalArgumentException("evaluation dataset versions must be unique");
                        }));
    }

    public List<AgentEvaluationScenario> scenarios(String version) {
        String normalizedVersion = required(version, "datasetVersion");
        AgentEvaluationDataset dataset = datasets.get(normalizedVersion);
        if (dataset == null) {
            throw new UnknownAgentEvaluationDatasetException(normalizedVersion);
        }
        return List.copyOf(dataset.scenarios());
    }

    private static String required(String value, String field) {
        String normalized = Objects.requireNonNull(value, field).strip();
        if (normalized.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return normalized;
    }
}
