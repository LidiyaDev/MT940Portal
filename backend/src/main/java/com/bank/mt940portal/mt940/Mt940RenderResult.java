package com.bank.mt940portal.mt940;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;

@Getter
@Builder
public class Mt940RenderResult {

    /** The full message including the {1:}/{2:}/{4:} blocks. */
    private String content;

    private String statementReference;
    private long statementNumber;
    private int pageSequence;
    private String isn;
    private String openingMark;
    private String closingMark;
    private BigDecimal openingBalance;
    private BigDecimal closingBalance;
    private int transactionCount;
    private String suggestedFileName;
}
