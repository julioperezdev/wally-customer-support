package com.wally.customersupport.agent.application.shadow;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class AgentTrafficRoutingPolicyTest {

    private final AgentTrafficRoutingPolicy policy = new AgentTrafficRoutingPolicy();

    @Test
    void shadowNeverPublishesTheCandidateResponse() {
        AgentTrafficRoutingDecision decision = policy.decide(
                new AgentTrafficRoutingRequest(AgentTrafficMode.SHADOW, 100, "conversation-hash"));

        assertThat(decision.candidateSelected()).isTrue();
        assertThat(decision.candidateResponsePublished()).isFalse();
        assertThat(decision.fallbackToActive()).isTrue();
    }

    @Test
    void shadowWithZeroPercentAlwaysFallsBack() {
        AgentTrafficRoutingDecision decision = policy.decide(
                new AgentTrafficRoutingRequest(AgentTrafficMode.SHADOW, 0, "conversation-hash"));

        assertThat(decision.candidateSelected()).isFalse();
        assertThat(decision.candidateResponsePublished()).isFalse();
        assertThat(decision.fallbackToActive()).isTrue();
        assertThat(decision.reason()).isEqualTo("SHADOW_BUCKET_NOT_SELECTED");
    }

    @Test
    void canarySelectionIsStableForTheSamePseudonymizedConversation() {
        AgentTrafficRoutingRequest request =
                new AgentTrafficRoutingRequest(AgentTrafficMode.CANARY, 50, "conversation-hash");

        AgentTrafficRoutingDecision first = policy.decide(request);
        AgentTrafficRoutingDecision second = policy.decide(request);

        assertThat(second).isEqualTo(first);
        assertThat(first.candidateResponsePublished()).isEqualTo(first.candidateSelected());
    }

    @Test
    void canaryWithZeroPercentAlwaysFallsBack() {
        AgentTrafficRoutingDecision decision = policy.decide(
                new AgentTrafficRoutingRequest(AgentTrafficMode.CANARY, 0, "conversation-hash"));

        assertThat(decision.candidateSelected()).isFalse();
        assertThat(decision.candidateResponsePublished()).isFalse();
        assertThat(decision.fallbackToActive()).isTrue();
    }

    @Test
    void activeModeKeepsTheCurrentRuntimeAsSourceOfTruth() {
        AgentTrafficRoutingDecision decision = policy.decide(
                new AgentTrafficRoutingRequest(AgentTrafficMode.ACTIVE, 100, "conversation-hash"));

        assertThat(decision.candidateSelected()).isFalse();
        assertThat(decision.candidateResponsePublished()).isFalse();
        assertThat(decision.fallbackToActive()).isTrue();
    }
}
