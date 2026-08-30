package com.wally.customersupport.poc.webhook;

public class PocPayloadException extends RuntimeException {

    public PocPayloadException(String message, Throwable cause) {
        super(message, cause);
    }
}
