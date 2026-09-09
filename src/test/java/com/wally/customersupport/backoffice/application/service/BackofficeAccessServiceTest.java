package com.wally.customersupport.backoffice.application.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class BackofficeAccessServiceTest {

    @Test
    void remainsClosedWhenNotExplicitlyEnabled() {
        BackofficeAccessService service = new BackofficeAccessService(false, true);

        assertThat(service.authorize("backoffice.catalog.read"))
                .usingRecursiveComparison()
                .isEqualTo(new BackofficeAccessService.Decision(false, 404, "BACKOFFICE_DISABLED"));
    }

    @Test
    void allowsLocalSmokeOnlyWhenBothGatesAreEnabled() {
        BackofficeAccessService service = new BackofficeAccessService(true, true);

        assertThat(service.authorize("backoffice.catalog.read")).isEqualTo(
                new BackofficeAccessService.Decision(true, 200, "backoffice.catalog.read"));
    }

    @Test
    void doesNotTreatProductionEnablementAsAuthorization() {
        BackofficeAccessService service = new BackofficeAccessService(true, false);

        assertThat(service.authorize("backoffice.catalog.read")).isEqualTo(
                new BackofficeAccessService.Decision(false, 403, "BACKOFFICE_AUTHORIZATION_REQUIRED"));
    }
}
