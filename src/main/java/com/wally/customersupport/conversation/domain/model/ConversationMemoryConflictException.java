package com.wally.customersupport.conversation.domain.model;

public class ConversationMemoryConflictException extends RuntimeException {

    public ConversationMemoryConflictException(String message) {
        super(message);
    }

    public ConversationMemoryConflictException(String message, Throwable cause) {
        super(message, cause);
    }
}
