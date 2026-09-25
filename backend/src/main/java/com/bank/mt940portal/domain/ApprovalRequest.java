package com.bank.mt940portal.domain;

import com.bank.mt940portal.domain.enums.ApprovalOperation;
import com.bank.mt940portal.domain.enums.ApprovalStatus;
import com.bank.mt940portal.domain.enums.EntityType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Maker / checker queue entry.
 * <p>
 * A maker never mutates master data directly: the proposed new state is stored
 * here as {@code payloadJson} and only applied by a different user holding the
 * checker role (enforced in {@code ApprovalService} and by a DB check
 * constraint).
 */
@Entity
@Table(name = "MT_APPROVAL_REQUEST")
@Getter
@Setter
public class ApprovalRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "mt940_seq_gen")
    @SequenceGenerator(name = "mt940_seq_gen", sequenceName = "MT940_SEQ", allocationSize = 50)
    @Column(name = "ID", nullable = false, updatable = false)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "ENTITY_TYPE", nullable = false, length = 40)
    private EntityType entityType;

    /** Null for CREATE operations. */
    @Column(name = "ENTITY_ID")
    private Long entityId;

    /** Human readable target, e.g. client code or account number. */
    @Column(name = "ENTITY_LABEL", length = 200)
    private String entityLabel;

    @Enumerated(EnumType.STRING)
    @Column(name = "OPERATION", nullable = false, length = 20)
    private ApprovalOperation operation;

    @Enumerated(EnumType.STRING)
    @Column(name = "STATUS", nullable = false, length = 20)
    private ApprovalStatus status = ApprovalStatus.PENDING;

    @Column(name = "REQUESTED_BY", nullable = false, length = 120)
    private String requestedBy;

    @Column(name = "REQUESTED_AT")
    private LocalDateTime requestedAt;

    @Column(name = "REQUEST_REASON", length = 1000)
    private String requestReason;

    /** Proposed state, serialised as JSON. */
    @Lob
    @Column(name = "PAYLOAD_JSON")
    private String payloadJson;

    /** State as it was when the request was raised, for the diff view. */
    @Lob
    @Column(name = "CURRENT_JSON")
    private String currentJson;

    @Column(name = "DIFF_SUMMARY", length = 2000)
    private String diffSummary;

    @Column(name = "REVIEWED_BY", length = 120)
    private String reviewedBy;

    @Column(name = "REVIEWED_AT")
    private LocalDateTime reviewedAt;

    @Column(name = "REVIEW_NOTE", length = 1000)
    private String reviewNote;

    @Version
    @Column(name = "VERSION", nullable = false)
    private Integer version = 0;
}
