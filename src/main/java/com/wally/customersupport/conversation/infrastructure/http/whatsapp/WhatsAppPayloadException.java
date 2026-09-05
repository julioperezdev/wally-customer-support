package com.wally.customersupport.conversation.infrastructure.http.whatsapp;

public class WhatsAppPayloadException extends RuntimeException {

    public WhatsAppPayloadException(String message, Throwable cause) {
        super(message, cause);
    }
}
