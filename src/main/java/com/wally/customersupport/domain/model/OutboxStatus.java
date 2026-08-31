package com.wally.customersupport.domain.model;

public enum OutboxStatus {
    PENDING,
    PROCESSING,
    SENT,
    FAILED
}
