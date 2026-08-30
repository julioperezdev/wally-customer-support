package com.wally.customersupport.poc.whatsapp;

public class MetaWhatsAppClientException extends RuntimeException {

    private final int statusCode;

    public MetaWhatsAppClientException(String message, int statusCode) {
        super(message);
        this.statusCode = statusCode;
    }

    public int statusCode() {
        return statusCode;
    }
}
