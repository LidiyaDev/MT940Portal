package com.bank.mt940portal.dto;

import com.bank.mt940portal.domain.enums.ApprovalOperation;
import com.bank.mt940portal.domain.enums.ApprovalStatus;
import com.bank.mt940portal.domain.enums.EntityType;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class ApprovalRequestDto {

    private Long id;
    private EntityType entityType;
    private Long entityId;
    private String entityLabel;
    private ApprovalOperation operation;
    private ApprovalStatus status;

    private String requestedBy;
    private LocalDateTime requestedAt;
    private String requestReason;

    /** Proposed state, as submitted by the maker. */
    private String payloadJson;

    /** State at the time the request was raised, for the side-by-side diff. */
    private String currentJson;

    private String diffSummary;

    private String reviewedBy;
    private LocalDateTime reviewedAt;
    private String reviewNote;

    /** True when the current user is not allowed to review this request. */
    private boolean selfApprovalBlocked;
}
