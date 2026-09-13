package com.wally.customersupport.backoffice.application.service;

import com.wally.customersupport.backoffice.application.port.out.BackofficeIdentityProvider;
import com.wally.customersupport.backoffice.infrastructure.config.BackofficeAuthenticationProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** Orchestrates the provider-neutral backoffice authentication use cases. */
@Service
@RequiredArgsConstructor
public class BackofficeAuthenticationService {

    private final BackofficeIdentityProvider identityProvider;
    private final BackofficeAuthenticationProperties properties;

    public BackofficeIdentityProvider.AuthenticationResult login(String username, String password) {
        ensureEnabled();
        if (isBlank(username) || isBlank(password)) {
            throw BackofficeAuthenticationException.invalidCredentials();
        }
        return resolve(identityProvider.authenticate(username.strip(), password));
    }

    public BackofficeIdentityProvider.AuthenticationResult refresh(String refreshToken) {
        ensureEnabled();
        if (isBlank(refreshToken)) {
            throw BackofficeAuthenticationException.invalidSession();
        }
        return resolve(identityProvider.refresh(refreshToken));
    }

    public void logout(String accessToken) {
        if (properties.enabled() && !isBlank(accessToken)) {
            identityProvider.revoke(accessToken);
        }
    }

    private BackofficeIdentityProvider.AuthenticationResult resolve(
            BackofficeIdentityProvider.AuthenticationResult result) {
        if (result == null) {
            throw BackofficeAuthenticationException.providerUnavailable();
        }
        if (result.challenged()) {
            throw BackofficeAuthenticationException.challengeRequired(result.challengeName());
        }
        if (!result.authenticated()) {
            throw BackofficeAuthenticationException.providerUnavailable();
        }
        return result;
    }

    private void ensureEnabled() {
        if (!properties.enabled()) {
            throw BackofficeAuthenticationException.disabled();
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
