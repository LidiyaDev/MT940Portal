package com.bank.mt940portal.service;

import com.bank.mt940portal.audit.AuditEntry;
import com.bank.mt940portal.audit.AuditService;
import com.bank.mt940portal.domain.ApprovalRequest;
import com.bank.mt940portal.domain.enums.ApprovalOperation;
import com.bank.mt940portal.domain.enums.ApprovalStatus;
import com.bank.mt940portal.domain.enums.EntityType;
import com.bank.mt940portal.repository.ApprovalRequestRepository;
import com.bank.mt940portal.security.CurrentUser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * Creates maker-checker queue entries.
 * <p>
 * Every mutating operation on master data or delivery configuration goes through
 * here: the change is stored as a pending request and nothing is written to the
 * live tables until a checker approves it.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ApprovalRequestFactory {

    private final ApprovalRequestRepository repository;
    private final ObjectMapper objectMapper;
    private final DiffUtil diffUtil;
    private final CurrentUser currentUser;
    private final AuditService auditService;

    public ApprovalRequest submit(Submission submission) {
        ApprovalRequest request = new ApprovalRequest();
        request.setEntityType(submission.entityType());
        request.setEntityId(submission.entityId());
        request.setEntityLabel(submission.entityLabel());
        request.setOperation(submission.operation());
        request.setStatus(ApprovalStatus.PENDING);
        request.setRequestedBy(currentUser.username());
        request.setRequestedAt(LocalDateTime.now());
        request.setRequestReason(submission.reason());
        request.setPayloadJson(toJson(submission.payload()));
        request.setCurrentJson(toJson(submission.current()));
        request.setDiffSummary(diffUtil.summarise(request.getCurrentJson(), request.getPayloadJson()));

        ApprovalRequest saved = repository.save(request);

        auditService.record(AuditEntry.builder()
                .action(switch (submission.operation()) {
                    case CREATE -> auditActionCreate(submission.entityType());
                    case UPDATE -> auditActionUpdate(submission.entityType());
                    case DELETE -> auditActionDelete(submission.entityType());
                    default -> auditActionStatus(submission.entityType());
                })
                .entityType(submission.entityType().name())
                .entityId(submission.entityId() == null ? null : String.valueOf(submission.entityId()))
                .entityLabel(submission.entityLabel())
                .description("Submitted for approval: " + submission.operation().name().toLowerCase()
                        + " " + submission.entityType().name().toLowerCase()
                        + " " + submission.entityLabel())
                .newValue(submission.payload())
                .build());
        return saved;
    }

    private static String auditActionCreate(EntityType type) {
        return switch (type) {
            case CLIENT -> com.bank.mt940portal.audit.AuditAction.CLIENT_CREATE;
            case ACCOUNT -> com.bank.mt940portal.audit.AuditAction.ACCOUNT_CREATE;
            case DELIVERY_SCHEDULE -> com.bank.mt940portal.audit.AuditAction.SCHEDULE_CREATE;
            case EMAIL_CONFIG -> com.bank.mt940portal.audit.AuditAction.EMAIL_CONFIG_CREATE;
            case STATEMENT -> com.bank.mt940portal.audit.AuditAction.STATEMENT_GENERATE;
        };
    }

    private static String auditActionUpdate(EntityType type) {
        return switch (type) {
            case CLIENT -> com.bank.mt940portal.audit.AuditAction.CLIENT_UPDATE;
            case ACCOUNT -> com.bank.mt940portal.audit.AuditAction.ACCOUNT_UPDATE;
            case DELIVERY_SCHEDULE -> com.bank.mt940portal.audit.AuditAction.SCHEDULE_UPDATE;
            case EMAIL_CONFIG -> com.bank.mt940portal.audit.AuditAction.EMAIL_CONFIG_UPDATE;
            case STATEMENT -> com.bank.mt940portal.audit.AuditAction.STATEMENT_SEND;
        };
    }

    private static String auditActionDelete(EntityType type) {
        return switch (type) {
            case CLIENT -> com.bank.mt940portal.audit.AuditAction.CLIENT_DELETE;
            case ACCOUNT -> com.bank.mt940portal.audit.AuditAction.ACCOUNT_DELETE;
            case DELIVERY_SCHEDULE -> com.bank.mt940portal.audit.AuditAction.SCHEDULE_DELETE;
            case EMAIL_CONFIG -> com.bank.mt940portal.audit.AuditAction.EMAIL_CONFIG_DELETE;
            case STATEMENT -> com.bank.mt940portal.audit.AuditAction.STATEMENT_SEND;
        };
    }

    private static String auditActionStatus(EntityType type) {
        return switch (type) {
            case CLIENT -> com.bank.mt940portal.audit.AuditAction.CLIENT_STATUS_CHANGE;
            case ACCOUNT -> com.bank.mt940portal.audit.AuditAction.ACCOUNT_STATUS_CHANGE;
            case DELIVERY_SCHEDULE -> com.bank.mt940portal.audit.AuditAction.SCHEDULE_TOGGLE;
            default -> com.bank.mt940portal.audit.AuditAction.SCHEDULE_UPDATE;
        };
    }

    private String toJson(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            log.warn("Unable to serialise approval payload", ex);
            return String.valueOf(value);
        }
    }

    public record Submission(EntityType entityType,
                             Long entityId,
                             String entityLabel,
                             ApprovalOperation operation,
                             Object payload,
                             Object current,
                             String reason) {

        public static Submission of(EntityType entityType, Long entityId, String entityLabel,
                                    ApprovalOperation operation, Object payload,
                                    Object current, String reason) {
            return new Submission(entityType, entityId, entityLabel, operation, payload, current, reason);
        }
    }
}
