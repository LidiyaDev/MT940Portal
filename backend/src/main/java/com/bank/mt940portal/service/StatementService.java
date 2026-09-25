package com.bank.mt940portal.service;

import com.bank.mt940portal.audit.AuditAction;
import com.bank.mt940portal.audit.AuditEntry;
import com.bank.mt940portal.audit.AuditService;
import com.bank.mt940portal.domain.Account;
import com.bank.mt940portal.domain.Client;
import com.bank.mt940portal.domain.Statement;
import com.bank.mt940portal.domain.StatementTransaction;
import com.bank.mt940portal.domain.enums.DeliveryStatus;
import com.bank.mt940portal.domain.enums.StatementStatus;
import com.bank.mt940portal.dto.StatementDto;
import com.bank.mt940portal.exception.BusinessException;
import com.bank.mt940portal.exception.ResourceNotFoundException;
import com.bank.mt940portal.integration.TransactionServiceClient;
import com.bank.mt940portal.integration.dto.TransactionRecord;
import com.bank.mt940portal.mt940.Mt940Generator;
import com.bank.mt940portal.mt940.Mt940RenderRequest;
import com.bank.mt940portal.mt940.Mt940RenderResult;
import com.bank.mt940portal.mt940.Mt940Transaction;
import com.bank.mt940portal.mt940.SwiftProperties;
import com.bank.mt940portal.repository.StatementRepository;
import com.bank.mt940portal.security.CurrentUser;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;

/**
 * Builds MT940 statements from the core banking transaction feed.
 * <p>
 * The core banking endpoint returns each entry with its own opening and closing
 * balance, so the statement's :60F: is the first row's beginning balance and
 * :62F: is the last row's closing balance.
 */
@Slf4j
@Service
@Transactional(readOnly = true)
public class StatementService {

    private final StatementRepository statementRepository;
    private final TransactionServiceClient transactionServiceClient;
    private final Mt940Generator generator;
    private final SwiftProperties swiftProperties;
    private final AccountService accountService;
    private final AuditService auditService;
    private final CurrentUser currentUser;
    private final Path storageDir;

    public StatementService(StatementRepository statementRepository,
                            TransactionServiceClient transactionServiceClient,
                            Mt940Generator generator,
                            SwiftProperties swiftProperties,
                            AccountService accountService,
                            AuditService auditService,
                            CurrentUser currentUser,
                            @Value("${mt940.storage.base-dir:./mt940-output}") String storageDir) {
        this.statementRepository = statementRepository;
        this.transactionServiceClient = transactionServiceClient;
        this.generator = generator;
        this.swiftProperties = swiftProperties;
        this.accountService = accountService;
        this.auditService = auditService;
        this.currentUser = currentUser;
        this.storageDir = Paths.get(storageDir);
    }

    public Statement findEntity(Long id) {
        return statementRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(Statement.class, id));
    }

    public StatementDto get(Long id, boolean withContent) {
        Statement statement = findEntity(id);
        StatementDto dto = toDto(statement);
        if (withContent) {
            dto.setContent(statement.getContent());
        }
        return dto;
    }

    public Page<StatementDto> listByClient(Long clientId, Pageable pageable) {
        return statementRepository.findAllByClientIdOrderByGeneratedAtDesc(clientId, pageable)
                .map(this::toDto);
    }

    public Page<StatementDto> listAll(Pageable pageable) {
        return statementRepository.findAllByOrderByGeneratedAtDesc(pageable).map(this::toDto);
    }

    public List<StatementDto> listByAccount(Long accountId) {
        return statementRepository.findAllByAccountIdOrderByGeneratedAtDesc(accountId)
                .stream().map(this::toDto).toList();
    }

    /** True when the account has at least one transaction in the period. */
    public boolean hasTransactions(Account account, LocalDate from, LocalDate to) {
        try {
            return !transactionServiceClient
                    .fetchTransactions(account.getAccountNumber(), from, to).isEmpty();
        } catch (RuntimeException ex) {
            log.warn("Unable to check transactions for account {}", account.getAccountNumber(), ex);
            return false;
        }
    }

    // ------------------------------------------------------------------ render

    /**
     * Renders a statement without storing it, so a maker can eyeball the output
     * before committing to a generation or a send.
     */
    public StatementDto preview(Long accountId, LocalDate from, LocalDate to) {
        Account account = accountService.findEntity(accountId);
        StatementDraft draft = prepare(account, from, to);

        auditService.record(AuditEntry.builder()
                .action(AuditAction.STATEMENT_PREVIEW)
                .entityType("ACCOUNT")
                .entityId(String.valueOf(accountId))
                .entityLabel(account.getAccountNumber())
                .description("Previewed MT940 for account " + account.getAccountNumber()
                        + " covering " + from + " to " + to)
                .detail(java.util.Map.of("transactions", draft.transactions().size()))
                .build());

        return draftToDto(draft);
    }

    /** Generates and stores a statement. */
    @Transactional
    public Statement generate(Long accountId, LocalDate from, LocalDate to,
                              com.bank.mt940portal.domain.DeliverySchedule schedule) {
        Account account = accountService.findEntity(accountId);
        StatementDraft draft = prepare(account, from, to);

        Statement statement = new Statement();
        statement.setClient(account.getClient());
        statement.setAccount(account);
        statement.setSchedule(schedule);
        statement.setStatementReference(draft.result().getStatementReference());
        statement.setStatementNumber(draft.result().getStatementNumber());
        statement.setPageSequence(draft.result().getPageSequence());
        statement.setIsn(draft.result().getIsn());
        statement.setPeriodFrom(from);
        statement.setPeriodTo(to);
        statement.setCurrency(draft.currency());
        statement.setOpeningBalance(draft.result().getOpeningBalance());
        statement.setOpeningMark(draft.result().getOpeningMark());
        statement.setClosingBalance(draft.result().getClosingBalance());
        statement.setClosingMark(draft.result().getClosingMark());
        statement.setTransactionCount(draft.result().getTransactionCount());
        statement.setContent(draft.result().getContent());
        statement.setFileName(draft.result().getSuggestedFileName());
        statement.setChecksum(sha256(draft.result().getContent()));
        statement.setStatus(StatementStatus.GENERATED);
        statement.setDeliveryStatus(DeliveryStatus.PENDING);
        statement.setGeneratedBy(currentUser.username());
        statement.setGeneratedAt(LocalDateTime.now());

        int line = 1;
        for (Mt940Transaction txn : draft.transactions()) {
            statement.addTransaction(toEntity(txn, line++, draft.currency()));
        }

        Statement saved = statementRepository.save(statement);
        writeToDisk(saved);

        auditService.record(AuditEntry.builder()
                .action(AuditAction.STATEMENT_GENERATE)
                .entityType("STATEMENT")
                .entityId(String.valueOf(saved.getId()))
                .entityLabel(saved.getStatementReference())
                .description("Generated MT940 " + saved.getStatementReference()
                        + " for account " + account.getAccountNumber()
                        + " (" + saved.getTransactionCount() + " transactions)")
                .detail(java.util.Map.of("fileName", saved.getFileName(),
                        "periodFrom", String.valueOf(from),
                        "periodTo", String.valueOf(to),
                        "checksum", saved.getChecksum()))
                .build());

        return saved;
    }

    // ------------------------------------------------------------------ plumbing

    private StatementDraft prepare(Account account, LocalDate from, LocalDate to) {
        if (from == null || to == null || to.isBefore(from)) {
            throw new BusinessException("Invalid statement period: " + from + " to " + to);
        }

        Client client = account.getClient();
        SwiftProperties settings = swiftProperties.copyWith(SwiftProperties.ClientOverride.builder()
                .senderLtAddress(client.getSenderLtAddress())
                .receiverLtAddress(client.getReceiverLtAddress())
                .currency(account.getCurrency())
                .blankLineBetweenTags(client.isBlankLineBetweenTags())
                .fundsCodeStrategy(client.getFundsCodeStrategy())
                .openingBalanceTag(client.getOpeningBalanceTag())
                .closingBalanceTag(client.getClosingBalanceTag())
                .emit13d(client.isEmit13d())
                .emit64(client.isEmit64())
                .emit65(client.isEmit65())
                .emit90d(client.isEmit90d())
                .build());

        List<TransactionRecord> records = transactionServiceClient.fetchTransactions(
                account.getAccountNumber(), from, to);

        List<Mt940Transaction> transactions = new ArrayList<>();
        for (TransactionRecord record : records) {
            transactions.add(map(record, settings));
        }

        BigDecimal opening = records.isEmpty() ? null : records.get(0).getBeginningBalance();
        BigDecimal closing = records.isEmpty() ? null : records.get(records.size() - 1).getClosingBalance();

        if (opening == null) {
            opening = lastKnownBalance(account);
        }
        if (closing == null) {
            closing = opening;
        }

        long statementNumber = nextStatementNumber(account);
        Mt940RenderRequest request = Mt940RenderRequest.builder()
                .accountNumber(account.getAccountNumber())
                .currency(account.getCurrency())
                .statementDate(to)
                .periodFrom(from)
                .periodTo(to)
                .statementNumber(statementNumber)
                .pageSequence(1)
                .openingBalance(opening)
                .closingBalance(closing)
                .generationTime(LocalDateTime.now())
                .transactions(transactions)
                .settings(settings)
                .build();

        Mt940RenderResult result = generator.render(request);
        return new StatementDraft(result, transactions, account.getCurrency());
    }

    private Mt940Transaction map(TransactionRecord record, SwiftProperties settings) {
        LocalDate txnDate = parseDate(record.getTransactionDate());
        BigDecimal debit = nz(record.getDebitAmt());
        BigDecimal credit = nz(record.getCreditAmt());

        boolean isDebit = debit.signum() > 0 || credit.signum() == 0;
        BigDecimal amount = isDebit ? debit : credit;

        String description = record.getTxnDescription() == null ? "" : record.getTxnDescription();

        return Mt940Transaction.builder()
                .transactionDate(txnDate)
                .valueDate(txnDate)
                .entryDate(txnDate)
                .amount(amount)
                .debitCredit(isDebit ? "D" : "C")
                .customerReference(record.getTransReference())
                .transactionType(resolveType(description, settings))
                .description(description)
                .build();
    }

    /** Keyword rules first, then the configured default (NTRF). */
    private String resolveType(String description, SwiftProperties settings) {
        if (settings.getTransactionTypeRules() != null && description != null) {
            String upper = description.toUpperCase(Locale.ROOT);
            for (var entry : settings.getTransactionTypeRules().entrySet()) {
                if (upper.contains(entry.getKey().toUpperCase(Locale.ROOT))) {
                    return entry.getValue();
                }
            }
        }
        return settings.getDefaultTransactionType();
    }

    private StatementTransaction toEntity(Mt940Transaction txn, int lineNo, String currency) {
        StatementTransaction entity = new StatementTransaction();
        entity.setLineNo(lineNo);
        entity.setTransactionDate(txn.getTransactionDate());
        entity.setValueDate(txn.getValueDate());
        entity.setEntryDate(txn.getEntryDate());
        entity.setDebitCreditMark(txn.getDebitCredit());
        entity.setAmount(txn.getAmount());
        entity.setCurrency(currency);
        entity.setCustomerReference(txn.getCustomerReference());
        entity.setBankReference(txn.getBankReference());
        entity.setTransactionType(txn.getTransactionType());
        entity.setDescription(txn.getDescription());
        return entity;
    }

    /** Next :28C: value for the account; the seed sets the starting point. */
    private long nextStatementNumber(Account account) {
        long seed = Math.max(
                account.getStatementNumberSeed() == null ? 0L : account.getStatementNumberSeed(),
                account.getLastStatementNumber() == null ? 0L : account.getLastStatementNumber());
        long next = seed + 1;
        account.setLastStatementNumber(next);
        return next;
    }

    private BigDecimal lastKnownBalance(Account account) {
        List<Statement> previous = statementRepository
                .findAllByAccountIdOrderByGeneratedAtDesc(account.getId());
        return previous.isEmpty() ? BigDecimal.ZERO : nz(previous.get(0).getClosingBalance());
    }

    private void writeToDisk(Statement statement) {
        try {
            Files.createDirectories(storageDir);
            Path file = storageDir.resolve(statement.getFileName());
            Files.writeString(file, statement.getContent(), StandardCharsets.UTF_8);
            statement.setFilePath(file.toAbsolutePath().toString());
        } catch (IOException ex) {
            // A statement that cannot be written to disk is still recoverable
            // from MT_STATEMENT.CONTENT.
            log.warn("Unable to write statement {} to {}", statement.getFileName(), storageDir, ex);
        }
    }

    public byte[] fileContent(Statement statement) {
        return statement.getContent() == null
                ? new byte[0]
                : statement.getContent().getBytes(StandardCharsets.UTF_8);
    }

    private static LocalDate parseDate(String value) {
        if (value == null || value.isBlank()) {
            return LocalDate.now();
        }
        String trimmed = value.trim();
        try {
            return LocalDate.parse(trimmed.length() > 10 ? trimmed.substring(0, 10) : trimmed);
        } catch (DateTimeParseException ex) {
            log.warn("Unparsable transaction date '{}', using today", value);
            return LocalDate.now();
        }
    }

    private static BigDecimal nz(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private static String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException ex) {
            return null;
        }
    }

    // ------------------------------------------------------------------ mapping

    public StatementDto toDto(Statement statement) {
        return StatementDto.builder()
                .id(statement.getId())
                .clientId(statement.getClient().getId())
                .clientCode(statement.getClient().getClientCode())
                .clientName(statement.getClient().getClientName())
                .accountId(statement.getAccount().getId())
                .accountNumber(statement.getAccount().getAccountNumber())
                .statementReference(statement.getStatementReference())
                .statementNumber(statement.getStatementNumber())
                .pageSequence(statement.getPageSequence())
                .isn(statement.getIsn())
                .periodFrom(statement.getPeriodFrom())
                .periodTo(statement.getPeriodTo())
                .currency(statement.getCurrency())
                .openingBalance(statement.getOpeningBalance())
                .openingMark(statement.getOpeningMark())
                .closingBalance(statement.getClosingBalance())
                .closingMark(statement.getClosingMark())
                .transactionCount(statement.getTransactionCount())
                .fileName(statement.getFileName())
                .checksum(statement.getChecksum())
                .status(statement.getStatus())
                .deliveryStatus(statement.getDeliveryStatus())
                .errorMessage(statement.getErrorMessage())
                .generatedAt(statement.getGeneratedAt())
                .generatedBy(statement.getGeneratedBy())
                .build();
    }

    private StatementDto draftToDto(StatementDraft draft) {
        Mt940RenderResult result = draft.result();
        return StatementDto.builder()
                .statementReference(result.getStatementReference())
                .statementNumber(result.getStatementNumber())
                .pageSequence(result.getPageSequence())
                .isn(result.getIsn())
                .currency(draft.currency())
                .openingBalance(result.getOpeningBalance())
                .openingMark(result.getOpeningMark())
                .closingBalance(result.getClosingBalance())
                .closingMark(result.getClosingMark())
                .transactionCount(result.getTransactionCount())
                .fileName(result.getSuggestedFileName())
                .content(result.getContent())
                .status(StatementStatus.GENERATED)
                .deliveryStatus(DeliveryStatus.PENDING)
                .build();
    }

    private record StatementDraft(Mt940RenderResult result,
                                  List<Mt940Transaction> transactions,
                                  String currency) {
    }
}
