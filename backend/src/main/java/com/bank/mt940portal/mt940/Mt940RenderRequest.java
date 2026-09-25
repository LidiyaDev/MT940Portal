package com.bank.mt940portal.mt940;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Getter
@Builder
public class Mt940RenderRequest {

    private String accountNumber;
    private String currency;
    private LocalDate statementDate;
    private LocalDate periodFrom;
    private LocalDate periodTo;

    /** Tag :28C: statement number. */
    private long statementNumber;

    /** Tag :28C: page / sequence number. */
    private int pageSequence;

    /** Input sequence number for the basic header; derived when null. */
    private String isn;

    private BigDecimal openingBalance;
    private BigDecimal closingBalance;

    private LocalDateTime generationTime;

    @Builder.Default
    private List<Mt940Transaction> transactions = new ArrayList<>();

    /** Resolved rendering settings (global defaults merged with client overrides). */
    private SwiftProperties settings;
}
