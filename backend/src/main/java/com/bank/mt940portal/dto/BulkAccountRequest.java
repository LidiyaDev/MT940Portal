package com.bank.mt940portal.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/** Adds several accounts to a client in a single maker request. */
@Data
public class BulkAccountRequest {

    @NotNull
    private Long clientId;

    @Valid
    @NotEmpty(message = "At least one account is required")
    private List<AccountDto> accounts = new ArrayList<>();

    private String reason;
}
