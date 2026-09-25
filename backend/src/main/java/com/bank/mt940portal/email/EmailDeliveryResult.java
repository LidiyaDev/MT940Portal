package com.bank.mt940portal.email;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class EmailDeliveryResult {

    private boolean success;
    private String providerResponse;
    private String errorMessage;

    public static EmailDeliveryResult ok(String response) {
        return EmailDeliveryResult.builder().success(true).providerResponse(response).build();
    }

    public static EmailDeliveryResult failed(String message) {
        return EmailDeliveryResult.builder().success(false).errorMessage(message).build();
    }
}
