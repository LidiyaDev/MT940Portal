package com.bank.mt940portal.dto;

import com.bank.mt940portal.domain.enums.AuditOutcome;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class AuditLogDto {

    private Long id;
    private LocalDateTime eventTime;
    private String actorUsername;
    private String actorName;
    private String actorRoles;
    private String action;
    private String entityType;
    private String entityId;
    private String entityLabel;
    private String description;
    private AuditOutcome outcome;
    private String oldValue;
    private String newValue;
    private String detail;
    private String ipAddress;
    private String sessionId;
    private String correlationId;
}
