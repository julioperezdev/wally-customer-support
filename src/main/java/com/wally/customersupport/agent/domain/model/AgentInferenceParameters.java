package com.wally.customersupport.agent.domain.model;

import java.math.BigDecimal;
import java.util.Objects;

public record AgentInferenceParameters(
        BigDecimal temperature,
        BigDecimal topP) {

    public AgentInferenceParameters {
        temperature = Objects.requireNonNull(temperature, "temperature");
        topP = Objects.requireNonNull(topP, "topP");

        if (temperature.signum() < 0 || temperature.compareTo(BigDecimal.valueOf(2)) > 0) {
            throw new IllegalArgumentException("temperature must be between 0 and 2");
        }
        if (topP.signum() <= 0 || topP.compareTo(BigDecimal.ONE) > 0) {
            throw new IllegalArgumentException("topP must be greater than 0 and at most 1");
        }
    }

    public static AgentInferenceParameters deterministic() {
        return new AgentInferenceParameters(BigDecimal.ZERO, BigDecimal.ONE);
    }
}
