package com.wally.customersupport.conversation.application.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class OptOutDetectorTest {

    private final OptOutDetector detector = new OptOutDetector();

    @Test
    void recognizesSupportedOptOutTermsWithoutBeingCaseSensitive() {
        assertTrue(detector.isOptOut("BAJA"));
        assertTrue(detector.isOptOut("stop"));
        assertTrue(detector.isOptOut("No quiero recibir más mensajes"));
        assertTrue(detector.isOptOut("dejen de escribirme, por favor"));
    }

    @Test
    void doesNotSuppressARegularCustomerMessage() {
        assertFalse(detector.isOptOut("¿Qué productos tienen?"));
        assertFalse(detector.isOptOut("quiero darme de baja de una promoción"));
    }
}
