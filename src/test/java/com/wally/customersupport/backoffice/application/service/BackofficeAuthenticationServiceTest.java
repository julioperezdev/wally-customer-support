package com.wally.customersupport.backoffice.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.wally.customersupport.backoffice.application.port.out.BackofficeIdentityProvider;
import com.wally.customersupport.backoffice.infrastructure.config.BackofficeAuthenticationProperties;
import org.junit.jupiter.api.Test;

class BackofficeAuthenticationServiceTest {

    private static final BackofficeAuthenticationProperties ENABLED = new BackofficeAuthenticationProperties(
            true, true, "None", "access", "refresh", 3600, "http://localhost:5173",
            new BackofficeAuthenticationProperties.Cognito("us-east-1", "client-id"));

    @Test
    void trimsTheUsernameBeforeSendingItToCognito() {
        BackofficeIdentityProvider provider = mock(BackofficeIdentityProvider.class);
        BackofficeIdentityProvider.AuthenticationResult result = authenticated();
        when(provider.authenticate("operator@example.com", "secret")).thenReturn(result);

        assertThat(new BackofficeAuthenticationService(provider, ENABLED).login(
                " operator@example.com ", "secret")).isEqualTo(result);

        verify(provider).authenticate("operator@example.com", "secret");
    }

    @Test
    void rejectsBlankCredentialsWithoutCallingCognito() {
        BackofficeIdentityProvider provider = mock(BackofficeIdentityProvider.class);

        assertThatThrownBy(() -> new BackofficeAuthenticationService(provider, ENABLED)
                .login(" ", "secret"))
                .isInstanceOf(BackofficeAuthenticationException.class)
                .satisfies(error -> assertThat(((BackofficeAuthenticationException) error).reason())
                        .isEqualTo(BackofficeAuthenticationException.Reason.INVALID_CREDENTIALS));

        verifyNoInteractions(provider);
    }

    @Test
    void translatesCognitoChallengesIntoAnExplicitApplicationError() {
        BackofficeIdentityProvider provider = mock(BackofficeIdentityProvider.class);
        when(provider.authenticate("operator", "temporary-password"))
                .thenReturn(new BackofficeIdentityProvider.AuthenticationResult(
                        null, null, 0, "NEW_PASSWORD_REQUIRED"));

        assertThatThrownBy(() -> new BackofficeAuthenticationService(provider, ENABLED)
                .login("operator", "temporary-password"))
                .isInstanceOf(BackofficeAuthenticationException.class)
                .satisfies(error -> {
                    BackofficeAuthenticationException exception = (BackofficeAuthenticationException) error;
                    assertThat(exception.reason())
                            .isEqualTo(BackofficeAuthenticationException.Reason.CHALLENGE_REQUIRED);
                    assertThat(exception.challengeName()).isEqualTo("NEW_PASSWORD_REQUIRED");
                });
    }

    @Test
    void doesNotRevokeAnythingWhenAuthenticationIsDisabled() {
        BackofficeIdentityProvider provider = mock(BackofficeIdentityProvider.class);
        BackofficeAuthenticationProperties disabled = new BackofficeAuthenticationProperties(
                false, false, "Lax", "access", "refresh", 3600, "http://localhost:5173",
                new BackofficeAuthenticationProperties.Cognito("us-east-1", "client-id"));

        new BackofficeAuthenticationService(provider, disabled).logout("access-token");

        verifyNoInteractions(provider);
    }

    private static BackofficeIdentityProvider.AuthenticationResult authenticated() {
        return new BackofficeIdentityProvider.AuthenticationResult("access-token", "refresh-token", 3600, null);
    }
}
