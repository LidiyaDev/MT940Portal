package com.bank.mt940portal.dto;

import com.bank.mt940portal.domain.enums.ApprovalOperation;
import com.bank.mt940portal.domain.enums.ApprovalStatus;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class PendingChangeDto {

    private Long approvalId;
    private ApprovalOperation operation;
    private ApprovalStatus status;
    private String requestedBy;
    private LocalDateTime requestedAt;
    private String reviewNote;
}
