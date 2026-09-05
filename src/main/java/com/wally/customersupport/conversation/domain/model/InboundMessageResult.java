package com.wally.customersupport.conversation.domain.model;

public record InboundMessageResult(Result result) {

    public enum Result {
        ACCEPTED,
        DUPLICATE,
        IGNORED
    }

    public static InboundMessageResult accepted() {
        return new InboundMessageResult(Result.ACCEPTED);
    }

    public static InboundMessageResult duplicate() {
        return new InboundMessageResult(Result.DUPLICATE);
    }

    public static InboundMessageResult ignored() {
        return new InboundMessageResult(Result.IGNORED);
    }
}
