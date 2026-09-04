package com.wally.customersupport.infrastructure.observability;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

class AiPricingCalculatorTest {

    @Test
    void calculatesInputAndOutputCostPerMillionTokens() {
        BigDecimal cost = AiPricingCalculator.estimatedCostUsd(
                1_000,
                500,
                new BigDecimal("0.0721"),
                new BigDecimal("0.3090"));

        assertThat(cost).isEqualByComparingTo("0.0002266");
    }

    @Test
    void treatsMissingOrNegativePricesAsZero() {
        BigDecimal cost = AiPricingCalculator.estimatedCostUsd(
                100,
                50,
                null,
                new BigDecimal("-1"));

        assertThat(cost).isEqualByComparingTo("0");
    }
}
