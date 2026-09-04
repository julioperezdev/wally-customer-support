package com.wally.customersupport.infrastructure.observability;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Calculates an estimated USD cost from the token counts returned by a provider. */
public final class AiPricingCalculator {

    private static final BigDecimal TOKENS_PER_MILLION = BigDecimal.valueOf(1_000_000L);
    private static final int COST_SCALE = 12;

    private AiPricingCalculator() {
    }

    public static BigDecimal estimatedCostUsd(
            int inputTokens,
            int outputTokens,
            BigDecimal inputPriceUsdPerMillion,
            BigDecimal outputPriceUsdPerMillion) {
        BigDecimal inputCost = BigDecimal.valueOf(Math.max(0, inputTokens))
                .multiply(nonNegative(inputPriceUsdPerMillion));
        BigDecimal outputCost = BigDecimal.valueOf(Math.max(0, outputTokens))
                .multiply(nonNegative(outputPriceUsdPerMillion));
        return inputCost.add(outputCost)
                .divide(TOKENS_PER_MILLION, COST_SCALE, RoundingMode.HALF_UP);
    }

    private static BigDecimal nonNegative(BigDecimal value) {
        return value == null || value.signum() < 0 ? BigDecimal.ZERO : value;
    }
}
