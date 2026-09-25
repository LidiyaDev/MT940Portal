package com.bank.mt940portal.integration.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/** Envelope returned by the core banking endpoint. */
@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class TransactionResponse {

    private Integer statusCode;
    private String message;

    private List<TransactionRecord> data = new ArrayList<>();
}
