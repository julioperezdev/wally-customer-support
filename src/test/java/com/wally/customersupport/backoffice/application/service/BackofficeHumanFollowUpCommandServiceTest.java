package com.wally.customersupport.backoffice.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import com.wally.customersupport.conversation.application.port.out.HumanFollowUpTaskOperator;
import org.junit.jupiter.api.Test;

class BackofficeHumanFollowUpCommandServiceTest {

    private static final Instant NOW = Instant.parse("2026-09-09T00:00:00Z");

    @Test
    void resolvesOnlyWhenTheOwnerTransitionSucceeds() {
        HumanFollowUpTaskOperator operator = mock(HumanFollowUpTaskOperator.class);
        when(operator.resolve(any(), org.mockito.ArgumentMatchers.eq("operator-1"), any())).thenReturn(true);
        var service = new BackofficeHumanFollowUpCommandService(
                operator, Clock.fixed(NOW, ZoneOffset.UTC));

        var result = service.resolve(UUID.randomUUID(), "operator-1");

        assertThat(result.status()).isEqualTo("DONE");
        verify(operator).resolve(any(), org.mockito.ArgumentMatchers.eq("operator-1"), any());
    }

    @Test
    void rejectsAStateConflictWithoutPretendingTheTaskChanged() {
        HumanFollowUpTaskOperator operator = mock(HumanFollowUpTaskOperator.class);
        when(operator.claim(any(), org.mockito.ArgumentMatchers.eq("operator-1"), any())).thenReturn(false);
        var service = new BackofficeHumanFollowUpCommandService(
                operator, Clock.fixed(NOW, ZoneOffset.UTC));

        assertThatThrownBy(() -> service.claim(UUID.randomUUID(), "operator-1"))
                .isInstanceOf(IllegalStateException.class);
    }
}
