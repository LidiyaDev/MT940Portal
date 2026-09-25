package com.bank.mt940portal.service;

import com.bank.mt940portal.audit.AuditAction;
import com.bank.mt940portal.audit.AuditEntry;
import com.bank.mt940portal.audit.AuditService;
import com.bank.mt940portal.domain.ApprovalRequest;
import com.bank.mt940portal.domain.Client;
import com.bank.mt940portal.domain.enums.ApprovalOperation;
import com.bank.mt940portal.domain.enums.ApprovalStatus;
import com.bank.mt940portal.domain.enums.ClientStatus;
import com.bank.mt940portal.domain.enums.EntityType;
import com.bank.mt940portal.dto.ClientDto;
import com.bank.mt940portal.dto.PageResponse;
import com.bank.mt940portal.dto.PendingChangeDto;
import com.bank.mt940portal.exception.BusinessException;
import com.bank.mt940portal.exception.ResourceNotFoundException;
import com.bank.mt940portal.repository.AccountRepository;
import com.bank.mt940portal.repository.ApprovalRequestRepository;
import com.bank.mt940portal.repository.ClientRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Optional;

/**
 * Client master data.
 * <p>
 * Reads are open to every role. Every write is raised as a maker-checker request
 * and only applied by {@link ApprovalService}.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ClientService {

    private final ClientRepository clientRepository;
    private final AccountRepository accountRepository;
    private final ApprovalRequestRepository approvalRequestRepository;
    private final ApprovalRequestFactory approvalRequestFactory;
    private final AuditService auditService;

    // ------------------------------------------------------------------ reads

    public PageResponse<ClientDto> list(Pageable pageable, String search) {
        Page<Client> page = StringUtils.hasText(search)
                ? clientRepository.search(search.trim(), pageable)
                : clientRepository.findAll(pageable);
        return PageResponse.of(page, this::toDto);
    }

    public ClientDto get(Long id) {
        return toDto(findEntity(id));
    }

    public Client findEntity(Long id) {
        return clientRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(Client.class, id));
    }

    public Client findEntityByCode(String code) {
        return clientRepository.findByClientCode(code)
                .orElseThrow(() -> new ResourceNotFoundException("Client not found: " + code));
    }

    public ClientDto toDto(Client client) {
        ClientDto dto = ClientDto.builder()
                .id(client.getId())
                .clientCode(client.getClientCode())
                .clientName(client.getClientName())
                .primaryEmail(client.getPrimaryEmail())
                .contactPerson(client.getContactPerson())
                .phone(client.getPhone())
                .address(client.getAddress())
                .defaultCurrency(client.getDefaultCurrency())
                .timezone(client.getTimezone())
                .status(client.getStatus())
                .senderLtAddress(client.getSenderLtAddress())
                .receiverLtAddress(client.getReceiverLtAddress())
                .fundsCodeStrategy(client.getFundsCodeStrategy())
                .openingBalanceTag(client.getOpeningBalanceTag())
                .closingBalanceTag(client.getClosingBalanceTag())
                .emit13d(client.isEmit13d())
                .emit64(client.isEmit64())
                .emit65(client.isEmit65())
                .emit90d(client.isEmit90d())
                .blankLineBetweenTags(client.isBlankLineBetweenTags())
                .statementNumberSeed(client.getStatementNumberSeed())
                .remarks(client.getRemarks())
                .accountCount(client.getAccounts() == null ? 0 : client.getAccounts().size())
                .createdAt(client.getCreatedAt())
                .createdBy(client.getCreatedBy())
                .updatedAt(client.getUpdatedAt())
                .updatedBy(client.getUpdatedBy())
                .build();

        pendingChange(client.getId()).ifPresent(dto::setPendingChange);
        return dto;
    }

    private Optional<PendingChangeDto> pendingChange(Long clientId) {
        return approvalRequestRepository
                .findFirstByEntityTypeAndEntityIdAndStatus(
                        EntityType.CLIENT, clientId, ApprovalStatus.PENDING)
                .map(this::pendingDto);
    }

    private PendingChangeDto pendingDto(ApprovalRequest request) {
        return PendingChangeDto.builder()
                .approvalId(request.getId())
                .operation(request.getOperation())
                .status(request.getStatus())
                .requestedBy(request.getRequestedBy())
                .requestedAt(request.getRequestedAt())
                .reviewNote(request.getReviewNote())
                .build();
    }

    // ------------------------------------------------------- maker submissions

    @PreAuthorize("hasAnyAuthority(@securityRoles.makerAuthority, @securityRoles.adminAuthority)")
    @Transactional
    public ApprovalRequest submitCreate(ClientDto dto, String reason) {
        if (clientRepository.existsByClientCodeIgnoreCase(dto.getClientCode())) {
            throw new BusinessException("Client code already exists: " + dto.getClientCode());
        }
        return approvalRequestFactory.submit(ApprovalRequestFactory.Submission.of(
                EntityType.CLIENT, null, dto.getClientCode(), ApprovalOperation.CREATE,
                dto, null, reason));
    }

    @PreAuthorize("hasAnyAuthority(@securityRoles.makerAuthority, @securityRoles.adminAuthority)")
    @Transactional
    public ApprovalRequest submitUpdate(Long id, ClientDto dto, String reason) {
        Client existing = findEntity(id);
        rejectIfPending(id);
        if (!existing.getClientCode().equalsIgnoreCase(dto.getClientCode())
                && clientRepository.existsByClientCodeIgnoreCase(dto.getClientCode())) {
            throw new BusinessException("Client code already exists: " + dto.getClientCode());
        }
        ClientDto normalised = normalise(dto, existing);
        return approvalRequestFactory.submit(ApprovalRequestFactory.Submission.of(
                EntityType.CLIENT, id, existing.getClientCode(), ApprovalOperation.UPDATE,
                normalised, toDto(existing), reason));
    }

    @PreAuthorize("hasAnyAuthority(@securityRoles.makerAuthority, @securityRoles.adminAuthority)")
    @Transactional
    public ApprovalRequest submitStatusChange(Long id, ClientStatus status, String reason) {
        Client existing = findEntity(id);
        rejectIfPending(id);
        return approvalRequestFactory.submit(ApprovalRequestFactory.Submission.of(
                EntityType.CLIENT, id, existing.getClientCode(), ApprovalOperation.UPDATE,
                ClientDto.builder().status(status).build(),
                ClientDto.builder().status(existing.getStatus()).build(),
                reason));
    }

    // ------------------------------------------------------ checker application

    @Transactional
    public Client applyCreate(ClientDto dto) {
        Client client = new Client();
        client.setClientCode(dto.getClientCode().trim().toUpperCase());
        copyTo(dto, client);
        Client saved = clientRepository.save(client);

        auditService.record(AuditEntry.builder()
                .action(AuditAction.CLIENT_CREATE)
                .entityType(EntityType.CLIENT.name())
                .entityId(String.valueOf(saved.getId()))
                .entityLabel(saved.getClientCode())
                .description("Approved creation of client " + saved.getClientCode())
                .newValue(dto)
                .build());
        return saved;
    }

    @Transactional
    public Client applyUpdate(Long id, ClientDto dto) {
        Client client = findEntity(id);
        Object before = toDto(client);

        if (dto.getStatus() != null) {
            client.setStatus(dto.getStatus());
        }
        if (dto.getClientCode() != null) {
            client.setClientCode(dto.getClientCode().trim().toUpperCase());
        }
        copyTo(dto, client);
        Client saved = clientRepository.save(client);

        auditService.record(AuditEntry.builder()
                .action(dto.getClientName() == null && dto.getStatus() != null
                        ? AuditAction.CLIENT_STATUS_CHANGE : AuditAction.CLIENT_UPDATE)
                .entityType(EntityType.CLIENT.name())
                .entityId(String.valueOf(id))
                .entityLabel(saved.getClientCode())
                .description("Approved update of client " + saved.getClientCode())
                .oldValue(before)
                .newValue(dto)
                .build());
        return saved;
    }

    private void copyTo(ClientDto dto, Client client) {
        if (dto.getClientName() != null) {
            client.setClientName(dto.getClientName().trim());
        }
        if (dto.getPrimaryEmail() != null) {
            client.setPrimaryEmail(dto.getPrimaryEmail().trim());
        }
        if (dto.getContactPerson() != null) {
            client.setContactPerson(dto.getContactPerson().trim());
        }
        if (dto.getPhone() != null) {
            client.setPhone(dto.getPhone().trim());
        }
        if (dto.getAddress() != null) {
            client.setAddress(dto.getAddress().trim());
        }
        if (dto.getDefaultCurrency() != null) {
            client.setDefaultCurrency(dto.getDefaultCurrency().trim().toUpperCase());
        }
        if (dto.getTimezone() != null) {
            client.setTimezone(dto.getTimezone().trim());
        }
        if (dto.getStatus() != null) {
            client.setStatus(dto.getStatus());
        }
        if (dto.getSenderLtAddress() != null) {
            client.setSenderLtAddress(dto.getSenderLtAddress().trim().toUpperCase());
        }
        if (dto.getReceiverLtAddress() != null) {
            client.setReceiverLtAddress(dto.getReceiverLtAddress().trim().toUpperCase());
        }
        if (dto.getFundsCodeStrategy() != null) {
            client.setFundsCodeStrategy(dto.getFundsCodeStrategy());
        }
        if (dto.getOpeningBalanceTag() != null) {
            client.setOpeningBalanceTag(dto.getOpeningBalanceTag());
        }
        if (dto.getClosingBalanceTag() != null) {
            client.setClosingBalanceTag(dto.getClosingBalanceTag());
        }
        if (dto.getEmit13d() != null) {
            client.setEmit13d(dto.getEmit13d());
        }
        if (dto.getEmit64() != null) {
            client.setEmit64(dto.getEmit64());
        }
        if (dto.getEmit65() != null) {
            client.setEmit65(dto.getEmit65());
        }
        if (dto.getEmit90d() != null) {
            client.setEmit90d(dto.getEmit90d());
        }
        if (dto.getBlankLineBetweenTags() != null) {
            client.setBlankLineBetweenTags(dto.getBlankLineBetweenTags());
        }
        if (dto.getStatementNumberSeed() != null) {
            client.setStatementNumberSeed(dto.getStatementNumberSeed());
        }
        if (dto.getRemarks() != null) {
            client.setRemarks(dto.getRemarks());
        }
    }

    /** A status only update arrives as a partial DTO; keep the current values. */
    private ClientDto normalise(ClientDto dto, Client existing) {
        if (dto.getClientCode() == null) {
            dto.setClientCode(existing.getClientCode());
        }
        return dto;
    }

    private void rejectIfPending(Long clientId) {
        if (pendingChange(clientId).isPresent()) {
            throw new BusinessException(
                    "This client already has a pending change waiting for approval");
        }
    }

    public long countActive() {
        return clientRepository.countByStatus(ClientStatus.ACTIVE);
    }

    public long countAccounts(Long clientId) {
        return accountRepository.countByClient(clientId);
    }
}
