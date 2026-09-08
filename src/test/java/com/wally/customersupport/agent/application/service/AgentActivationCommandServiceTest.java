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
import java.util.Optional;
import java.util.Set;

import com.wally.customersupport.agent.application.activation.AgentActivationActionCommand;
import com.wally.customersupport.agent.application.activation.AgentActivationCommand;
import com.wally.customersupport.agent.application.activation.AgentActivationMutationReason;
import com.wally.customersupport.agent.application.activation.AgentActivationMutationStatus;
import com.wally.customersupport.agent.application.evaluation.AgentEvaluationControlPlaneAccessDecision;
import com.wally.customersupport.agent.application.evaluation.AgentEvaluationControlPlaneAccessReason;
import com.wally.customersupport.agent.application.evaluation.AgentEvaluationControlPlaneAccessStatus;
import com.wally.customersupport.agent.application.port.out.AgentActivationCommandGuard;
import com.wally.customersupport.agent.application.port.out.AgentRegistryRepository;
import com.wally.customersupport.agent.domain.model.AgentActivation;
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
class AgentActivationCommandServiceTest {

    private static final String ACTOR = "activation-operator";
    private static final String IDEMPOTENCY_KEY = "activation-request-001";
    private static final Instant CREATED_AT = Instant.parse("2026-09-08T10:00:00Z");

    @Mock
    private AgentEvaluationControlPlaneAccessService accessService;

    @Mock
    private AgentRegistryRepository registry;

    @Mock
    private AgentActivationCommandGuard guard;

    private AgentActivationCommandService service;

    @BeforeEach
    void setUp() {
        service = new AgentActivationCommandService(
                accessService,
                registry,
                guard,
                new AgentActivationPolicy());
    }

    @Test
    void keepsMutationsClosedWithoutReadingOrWritingTheRegistry() {
        AgentActivationCommand command = activateCommand();

        var result = service.activate(command, ACTOR, IDEMPOTENCY_KEY, false);

        assertThat(result.status()).isEqualTo(AgentActivationMutationStatus.DISABLED);
        assertThat(result.reason()).isEqualTo(AgentActivationMutationReason.FEATURE_DISABLED);
        verifyNoInteractions(accessService, registry, guard);
    }

    @Test
    void deniesAnEnvironmentOutsideTheConfiguredBoundaryBeforeClaimingTheKey() {
        AgentActivationCommand command = new AgentActivationCommand(
                "catalog-specialist", 1, "test", "telegram", "catalog-search",
                "test rollout", 100, true, "approval-1", "ops-approval-1");
        when(accessService.authorizeRegistryWrite(ACTOR, "test")).thenReturn(denied());

        var result = service.activate(command, ACTOR, IDEMPOTENCY_KEY, true);

        assertThat(result.status()).isEqualTo(AgentActivationMutationStatus.DENIED);
        assertThat(result.reason()).isEqualTo(AgentActivationMutationReason.AUTHORIZATION_DENIED);
        verifyNoInteractions(registry, guard);
    }

    @Test
    void persistsAnActivationOnlyAfterTheApprovedVersionAndIdempotencyClaim() {
        AgentVersion version = approvedVersion();
        AgentActivationCommand command = activateCommand();
        when(accessService.authorizeRegistryWrite(ACTOR, "prod")).thenReturn(authorized());
        when(registry.findVersion("catalog-specialist", 1)).thenReturn(Optional.of(version));
        when(registry.findLatestActivation("catalog-specialist", "prod", "telegram", "catalog-search"))
                .thenReturn(Optional.empty());
        when(guard.tryAcquire(IDEMPOTENCY_KEY)).thenReturn(true);
        when(registry.saveActivation(any())).thenAnswer(invocation -> invocation.getArgument(0));

        var result = service.activate(command, ACTOR, IDEMPOTENCY_KEY, true);

        assertThat(result.status()).isEqualTo(AgentActivationMutationStatus.ACTIVATED);
        assertThat(result.reason()).isEqualTo(AgentActivationMutationReason.ACTIVATION_PERSISTED);
        assertThat(result.agentVersion()).isEqualTo(1);
        verify(guard).tryAcquire(IDEMPOTENCY_KEY);
        verify(registry).saveActivation(any());
    }

    @Test
    void doesNotPersistWhenTheIdempotencyClaimAlreadyExists() {
        when(accessService.authorizeRegistryWrite(ACTOR, "prod")).thenReturn(authorized());
        when(registry.findVersion("catalog-specialist", 1)).thenReturn(Optional.of(approvedVersion()));
        when(registry.findLatestActivation("catalog-specialist", "prod", "telegram", "catalog-search"))
                .thenReturn(Optional.empty());
        when(guard.tryAcquire(IDEMPOTENCY_KEY)).thenReturn(false);

        var result = service.activate(activateCommand(), ACTOR, IDEMPOTENCY_KEY, true);

        assertThat(result.status()).isEqualTo(AgentActivationMutationStatus.ALREADY_PROCESSED);
        assertThat(result.reason()).isEqualTo(AgentActivationMutationReason.IDEMPOTENCY_ALREADY_CLAIMED);
        verify(registry, never()).saveActivation(any());
    }

    @Test
    void createsAnAuditableKillSwitchReference() {
        AgentActivationActionCommand command = actionCommand();
        var activation = new AgentActivation(
                "catalog-specialist", 1, "prod", "telegram", "catalog-search",
                "initial rollout", 100, true, false, null, CREATED_AT, ACTOR);
        when(accessService.authorizeRegistryWrite(ACTOR, "prod")).thenReturn(authorized());
        when(registry.findLatestActivation("catalog-specialist", "prod", "telegram", "catalog-search"))
                .thenReturn(Optional.of(activation));
        when(guard.tryAcquire(IDEMPOTENCY_KEY)).thenReturn(true);
        when(registry.saveActivation(any())).thenAnswer(invocation -> invocation.getArgument(0));

        var result = service.killSwitch(command, ACTOR, IDEMPOTENCY_KEY, true);

        assertThat(result.status()).isEqualTo(AgentActivationMutationStatus.KILL_SWITCHED);
        assertThat(result.reason()).isEqualTo(AgentActivationMutationReason.KILL_SWITCH_PERSISTED);
        verify(registry).saveActivation(any());
    }

    private static AgentActivationCommand activateCommand() {
        return new AgentActivationCommand(
                "catalog-specialist", 1, "prod", "telegram", "catalog-search",
                "initial rollout", 100, true, "approval-1", "ops-approval-1");
    }

    private static AgentActivationActionCommand actionCommand() {
        return new AgentActivationActionCommand(
                "catalog-specialist", "prod", "telegram", "catalog-search",
                "approval-1", "ops-approval-1");
    }

    private static AgentEvaluationControlPlaneAccessDecision authorized() {
        return new AgentEvaluationControlPlaneAccessDecision(
                AgentEvaluationControlPlaneAccessStatus.AUTHORIZED,
                ACTOR,
                "prod",
                AgentEvaluationControlPlaneAccessService.REGISTRY_WRITE_CAPABILITY,
                AgentEvaluationControlPlaneAccessReason.AUTHORIZED);
    }

    private static AgentEvaluationControlPlaneAccessDecision denied() {
        return new AgentEvaluationControlPlaneAccessDecision(
                AgentEvaluationControlPlaneAccessStatus.DENIED,
                ACTOR,
                "test",
                AgentEvaluationControlPlaneAccessService.REGISTRY_WRITE_CAPABILITY,
                AgentEvaluationControlPlaneAccessReason.ENVIRONMENT_NOT_ALLOWED);
    }

    private static AgentVersion approvedVersion() {
        return new AgentVersion(
                "catalog-specialist",
                1,
                "Catalog specialist",
                "Search products using deterministic catalog tools",
                AgentLifecycleState.APPROVED,
                "wcs",
                "deterministic-tool-v1",
                new AgentInferenceParameters(new BigDecimal("0.000"), new BigDecimal("1.000")),
                "system-v1",
                "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
                "catalog-input-v1",
                "catalog-output-v1",
                Set.of("catalog.search", "catalog.stock"),
                Set.of(),
                "conversation-summary-v1",
                "grounded-customer-support-v1",
                Duration.ofSeconds(10),
                2,
                2_000,
                1_000,
                new BigDecimal("0.050000"),
                "safe-fallback",
                "catalog-eval-v1",
                "author",
                CREATED_AT,
                "reviewer",
                CREATED_AT.plusSeconds(60));
    }
}
