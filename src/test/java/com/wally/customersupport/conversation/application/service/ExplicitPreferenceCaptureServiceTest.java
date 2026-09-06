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

        var result = service.capture("actor-1", "Prefiero el negro.", NOW);

        assertEquals(ExplicitPreferenceCaptureService.Status.SAVED, result.status());
        assertEquals("negro", result.color());
        verify(customerPreferenceService).recordExplicitColor("actor-1", "negro", NOW);
    }

    @Test
    void doesNotTurnCatalogColorIntoPreference() {
        var result = service.capture("actor-1", "Busco una remera negra talle M", NOW);

        assertEquals(ExplicitPreferenceCaptureService.Status.NOT_DETECTED, result.status());
        verify(customerPreferenceService, never()).recordExplicitColor(any(), any(), any());
    }

    @Test
    void keepsUnsupportedExplicitColorOutOfPersistence() {
        when(customerPreferenceService.recordExplicitColor(eq("actor-1"), eq("naranja"), eq(NOW)))
                .thenReturn(Optional.empty());

        var result = service.capture("actor-1", "Mi color favorito es naranja", NOW);

        assertEquals(ExplicitPreferenceCaptureService.Status.REJECTED, result.status());
        assertTrue(result.shouldAcknowledge());
        verify(customerPreferenceService).recordExplicitColor("actor-1", "naranja", NOW);
    }
}
