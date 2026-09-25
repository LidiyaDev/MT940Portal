package com.bank.mt940portal.audit;

import com.bank.mt940portal.domain.enums.AuditOutcome;
import com.bank.mt940portal.security.CurrentUser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Writes the append-only activity trail.
 * <p>
 * Audit rows are persisted in their own transaction ({@code REQUIRES_NEW}) so a
 * record survives a rollback of the business transaction that triggered it -
 * a failed change must still be traceable.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuditService {

    private final AuditLogRepository repository;
    private final CurrentUser currentUser;
    private final ObjectMapper objectMapper;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(AuditEntry entry) {
        if (entry == null || entry.getAction() == null) {
            return;
        }
        try {
            AuditLog log = new AuditLog();
            log.setEventTime(java.time.LocalDateTime.now());
            log.setActorUsername(entry.getActorOverride() != null
                    ? entry.getActorOverride() : currentUser.username());
            log.setActorName(currentUser.fullName());
            log.setActorRoles(currentUser.authoritiesCsv());
            log.setAction(entry.getAction());
            log.setEntityType(entry.getEntityType());
            log.setEntityId(entry.getEntityId() == null ? null : String.valueOf(entry.getEntityId()));
            log.setEntityLabel(entry.getEntityLabel());
            log.setDescription(entry.getDescription());
            log.setOutcome(entry.getOutcome() == null ? AuditOutcome.SUCCESS : entry.getOutcome());
            log.setOldValue(toJson(entry.getOldValue()));
            log.setNewValue(toJson(entry.getNewValue()));
            log.setDetail(toJson(entry.getDetail()));
            log.setIpAddress(clientIp());
            log.setUserAgent(userAgent());
            log.setSessionId(currentUser.sessionId());
            log.setCorrelationId(MDC.get(CorrelationFilter.MDC_KEY));
            repository.save(log);
        } catch (RuntimeException ex) {
            // Auditing must never break the business flow.
            log.error("Failed to write audit entry for action {}", entry.getAction(), ex);
        }
    }

    /** Convenience for the {@code system} actor (scheduler, startup tasks). */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void systemFailure(String path, String message) {
        record(AuditEntry.builder()
                .action("SYSTEM_ERROR")
                .entityType("REQUEST")
                .entityLabel(path)
                .description(message)
                .outcome(AuditOutcome.FAILURE)
                .actorOverride("system")
                .build());
    }

    private String toJson(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            return String.valueOf(value);
        }
    }

    private String clientIp() {
        var attributes = RequestContextHolder.getRequestAttributes();
        if (attributes instanceof ServletRequestAttributes servlet) {
            HttpServletRequest request = servlet.getRequest();
            String forwarded = request.getHeader("X-Forwarded-For");
            if (forwarded != null && !forwarded.isBlank()) {
                return forwarded.split(",")[0].trim();
            }
            return request.getRemoteAddr();
        }
        return null;
    }

    private String userAgent() {
        var attributes = RequestContextHolder.getRequestAttributes();
        if (attributes instanceof ServletRequestAttributes servlet) {
            return servlet.getRequest().getHeader("User-Agent");
        }
        return null;
    }
}
