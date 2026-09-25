package com.bank.mt940portal.audit;

import com.bank.mt940portal.domain.enums.AuditOutcome;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

/**
 * Writes an audit row around every {@link Auditable} operation, recording the
 * outcome and - on failure - the exception message.
 */
@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class AuditAspect {

    private final AuditService auditService;

    @Around("@annotation(auditable)")
    public Object audit(ProceedingJoinPoint joinPoint, Auditable auditable) throws Throwable {
        long started = System.currentTimeMillis();
        try {
            Object result = joinPoint.proceed();
            auditService.record(AuditEntry.builder()
                    .action(auditable.action())
                    .entityType(auditable.entityType())
                    .description(defaultDescription(auditable, joinPoint))
                    .outcome(AuditOutcome.SUCCESS)
                    .detail(java.util.Map.of("method", joinPoint.getSignature().toShortString(),
                            "durationMs", System.currentTimeMillis() - started))
                    .build());
            return result;
        } catch (Throwable ex) {
            auditService.record(AuditEntry.builder()
                    .action(auditable.action())
                    .entityType(auditable.entityType())
                    .description(defaultDescription(auditable, joinPoint) + " - " + ex.getMessage())
                    .outcome(AuditOutcome.FAILURE)
                    .build());
            throw ex;
        }
    }

    private String defaultDescription(Auditable auditable, ProceedingJoinPoint joinPoint) {
        return auditable.description().isBlank()
                ? joinPoint.getSignature().getName()
                : auditable.description();
    }
}
