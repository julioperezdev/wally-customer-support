package com.wally.customersupport.backoffice.application.service;

public final class BackofficeAuthenticationException extends RuntimeException {

    public enum Reason {
        DISABLED,
        INVALID_CREDENTIALS,
        INVALID_SESSION,
        CHALLENGE_REQUIRED,
        PROVIDER_UNAVAILABLE
    }

    private final Reason reason;
    private final String challengeName;

    private BackofficeAuthenticationException(Reason reason, String challengeName) {
        super(reason.name());
        this.reason = reason;
        this.challengeName = challengeName;
    }

    public static BackofficeAuthenticationException disabled() {
        return new BackofficeAuthenticationException(Reason.DISABLED, null);
    }

    public static BackofficeAuthenticationException invalidCredentials() {
        return new BackofficeAuthenticationException(Reason.INVALID_CREDENTIALS, null);
    }

    public static BackofficeAuthenticationException invalidSession() {
        return new BackofficeAuthenticationException(Reason.INVALID_SESSION, null);
    }

    public static BackofficeAuthenticationException challengeRequired(String challengeName) {
        return new BackofficeAuthenticationException(Reason.CHALLENGE_REQUIRED, challengeName);
    }

    public static BackofficeAuthenticationException providerUnavailable() {
        return new BackofficeAuthenticationException(Reason.PROVIDER_UNAVAILABLE, null);
    }

    public Reason reason() {
        return reason;
    }

    public String challengeName() {
        return challengeName;
    }
}
