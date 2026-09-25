package com.bank.mt940portal.audit;

import com.bank.mt940portal.domain.enums.AuditOutcome;
import lombok.Builder;
import lombok.Getter;

/** Fluent description of one auditable activity. */
@Getter
@Builder
public class AuditEntry {

    private String action;
    private String entityType;
    private String entityId;
    private String entityLabel;
    private String description;
    private Object oldValue;
    private Object newValue;
    private Object detail;

    @Builder.Default
    private AuditOutcome outcome = AuditOutcome.SUCCESS;

    /** Overrides the resolved actor; used by the scheduler for system runs. */
    private String actorOverride;
}
