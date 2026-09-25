package com.bank.mt940portal.service;

import com.bank.mt940portal.audit.AuditAction;
import com.bank.mt940portal.audit.AuditEntry;
import com.bank.mt940portal.audit.AuditService;
import com.bank.mt940portal.domain.Account;
import com.bank.mt940portal.domain.Client;
import com.bank.mt940portal.domain.DeliveryLog;
import com.bank.mt940portal.domain.DeliverySchedule;
import com.bank.mt940portal.domain.EmailConfiguration;
import com.bank.mt940portal.domain.Statement;
import com.bank.mt940portal.domain.enums.DeliveryStatus;
import com.bank.mt940portal.email.EmailDeliveryResult;
import com.bank.mt940portal.email.EmailMessage;
import com.bank.mt940portal.email.EmailProperties;
import com.bank.mt940portal.email.EmailProvider;
import com.bank.mt940portal.exception.BusinessException;
import com.bank.mt940portal.repository.DeliveryLogRepository;
import com.bank.mt940portal.repository.StatementRepository;
import com.bank.mt940portal.scheduler.ScheduleCalculator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Emails a generated statement and records every attempt.
 * <p>
 * Delivery is retried according to {@code mt940.email.retry}; each attempt is
 * written to MT_DELIVERY_LOG so the operator can see what was sent, to whom and
 * what the notification service answered.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class StatementDeliveryService {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd-MMM-yyyy");

    private static final String DEFAULT_SUBJECT =
            "MT940 Statement - Account ${accountNumber} - ${periodFrom} to ${periodTo}";

    private static final String DEFAULT_BODY =
            "Dear ${clientName},\n\n"
                    + "Please find attached the MT940 statement for account ${accountNumber}.\n\n"
                    + "Statement reference : ${statementReference}\n"
                    + "Statement number    : ${statementNumber}\n"
                    + "Period              : ${periodFrom} to ${periodTo}\n"
                    + "Currency            : ${currency}\n"
                    + "Opening balance     : ${openingBalance}\n"
                    + "Closing balance     : ${closingBalance}\n"
                    + "Transactions        : ${transactionCount}\n\n"
                    + "Regards,\n${fromName}";

    private final StatementRepository statementRepository;
    private final DeliveryLogRepository deliveryLogRepository;
    private final StatementService statementService;
    private final EmailConfigurationService emailConfigurationService;
    private final EmailProperties emailProperties;
    private final EmailProvider emailProvider;
    private final AuditService auditService;
    private final ScheduleCalculator scheduleCalculator;

    private final DeliveryScheduleService deliveryScheduleService;

    // -------------------------------------------------------------------- send

    /**
     * Emails an already generated statement.
     *
     * @param recipients optional override of the configured recipient list
     */
    @Transactional
    public DeliveryLog send(Long statementId, List<String> recipients, String operator) {
        Statement statement = statementService.findEntity(statementId);
        return send(statement, recipients, operator, null);
    }

    @Transactional
    public DeliveryLog send(Statement statement, List<String> recipients,
                            String operator, DeliverySchedule schedule) {
        Client client = statement.getClient();
        Account account = statement.getAccount();

        List<String> to = resolveRecipients(client, recipients);
        if (to.isEmpty()) {
            throw new BusinessException(
                    "No recipients configured for client " + client.getClientCode()
                            + ". Add an email configuration or supply recipients.");
        }

        EmailConfiguration config = emailConfigurationService.resolveFor(client);
        Map<String, String> variables = templateVariables(statement, client, account);

        String subject = render(nvl(config == null ? null : config.getSubjectTemplate(), DEFAULT_SUBJECT), variables);
        String body = render(nvl(config == null ? null : config.getBodyTemplate(), DEFAULT_BODY), variables);

        EmailMessage message = EmailMessage.builder()
                .fromAddress(emailProperties.getFromAddress())
                .fromName(emailProperties.getFromName())
                .to(to)
                .cc(split(config == null ? null : config.getCcAddresses()))
                .bcc(split(config == null ? null : config.getBccAddresses()))
                .subject(subject)
                .body(body)
                .attachmentFileName(statement.getFileName())
                .attachmentContentType("text/plain")
                .attachment(statementService.fileContent(statement))
                .variables(variables)
                .correlationId(MDC.get(com.bank.mt940portal.audit.CorrelationFilter.MDC_KEY))
                .build();

        DeliveryLog logEntry = new DeliveryLog();
        logEntry.setStatement(statement);
        logEntry.setSchedule(schedule);
        logEntry.setClient(client);
        logEntry.setAccount(account);
        logEntry.setRecipient(String.join(",", to));
        logEntry.setSubject(subject);
        logEntry.setProvider(emailProvider.name());
        logEntry.setSentBy(operator);
        logEntry.setSentAt(LocalDateTime.now());
        logEntry.setCorrelationId(message.getCorrelationId());

        EmailDeliveryResult result = null;
        int maxAttempts = Math.max(1, emailProperties.getRetry().getMaxAttempts());
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            logEntry.setAttempt(attempt);
            result = emailProvider.send(message);
            if (result.isSuccess()) {
                break;
            }
            log.warn("Delivery attempt {}/{} for statement {} failed: {}",
                    attempt, maxAttempts, statement.getId(), result.getErrorMessage());
            sleepQuietly(emailProperties.getRetry().getBackoffMs() * attempt);
        }

        boolean success = result != null && result.isSuccess();
        logEntry.setStatus(success ? DeliveryStatus.SENT : DeliveryStatus.FAILED);
        logEntry.setProviderResponse(result == null ? null : result.getProviderResponse());
        logEntry.setErrorMessage(result == null ? "No delivery attempt was made" : result.getErrorMessage());
        DeliveryLog saved = deliveryLogRepository.save(logEntry);

        statement.setDeliveryStatus(success ? DeliveryStatus.SENT : DeliveryStatus.FAILED);
        statement.setErrorMessage(success ? null : logEntry.getErrorMessage());
        statementRepository.save(statement);

        auditService.record(AuditEntry.builder()
                .action(AuditAction.STATEMENT_SEND)
                .entityType("STATEMENT")
                .entityId(String.valueOf(statement.getId()))
                .entityLabel(statement.getStatementReference())
                .description((success ? "Emailed" : "Failed to email") + " statement "
                        + statement.getStatementReference() + " to " + String.join(",", to))
                .outcome(success
                        ? com.bank.mt940portal.domain.enums.AuditOutcome.SUCCESS
                        : com.bank.mt940portal.domain.enums.AuditOutcome.FAILURE)
                .actorOverride(operator)
                .detail(Map.of("attachments", statement.getFileName(),
                        "provider", emailProvider.name(),
                        "attempts", String.valueOf(logEntry.getAttempt()),
                        "account", account.getAccountNumber()))
                .build());

        return saved;
    }

    // --------------------------------------------------------------- scheduled

    /**
     * Runs one schedule: generates a statement for every covered account and
     * emails it. Failures are isolated per account so one bad account does not
     * stop the others.
     */
    @Transactional
    public int runSchedule(DeliverySchedule schedule, LocalDateTime scheduledFor) {
        ScheduleCalculator.StatementPeriod period = scheduleCalculator.period(schedule, scheduledFor);
        List<Account> accounts = deliveryScheduleService.resolveAccounts(schedule);
        int delivered = 0;

        log.info("Running schedule {} for client {} covering {} to {} ({} account(s))",
                schedule.getId(), schedule.getClient().getClientCode(),
                period.from(), period.to(), accounts.size());

        for (Account account : accounts) {
            try {
                if (!schedule.isIncludeZeroTransactionStatements()
                        && !statementService.hasTransactions(account, period.from(), period.to())) {
                    log.debug("Skipping account {}: no transactions in period",
                            account.getAccountNumber());
                    continue;
                }
                Statement statement = statementService.generate(
                        account.getId(), period.from(), period.to(), schedule);
                send(statement, null, "scheduler", schedule);
                delivered++;
            } catch (Exception ex) {
                log.error("Scheduled delivery failed for account {}", account.getAccountNumber(), ex);
                auditService.record(AuditEntry.builder()
                        .action(AuditAction.SCHEDULER_RUN)
                        .entityType("DELIVERY_SCHEDULE")
                        .entityId(String.valueOf(schedule.getId()))
                        .entityLabel(account.getAccountNumber())
                        .description("Scheduled delivery failed: " + ex.getMessage())
                        .outcome(com.bank.mt940portal.domain.enums.AuditOutcome.FAILURE)
                        .actorOverride("scheduler")
                        .build());
            }
        }

        deliveryScheduleService.markSent(schedule, scheduledFor);
        return delivered;
    }

    /** Moves a schedule that missed its window on to the next occurrence. */
    @Transactional
    public void reschedule(DeliverySchedule schedule, LocalDateTime now) {
        deliveryScheduleService.markSent(schedule, now);
    }

    // ---------------------------------------------------------------- plumbing

    private List<String> resolveRecipients(Client client, List<String> override) {
        if (override != null && !override.isEmpty()) {
            return override;
        }
        EmailConfiguration config = emailConfigurationService.resolveFor(client);
        List<String> configured = split(config == null ? null : config.getToAddresses());
        if (!configured.isEmpty()) {
            return configured;
        }
        if (StringUtils.hasText(client.getPrimaryEmail())) {
            return List.of(client.getPrimaryEmail().trim());
        }
        return List.of();
    }

    private static List<String> split(String csv) {
        if (!StringUtils.hasText(csv)) {
            return new ArrayList<>();
        }
        return Arrays.stream(csv.split("[,;]"))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .toList();
    }

    private Map<String, String> templateVariables(Statement statement, Client client, Account account) {
        Map<String, String> vars = new HashMap<>();
        vars.put("clientCode", nvl(client.getClientCode()));
        vars.put("clientName", nvl(client.getClientName()));
        vars.put("accountNumber", nvl(account.getAccountNumber()));
        vars.put("accountName", nvl(account.getAccountName()));
        vars.put("statementReference", nvl(statement.getStatementReference()));
        vars.put("statementNumber", String.valueOf(statement.getStatementNumber()));
        vars.put("periodFrom", statement.getPeriodFrom() == null ? "" : statement.getPeriodFrom().format(DATE));
        vars.put("periodTo", statement.getPeriodTo() == null ? "" : statement.getPeriodTo().format(DATE));
        vars.put("currency", nvl(statement.getCurrency()));
        vars.put("openingBalance", balance(statement.getOpeningMark(), statement.getOpeningBalance()));
        vars.put("closingBalance", balance(statement.getClosingMark(), statement.getClosingBalance()));
        vars.put("transactionCount", String.valueOf(statement.getTransactionCount()));
        vars.put("fileName", nvl(statement.getFileName()));
        vars.put("fromName", nvl(emailProperties.getFromName()));
        vars.put("today", LocalDateTime.now().format(DATE));
        return vars;
    }

    private static String balance(String mark, java.math.BigDecimal value) {
        if (value == null) {
            return "";
        }
        return (mark == null ? "" : mark) + " " + value.toPlainString();
    }

    /** Replaces ${placeholder} tokens in the subject and body templates. */
    static String render(String template, Map<String, String> variables) {
        if (template == null) {
            return "";
        }
        String rendered = template;
        for (Map.Entry<String, String> entry : variables.entrySet()) {
            rendered = rendered.replace("${" + entry.getKey() + "}", entry.getValue() == null ? "" : entry.getValue());
        }
        return rendered;
    }

    private static String nvl(String value) {
        return value == null ? "" : value;
    }

    private static void sleepQuietly(long millis) {
        try {
            Thread.sleep(Math.min(millis, 30_000L));
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }

    public byte[] contentOf(Statement statement) {
        return statement.getContent() == null
                ? new byte[0]
                : statement.getContent().getBytes(StandardCharsets.UTF_8);
    }
}
