package com.wally.customersupport.featureflag.application;

public class FeatureFlagValidationException extends RuntimeException {

    public FeatureFlagValidationException(String message) {
        super(message);
    }
}
