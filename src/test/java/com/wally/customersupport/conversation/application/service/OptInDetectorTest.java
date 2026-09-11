package com.wally.customersupport.conversation.application.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class OptInDetectorTest {

    private final OptInDetector detector = new OptInDetector();

    @Test
    void recognizesOnlyExplicitReactivationCommands() {
        assertTrue(detector.isOptIn("ALTA"));
        assertTrue(detector.isOptIn("REANUDAR"));
        assertTrue(detector.isOptIn("/start"));
    }

    @Test
    void doesNotTreatRegularMessagesAsConsent() {
        assertFalse(detector.isOptIn("Hola"));
        assertFalse(detector.isOptIn("Quiero continuar con mi compra"));
        assertFalse(detector.isOptIn("alta de producto"));
    }
}
