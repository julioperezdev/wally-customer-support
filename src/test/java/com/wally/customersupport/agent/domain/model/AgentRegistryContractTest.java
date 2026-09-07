package com.wally.customersupport.agent.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.Set;

import org.junit.jupiter.api.Test;

class AgentRegistryContractTest {

    private static final Instant CREATED_AT = Instant.parse("2026-09-07T12:00:00Z");
    private static final Instant TRANSITION_AT = Instant.parse("2026-09-07T12:05:00Z");

    @Test
    void progressesOnlyThroughTheApprovedLifecycle() {
        AgentLifecyclePolicy policy = new AgentLifecyclePolicy();
        AgentVersion version = version(AgentLifecycleState.DRAFT);

        version = policy.transition(version, AgentLifecycleState.CANDIDATE, "author", TRANSITION_AT);
        version = policy.transition(version, AgentLifecycleState.EVALUATED, "evaluator", TRANSITION_AT);
        version = policy.transition(version, AgentLifecycleState.APPROVED, "reviewer", TRANSITION_AT);
        version = policy.transition(version, AgentLifecycleState.ACTIVE, "operator", TRANSITION_AT);

        assertThat(version.state()).isEqualTo(AgentLifecycleState.ACTIVE);
        assertThat(version.approvedBy()).isEqualTo("reviewer");
        assertThat(version.approvedAt()).isEqualTo(TRANSITION_AT);
    }

    @Test
    void rejectsSkippingEvaluationAndApproval() {
        AgentLifecyclePolicy policy = new AgentLifecyclePolicy();

        assertThatThrownBy(() -> policy.transition(
                version(AgentLifecycleState.DRAFT),
                AgentLifecycleState.ACTIVE,
                "operator",
                TRANSITION_AT))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("DRAFT to ACTIVE");
    }

    @Test
    void activationOnlyAcceptsApprovedVersionsAndCapturesPreviousVersion() {
        AgentActivationPolicy policy = new AgentActivationPolicy();
        AgentActivationRequest request = new AgentActivationRequest(
                "prod", "telegram", "catalog-search", "initial rollout", 100, true);

        assertThatThrownBy(() -> policy.activate(
                version(AgentLifecycleState.CANDIDATE), request, 1, "operator", TRANSITION_AT))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("only approved");

        AgentActivation activation = policy.activate(
                version(AgentLifecycleState.APPROVED), request, 1, "operator", TRANSITION_AT);

        assertThat(activation.agentId()).isEqualTo("catalog-specialist");
        assertThat(activation.agentVersion()).isEqualTo(2);
        assertThat(activation.previousVersion()).isEqualTo(1);
        assertThat(activation.reason()).isEqualTo("initial rollout");
        assertThat(activation.killSwitch()).isFalse();
    }

    @Test
    void killSwitchStopsAnActivationWithoutChangingItsVersionReference() {
        AgentActivation activation = new AgentActivationPolicy().activate(
                version(AgentLifecycleState.APPROVED),
                new AgentActivationRequest(
                        "prod", "whatsapp", "catalog-search", "initial rollout", 100, true),
                1,
                "operator",
                TRANSITION_AT);

        AgentActivation stopped = activation.killSwitch("on-call", TRANSITION_AT.plusSeconds(30));

        assertThat(stopped.agentVersion()).isEqualTo(activation.agentVersion());
        assertThat(stopped.enabled()).isFalse();
        assertThat(stopped.killSwitch()).isTrue();
        assertThat(stopped.rolloutPercentage()).isZero();
    }

    @Test
    void rollbackRestoresThePreviouslyActiveApprovedVersion() {
        AgentActivation activation = new AgentActivationPolicy().activate(
                version(AgentLifecycleState.APPROVED),
                new AgentActivationRequest(
                        "prod", "telegram", "catalog-search", "initial rollout", 100, true),
                1,
                "operator",
                TRANSITION_AT);

        AgentActivation rollback = new AgentActivationPolicy().rollback(
                activation,
                version(AgentLifecycleState.ACTIVE, 1),
                "on-call",
                TRANSITION_AT.plusSeconds(60));

        assertThat(rollback.agentVersion()).isEqualTo(1);
        assertThat(rollback.previousVersion()).isEqualTo(2);
        assertThat(rollback.enabled()).isTrue();
        assertThat(rollback.killSwitch()).isFalse();
    }

    @Test
    void validatesOperationalBoundsAndKeepsCollectionsImmutable() {
        AgentVersion version = version(AgentLifecycleState.DRAFT);

        assertThatThrownBy(() -> new AgentVersion(
                "catalog-specialist", 1, "Catalog", "Catalog search", AgentLifecycleState.DRAFT,
                "bedrock", "model", AgentInferenceParameters.deterministic(), "v1", "not-a-hash",
                "v1", "v1", Set.of(), Set.of(), "none", "grounded", Duration.ofSeconds(5),
                1, 1, 1, BigDecimal.ZERO, null, "eval-v1", "author", CREATED_AT, null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("SHA-256");

        assertThatThrownBy(() -> new AgentVersion(
                "catalog-specialist", 1, "Catalog", "Catalog search", AgentLifecycleState.DRAFT,
                "bedrock", "model", AgentInferenceParameters.deterministic(), "v1", hash(),
                "v1", "v1", Set.of(), Set.of(), "none", "grounded", Duration.ofSeconds(5),
                4, 1, 1, BigDecimal.ZERO, null, "eval-v1", "author", CREATED_AT, null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("maxSteps");

        assertThatThrownBy(() -> version.allowedTools().add("unsafe.tool"))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> version.knowledgeSources().add("unsafe.source"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    private static AgentVersion version(AgentLifecycleState state) {
        return version(state, 2);
    }

    private static AgentVersion version(AgentLifecycleState state, int version) {
        boolean approved = state == AgentLifecycleState.APPROVED
                || state == AgentLifecycleState.ACTIVE;
        return new AgentVersion(
                "catalog-specialist",
                version,
                "Catalog specialist",
                "Search products using deterministic catalog tools",
                state,
                "bedrock",
                "openai.gpt-oss-20b-1:0",
                AgentInferenceParameters.deterministic(),
                "system-v1",
                hash(),
                "catalog-input-v1",
                "catalog-output-v1",
                Set.of("catalog.search"),
                Set.of(),
                "conversation-summary-v1",
                "grounded-customer-support-v1",
                Duration.ofSeconds(10),
                2,
                2_000,
                1_000,
                BigDecimal.valueOf(0.05),
                "safe-fallback",
                "catalog-eval-v1",
                "author",
                CREATED_AT,
                approved ? "reviewer" : null,
                approved ? TRANSITION_AT : null);
    }

    private static String hash() {
        return "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";
    }
}
