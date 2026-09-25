package com.bank.mt940portal.mt940;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

/** One entry to be rendered as a :61:/:86: pair. */
@Getter
@Setter
@Builder
public class Mt940Transaction {

    private LocalDate transactionDate;

    /** Defaults to transactionDate. */
    private LocalDate valueDate;

    /** Entry date is rendered as MMDD; defaults to valueDate. */
    private LocalDate entryDate;

    /** Always positive; the direction is carried by debitCredit. */
    private BigDecimal amount;

    /** "D" or "C". */
    private String debitCredit;

    /** 16x customer reference. */
    private String customerReference;

    /** Optional bank reference emitted after "//". */
    private String bankReference;

    /** 1!a3!c transaction type identification code, e.g. NTRF. */
    private String transactionType;

    /** Free text for tag :86:. */
    private String description;

    public boolean isDebit() {
        return "D".equalsIgnoreCase(debitCredit);
    }
}
