package com.wally.customersupport.backoffice.application.model;

public record BackofficeHumanFollowUpAction(
        String operation,
        String actor,
        String status) {
}
