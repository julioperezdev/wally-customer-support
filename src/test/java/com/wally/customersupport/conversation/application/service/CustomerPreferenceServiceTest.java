package com.wally.customersupport.conversation.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import com.wally.customersupport.conversation.infrastructure.memory.mock.InMemoryCustomerPreferenceStore;
import com.wally.customersupport.shared.infrastructure.config.ConversationPreferenceProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CustomerPreferenceServiceTest {

    private static final Instant NOW = Instant.parse("2026-09-06T03:00:00Z");

    private CustomerPreferenceService service;

    @BeforeEach
    void setUp() {
        service = new CustomerPreferenceService(
                new InMemoryCustomerPreferenceStore(),
                new ConversationPreferenceProperties(true, Duration.ofHours(1), 5, 64),
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void storesOnlyAnAllowedExplicitColorAndReplacesThePreviousValue() {
        assertTrue(service.recordExplicitColor("actor-1", " Negro ", NOW).isPresent());
        assertTrue(service.recordExplicitColor("actor-1", "azul", NOW.plusSeconds(1)).isPresent());

        var preferences = service.findForContext("actor-1", UUID.randomUUID());

        assertEquals(1, preferences.size());
        assertEquals("preferred_color", preferences.getFirst().key());
        assertEquals("azul", preferences.getFirst().value());
    }

    @Test
    void rejectsUnsupportedValuesAndExpiresStoredPreferences() {
        assertTrue(service.recordExplicitColor("actor-1", "talle M", NOW).isEmpty());
        assertTrue(service.recordExplicitColor("actor-1", "negro", NOW).isPresent());

        var store = new InMemoryCustomerPreferenceStore();
        CustomerPreferenceService storingService = new CustomerPreferenceService(
                store,
                new ConversationPreferenceProperties(true, Duration.ofSeconds(1), 5, 64),
                Clock.fixed(NOW, ZoneOffset.UTC));
        storingService.recordExplicitColor("actor-expiring", "negro", NOW);
        CustomerPreferenceService expiredService = new CustomerPreferenceService(
                store,
                new ConversationPreferenceProperties(true, Duration.ofSeconds(1), 5, 64),
                Clock.fixed(NOW.plusSeconds(2), ZoneOffset.UTC));

        assertTrue(expiredService.findForContext("actor-expiring", UUID.randomUUID()).isEmpty());
    }

    @Test
    void clearActorDoesNotAffectAnotherActor() {
        service.recordExplicitColor("actor-1", "negro", NOW);
        service.recordExplicitColor("actor-2", "blanco", NOW);

        service.clearActor("actor-1");

        assertTrue(service.findForContext("actor-1", UUID.randomUUID()).isEmpty());
        assertEquals("blanco", service.findForContext("actor-2", UUID.randomUUID()).getFirst().value());
    }
}
