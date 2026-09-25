package com.bank.mt940portal.service;

import com.bank.mt940portal.audit.AuditAction;
import com.bank.mt940portal.audit.AuditEntry;
import com.bank.mt940portal.audit.AuditService;
import com.bank.mt940portal.domain.Account;
import com.bank.mt940portal.domain.ApprovalRequest;
import com.bank.mt940portal.domain.Client;
import com.bank.mt940portal.domain.enums.AccountStatus;
import com.bank.mt940portal.domain.enums.ApprovalOperation;
import com.bank.mt940portal.domain.enums.ApprovalStatus;
import com.bank.mt940portal.domain.enums.EntityType;
import com.bank.mt940portal.dto.AccountDto;
import com.bank.mt940portal.dto.BulkAccountRequest;
import com.bank.mt940portal.dto.PageResponse;
import com.bank.mt940portal.dto.PendingChangeDto;
import com.bank.mt940portal.exception.BusinessException;
import com.bank.mt940portal.exception.ResourceNotFoundException;
import com.bank.mt940portal.repository.AccountRepository;
import com.bank.mt940portal.repository.ApprovalRequestRepository;
import com.bank.mt940portal.repository.StatementRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Accounts belonging to a client.
 * <p>
 * A client may have any number of accounts. Accounts are added one at a time or
 * in bulk; both paths go through the maker-checker queue as a single request so
 * the checker sees the whole batch.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AccountService {

    private final AccountRepository accountRepository;
    private final ApprovalRequestRepository approvalRequestRepository;
    private final ApprovalRequestFactory approvalRequestFactory;
    private final StatementRepository statementRepository;
    private final ClientService clientService;
    private final AuditService auditService;

    // ------------------------------------------------------------------ reads

    public PageResponse<AccountDto> listByClient(Long clientId, Pageable pageable) {
        Page<Account> page = accountRepository.findAllByClientIdOrderByAccountNumberAsc(clientId, pageable);
        return PageResponse.of(page, this::toDto);
    }

    public List<AccountDto> listByClient(Long clientId) {
        return accountRepository.findAllByClientIdOrderByAccountNumberAsc(clientId)
                .stream().map(this::toDto).toList();
    }

    public AccountDto get(Long id) {
        return toDto(findEntity(id));
    }

    public Account findEntity(Long id) {
        return accountRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(Account.class, id));
    }

    public List<Account> activeAccountsOf(Long clientId) {
        return accountRepository.findAllByClientIdAndStatusOrderByAccountNumberAsc(
                clientId, AccountStatus.ACTIVE);
    }

    public AccountDto toDto(Account account) {
        AccountDto dto = AccountDto.builder()
                .id(account.getId())
                .clientId(account.getClient().getId())
                .clientCode(account.getClient().getClientCode())
                .accountNumber(account.getAccountNumber())
                .accountName(account.getAccountName())
                .currency(account.getCurrency())
                .branchCode(account.getBranchCode())
                .iban(account.getIban())
                .bic(account.getBic())
                .status(account.getStatus())
                .statementNumberSeed(account.getStatementNumberSeed())
                .lastStatementNumber(account.getLastStatementNumber())
                .remarks(account.getRemarks())
                .statementCount(statementRepository
                        .findAllByAccountIdOrderByGeneratedAtDesc(account.getId()).size())
                .createdAt(account.getCreatedAt())
                .createdBy(account.getCreatedBy())
                .updatedAt(account.getUpdatedAt())
                .updatedBy(account.getUpdatedBy())
                .build();

        pendingChange(account.getId()).ifPresent(dto::setPendingChange);
        return dto;
    }

    private Optional<PendingChangeDto> pendingChange(Long accountId) {
        return approvalRequestRepository
                .findFirstByEntityTypeAndEntityIdAndStatus(
                        EntityType.ACCOUNT, accountId, ApprovalStatus.PENDING)
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

    /** Adds one or many accounts to a client in a single approval request. */
    @PreAuthorize("hasAnyAuthority(@securityRoles.makerAuthority, @securityRoles.adminAuthority)")
    @Transactional
    public ApprovalRequest submitCreate(BulkAccountRequest request) {
        if (request.getAccounts() == null || request.getAccounts().isEmpty()) {
            throw new BusinessException("At least one account is required");
        }
        Client client = clientService.findEntity(request.getClientId());

        Set<String> seen = new HashSet<>();
        List<AccountDto> cleaned = new ArrayList<>();
        for (AccountDto dto : request.getAccounts()) {
            String number = normalise(dto.getAccountNumber());
            if (number.isEmpty()) {
                continue;
            }
            if (!seen.add(number)) {
                throw new BusinessException("Duplicate account number in request: " + number);
            }
            if (accountRepository.existsByAccountNumber(number)) {
                throw new BusinessException("Account number already exists: " + number);
            }
            dto.setAccountNumber(number);
            dto.setClientId(client.getId());
            if (dto.getCurrency() == null || dto.getCurrency().isBlank()) {
                dto.setCurrency(client.getDefaultCurrency());
            }
            cleaned.add(dto);
        }
        if (cleaned.isEmpty()) {
            throw new BusinessException("No valid account numbers supplied");
        }
        request.setAccounts(cleaned);

        String label = cleaned.size() == 1
                ? cleaned.get(0).getAccountNumber()
                : cleaned.size() + " accounts for " + client.getClientCode();

        return approvalRequestFactory.submit(ApprovalRequestFactory.Submission.of(
                EntityType.ACCOUNT, null, label, ApprovalOperation.CREATE,
                request, null, request.getReason()));
    }

    @PreAuthorize("hasAnyAuthority(@securityRoles.makerAuthority, @securityRoles.adminAuthority)")
    @Transactional
    public ApprovalRequest submitUpdate(Long id, AccountDto dto, String reason) {
        Account existing = findEntity(id);
        rejectIfPending(id);
        String number = normalise(dto.getAccountNumber());
        if (StringUtils.hasText(number)
                && !number.equals(existing.getAccountNumber())
                && accountRepository.existsByAccountNumber(number)) {
            throw new BusinessException("Account number already exists: " + number);
        }
        dto.setClientId(existing.getClient().getId());
        return approvalRequestFactory.submit(ApprovalRequestFactory.Submission.of(
                EntityType.ACCOUNT, id, existing.getAccountNumber(), ApprovalOperation.UPDATE,
                dto, toDto(existing), reason));
    }

    @PreAuthorize("hasAnyAuthority(@securityRoles.makerAuthority, @securityRoles.adminAuthority)")
    @Transactional
    public ApprovalRequest submitStatusChange(Long id, AccountStatus status, String reason) {
        Account existing = findEntity(id);
        rejectIfPending(id);
        AccountDto payload = AccountDto.builder()
                .id(id)
                .clientId(existing.getClient().getId())
                .accountNumber(existing.getAccountNumber())
                .status(status)
                .build();
        return approvalRequestFactory.submit(ApprovalRequestFactory.Submission.of(
                EntityType.ACCOUNT, id, existing.getAccountNumber(), ApprovalOperation.UPDATE,
                payload, toDto(existing), reason));
    }

    // ------------------------------------------------------ checker application

    @Transactional
    public List<Account> applyCreate(BulkAccountRequest request) {
        Client client = clientService.findEntity(request.getClientId());
        List<Account> created = new ArrayList<>();
        for (AccountDto dto : request.getAccounts()) {
            created.add(applyCreateSingle(client, dto));
        }

        auditService.record(AuditEntry.builder()
                .action(AuditAction.ACCOUNT_CREATE)
                .entityType(EntityType.ACCOUNT.name())
                .entityLabel(client.getClientCode())
                .description("Approved creation of " + created.size()
                        + " account(s) for client " + client.getClientCode())
                .newValue(request)
                .build());
        return created;
    }

    private Account applyCreateSingle(Client client, AccountDto dto) {
        if (accountRepository.existsByAccountNumber(dto.getAccountNumber())) {
            throw new BusinessException("Account number already exists: " + dto.getAccountNumber());
        }
        Account account = new Account();
        account.setClient(client);
        account.setAccountNumber(dto.getAccountNumber());
        account.setAccountName(dto.getAccountName() == null
                ? client.getClientName() : dto.getAccountName());
        account.setCurrency(dto.getCurrency() == null ? client.getDefaultCurrency() : dto.getCurrency());
        account.setBranchCode(dto.getBranchCode());
        account.setIban(dto.getIban());
        account.setBic(dto.getBic());
        account.setStatus(dto.getStatus() == null ? AccountStatus.ACTIVE : dto.getStatus());
        account.setStatementNumberSeed(dto.getStatementNumberSeed() == null
                ? client.getStatementNumberSeed() : dto.getStatementNumberSeed());
        account.setLastStatementNumber(dto.getStatementNumberSeed() == null
                ? client.getStatementNumberSeed() : dto.getStatementNumberSeed());
        account.setRemarks(dto.getRemarks());
        return accountRepository.save(account);
    }

    @Transactional
    public Account applyUpdate(Long id, AccountDto dto) {
        Account account = findEntity(id);
        Object before = toDto(account);
        boolean statusOnly = dto.getAccountName() == null && dto.getCurrency() == null
                && dto.getAccountNumber() == null;

        if (StringUtils.hasText(dto.getAccountNumber())) {
            account.setAccountNumber(normalise(dto.getAccountNumber()));
        }
        if (dto.getAccountName() != null) {
            account.setAccountName(dto.getAccountName().trim());
        }
        if (dto.getCurrency() != null) {
            account.setCurrency(dto.getCurrency().toUpperCase());
        }
        if (dto.getBranchCode() != null) {
            account.setBranchCode(dto.getBranchCode().trim());
        }
        if (dto.getIban() != null) {
            account.setIban(dto.getIban().trim());
        }
        if (dto.getBic() != null) {
            account.setBic(dto.getBic().trim().toUpperCase());
        }
        if (dto.getStatus() != null) {
            account.setStatus(dto.getStatus());
        }
        if (dto.getStatementNumberSeed() != null) {
            account.setStatementNumberSeed(dto.getStatementNumberSeed());
        }
        if (dto.getRemarks() != null) {
            account.setRemarks(dto.getRemarks());
        }

        Account saved = accountRepository.save(account);
        auditService.record(AuditEntry.builder()
                .action(statusOnly ? AuditAction.ACCOUNT_STATUS_CHANGE : AuditAction.ACCOUNT_UPDATE)
                .entityType(EntityType.ACCOUNT.name())
                .entityId(String.valueOf(id))
                .entityLabel(saved.getAccountNumber())
                .description("Approved update of account " + saved.getAccountNumber())
                .oldValue(before)
                .newValue(dto)
                .build());
        return saved;
    }

    private void rejectIfPending(Long accountId) {
        if (pendingChange(accountId).isPresent()) {
            throw new BusinessException(
                    "This account already has a pending change waiting for approval");
        }
    }

    private static String normalise(String accountNumber) {
        return accountNumber == null ? "" : accountNumber.trim().toUpperCase();
    }

    public long countActive() {
        return accountRepository.countByStatus(AccountStatus.ACTIVE);
    }
}
