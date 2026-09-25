package com.bank.mt940portal.service;

import com.bank.mt940portal.audit.AuditAction;
import com.bank.mt940portal.audit.AuditEntry;
import com.bank.mt940portal.audit.AuditService;
import com.bank.mt940portal.domain.ApprovalRequest;
import com.bank.mt940portal.domain.Client;
import com.bank.mt940portal.domain.EmailConfiguration;
import com.bank.mt940portal.domain.enums.ApprovalOperation;
import com.bank.mt940portal.domain.enums.ApprovalStatus;
import com.bank.mt940portal.domain.enums.EntityType;
import com.bank.mt940portal.dto.EmailConfigurationDto;
import com.bank.mt940portal.dto.PendingChangeDto;
import com.bank.mt940portal.exception.BusinessException;
import com.bank.mt940portal.exception.ResourceNotFoundException;
import com.bank.mt940portal.repository.ApprovalRequestRepository;
import com.bank.mt940portal.repository.EmailConfigurationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Optional;

/**
 * Per client recipient lists and message templates.
 * <p>
 * A row with no client is the global default used when a client has no
 * configuration of its own.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class EmailConfigurationService {

    private final EmailConfigurationRepository repository;
    private final ApprovalRequestRepository approvalRequestRepository;
    private final ApprovalRequestFactory approvalRequestFactory;
    private final ClientService clientService;
    private final AuditService auditService;

    public List<EmailConfigurationDto> listByClient(Long clientId) {
        return repository.findAllByClientIdOrderByIdAsc(clientId).stream().map(this::toDto).toList();
    }

    public EmailConfigurationDto get(Long id) {
        return toDto(findEntity(id));
    }

    public EmailConfiguration findEntity(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(EmailConfiguration.class, id));
    }

    /**
     * Recipients for a client: its own default configuration, otherwise the
     * client's primary email, otherwise the global default row.
     */
    public EmailConfiguration resolveFor(Client client) {
        return repository
                .findFirstByClientIdAndEnabledTrueOrderByIsDefaultDescIdAsc(client.getId())
                .orElseGet(() -> repository.findAllByClientIsNullOrderByIdAsc().stream()
                        .filter(EmailConfiguration::isEnabled)
                        .findFirst()
                        .orElse(null));
    }

    public EmailConfigurationDto toDto(EmailConfiguration config) {
        EmailConfigurationDto dto = EmailConfigurationDto.builder()
                .id(config.getId())
                .clientId(config.getClient() == null ? null : config.getClient().getId())
                .clientCode(config.getClient() == null ? null : config.getClient().getClientCode())
                .name(config.getName())
                .toAddresses(config.getToAddresses())
                .ccAddresses(config.getCcAddresses())
                .bccAddresses(config.getBccAddresses())
                .subjectTemplate(config.getSubjectTemplate())
                .bodyTemplate(config.getBodyTemplate())
                .enabled(config.isEnabled())
                .isDefault(config.isDefault())
                .createdAt(config.getCreatedAt())
                .createdBy(config.getCreatedBy())
                .updatedAt(config.getUpdatedAt())
                .updatedBy(config.getUpdatedBy())
                .build();

        pendingChange(config.getId()).ifPresent(dto::setPendingChange);
        return dto;
    }

    private Optional<PendingChangeDto> pendingChange(Long configId) {
        return approvalRequestRepository
                .findFirstByEntityTypeAndEntityIdAndStatus(
                        EntityType.EMAIL_CONFIG, configId, ApprovalStatus.PENDING)
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
    public ApprovalRequest submitCreate(EmailConfigurationDto dto, String reason) {
        validate(dto);
        return approvalRequestFactory.submit(ApprovalRequestFactory.Submission.of(
                EntityType.EMAIL_CONFIG, null, label(dto), ApprovalOperation.CREATE,
                dto, null, reason));
    }

    @PreAuthorize("hasAnyAuthority(@securityRoles.makerAuthority, @securityRoles.adminAuthority)")
    @Transactional
    public ApprovalRequest submitUpdate(Long id, EmailConfigurationDto dto, String reason) {
        EmailConfiguration existing = findEntity(id);
        rejectIfPending(id);
        validate(dto);
        return approvalRequestFactory.submit(ApprovalRequestFactory.Submission.of(
                EntityType.EMAIL_CONFIG, id, label(dto), ApprovalOperation.UPDATE,
                dto, toDto(existing), reason));
    }

    @PreAuthorize("hasAnyAuthority(@securityRoles.makerAuthority, @securityRoles.adminAuthority)")
    @Transactional
    public ApprovalRequest submitDelete(Long id, String reason) {
        EmailConfiguration existing = findEntity(id);
        rejectIfPending(id);
        EmailConfigurationDto current = toDto(existing);
        return approvalRequestFactory.submit(ApprovalRequestFactory.Submission.of(
                EntityType.EMAIL_CONFIG, id, label(current), ApprovalOperation.DELETE,
                current, current, reason));
    }

    // ------------------------------------------------------ checker application

    @Transactional
    public EmailConfiguration applyCreate(EmailConfigurationDto dto) {
        validate(dto);
        EmailConfiguration config = new EmailConfiguration();
        if (dto.getClientId() != null) {
            config.setClient(clientService.findEntity(dto.getClientId()));
        }
        copyTo(dto, config);
        EmailConfiguration saved = repository.save(config);

        auditService.record(AuditEntry.builder()
                .action(AuditAction.EMAIL_CONFIG_CREATE)
                .entityType(EntityType.EMAIL_CONFIG.name())
                .entityId(String.valueOf(saved.getId()))
                .entityLabel(label(dto))
                .description("Approved creation of email configuration " + saved.getName())
                .newValue(dto)
                .build());
        return saved;
    }

    @Transactional
    public EmailConfiguration applyUpdate(Long id, EmailConfigurationDto dto) {
        EmailConfiguration config = findEntity(id);
        Object before = toDto(config);
        copyTo(dto, config);
        EmailConfiguration saved = repository.save(config);

        auditService.record(AuditEntry.builder()
                .action(AuditAction.EMAIL_CONFIG_UPDATE)
                .entityType(EntityType.EMAIL_CONFIG.name())
                .entityId(String.valueOf(id))
                .entityLabel(label(dto))
                .description("Approved update of email configuration " + saved.getName())
                .oldValue(before)
                .newValue(dto)
                .build());
        return saved;
    }

    @Transactional
    public void applyDelete(Long id) {
        EmailConfiguration config = findEntity(id);
        EmailConfigurationDto before = toDto(config);
        repository.delete(config);

        auditService.record(AuditEntry.builder()
                .action(AuditAction.EMAIL_CONFIG_DELETE)
                .entityType(EntityType.EMAIL_CONFIG.name())
                .entityId(String.valueOf(id))
                .entityLabel(before.getName())
                .description("Approved deletion of email configuration " + before.getName())
                .oldValue(before)
                .build());
    }

    private void copyTo(EmailConfigurationDto dto, EmailConfiguration config) {
        if (dto.getName() != null) {
            config.setName(dto.getName().trim());
        }
        if (dto.getToAddresses() != null) {
            config.setToAddresses(dto.getToAddresses().trim());
        }
        if (dto.getCcAddresses() != null) {
            config.setCcAddresses(dto.getCcAddresses().trim());
        }
        if (dto.getBccAddresses() != null) {
            config.setBccAddresses(dto.getBccAddresses().trim());
        }
        if (dto.getSubjectTemplate() != null) {
            config.setSubjectTemplate(dto.getSubjectTemplate().trim());
        }
        if (dto.getBodyTemplate() != null) {
            config.setBodyTemplate(dto.getBodyTemplate());
        }
        if (dto.getEnabled() != null) {
            config.setEnabled(dto.getEnabled());
        }
        if (dto.getIsDefault() != null) {
            config.setDefault(dto.getIsDefault());
        }
    }

    private void validate(EmailConfigurationDto dto) {
        if (!StringUtils.hasText(dto.getToAddresses())
                && !StringUtils.hasText(dto.getCcAddresses())
                && !StringUtils.hasText(dto.getBccAddresses())) {
            throw new BusinessException("At least one recipient is required");
        }
    }

    private String label(EmailConfigurationDto dto) {
        return dto.getClientId() == null ? "global default" : "client " + dto.getClientId();
    }

    private void rejectIfPending(Long configId) {
        if (pendingChange(configId).isPresent()) {
            throw new BusinessException(
                    "This email configuration already has a pending change waiting for approval");
        }
    }
}
