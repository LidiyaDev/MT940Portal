package com.bank.mt940portal.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class UpcomingDeliveryDto {

    private Long scheduleId;
    private String clientCode;
    private String clientName;
    private String accountNumber;
    private String frequency;
    private LocalDateTime nextRunAt;
}
