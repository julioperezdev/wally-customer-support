package com.wally.customersupport.agent.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;

import com.wally.customersupport.agent.application.activation.AgentActivationPreflightCommand;
import com.wally.customersupport.agent.application.activation.AgentActivationPreflightCheckStatus;
import com.wally.customersupport.agent.application.activation.AgentActivationPreflightStatus;
import com.wally.customersupport.agent.application.evaluation.AgentEvaluationControlPlaneAccessDecision;
import com.wally.customersupport.agent.application.evaluation.AgentEvaluationControlPlaneAccessReason;
import com.wally.customersupport.agent.application.evaluation.AgentEvaluationControlPlaneAccessStatus;
import com.wally.customersupport.agent.application.port.out.AgentRegistryRepository;
import com.wally.customersupport.agent.domain.model.AgentActivationPolicy;
import com.wally.customersupport.agent.domain.model.AgentInferenceParameters;
import com.wally.customersupport.agent.domain.model.AgentLifecycleState;
import com.wally.customersupport.agent.domain.model.AgentVersion;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AgentActivationPreflightServiceTest {

    private static final String ACTOR = "activation-operator";
    private static final Instant CREATED_AT = Instant.parse("2026-09-08T10:00:00Z");

    @Mock
    private AgentEvaluationControlPlaneAccessService accessService;

    @Mock
    private AgentRegistryRepository registry;

    private AgentActivationPreflightService service;

    @BeforeEach
    void setUp() {
        service = new AgentActivationPreflightService(
                accessService, registry, new AgentActivationPolicy());
    }

    @Test
    void reportsReadyWithoutMutatingTheRegistry() {
        when(accessService.authorizeRegistry(ACTOR, "prod")).thenReturn(authorized());
        when(registry.findVersion("catalog-specialist", 1)).thenReturn(Optional.of(approvedVersion()));
        when(registry.findLatestActivation("catalog-specialist", "prod", "telegram", "catalog-search"))
                .thenReturn(Optional.empty());

        var result = service.check(command(), ACTOR);

        assertThat(result.status()).isEqualTo(AgentActivationPreflightStatus.READY);
        assertThat(result.canActivate()).isTrue();
        assertThat(result.checks()).extracting("code")
                .contains("AUTHORIZATION", "VERSION_EXISTS", "VERSION_APPROVED", "ACTIVATION_POLICY");
        verify(registry, never()).saveActivation(any());
    }

    @Test
    void blocksAVersionThatIsNotApproved() {
        when(accessService.authorizeRegistry(ACTOR, "prod")).thenReturn(authorized());
        when(registry.findVersion("catalog-specialist", 1)).thenReturn(Optional.of(draftVersion()));
        when(registry.findLatestActivation("catalog-specialist", "prod", "telegram", "catalog-search"))
                .thenReturn(Optional.empty());

        var result = service.check(command(), ACTOR);

        assertThat(result.status()).isEqualTo(AgentActivationPreflightStatus.BLOCKED);
        assertThat(result.canActivate()).isFalse();
        assertThat(result.checks()).anyMatch(check -> check.code().equals("VERSION_APPROVED")
                && check.status() == AgentActivationPreflightCheckStatus.FAIL);
        verify(registry, never()).saveActivation(any());
    }

    @Test
    void deniesBeforeReadingRegistryWhenTheActorIsNotAuthorized() {
        when(accessService.authorizeRegistry(ACTOR, "prod")).thenReturn(denied());

        var result = service.check(command(), ACTOR);

        assertThat(result.status()).isEqualTo(AgentActivationPreflightStatus.DENIED);
        assertThat(result.canActivate()).isFalse();
        verify(registry, never()).findVersion(any(), any(Integer.class));
    }

    @Test
    void blocksInvalidActivationPolicyWithoutPersistingAnything() {
        when(accessService.authorizeRegistry(ACTOR, "prod")).thenReturn(authorized());
        when(registry.findVersion("catalog-specialist", 1)).thenReturn(Optional.of(approvedVersion()));
        when(registry.findLatestActivation("catalog-specialist", "prod", "telegram", "catalog-search"))
                .thenReturn(Optional.of(new com.wally.customersupport.agent.domain.model.AgentActivation(
                        "catalog-specialist", 1, "prod", "telegram", "catalog-search", "initial", 100,
                        true, false, null, CREATED_AT, ACTOR)));

        var result = service.check(command(), ACTOR);

        assertThat(result.status()).isEqualTo(AgentActivationPreflightStatus.BLOCKED);
        assertThat(result.checks()).anyMatch(check -> check.code().equals("ACTIVATION_POLICY")
                && check.status() == AgentActivationPreflightCheckStatus.FAIL);
        verify(registry, never()).saveActivation(any());
    }

    private static AgentActivationPreflightCommand command() {
        return new AgentActivationPreflightCommand(
                "catalog-specialist", 1, "prod", "telegram", "catalog-search",
                "preflight test", 100, true, "approval-1", "ops-approval-1");
    }

    private static AgentEvaluationControlPlaneAccessDecision authorized() {
        return new AgentEvaluationControlPlaneAccessDecision(
                AgentEvaluationControlPlaneAccessStatus.AUTHORIZED,
                ACTOR,
                "prod",
                AgentEvaluationControlPlaneAccessService.REGISTRY_READ_CAPABILITY,
                AgentEvaluationControlPlaneAccessReason.AUTHORIZED);
    }

    private static AgentEvaluationControlPlaneAccessDecision denied() {
        return new AgentEvaluationControlPlaneAccessDecision(
                AgentEvaluationControlPlaneAccessStatus.DENIED,
                ACTOR,
                "prod",
                AgentEvaluationControlPlaneAccessService.REGISTRY_READ_CAPABILITY,
                AgentEvaluationControlPlaneAccessReason.AUTHORIZER_DENIED);
    }

    private static AgentVersion approvedVersion() {
        return version(AgentLifecycleState.APPROVED, "reviewer", CREATED_AT.plusSeconds(60));
    }

    private static AgentVersion draftVersion() {
        return version(AgentLifecycleState.DRAFT, null, null);
    }

    private static AgentVersion version(AgentLifecycleState state, String approvedBy, Instant approvedAt) {
        return new AgentVersion(
                "catalog-specialist", 1, "Catalog specialist",
                "Search products using deterministic catalog tools", state, "wcs", "deterministic-tool-v1",
                new AgentInferenceParameters(new BigDecimal("0.000"), new BigDecimal("1.000")), "system-v1",
                "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
                "catalog-input-v1", "catalog-output-v1", Set.of("catalog.search"), Set.of(),
                "conversation-summary-v1", "grounded-customer-support-v1", Duration.ofSeconds(10), 2,
                2_000, 1_000, new BigDecimal("0.050000"), null, "catalog-eval-v1", "author", CREATED_AT,
                approvedBy, approvedAt);
    }
}
