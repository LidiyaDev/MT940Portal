package com.bank.mt940portal.dto;

import com.bank.mt940portal.domain.enums.DeliveryStatus;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class DeliveryLogDto {

    private Long id;
    private Long statementId;
    private String statementReference;
    private Long clientId;
    private String clientCode;
    private Long accountId;
    private String accountNumber;
    private String recipient;
    private String subject;
    private DeliveryStatus status;
    private Integer attempt;
    private String provider;
    private String providerResponse;
    private String errorMessage;
    private String correlationId;
    private String sentBy;
    private LocalDateTime sentAt;
}
