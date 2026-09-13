package com.wally.customersupport.backoffice.infrastructure.cognito;

import java.util.Map;

import com.wally.customersupport.backoffice.application.port.out.BackofficeIdentityProvider;
import com.wally.customersupport.backoffice.application.service.BackofficeAuthenticationException;
import com.wally.customersupport.backoffice.infrastructure.config.BackofficeAuthenticationProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import software.amazon.awssdk.services.cognitoidentityprovider.CognitoIdentityProviderClient;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AuthenticationResultType;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AuthFlowType;
import software.amazon.awssdk.services.cognitoidentityprovider.model.ChallengeNameType;
import software.amazon.awssdk.services.cognitoidentityprovider.model.CognitoIdentityProviderException;
import software.amazon.awssdk.services.cognitoidentityprovider.model.GlobalSignOutRequest;
import software.amazon.awssdk.services.cognitoidentityprovider.model.InitiateAuthRequest;
import software.amazon.awssdk.services.cognitoidentityprovider.model.InitiateAuthResponse;
import software.amazon.awssdk.services.cognitoidentityprovider.model.NotAuthorizedException;
import software.amazon.awssdk.services.cognitoidentityprovider.model.PasswordResetRequiredException;
import software.amazon.awssdk.services.cognitoidentityprovider.model.UserNotConfirmedException;
import software.amazon.awssdk.services.cognitoidentityprovider.model.UserNotFoundException;

/** Amazon Cognito adapter. Credentials and tokens never leave this boundary. */
@Slf4j
@RequiredArgsConstructor
public class CognitoBackofficeIdentityProvider implements BackofficeIdentityProvider {

    private final CognitoIdentityProviderClient client;
    private final BackofficeAuthenticationProperties properties;

    @Override
    public AuthenticationResult authenticate(String username, String password) {
        try {
            InitiateAuthResponse response = client.initiateAuth(InitiateAuthRequest.builder()
                    .authFlow(AuthFlowType.USER_PASSWORD_AUTH)
                    .clientId(clientId())
                    .authParameters(Map.of("USERNAME", username, "PASSWORD", password))
                    .build());
            return toResult(response, true);
        } catch (NotAuthorizedException | UserNotFoundException | PasswordResetRequiredException
                | UserNotConfirmedException exception) {
            throw BackofficeAuthenticationException.invalidCredentials();
        } catch (CognitoIdentityProviderException exception) {
            log.warn("Cognito login failed with provider error {}", providerCode(exception));
            throw BackofficeAuthenticationException.providerUnavailable();
        }
    }

    @Override
    public AuthenticationResult refresh(String refreshToken) {
        try {
            InitiateAuthResponse response = client.initiateAuth(InitiateAuthRequest.builder()
                    .authFlow(AuthFlowType.REFRESH_TOKEN_AUTH)
                    .clientId(clientId())
                    .authParameters(Map.of("REFRESH_TOKEN", refreshToken))
                    .build());
            return toResult(response, false);
        } catch (NotAuthorizedException | UserNotFoundException exception) {
            throw BackofficeAuthenticationException.invalidSession();
        } catch (CognitoIdentityProviderException exception) {
            log.warn("Cognito refresh failed with provider error {}", providerCode(exception));
            throw BackofficeAuthenticationException.providerUnavailable();
        }
    }

    @Override
    public void revoke(String accessToken) {
        try {
            client.globalSignOut(GlobalSignOutRequest.builder().accessToken(accessToken).build());
        } catch (CognitoIdentityProviderException exception) {
            // Logout remains fail-closed locally even if Cognito is temporarily unavailable.
            log.warn("Cognito global sign out failed with provider error {}", providerCode(exception));
        }
    }

    private AuthenticationResult toResult(InitiateAuthResponse response, boolean includeRefreshToken) {
        String challenge = response.challengeNameAsString();
        if (challenge != null && !challenge.isBlank()
                && !ChallengeNameType.UNKNOWN_TO_SDK_VERSION.toString().equals(challenge)) {
            return new AuthenticationResult(null, null, 0, challenge);
        }
        AuthenticationResultType result = response.authenticationResult();
        if (result == null || result.accessToken() == null || result.accessToken().isBlank()) {
            return new AuthenticationResult(null, null, 0, null);
        }
        return new AuthenticationResult(
                result.accessToken(),
                includeRefreshToken ? result.refreshToken() : null,
                result.expiresIn() == null ? 0 : result.expiresIn(),
                null);
    }

    private String clientId() {
        if (properties.cognito().clientId().isBlank()) {
            throw BackofficeAuthenticationException.providerUnavailable();
        }
        return properties.cognito().clientId();
    }

    private static String providerCode(CognitoIdentityProviderException exception) {
        return exception.awsErrorDetails() == null
                ? "unknown"
                : exception.awsErrorDetails().errorCode();
    }
}
