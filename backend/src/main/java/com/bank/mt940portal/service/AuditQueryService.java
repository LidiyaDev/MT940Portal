package com.bank.mt940portal.service;

import com.bank.mt940portal.audit.AuditLog;
import com.bank.mt940portal.audit.AuditLogRepository;
import com.bank.mt940portal.dto.AuditLogDto;
import com.bank.mt940portal.dto.PageResponse;
import com.bank.mt940portal.security.CurrentUser;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

/** Search and export of the audit trail. */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AuditQueryService {

    private final AuditLogRepository repository;
    private final CurrentUser currentUser;

    @PreAuthorize("hasAnyAuthority(@securityRoles.checkerAuthority, @securityRoles.adminAuthority, "
            + "@securityRoles.viewerAuthority, @securityRoles.makerAuthority)")
    public PageResponse<AuditLogDto> search(AuditFilter filter, Pageable pageable) {
        Specification<AuditLog> spec = (root, query, builder) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (filter.from() != null) {
                predicates.add(builder.greaterThanOrEqualTo(root.get("eventTime"),
                        filter.from().atStartOfDay()));
            }
            if (filter.to() != null) {
                predicates.add(builder.lessThanOrEqualTo(root.get("eventTime"),
                        filter.to().atTime(LocalTime.MAX)));
            }
            if (StringUtils.hasText(filter.actor())) {
                predicates.add(builder.like(builder.lower(root.get("actorUsername")),
                        "%" + filter.actor().trim().toLowerCase() + "%"));
            }
            if (StringUtils.hasText(filter.action())) {
                predicates.add(builder.equal(root.get("action"), filter.action().trim()));
            }
            if (StringUtils.hasText(filter.entityType())) {
                predicates.add(builder.equal(root.get("entityType"), filter.entityType().trim()));
            }
            if (StringUtils.hasText(filter.entityId())) {
                predicates.add(builder.equal(root.get("entityId"), filter.entityId().trim()));
            }
            if (StringUtils.hasText(filter.outcome())) {
                predicates.add(builder.equal(root.get("outcome"),
                        com.bank.mt940portal.domain.enums.AuditOutcome.valueOf(
                                filter.outcome().trim().toUpperCase())));
            }
            if (StringUtils.hasText(filter.correlationId())) {
                predicates.add(builder.equal(root.get("correlationId"), filter.correlationId().trim()));
            }
            if (StringUtils.hasText(filter.search())) {
                String like = "%" + filter.search().trim().toLowerCase() + "%";
                predicates.add(builder.or(
                        builder.like(builder.lower(root.get("description")), like),
                        builder.like(builder.lower(root.get("entityLabel")), like),
                        builder.like(builder.lower(root.get("action")), like)));
            }
            return builder.and(predicates.toArray(Predicate[]::new));
        };

        Page<AuditLog> page = repository.findAll(spec, pageable);
        return PageResponse.of(page, this::toDto);
    }

    @PreAuthorize("hasAnyAuthority(@securityRoles.checkerAuthority, @securityRoles.adminAuthority, "
            + "@securityRoles.viewerAuthority, @securityRoles.makerAuthority)")
    public List<AuditLogDto> history(String entityType, String entityId) {
        return repository.findTop50ByEntityTypeAndEntityIdOrderByEventTimeDesc(entityType, entityId)
                .stream().map(this::toDto).toList();
    }

    public List<AuditLogDto> recent(int limit) {
        return repository.findAll(
                        org.springframework.data.domain.PageRequest.of(0, Math.max(1, limit),
                                org.springframework.data.domain.Sort.by(
                                        org.springframework.data.domain.Sort.Direction.DESC, "eventTime")))
                .stream().map(this::toDto).toList();
    }

    private AuditLogDto toDto(AuditLog log) {
        return AuditLogDto.builder()
                .id(log.getId())
                .eventTime(log.getEventTime())
                .actorUsername(log.getActorUsername())
                .actorName(log.getActorName())
                .actorRoles(log.getActorRoles())
                .action(log.getAction())
                .entityType(log.getEntityType())
                .entityId(log.getEntityId())
                .entityLabel(log.getEntityLabel())
                .description(log.getDescription())
                .outcome(log.getOutcome())
                .oldValue(log.getOldValue())
                .newValue(log.getNewValue())
                .detail(log.getDetail())
                .ipAddress(log.getIpAddress())
                .sessionId(log.getSessionId())
                .correlationId(log.getCorrelationId())
                .build();
    }

    public record AuditFilter(LocalDate from,
                              LocalDate to,
                              String actor,
                              String action,
                              String entityType,
                              String entityId,
                              String outcome,
                              String correlationId,
                              String search) {
    }

    /** Convenience for the dashboard counters. */
    public long countFailuresSince(LocalDateTime since) {
        return repository.countFailuresSince(since);
    }
}
