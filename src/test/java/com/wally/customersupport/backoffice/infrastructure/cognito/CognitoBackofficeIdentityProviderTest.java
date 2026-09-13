package com.wally.customersupport.backoffice.infrastructure.cognito;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.wally.customersupport.backoffice.application.service.BackofficeAuthenticationException;
import com.wally.customersupport.backoffice.infrastructure.config.BackofficeAuthenticationProperties;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.cognitoidentityprovider.CognitoIdentityProviderClient;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AuthenticationResultType;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AuthFlowType;
import software.amazon.awssdk.services.cognitoidentityprovider.model.InitiateAuthRequest;
import software.amazon.awssdk.services.cognitoidentityprovider.model.InitiateAuthResponse;
import software.amazon.awssdk.services.cognitoidentityprovider.model.NotAuthorizedException;

class CognitoBackofficeIdentityProviderTest {

    @Test
    void startsPasswordAuthenticationAndReturnsTokensWithoutLoggingThem() {
        CognitoIdentityProviderClient client = mock(CognitoIdentityProviderClient.class);
        when(client.initiateAuth(any(InitiateAuthRequest.class))).thenReturn(InitiateAuthResponse.builder()
                .authenticationResult(AuthenticationResultType.builder()
                        .accessToken("access-token")
                        .refreshToken("refresh-token")
                        .expiresIn(3600)
                        .build())
                .build());

        var result = provider(client).authenticate("operator@example.com", "secret");

        assertThat(result.accessToken()).isEqualTo("access-token");
        assertThat(result.refreshToken()).isEqualTo("refresh-token");
        assertThat(result.accessTokenExpiresInSeconds()).isEqualTo(3600);

        var request = org.mockito.ArgumentCaptor.forClass(InitiateAuthRequest.class);
        verify(client).initiateAuth(request.capture());
        assertThat(request.getValue().authFlow()).isEqualTo(AuthFlowType.USER_PASSWORD_AUTH);
        assertThat(request.getValue().clientId()).isEqualTo("client-id");
        assertThat(request.getValue().authParameters())
                .containsEntry("USERNAME", "operator@example.com")
                .containsEntry("PASSWORD", "secret");
    }

    @Test
    void mapsInvalidCognitoCredentialsToAStableApplicationReason() {
        CognitoIdentityProviderClient client = mock(CognitoIdentityProviderClient.class);
        when(client.initiateAuth(any(InitiateAuthRequest.class)))
                .thenThrow(NotAuthorizedException.builder().message("invalid").build());

        assertThatThrownBy(() -> provider(client).authenticate("operator", "wrong"))
                .isInstanceOf(BackofficeAuthenticationException.class)
                .extracting(error -> ((BackofficeAuthenticationException) error).reason())
                .isEqualTo(BackofficeAuthenticationException.Reason.INVALID_CREDENTIALS);
    }

    private static CognitoBackofficeIdentityProvider provider(CognitoIdentityProviderClient client) {
        return new CognitoBackofficeIdentityProvider(client, new BackofficeAuthenticationProperties(
                true, true, "None", "access", "refresh", 3600, "http://localhost:5173",
                new BackofficeAuthenticationProperties.Cognito("us-east-1", "client-id")));
    }
}
