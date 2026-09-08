package com.wally.customersupport.agent.application.service;

import com.wally.customersupport.agent.application.shadow.AgentShadowQualityGate;
import com.wally.customersupport.agent.application.shadow.AgentShadowQualityMetrics;
import com.wally.customersupport.agent.application.shadow.AgentShadowQualityScorecard;
import com.wally.customersupport.shared.infrastructure.config.AgentShadowQualityProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** Application facade for the review-only shadow quality gate. */
@Service
@RequiredArgsConstructor
public class AgentShadowQualityGateService {

    private final AgentShadowQualityProperties properties;
    private final AgentShadowQualityGate gate = new AgentShadowQualityGate();

    public AgentShadowQualityScorecard evaluate(AgentShadowQualityMetrics metrics) {
        return gate.evaluate(metrics, properties.effectivePolicy());
    }
}
