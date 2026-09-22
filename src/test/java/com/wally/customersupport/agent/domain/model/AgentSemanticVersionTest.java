package com.wally.customersupport.agent.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class AgentSemanticVersionTest {

    @Test
    void ordersPatchMinorAndMajorReleasesAndIncrementsPatch() {
        assertThat(AgentSemanticVersion.parse("1.0.1"))
                .isGreaterThan(AgentSemanticVersion.parse("1.0.0"));
        assertThat(AgentSemanticVersion.parse("1.1.0"))
                .isGreaterThan(AgentSemanticVersion.parse("1.0.99"));
        assertThat(AgentSemanticVersion.parse("2.0.0"))
                .isGreaterThan(AgentSemanticVersion.parse("1.99.99"));
        assertThat(AgentSemanticVersion.parse("1.4.9").nextPatch().toString()).isEqualTo("1.4.10");
    }

    @Test
    void rejectsMalformedOrAmbiguousVersions() {
        assertThatThrownBy(() -> AgentSemanticVersion.parse("01.0.0"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> AgentSemanticVersion.parse("1.0"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> AgentSemanticVersion.parse("1.0.0-rc.1"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
