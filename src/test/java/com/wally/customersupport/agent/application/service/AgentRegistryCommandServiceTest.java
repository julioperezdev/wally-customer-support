package com.wally.customersupport.agent.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.Set;

import com.wally.customersupport.agent.application.port.out.AgentRegistryCommandGuard;
import com.wally.customersupport.agent.application.port.out.AgentRegistryRepository;
import com.wally.customersupport.agent.application.evaluation.AgentEvaluationControlPlaneAccessDecision;
import com.wally.customersupport.agent.application.evaluation.AgentEvaluationControlPlaneAccessReason;
import com.wally.customersupport.agent.application.evaluation.AgentEvaluationControlPlaneAccessStatus;
import com.wally.customersupport.agent.application.registry.AgentLifecycleTransitionCommand;
import com.wally.customersupport.agent.application.registry.AgentRegistryMutationReason;
import com.wally.customersupport.agent.application.registry.AgentRegistryMutationStatus;
import com.wally.customersupport.agent.application.registry.AgentVersionDraftCommand;
import com.wally.customersupport.agent.domain.model.AgentLifecyclePolicy;
import com.wally.customersupport.agent.domain.model.AgentLifecycleState;
import com.wally.customersupport.agent.domain.model.AgentVersion;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AgentRegistryCommandServiceTest {

    private static final String ACTOR = "agent-operator";
    private static final String IDEMPOTENCY_KEY = "authoring-001";
    private static final Instant CREATED_AT = Instant.parse("2026-09-13T03:00:00Z");
    private static final String PROMPT_HASH = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";

    @Mock
    private AgentEvaluationControlPlaneAccessService accessService;

    @Mock
    private AgentRegistryRepository registry;

    @Mock
    private AgentRegistryCommandGuard guard;

    private AgentRegistryCommandService service;

    @BeforeEach
    void setUp() {
        service = new AgentRegistryCommandService(
                accessService, registry, guard, new AgentLifecyclePolicy(), true);
    }

    @Test
    void createsAnImmutableDraftWithTheNextVersionWhenVersionIsOmitted() {
        when(accessService.authorizeRegistryWrite(ACTOR)).thenReturn(authorized());
        when(registry.findVersions("support-specialist")).thenReturn(java.util.List.of());
        when(registry.findVersion("support-specialist", 1)).thenReturn(java.util.Optional.empty());
        when(guard.tryAcquire("create_draft:1:support-specialist:" + IDEMPOTENCY_KEY)).thenReturn(true);
        when(registry.saveVersion(any())).thenAnswer(invocation -> invocation.getArgument(0));

        var result = service.createDraft(draftCommand(), ACTOR, IDEMPOTENCY_KEY);

        assertThat(result.status()).isEqualTo(AgentRegistryMutationStatus.CREATED);
        assertThat(result.reason()).isEqualTo(AgentRegistryMutationReason.DRAFT_PERSISTED);
        assertThat(result.agentId()).isEqualTo("support-specialist");
        assertThat(result.version()).isEqualTo(1);
        assertThat(result.state()).isEqualTo(AgentLifecycleState.DRAFT);
        verify(registry).saveVersion(any(AgentVersion.class));
    }

    @Test
    void keepsAuthoringClosedWithoutReadingTheRegistry() {
        AgentRegistryCommandService closed = new AgentRegistryCommandService(
                accessService, registry, guard, new AgentLifecyclePolicy(), false);

        var result = closed.createDraft(draftCommand(), ACTOR, IDEMPOTENCY_KEY);

        assertThat(result.status()).isEqualTo(AgentRegistryMutationStatus.DISABLED);
        assertThat(result.reason()).isEqualTo(AgentRegistryMutationReason.FEATURE_DISABLED);
        verifyNoInteractions(accessService, registry, guard);
    }

    @Test
    void transitionsLifecycleOnlyWhenTheCurrentStateMatchesTheDomainPolicy() {
        AgentVersion current = draft(AgentLifecycleState.CANDIDATE);
        AgentVersion transitioned = new AgentLifecyclePolicy().transition(
                current, AgentLifecycleState.EVALUATED, ACTOR, CREATED_AT);
        when(accessService.authorizeRegistryWrite(ACTOR)).thenReturn(authorized());
        when(registry.findVersion("support-specialist", 1)).thenReturn(java.util.Optional.of(current));
        when(guard.tryAcquire("transition_lifecycle:1:support-specialist:" + IDEMPOTENCY_KEY)).thenReturn(true);
        when(registry.updateLifecycle(
                "support-specialist", 1, AgentLifecycleState.CANDIDATE,
                AgentLifecycleState.EVALUATED, null, null)).thenReturn(transitioned);

        var result = service.transition(
                new AgentLifecycleTransitionCommand(
                        "support-specialist", 1, AgentLifecycleState.EVALUATED,
                        "evaluation passed", null, null),
                ACTOR,
                IDEMPOTENCY_KEY);

        assertThat(result.status()).isEqualTo(AgentRegistryMutationStatus.TRANSITIONED);
        assertThat(result.state()).isEqualTo(AgentLifecycleState.EVALUATED);
        verify(registry).updateLifecycle(
                "support-specialist", 1, AgentLifecycleState.CANDIDATE,
                AgentLifecycleState.EVALUATED, null, null);
    }

    @Test
    void requiresTwoApprovalReferencesBeforeApproving() {
        when(accessService.authorizeRegistryWrite(ACTOR)).thenReturn(authorized());

        var result = service.transition(
                new AgentLifecycleTransitionCommand(
                        "support-specialist", 1, AgentLifecycleState.APPROVED,
                        "approve candidate", null, null),
                ACTOR,
                IDEMPOTENCY_KEY);

        assertThat(result.status()).isEqualTo(AgentRegistryMutationStatus.INVALID);
        assertThat(result.reason()).isEqualTo(AgentRegistryMutationReason.APPROVAL_REFERENCES_REQUIRED);
        verify(registry, never()).findVersion(any(), any(Integer.class));
        verifyNoInteractions(guard);
    }

    private static AgentVersionDraftCommand draftCommand() {
        return new AgentVersionDraftCommand(
                "support-specialist",
                null,
                "Support specialist",
                "Answers grounded support questions",
                "bedrock",
                "openai.gpt-oss-20b-1:0",
                BigDecimal.ZERO,
                BigDecimal.ONE,
                "system-v1",
                PROMPT_HASH,
                "support-input-v1",
                "support-output-v1",
                Set.of("support.lookup"),
                Set.of("wcs-support-kb"),
                "conversation-summary-v1",
                "grounded-customer-support-v1",
                10_000L,
                2,
                2_000,
                1_000,
                new BigDecimal("0.050000"),
                null,
                "support-eval-v1");
    }

    private static AgentVersion draft(AgentLifecycleState state) {
        AgentVersion version = AgentVersion.draft(
                "support-specialist", 1, "Support specialist", "Answers support questions",
                "bedrock", "openai.gpt-oss-20b-1:0", new com.wally.customersupport.agent.domain.model.AgentInferenceParameters(
                        BigDecimal.ZERO, BigDecimal.ONE), "system-v1", PROMPT_HASH,
                "support-input-v1", "support-output-v1", Set.of("support.lookup"), Set.of("wcs-support-kb"),
                "conversation-summary-v1", "grounded-customer-support-v1", Duration.ofSeconds(10), 2,
                2_000, 1_000, new BigDecimal("0.050000"), null, "support-eval-v1", ACTOR, CREATED_AT);
        return state == AgentLifecycleState.DRAFT
                ? version
                : new AgentLifecyclePolicy().transition(version, state, ACTOR, CREATED_AT);
    }

    private static AgentEvaluationControlPlaneAccessDecision authorized() {
        return new AgentEvaluationControlPlaneAccessDecision(
                AgentEvaluationControlPlaneAccessStatus.AUTHORIZED,
                ACTOR,
                "prod",
                AgentEvaluationControlPlaneAccessService.REGISTRY_WRITE_CAPABILITY,
                AgentEvaluationControlPlaneAccessReason.AUTHORIZED);
    }
}
