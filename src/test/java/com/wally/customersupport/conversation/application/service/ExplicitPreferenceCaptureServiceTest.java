package com.wally.customersupport.conversation.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import com.wally.customersupport.conversation.domain.model.CustomerPreference;
import com.wally.customersupport.shared.infrastructure.config.ConversationPreferenceProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ExplicitPreferenceCaptureServiceTest {

    private static final Instant NOW = Instant.parse("2026-09-06T10:00:00Z");

    @Mock
    private CustomerPreferenceService customerPreferenceService;

    private ExplicitPreferenceCaptureService service;

    @BeforeEach
    void setUp() {
        service = new ExplicitPreferenceCaptureService(
                customerPreferenceService,
                new ConversationPreferenceProperties(true, Duration.ofHours(24), 5, 64));
    }

    @Test
    void capturesOnlyAnExplicitPreferencePhrase() {
        CustomerPreference preference = new CustomerPreference(
                null, "actor-1", CustomerPreferenceService.PREFERRED_COLOR, "negro",
                com.wally.customersupport.conversation.domain.model.PreferenceScope.ACTOR,
                1.0,
                com.wally.customersupport.conversation.domain.model.PreferenceOrigin.EXPLICIT_USER,
                true,
                NOW,
                NOW.plus(Duration.ofHours(24)));
        when(customerPreferenceService.recordExplicitColor(eq("actor-1"), eq("negro"), eq(NOW)))
                .thenReturn(Optional.of(preference));

        var result = service.capture("actor-1", UUID.randomUUID(), "Prefiero el negro.", NOW);

        assertEquals(ExplicitPreferenceCaptureService.Status.SAVED, result.status());
        assertEquals(CustomerPreferenceService.PREFERRED_COLOR, result.key());
        assertEquals("negro", result.value());
        verify(customerPreferenceService).recordExplicitColor("actor-1", "negro", NOW);
    }

    @Test
    void doesNotTurnCatalogColorIntoPreference() {
        var result = service.capture("actor-1", UUID.randomUUID(), "Busco una remera negra talle M", NOW);

        assertEquals(ExplicitPreferenceCaptureService.Status.NOT_DETECTED, result.status());
        verify(customerPreferenceService, never()).recordExplicitColor(any(), any(), any());
    }

    @Test
    void keepsUnsupportedExplicitColorOutOfPersistence() {
        when(customerPreferenceService.recordExplicitColor(eq("actor-1"), eq("naranja"), eq(NOW)))
                .thenReturn(Optional.empty());

        var result = service.capture("actor-1", UUID.randomUUID(), "Mi color favorito es naranja", NOW);

        assertEquals(ExplicitPreferenceCaptureService.Status.REJECTED, result.status());
        assertTrue(result.shouldAcknowledge());
        verify(customerPreferenceService).recordExplicitColor("actor-1", "naranja", NOW);
    }

    @Test
    void capturesColloquialExplicitSizeButNotAnIncidentalSearchFilter() {
        CustomerPreference preference = new CustomerPreference(
                null, "actor-1", CustomerPreferenceService.PREFERRED_SIZE, "M",
                com.wally.customersupport.conversation.domain.model.PreferenceScope.ACTOR,
                1.0,
                com.wally.customersupport.conversation.domain.model.PreferenceOrigin.EXPLICIT_USER,
                true,
                NOW,
                NOW.plus(Duration.ofHours(24)));
        when(customerPreferenceService.recordExplicitSize(eq("actor-1"), eq("m"), eq(NOW)))
                .thenReturn(Optional.of(preference));

        var captured = service.capture("actor-1", UUID.randomUUID(), "Soy M", NOW);
        var incidental = service.capture("actor-1", UUID.randomUUID(), "Busco un buzo talle M", NOW);

        assertEquals(ExplicitPreferenceCaptureService.Status.SAVED, captured.status());
        assertEquals(CustomerPreferenceService.PREFERRED_SIZE, captured.key());
        assertEquals("M", captured.value());
        assertEquals(ExplicitPreferenceCaptureService.Status.NOT_DETECTED, incidental.status());
        verify(customerPreferenceService).recordExplicitSize("actor-1", "m", NOW);
        verify(customerPreferenceService, never()).recordExplicitSize(eq("actor-1"), eq("M"), any());
    }

    @Test
    void capturesNaturalLanguageSizeAlias() {
        CustomerPreference preference = new CustomerPreference(
                null, "actor-1", CustomerPreferenceService.PREFERRED_SIZE, "M",
                com.wally.customersupport.conversation.domain.model.PreferenceScope.ACTOR,
                1.0,
                com.wally.customersupport.conversation.domain.model.PreferenceOrigin.EXPLICIT_USER,
                true,
                NOW,
                NOW.plus(Duration.ofHours(24)));
        when(customerPreferenceService.recordExplicitSize(eq("actor-1"), eq("mediano"), eq(NOW)))
                .thenReturn(Optional.of(preference));

        var result = service.capture("actor-1", UUID.randomUUID(), "Mi talle es mediano", NOW);

        assertEquals(ExplicitPreferenceCaptureService.Status.SAVED, result.status());
        assertEquals("M", result.value());
        verify(customerPreferenceService).recordExplicitSize("actor-1", "mediano", NOW);
    }

    @Test
    void capturesPreferenceClauseWithoutSwallowingTheRestOfTheCustomerTurn() {
        CustomerPreference preference = new CustomerPreference(
                null, "actor-1", CustomerPreferenceService.PREFERRED_SIZE, "M",
                com.wally.customersupport.conversation.domain.model.PreferenceScope.ACTOR,
                1.0,
                com.wally.customersupport.conversation.domain.model.PreferenceOrigin.EXPLICIT_USER,
                true,
                NOW,
                NOW.plus(Duration.ofHours(24)));
        when(customerPreferenceService.recordExplicitSize(eq("actor-1"), eq("m"), eq(NOW)))
                .thenReturn(Optional.of(preference));

        var result = service.capture("actor-1", UUID.randomUUID(), "Estoy buscando ropa; uso M", NOW);

        assertEquals(ExplicitPreferenceCaptureService.Status.SAVED, result.status());
        assertEquals(false, result.shouldAcknowledge());
        verify(customerPreferenceService).recordExplicitSize("actor-1", "m", NOW);
    }

    @Test
    void explicitForgetDeletesOnlyThePreferenceAndAmbiguousRejectionAsksForClarification() {
        UUID conversationId = UUID.randomUUID();
        when(customerPreferenceService.forgetExplicitPreference(
                "actor-1", conversationId, CustomerPreferenceService.PREFERRED_SIZE)).thenReturn(1);

        var forgotten = service.capture("actor-1", conversationId, "Olvidá mi talle", NOW);
        var ambiguous = service.capture("actor-1", conversationId, "No quiero eso", NOW);

        assertEquals(ExplicitPreferenceCaptureService.Status.FORGOTTEN, forgotten.status());
        assertEquals(CustomerPreferenceService.PREFERRED_SIZE, forgotten.key());
        assertEquals(ExplicitPreferenceCaptureService.Status.CLARIFICATION_REQUIRED, ambiguous.status());
        verify(customerPreferenceService).forgetExplicitPreference(
                "actor-1", conversationId, CustomerPreferenceService.PREFERRED_SIZE);
        verify(customerPreferenceService, never()).clearActor("actor-1");
    }
}
