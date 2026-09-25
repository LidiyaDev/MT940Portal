package com.bank.mt940portal.service;

import com.bank.mt940portal.audit.AuditAction;
import com.bank.mt940portal.audit.AuditEntry;
import com.bank.mt940portal.audit.AuditService;
import com.bank.mt940portal.domain.Account;
import com.bank.mt940portal.domain.ApprovalRequest;
import com.bank.mt940portal.domain.Statement;
import com.bank.mt940portal.domain.enums.ApprovalOperation;
import com.bank.mt940portal.domain.enums.ApprovalStatus;
import com.bank.mt940portal.domain.enums.EntityType;
import com.bank.mt940portal.dto.ApprovalRequestDto;
import com.bank.mt940portal.dto.SendStatementRequest;
import com.bank.mt940portal.dto.PageResponse;
import com.bank.mt940portal.exception.AccessDeniedException;
import com.bank.mt940portal.exception.BusinessException;
import com.bank.mt940portal.exception.ResourceNotFoundException;
import com.bank.mt940portal.repository.ApprovalRequestRepository;
import com.bank.mt940portal.security.CurrentUser;
import com.bank.mt940portal.security.SecurityRoles;
import com.bank.mt940portal.service.approval.ApprovalApplier;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * The checker side of maker-checker.
 * <p>
 * Two rules are enforced here and backed by a database check constraint:
 * only a user with the checker (or admin) authority may decide, and the person
 * who raised the request may never be the one who approves it.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ApprovalService {

    private final ApprovalRequestRepository repository;
    private final List<ApprovalApplier> appliers;
    private final ApprovalRequestFactory approvalRequestFactory;
    private final StatementService statementService;
    private final AccountService accountService;
    private final CurrentUser currentUser;
    private final SecurityRoles securityRoles;
    private final AuditService auditService;

    private Map<EntityType, ApprovalApplier> appliersByType() {
        Map<EntityType, ApprovalApplier> map = new EnumMap<>(EntityType.class);
        for (ApprovalApplier applier : appliers) {
            map.put(applier.supports(), applier);
        }
        return map;
    }

    // ------------------------------------------------------------------ reads

    public PageResponse<ApprovalRequestDto> list(ApprovalStatus status, Pageable pageable) {
        Page<ApprovalRequest> page = status == null
                ? repository.findAllByOrderByRequestedAtDesc(pageable)
                : repository.findAllByStatusOrderByRequestedAtDesc(status, pageable);
        return PageResponse.of(page, this::toDto);
    }

    public List<ApprovalRequestDto> listPending() {
        return repository.findAllByStatusOrderByRequestedAtAsc(ApprovalStatus.PENDING)
                .stream().map(this::toDto).toList();
    }

    public ApprovalRequestDto get(Long id) {
        return toDto(findEntity(id));
    }

    public ApprovalRequest findEntity(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(ApprovalRequest.class, id));
    }

    public long countPending() {
        return repository.countByStatus(ApprovalStatus.PENDING);
    }

    public ApprovalRequestDto toDto(ApprovalRequest request) {
        String reviewer = currentUser.username();
        return ApprovalRequestDto.builder()
                .id(request.getId())
                .entityType(request.getEntityType())
                .entityId(request.getEntityId())
                .entityLabel(request.getEntityLabel())
                .operation(request.getOperation())
                .status(request.getStatus())
                .requestedBy(request.getRequestedBy())
                .requestedAt(request.getRequestedAt())
                .requestReason(request.getRequestReason())
                .payloadJson(request.getPayloadJson())
                .currentJson(request.getCurrentJson())
                .diffSummary(request.getDiffSummary())
                .reviewedBy(request.getReviewedBy())
                .reviewedAt(request.getReviewedAt())
                .reviewNote(request.getReviewNote())
                .selfApprovalBlocked(request.getRequestedBy() != null
                        && request.getRequestedBy().equalsIgnoreCase(reviewer))
                .build();
    }

    // ---------------------------------------------------------------- decisions

    @PreAuthorize("hasAnyAuthority(@securityRoles.checkerAuthority, @securityRoles.adminAuthority)")
    @Transactional
    public ApprovalRequest approve(Long id, String note) {
        ApprovalRequest request = findEntity(id);
        String reviewer = currentUser.username();

        requirePending(request);
        requireNotSelf(request, reviewer);
        requireCheckerAuthority();

        request.setStatus(ApprovalStatus.APPROVED);
        request.setReviewedBy(reviewer);
        request.setReviewedAt(LocalDateTime.now());
        request.setReviewNote(note);

        try {
            ApprovalApplier applier = appliersByType().get(request.getEntityType());
            if (applier == null) {
                throw new BusinessException("No applier registered for " + request.getEntityType());
            }
            applier.apply(request);
        } catch (RuntimeException ex) {
            // Roll back the whole decision: the request stays pending and the
            // checker sees the reason.
            log.error("Approval of request {} failed", id, ex);
            auditService.record(AuditEntry.builder()
                    .action(AuditAction.APPROVAL_APPROVE)
                    .entityType("APPROVAL_REQUEST")
                    .entityId(String.valueOf(id))
                    .entityLabel(request.getEntityLabel())
                    .description("Approval failed: " + ex.getMessage())
                    .outcome(com.bank.mt940portal.domain.enums.AuditOutcome.FAILURE)
                    .build());
            throw new BusinessException("Approval could not be applied: " + ex.getMessage());
        }

        ApprovalRequest saved = repository.save(request);
        auditService.record(AuditEntry.builder()
                .action(AuditAction.APPROVAL_APPROVE)
                .entityType("APPROVAL_REQUEST")
                .entityId(String.valueOf(id))
                .entityLabel(request.getEntityLabel())
                .description("Approved " + request.getOperation().name().toLowerCase()
                        + " of " + request.getEntityType().name().toLowerCase()
                        + " " + request.getEntityLabel()
                        + " (raised by " + request.getRequestedBy() + ")")
                .oldValue(request.getCurrentJson())
                .newValue(request.getPayloadJson())
                .detail(Map.of("diff", String.valueOf(request.getDiffSummary())))
                .build());
        return saved;
    }

    @PreAuthorize("hasAnyAuthority(@securityRoles.checkerAuthority, @securityRoles.adminAuthority)")
    @Transactional
    public ApprovalRequest reject(Long id, String note) {
        ApprovalRequest request = findEntity(id);
        String reviewer = currentUser.username();

        requirePending(request);
        requireNotSelf(request, reviewer);
        requireCheckerAuthority();

        request.setStatus(ApprovalStatus.REJECTED);
        request.setReviewedBy(reviewer);
        request.setReviewedAt(LocalDateTime.now());
        request.setReviewNote(note);
        ApprovalRequest saved = repository.save(request);

        auditService.record(AuditEntry.builder()
                .action(AuditAction.APPROVAL_REJECT)
                .entityType("APPROVAL_REQUEST")
                .entityId(String.valueOf(id))
                .entityLabel(request.getEntityLabel())
                .description("Rejected " + request.getOperation().name().toLowerCase()
                        + " of " + request.getEntityType().name().toLowerCase()
                        + " " + request.getEntityLabel()
                        + (note == null ? "" : " - " + note))
                .build());
        return saved;
    }

    /** The maker withdraws their own request. */
    @Transactional
    public ApprovalRequest cancel(Long id, String note) {
        ApprovalRequest request = findEntity(id);
        String user = currentUser.username();

        if (request.getStatus() != ApprovalStatus.PENDING) {
            throw new BusinessException("Only a pending request can be cancelled");
        }
        if (!request.getRequestedBy().equalsIgnoreCase(user) && !currentUser.isAdmin()) {
            throw new AccessDeniedException("Only the maker of the request, or an admin, can cancel it");
        }

        request.setStatus(ApprovalStatus.CANCELLED);
        request.setReviewedBy(user);
        request.setReviewedAt(LocalDateTime.now());
        request.setReviewNote(note);
        ApprovalRequest saved = repository.save(request);

        auditService.record(AuditEntry.builder()
                .action(AuditAction.APPROVAL_CANCEL)
                .entityType("APPROVAL_REQUEST")
                .entityId(String.valueOf(id))
                .entityLabel(request.getEntityLabel())
                .description("Cancelled request " + id)
                .build());
        return saved;
    }

    /** Raises a manual "generate and email now" request for checker sign-off. */
    @PreAuthorize("hasAnyAuthority(@securityRoles.makerAuthority, @securityRoles.adminAuthority)")
    @Transactional
    public ApprovalRequest submitStatementSend(SendStatementRequest payload, String reason) {
        String label;
        if (payload.getStatementId() != null) {
            Statement statement = statementService.findEntity(payload.getStatementId());
            label = statement.getStatementReference()
                    + " (" + statement.getAccount().getAccountNumber() + ")";
        } else if (payload.getAccountId() != null) {
            Account account = accountService.findEntity(payload.getAccountId());
            label = account.getAccountNumber() + " "
                    + payload.getPeriodFrom() + " to " + payload.getPeriodTo();
        } else {
            throw new BusinessException("A send request needs either a statement or an account");
        }

        return approvalRequestFactory.submit(ApprovalRequestFactory.Submission.of(
                EntityType.STATEMENT, payload.getStatementId(), label, ApprovalOperation.SEND,
                payload, null, reason));
    }

    // ---------------------------------------------------------------- plumbing

    private void requirePending(ApprovalRequest request) {
        if (request.getStatus() != ApprovalStatus.PENDING) {
            throw new BusinessException("Request " + request.getId() + " is already "
                    + request.getStatus().name().toLowerCase());
        }
    }

    private void requireNotSelf(ApprovalRequest request, String reviewer) {
        if (request.getRequestedBy() != null
                && request.getRequestedBy().equalsIgnoreCase(reviewer)) {
            throw new AccessDeniedException(
                    "A maker cannot approve their own request. Ask another checker to review it.");
        }
    }

    private void requireCheckerAuthority() {
        if (!currentUser.hasAny(securityRoles.checkerAuthorities())) {
            throw new AccessDeniedException(
                    "Your account does not hold the checker role required to review requests");
        }
    }

    public Map<EntityType, Long> pendingSummary() {
        return repository.findAllByStatusOrderByRequestedAtAsc(ApprovalStatus.PENDING)
                .stream()
                .collect(Collectors.groupingBy(ApprovalRequest::getEntityType, Collectors.counting()));
    }
}
