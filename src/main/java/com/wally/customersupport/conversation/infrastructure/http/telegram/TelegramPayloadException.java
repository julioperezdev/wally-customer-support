package com.wally.customersupport.conversation.infrastructure.http.telegram;

public class TelegramPayloadException extends RuntimeException {

    public TelegramPayloadException(String message, Throwable cause) {
        super(message, cause);
    }
}
