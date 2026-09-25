package com.bank.mt940portal.dto;

import com.bank.mt940portal.domain.enums.DeliveryStatus;
import com.bank.mt940portal.domain.enums.StatementStatus;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Builder
public class StatementDto {

    private Long id;
    private Long clientId;
    private String clientCode;
    private String clientName;
    private Long accountId;
    private String accountNumber;

    private String statementReference;
    private Long statementNumber;
    private Integer pageSequence;
    private String isn;

    private LocalDate periodFrom;
    private LocalDate periodTo;
    private String currency;

    private BigDecimal openingBalance;
    private String openingMark;
    private BigDecimal closingBalance;
    private String closingMark;
    private Integer transactionCount;

    private String fileName;
    private String checksum;
    private StatementStatus status;
    private DeliveryStatus deliveryStatus;
    private String errorMessage;

    private LocalDateTime generatedAt;
    private String generatedBy;

    /** Populated only when a single statement is fetched. */
    private String content;
}
