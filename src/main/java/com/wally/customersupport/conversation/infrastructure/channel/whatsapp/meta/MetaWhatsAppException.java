package com.wally.customersupport.conversation.infrastructure.channel.whatsapp.meta;

public class MetaWhatsAppException extends RuntimeException {

    private final int statusCode;

    public MetaWhatsAppException(String message, int statusCode) {
        super(message);
        this.statusCode = statusCode;
    }

    public int statusCode() {
        return statusCode;
    }
}
