package com.wally.customersupport.backoffice.application.port.out;

/** Provider-neutral contract for authenticating backoffice users. */
public interface BackofficeIdentityProvider {

    AuthenticationResult authenticate(String username, String password);

    AuthenticationResult refresh(String refreshToken);

    void revoke(String accessToken);

    record AuthenticationResult(
            String accessToken,
            String refreshToken,
            long accessTokenExpiresInSeconds,
            String challengeName) {

        public boolean authenticated() {
            return accessToken != null && !accessToken.isBlank();
        }

        public boolean challenged() {
            return challengeName != null && !challengeName.isBlank();
        }
    }
}
