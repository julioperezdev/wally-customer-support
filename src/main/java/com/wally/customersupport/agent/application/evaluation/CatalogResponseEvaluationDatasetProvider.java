package com.wally.customersupport.agent.application.evaluation;

import java.util.List;

import com.wally.customersupport.agent.domain.model.AgentEvaluationScenario;
import org.springframework.stereotype.Component;

/** Application provider for the first sanitized catalog evaluation dataset. */
@Component
public class CatalogResponseEvaluationDatasetProvider implements AgentEvaluationDataset {

    @Override
    public String version() {
        return CatalogResponseEvaluationDataset.VERSION;
    }

    @Override
    public List<AgentEvaluationScenario> scenarios() {
        return CatalogResponseEvaluationDataset.scenarios();
    }
}
