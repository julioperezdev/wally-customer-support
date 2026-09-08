package com.wally.customersupport.shared.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;

import org.junit.jupiter.api.Test;

class AgentRuntimePropertiesTest {

    @Test
    void allowsOnlyConfiguredShadowEnvironments() {
        var properties = properties("prod", " test, STAGING ", 25);

        assertThat(properties.shadowEnvironmentAllowed()).isFalse();
        assertThat(properties.effectiveShadowAllowedEnvironments())
                .containsExactlyInAnyOrder("test", "staging");
        assertThat(properties.effectiveShadowTrafficPercentage()).isEqualTo(25);
    }

    @Test
    void rejectsInvalidTrafficPercentageSafely() {
        var properties = properties("test", "test", 101);

        assertThat(properties.shadowEnvironmentAllowed()).isTrue();
        assertThat(properties.effectiveShadowTrafficPercentage()).isZero();
    }

    @Test
    void defaultsToTestAndZeroTraffic() {
        var properties = properties("prod", null, null);

        assertThat(properties.shadowEnvironmentAllowed()).isFalse();
        assertThat(properties.effectiveShadowTrafficPercentage()).isZero();
    }

    private static AgentRuntimeProperties properties(
            String environment,
            String allowedEnvironments,
            Integer trafficPercentage) {
        return new AgentRuntimeProperties(
                true,
                environment,
                true,
                Duration.ofSeconds(5),
                "bedrock",
                allowedEnvironments,
                trafficPercentage);
    }
}
