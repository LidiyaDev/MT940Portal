package com.bank.mt940portal.integration.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * One entry returned by the core banking endpoint.
 * <p>
 * Unknown properties are ignored and the numeric fields are aliased so that a
 * switch from {@code debit_amt} to {@code debitAmt} (or the addition of a credit
 * column) does not break the mapping.
 */
@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class TransactionRecord {

    private String transactionDate;

    @JsonAlias({"trans_reference", "transReference", "reference", "transactionReference"})
    private String transReference;

    @JsonAlias({"debit_amt", "debitAmt", "debit_amount", "debitAmount"})
    private BigDecimal debitAmt;

    @JsonAlias({"credit_amt", "creditAmt", "credit_amount", "creditAmount"})
    private BigDecimal creditAmt;

    @JsonAlias({"beginning_balance", "beginningBalance", "opening_balance", "openingBalance"})
    private BigDecimal beginningBalance;

    @JsonAlias({"closing_balance", "closingBalance"})
    private BigDecimal closingBalance;

    @JsonAlias({"txnDescription", "txn_description", "description", "narrative"})
    private String txnDescription;
}
