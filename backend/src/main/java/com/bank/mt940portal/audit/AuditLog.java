package com.bank.mt940portal.audit;

import com.bank.mt940portal.domain.enums.AuditOutcome;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Append-only activity trail. Rows are never updated or deleted by the
 * application; there is deliberately no JPA auditing metadata on this entity.
 */
@Entity
@Table(name = "MT_AUDIT_LOG")
@Getter
@Setter
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "mt940_seq_gen")
    @SequenceGenerator(name = "mt940_seq_gen", sequenceName = "MT940_SEQ", allocationSize = 50)
    @Column(name = "ID", nullable = false, updatable = false)
    private Long id;

    @Column(name = "EVENT_TIME", nullable = false)
    private LocalDateTime eventTime;

    @Column(name = "ACTOR_USERNAME", length = 120)
    private String actorUsername;

    @Column(name = "ACTOR_NAME", length = 200)
    private String actorName;

    @Column(name = "ACTOR_ROLES", length = 500)
    private String actorRoles;

    @Column(name = "ACTION", nullable = false, length = 60)
    private String action;

    @Column(name = "ENTITY_TYPE", length = 60)
    private String entityType;

    @Column(name = "ENTITY_ID", length = 64)
    private String entityId;

    @Column(name = "ENTITY_LABEL", length = 200)
    private String entityLabel;

    @Column(name = "DESCRIPTION", length = 1000)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "OUTCOME", nullable = false, length = 20)
    private AuditOutcome outcome = AuditOutcome.SUCCESS;

    /** JSON snapshot before the change. */
    @Lob
    @Column(name = "OLD_VALUE")
    private String oldValue;

    /** JSON snapshot after the change (or the submitted payload). */
    @Lob
    @Column(name = "NEW_VALUE")
    private String newValue;

    @Lob
    @Column(name = "DETAIL")
    private String detail;

    @Column(name = "IP_ADDRESS", length = 64)
    private String ipAddress;

    @Column(name = "USER_AGENT", length = 500)
    private String userAgent;

    @Column(name = "SESSION_ID", length = 64)
    private String sessionId;

    @Column(name = "CORRELATION_ID", length = 64)
    private String correlationId;
}
