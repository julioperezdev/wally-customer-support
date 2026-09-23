package com.wally.customersupport.agent.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.anyInt;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

import com.wally.customersupport.agent.application.port.out.AgentRegistryAuditRepository;
import com.wally.customersupport.agent.application.port.out.AgentRegistryCommandGuard;
import com.wally.customersupport.agent.application.port.out.AgentRegistryRepository;
import com.wally.customersupport.agent.application.evaluation.AgentEvaluationControlPlaneAccessDecision;
import com.wally.customersupport.agent.application.evaluation.AgentEvaluationControlPlaneAccessReason;
import com.wally.customersupport.agent.application.evaluation.AgentEvaluationControlPlaneAccessStatus;
import com.wally.customersupport.agent.application.registry.AgentLifecycleTransitionCommand;
import com.wally.customersupport.agent.application.registry.AgentPromotionEvaluationEvidence;
import com.wally.customersupport.agent.application.registry.AgentRegistryMutationReason;
import com.wally.customersupport.agent.application.registry.AgentRegistryMutationStatus;
import com.wally.customersupport.agent.application.registry.AgentVersionDraftCommand;
import com.wally.customersupport.agent.domain.model.AgentLifecyclePolicy;
import com.wally.customersupport.agent.domain.model.AgentLifecycleState;
import com.wally.customersupport.agent.domain.model.AgentInvocationConfiguration;
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

    @Mock
    private AgentRegistryAuditRepository auditRepository;

    @Mock
    private AgentPromotionEvidenceResolver promotionEvidenceResolver;

    private AgentRegistryCommandService service;

    @BeforeEach
    void setUp() {
        service = new AgentRegistryCommandService(
                accessService, registry, guard, new AgentLifecyclePolicy(),
                auditRepository, promotionEvidenceResolver, true);
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
        org.mockito.ArgumentCaptor<AgentVersion> saved = org.mockito.ArgumentCaptor.forClass(AgentVersion.class);
        verify(registry).saveVersion(saved.capture());
        assertThat(saved.getValue().semanticVersion()).isEqualTo("1.0.0");
        assertThat(saved.getValue().invocationConfiguration().systemPrompt()).isEqualTo("System prompt v1");
        assertThat(saved.getValue().systemPromptHash())
                .isEqualTo(AgentInvocationConfiguration.sha256("System prompt v1"));
    }

    @Test
    void allowsAnExplicitMinorSemverForTheNextImmutableDraft() {
        when(accessService.authorizeRegistryWrite(ACTOR)).thenReturn(authorized());
        AgentVersion previous = draft(AgentLifecycleState.DRAFT);
        when(registry.findVersions("support-specialist")).thenReturn(java.util.List.of(previous));
        when(registry.findVersion("support-specialist", 2)).thenReturn(java.util.Optional.empty());
        when(guard.tryAcquire("create_draft:2:support-specialist:" + IDEMPOTENCY_KEY)).thenReturn(true);
        when(registry.saveVersion(any())).thenAnswer(invocation -> invocation.getArgument(0));

        var result = service.createDraft(draftCommand("1.1.0"), ACTOR, IDEMPOTENCY_KEY);

        assertThat(result.status()).isEqualTo(AgentRegistryMutationStatus.CREATED);
        org.mockito.ArgumentCaptor<AgentVersion> saved = org.mockito.ArgumentCaptor.forClass(AgentVersion.class);
        verify(registry).saveVersion(saved.capture());
        assertThat(saved.getValue().semanticVersion()).isEqualTo("1.1.0");
    }

    @Test
    void rejectsARepeatedSemverWithoutPersistingAnotherDraft() {
        when(accessService.authorizeRegistryWrite(ACTOR)).thenReturn(authorized());
        when(registry.findVersions("support-specialist")).thenReturn(java.util.List.of(draft(AgentLifecycleState.DRAFT)));

        var result = service.createDraft(draftCommand("1.0.0"), ACTOR, IDEMPOTENCY_KEY);

        assertThat(result.status()).isEqualTo(AgentRegistryMutationStatus.INVALID);
        verify(registry, never()).saveVersion(any());
        verifyNoInteractions(guard);
    }

    @Test
    void cloningAnOlderVersionUsesThePatchAfterTheLatestSemanticVersion() {
        AgentVersion source = draft(1, "1.0.0", AgentLifecycleState.DRAFT);
        AgentVersion latest = draft(2, "1.2.0", AgentLifecycleState.DRAFT);
        when(accessService.authorizeRegistryWrite(ACTOR)).thenReturn(authorized());
        when(registry.findVersion("support-specialist", 1)).thenReturn(java.util.Optional.of(source));
        when(registry.findVersions("support-specialist")).thenReturn(java.util.List.of(source, latest));
        when(guard.tryAcquire("clone_version:1:support-specialist:" + IDEMPOTENCY_KEY)).thenReturn(true);
        when(registry.saveVersion(any())).thenAnswer(invocation -> invocation.getArgument(0));

        var result = service.cloneVersion("support-specialist", 1, ACTOR, IDEMPOTENCY_KEY);

        assertThat(result.status()).isEqualTo(AgentRegistryMutationStatus.CREATED);
        org.mockito.ArgumentCaptor<AgentVersion> saved = org.mockito.ArgumentCaptor.forClass(AgentVersion.class);
        verify(registry).saveVersion(saved.capture());
        assertThat(saved.getValue().version()).isEqualTo(3);
        assertThat(saved.getValue().semanticVersion()).isEqualTo("1.2.1");
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
        UUID baselineRunId = UUID.fromString("00000000-0000-0000-0000-000000000021");
        UUID candidateRunId = UUID.fromString("00000000-0000-0000-0000-000000000022");
        AgentVersion current = draft(AgentLifecycleState.CANDIDATE);
        AgentVersion transitioned = new AgentLifecyclePolicy().transition(
                current, AgentLifecycleState.EVALUATED, ACTOR, CREATED_AT);
        when(accessService.authorizeRegistryWrite(ACTOR)).thenReturn(authorized());
        when(registry.findVersion("support-specialist", 1)).thenReturn(java.util.Optional.of(current));
        when(promotionEvidenceResolver.resolve(current, baselineRunId, candidateRunId)).thenReturn(
                new AgentPromotionEvaluationEvidence(
                        baselineRunId, candidateRunId, "support-eval-v1", "QUALITY_IMPROVED"));
        when(guard.tryAcquire("transition_lifecycle:1:support-specialist:" + IDEMPOTENCY_KEY)).thenReturn(true);
        when(registry.updateLifecycle(
                "support-specialist", 1, AgentLifecycleState.CANDIDATE,
                AgentLifecycleState.EVALUATED, null, null)).thenReturn(transitioned);

        var result = service.transition(
                new AgentLifecycleTransitionCommand(
                        "support-specialist", 1, AgentLifecycleState.EVALUATED,
                        "evaluation reviewed", null, null, baselineRunId, candidateRunId),
                ACTOR,
                IDEMPOTENCY_KEY);

        assertThat(result.status()).isEqualTo(AgentRegistryMutationStatus.TRANSITIONED);
        assertThat(result.state()).isEqualTo(AgentLifecycleState.EVALUATED);
        verify(registry).updateLifecycle(
                "support-specialist", 1, AgentLifecycleState.CANDIDATE,
                AgentLifecycleState.EVALUATED, null, null);
        org.mockito.ArgumentCaptor<com.wally.customersupport.agent.domain.model.AgentRegistryAuditEvent> audit =
                org.mockito.ArgumentCaptor.forClass(
                        com.wally.customersupport.agent.domain.model.AgentRegistryAuditEvent.class);
        verify(auditRepository).save(audit.capture());
        assertThat(audit.getValue().baselineEvaluationRunId()).isEqualTo(baselineRunId);
        assertThat(audit.getValue().candidateEvaluationRunId()).isEqualTo(candidateRunId);
        assertThat(audit.getValue().evaluationDatasetVersion()).isEqualTo("support-eval-v1");
        assertThat(audit.getValue().evaluationAssessmentOutcome()).isEqualTo("QUALITY_IMPROVED");
    }

    @Test
    void refusesToMarkCandidateEvaluatedWithoutComparableRunReferences() {
        AgentVersion current = draft(AgentLifecycleState.CANDIDATE);
        when(accessService.authorizeRegistryWrite(ACTOR)).thenReturn(authorized());
        when(registry.findVersion("support-specialist", 1)).thenReturn(java.util.Optional.of(current));
        when(promotionEvidenceResolver.resolve(current, null, null)).thenThrow(
                new AgentPromotionEvidenceException(AgentPromotionEvidenceException.Reason.REQUIRED));

        var result = service.transition(
                new AgentLifecycleTransitionCommand(
                        "support-specialist", 1, AgentLifecycleState.EVALUATED,
                        "evaluation reviewed", null, null),
                ACTOR,
                IDEMPOTENCY_KEY);

        assertThat(result.status()).isEqualTo(AgentRegistryMutationStatus.INVALID);
        assertThat(result.reason()).isEqualTo(AgentRegistryMutationReason.EVALUATION_EVIDENCE_REQUIRED);
        verify(guard, never()).tryAcquire(any());
        verify(registry, never()).updateLifecycle(
                anyString(), anyInt(), any(AgentLifecycleState.class), any(AgentLifecycleState.class), any(), any());
    }

    @Test
    void requiresTwoApprovalReferencesBeforeApproving() {
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
        return draftCommand("1.0.0");
    }

    private static AgentVersionDraftCommand draftCommand(String semanticVersion) {
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
                "support-eval-v1",
                semanticVersion,
                new AgentInvocationConfiguration(
                        "System prompt v1", "Latest: {{latest_message}}", "{}", "{}", "medium", false));
    }

    private static AgentVersion draft(AgentLifecycleState state) {
        return draft(1, "1.0.0", state);
    }

    private static AgentVersion draft(int version, String semanticVersion, AgentLifecycleState state) {
        AgentVersion definition = AgentVersion.draft(
                "support-specialist", version, semanticVersion, "Support specialist", "Answers support questions",
                "bedrock", "openai.gpt-oss-20b-1:0", new com.wally.customersupport.agent.domain.model.AgentInferenceParameters(
                        BigDecimal.ZERO, BigDecimal.ONE), "system-v1", PROMPT_HASH,
                "support-input-v1", "support-output-v1", Set.of("support.lookup"), Set.of("wcs-support-kb"),
                "conversation-summary-v1", "grounded-customer-support-v1", Duration.ofSeconds(10), 2,
                2_000, 1_000, new BigDecimal("0.050000"), null, "support-eval-v1",
                AgentInvocationConfiguration.empty(), ACTOR, CREATED_AT);
        return state == AgentLifecycleState.DRAFT
                ? definition
                : new AgentLifecyclePolicy().transition(definition, state, ACTOR, CREATED_AT);
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
