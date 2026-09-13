package com.wally.customersupport.order.domain.model;

public enum OrderStatus {
    PENDING_PAYMENT,
    PAID,
    REJECTED,
    CANCELLED,
    EXPIRED;

    public boolean isTerminal() {
        return this == PAID || this == REJECTED || this == CANCELLED || this == EXPIRED;
    }
}
