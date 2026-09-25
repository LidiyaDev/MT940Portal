package com.bank.mt940portal.service;

import com.bank.mt940portal.audit.AuditAction;
import com.bank.mt940portal.audit.AuditEntry;
import com.bank.mt940portal.audit.AuditService;
import com.bank.mt940portal.domain.Account;
import com.bank.mt940portal.domain.ApprovalRequest;
import com.bank.mt940portal.domain.DeliverySchedule;
import com.bank.mt940portal.domain.enums.ApprovalOperation;
import com.bank.mt940portal.domain.enums.ApprovalStatus;
import com.bank.mt940portal.domain.enums.EntityType;
import com.bank.mt940portal.domain.enums.Frequency;
import com.bank.mt940portal.dto.DeliveryScheduleDto;
import com.bank.mt940portal.dto.PendingChangeDto;
import com.bank.mt940portal.exception.BusinessException;
import com.bank.mt940portal.exception.ResourceNotFoundException;
import com.bank.mt940portal.repository.ApprovalRequestRepository;
import com.bank.mt940portal.repository.DeliveryScheduleRepository;
import com.bank.mt940portal.scheduler.ScheduleCalculator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Delivery schedules: how often and at what time a client receives statements.
 * <p>
 * A schedule belongs to a client and optionally to one of its accounts. When
 * {@code accountId} is null the schedule covers every active account of the
 * client, which is the common case for corporate customers.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DeliveryScheduleService {

    private final DeliveryScheduleRepository repository;
    private final ApprovalRequestRepository approvalRequestRepository;
    private final ApprovalRequestFactory approvalRequestFactory;
    private final ClientService clientService;
    private final AccountService accountService;
    private final ScheduleCalculator scheduleCalculator;
    private final AuditService auditService;

    // ------------------------------------------------------------------ reads

    public List<DeliveryScheduleDto> listByClient(Long clientId) {
        return repository.findAllByClientIdOrderByIdAsc(clientId).stream().map(this::toDto).toList();
    }

    public List<DeliveryScheduleDto> listByAccount(Long accountId) {
        return repository.findAllByAccountIdOrderByIdAsc(accountId).stream().map(this::toDto).toList();
    }

    public List<DeliveryScheduleDto> listDue(LocalDateTime now) {
        return repository.findDueSchedules(now).stream().map(this::toDto).toList();
    }

    public DeliveryScheduleDto get(Long id) {
        return toDto(findEntity(id));
    }

    public DeliverySchedule findEntity(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(DeliverySchedule.class, id));
    }

    public DeliveryScheduleDto toDto(DeliverySchedule schedule) {
        DeliveryScheduleDto dto = DeliveryScheduleDto.builder()
                .id(schedule.getId())
                .clientId(schedule.getClient().getId())
                .clientCode(schedule.getClient().getClientCode())
                .accountId(schedule.getAccount() == null ? null : schedule.getAccount().getId())
                .accountNumber(schedule.getAccount() == null ? null : schedule.getAccount().getAccountNumber())
                .name(schedule.getName())
                .frequency(schedule.getFrequency())
                .dayOfWeek(schedule.getDayOfWeek())
                .dayOfMonth(schedule.getDayOfMonth())
                .sendTime(schedule.getSendTime())
                .timezone(schedule.getTimezone())
                .periodStrategy(schedule.getPeriodStrategy())
                .enabled(schedule.isEnabled())
                .includeZeroTransactionStatements(schedule.isIncludeZeroTransactionStatements())
                .lastSentAt(schedule.getLastSentAt())
                .nextRunAt(schedule.getNextRunAt())
                .description(schedule.describe())
                .remarks(schedule.getRemarks())
                .createdAt(schedule.getCreatedAt())
                .createdBy(schedule.getCreatedBy())
                .updatedAt(schedule.getUpdatedAt())
                .updatedBy(schedule.getUpdatedBy())
                .build();

        pendingChange(schedule.getId()).ifPresent(dto::setPendingChange);
        return dto;
    }

    private Optional<PendingChangeDto> pendingChange(Long scheduleId) {
        return approvalRequestRepository
                .findFirstByEntityTypeAndEntityIdAndStatus(
                        EntityType.DELIVERY_SCHEDULE, scheduleId, ApprovalStatus.PENDING)
                .map(request -> PendingChangeDto.builder()
                        .approvalId(request.getId())
                        .operation(request.getOperation())
                        .status(request.getStatus())
                        .requestedBy(request.getRequestedBy())
                        .requestedAt(request.getRequestedAt())
                        .reviewNote(request.getReviewNote())
                        .build());
    }

    // ------------------------------------------------------- maker submissions

    @PreAuthorize("hasAnyAuthority(@securityRoles.makerAuthority, @securityRoles.adminAuthority)")
    @Transactional
    public ApprovalRequest submitCreate(DeliveryScheduleDto dto, String reason) {
        validate(dto);
        clientService.findEntity(dto.getClientId());
        return approvalRequestFactory.submit(ApprovalRequestFactory.Submission.of(
                EntityType.DELIVERY_SCHEDULE, null, describe(dto), ApprovalOperation.CREATE,
                dto, null, reason));
    }

    @PreAuthorize("hasAnyAuthority(@securityRoles.makerAuthority, @securityRoles.adminAuthority)")
    @Transactional
    public ApprovalRequest submitUpdate(Long id, DeliveryScheduleDto dto, String reason) {
        DeliverySchedule existing = findEntity(id);
        rejectIfPending(id);
        validate(dto);
        return approvalRequestFactory.submit(ApprovalRequestFactory.Submission.of(
                EntityType.DELIVERY_SCHEDULE, id, existing.describe(), ApprovalOperation.UPDATE,
                dto, toDto(existing), reason));
    }

    @PreAuthorize("hasAnyAuthority(@securityRoles.makerAuthority, @securityRoles.adminAuthority)")
    @Transactional
    public ApprovalRequest submitToggle(Long id, boolean enabled, String reason) {
        DeliverySchedule existing = findEntity(id);
        rejectIfPending(id);
        DeliveryScheduleDto payload = DeliveryScheduleDto.builder()
                .id(id)
                .clientId(existing.getClient().getId())
                .accountId(existing.getAccount() == null ? null : existing.getAccount().getId())
                .frequency(existing.getFrequency())
                .sendTime(existing.getSendTime())
                .timezone(existing.getTimezone())
                .enabled(enabled)
                .build();
        return approvalRequestFactory.submit(ApprovalRequestFactory.Submission.of(
                EntityType.DELIVERY_SCHEDULE, id, existing.describe(), ApprovalOperation.UPDATE,
                payload, toDto(existing), reason));
    }

    @PreAuthorize("hasAnyAuthority(@securityRoles.makerAuthority, @securityRoles.adminAuthority)")
    @Transactional
    public ApprovalRequest submitDelete(Long id, String reason) {
        DeliverySchedule existing = findEntity(id);
        rejectIfPending(id);
        DeliveryScheduleDto current = toDto(existing);
        return approvalRequestFactory.submit(ApprovalRequestFactory.Submission.of(
                EntityType.DELIVERY_SCHEDULE, id, existing.describe(), ApprovalOperation.DELETE,
                current, current, reason));
    }

    // ------------------------------------------------------ checker application

    @Transactional
    public DeliverySchedule applyCreate(DeliveryScheduleDto dto) {
        validate(dto);
        DeliverySchedule schedule = new DeliverySchedule();
        schedule.setClient(clientService.findEntity(dto.getClientId()));
        schedule.setAccount(dto.getAccountId() == null ? null : accountService.findEntity(dto.getAccountId()));
        copyTo(dto, schedule);
        schedule.setLastSentAt(null);
        schedule.setNextRunAt(scheduleCalculator.nextRun(schedule, scheduleCalculator.now()));
        DeliverySchedule saved = repository.save(schedule);

        auditService.record(AuditEntry.builder()
                .action(AuditAction.SCHEDULE_CREATE)
                .entityType(EntityType.DELIVERY_SCHEDULE.name())
                .entityId(String.valueOf(saved.getId()))
                .entityLabel(describe(dto))
                .description("Approved creation of delivery schedule: " + saved.describe())
                .newValue(dto)
                .build());
        return saved;
    }

    @Transactional
    public DeliverySchedule applyUpdate(Long id, DeliveryScheduleDto dto) {
        DeliverySchedule schedule = findEntity(id);
        Object before = toDto(schedule);
        boolean onlyEnabledChanged = dto.getFrequency() == null && dto.getSendTime() == null;

        if (dto.getAccountId() != null && (schedule.getAccount() == null
                || !dto.getAccountId().equals(schedule.getAccount().getId()))) {
            schedule.setAccount(accountService.findEntity(dto.getAccountId()));
        }
        copyTo(dto, schedule);
        schedule = repository.save(schedule);

        if (schedule.isEnabled()) {
            schedule.setNextRunAt(scheduleCalculator.nextRun(schedule, scheduleCalculator.now()));
            schedule = repository.save(schedule);
        }

        auditService.record(AuditEntry.builder()
                .action(onlyEnabledChanged ? AuditAction.SCHEDULE_TOGGLE : AuditAction.SCHEDULE_UPDATE)
                .entityType(EntityType.DELIVERY_SCHEDULE.name())
                .entityId(String.valueOf(id))
                .entityLabel(schedule.describe())
                .description("Approved update of delivery schedule: " + schedule.describe())
                .oldValue(before)
                .newValue(dto)
                .build());
        return schedule;
    }

    @Transactional
    public void applyDelete(Long id) {
        DeliverySchedule schedule = findEntity(id);
        DeliveryScheduleDto before = toDto(schedule);
        repository.delete(schedule);

        auditService.record(AuditEntry.builder()
                .action(AuditAction.SCHEDULE_DELETE)
                .entityType(EntityType.DELIVERY_SCHEDULE.name())
                .entityId(String.valueOf(id))
                .entityLabel(before.getDescription())
                .description("Approved deletion of delivery schedule " + id)
                .oldValue(before)
                .build());
    }

    private void copyTo(DeliveryScheduleDto dto, DeliverySchedule schedule) {
        if (dto.getName() != null) {
            schedule.setName(dto.getName().trim());
        }
        if (dto.getFrequency() != null) {
            schedule.setFrequency(dto.getFrequency());
        }
        if (dto.getDayOfWeek() != null) {
            schedule.setDayOfWeek(dto.getDayOfWeek());
        }
        if (dto.getDayOfMonth() != null) {
            schedule.setDayOfMonth(dto.getDayOfMonth());
        }
        if (dto.getSendTime() != null) {
            schedule.setSendTime(dto.getSendTime());
        }
        if (dto.getTimezone() != null) {
            schedule.setTimezone(dto.getTimezone());
        }
        if (dto.getPeriodStrategy() != null) {
            schedule.setPeriodStrategy(dto.getPeriodStrategy());
        }
        if (dto.getEnabled() != null) {
            schedule.setEnabled(dto.getEnabled());
        }
        if (dto.getIncludeZeroTransactionStatements() != null) {
            schedule.setIncludeZeroTransactionStatements(dto.getIncludeZeroTransactionStatements());
        }
        if (dto.getRemarks() != null) {
            schedule.setRemarks(dto.getRemarks());
        }
    }

    /** Recomputes and stores the next firing time after a successful run. */
    @Transactional
    public void markSent(DeliverySchedule schedule, LocalDateTime sentAt) {
        schedule.setLastSentAt(sentAt);
        schedule.setNextRunAt(scheduleCalculator.nextRun(schedule, sentAt));
        repository.save(schedule);
    }

    private void validate(DeliveryScheduleDto dto) {
        if (dto.getClientId() == null) {
            throw new BusinessException("A delivery schedule must belong to a client");
        }
        if (dto.getFrequency() == Frequency.WEEKLY
                && (dto.getDayOfWeek() == null || dto.getDayOfWeek() < 1 || dto.getDayOfWeek() > 7)) {
            throw new BusinessException("A weekly schedule requires a day of week (1 = Monday .. 7 = Sunday)");
        }
        if (dto.getFrequency() == Frequency.MONTHLY
                && (dto.getDayOfMonth() == null || dto.getDayOfMonth() < 1 || dto.getDayOfMonth() > 31)) {
            throw new BusinessException("A monthly schedule requires a day of month between 1 and 31");
        }
        if (dto.getSendTime() == null || !dto.getSendTime().matches("([01]\\d|2[0-3]):[0-5]\\d")) {
            throw new BusinessException("Send time must be in HH:mm format");
        }
    }

    private String describe(DeliveryScheduleDto dto) {
        String scope = dto.getAccountId() == null ? "all accounts" : "account " + dto.getAccountId();
        return dto.getFrequency() + " " + scope + " at " + dto.getSendTime();
    }

    private void rejectIfPending(Long scheduleId) {
        if (pendingChange(scheduleId).isPresent()) {
            throw new BusinessException(
                    "This schedule already has a pending change waiting for approval");
        }
    }

    /** Accounts a schedule covers: the configured one, or every active account. */
    public List<Account> resolveAccounts(DeliverySchedule schedule) {
        if (schedule.getAccount() != null) {
            return List.of(schedule.getAccount());
        }
        return accountService.activeAccountsOf(schedule.getClient().getId());
    }
}
