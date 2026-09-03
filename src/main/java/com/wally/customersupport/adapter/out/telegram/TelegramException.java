package com.wally.customersupport.adapter.out.telegram;

public class TelegramException extends RuntimeException {

    private final int statusCode;

    public TelegramException(String message, int statusCode) {
        super(message);
        this.statusCode = statusCode;
    }

    public int statusCode() {
        return statusCode;
    }
}
