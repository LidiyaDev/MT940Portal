package com.bank.mt940portal.mt940;

import com.bank.mt940portal.domain.enums.FundsCodeStrategy;
import com.bank.mt940portal.domain.enums.IsnStrategy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Renders a SWIFT MT940 (Customer Statement) message.
 *
 * <pre>
 * {1:F01DASHETAXXXXX0001020719}{2:I940RECVETAAXXXXN}{4:
 * :20:STMT260923935012
 * :25:1144355935012
 * :28C:20719/1
 * :60F:C260923ETB327060,15
 * :61:2609230923D2340,00NTRF879FXSA262660001
 * :86:CASH FCY BOUGHT AND SOLD
 * :62F:C260923ETB324720,15
 * -}
 * </pre>
 *
 * Layout follows the sample statement supplied by the bank: no funds code after
 * the D/C mark, NTRF transaction type, a single free text :86: line, and F
 * (final) opening/closing balance tags. Tags :13D:, :90D:, :90C:, :64: and :65:
 * are optional and disabled by default.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class Mt940Generator {

    private static final DateTimeFormatter YYMMDD = DateTimeFormatter.ofPattern("yyMMdd");
    private static final DateTimeFormatter MMDD = DateTimeFormatter.ofPattern("MMdd");
    private static final DateTimeFormatter YYYYMMDD = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final SwiftProperties swiftProperties;

    public Mt940RenderResult render(Mt940RenderRequest request) {
        SwiftProperties cfg = request.getSettings() != null ? request.getSettings() : swiftProperties;
        String eol = cfg.getLineSeparator().value();
        String currency = cfg.getDefaultCurrency();
        if (request.getCurrency() != null && !request.getCurrency().isBlank()) {
            currency = request.getCurrency();
        }

        LocalDate statementDate = request.getStatementDate() != null
                ? request.getStatementDate()
                : LocalDate.now();

        List<Mt940Transaction> transactions = request.getTransactions() == null
                ? List.of() : request.getTransactions();

        BigDecimal closing = request.getClosingBalance();
        BigDecimal opening = request.getOpeningBalance();
        if (opening == null) {
            opening = deriveOpening(closing, transactions);
        }
        if (closing == null) {
            closing = deriveClosing(opening, transactions);
        }

        String openingMark = SwiftText.balanceMark(opening);
        String closingMark = SwiftText.balanceMark(closing);
        String isn = resolveIsn(request, cfg);
        String reference = buildReference(request, statementDate, cfg);

        StringBuilder sb = new StringBuilder();

        // --- Block 1: basic header -------------------------------------------------
        sb.append("{1:F01")
                .append(SwiftText.fixed(cfg.getSenderLtAddress(), 12))
                .append(SwiftText.fixed(cfg.getSessionNumber(), 4))
                .append(SwiftText.fixed(isn, 6))
                .append('}');

        // --- Block 2: application header -------------------------------------------
        sb.append("{2:I940")
                .append(SwiftText.fixed(cfg.getReceiverLtAddress(), 12))
                .append(SwiftText.sanitise(cfg.getPriority(), true))
                .append('}');

        // --- Block 4: text ---------------------------------------------------------
        sb.append("{4:").append(eol);

        append(sb, eol, ":20:", reference, cfg);
        append(sb, eol, ":25:", SwiftText.sanitise(request.getAccountNumber(), true), cfg);

        append(sb, eol, ":28C:", request.getStatementNumber() + "/" + Math.max(1, request.getPageSequence()), cfg);

        if (cfg.isEmit13d()) {
            LocalDateTime stamp = request.getGenerationTime() != null
                    ? request.getGenerationTime() : LocalDateTime.now();
            append(sb, eol, ":13D:",
                    stamp.format(DateTimeFormatter.ofPattern("yyMMddHHmm")) + "+0000", cfg);
        }

        append(sb, eol, ":60" + cfg.getOpeningBalanceTag().name() + ":",
                openingMark + statementDate.format(YYMMDD) + currency + SwiftText.amount(opening), cfg);

        for (Mt940Transaction txn : transactions) {
            sb.append(tag61(txn, currency, cfg)).append(eol);
            if (cfg.isBlankLineBetweenTags()) {
                sb.append(eol);
            }
            for (String line : SwiftText.narrativeLines(txn.getDescription(),
                    cfg.isUppercaseText(), cfg.getMax86Lines(), cfg.getMax86LineLength())) {
                sb.append(":86:").append(line).append(eol);
                if (cfg.isBlankLineBetweenTags()) {
                    sb.append(eol);
                }
            }
        }

        if (cfg.isEmit90d()) {
            append(sb, eol, ":90D:", countAndSum(transactions, true, currency), cfg);
            append(sb, eol, ":90C:", countAndSum(transactions, false, currency), cfg);
        }

        append(sb, eol, ":62" + cfg.getClosingBalanceTag().name() + ":",
                closingMark + statementDate.format(YYMMDD) + currency + SwiftText.amount(closing), cfg);

        if (cfg.isEmit64()) {
            append(sb, eol, ":64:",
                    closingMark + statementDate.format(YYMMDD) + currency + SwiftText.amount(closing), cfg);
        }
        if (cfg.isEmit65()) {
            LocalDate forward = statementDate.plusDays(1);
            append(sb, eol, ":65:",
                    closingMark + forward.format(YYMMDD) + currency + SwiftText.amount(closing), cfg);
        }

        sb.append("-}").append(eol);

        return Mt940RenderResult.builder()
                .content(sb.toString())
                .statementReference(reference)
                .statementNumber(request.getStatementNumber())
                .pageSequence(Math.max(1, request.getPageSequence()))
                .isn(isn)
                .openingMark(openingMark)
                .closingMark(closingMark)
                .openingBalance(opening)
                .closingBalance(closing)
                .transactionCount(transactions.size())
                .suggestedFileName(SwiftText.sanitise(request.getAccountNumber(), true)
                        + "_" + statementDate.format(YYYYMMDD) + ".txt")
                .build();
    }

    // -------------------------------------------------------------------------

    private void append(StringBuilder sb, String eol, String tag, String value, SwiftProperties cfg) {
        sb.append(tag).append(SwiftText.sanitise(value, cfg.isUppercaseText())).append(eol);
        if (cfg.isBlankLineBetweenTags()) {
            sb.append(eol);
        }
    }

    /**
     * :61: 6!n[4!n]2a[1!a]15d1!a3!c16x[//16x]
     * value date, entry date (MMDD), D/C mark, optional funds code, amount,
     * transaction type identification code, customer reference.
     */
    private String tag61(Mt940Transaction txn, String currency, SwiftProperties cfg) {
        LocalDate valueDate = txn.getValueDate() != null ? txn.getValueDate() : txn.getTransactionDate();
        if (valueDate == null) {
            valueDate = LocalDate.now();
        }
        LocalDate entryDate = txn.getEntryDate() != null ? txn.getEntryDate() : valueDate;

        String mark = "C".equalsIgnoreCase(txn.getDebitCredit()) ? "C" : "D";

        String fundsCode = "";
        if (cfg.getFundsCodeStrategy() == FundsCodeStrategy.CURRENCY_THIRD_CHAR
                && currency != null && currency.length() == 3) {
            fundsCode = String.valueOf(currency.charAt(2)).toUpperCase();
        }

        String type = (txn.getTransactionType() == null || txn.getTransactionType().isBlank())
                ? cfg.getDefaultTransactionType()
                : SwiftText.sanitise(txn.getTransactionType(), true);

        StringBuilder sb = new StringBuilder();
        sb.append(valueDate.format(YYMMDD))
                .append(entryDate.format(MMDD))
                .append(mark)
                .append(fundsCode)
                .append(SwiftText.amount(txn.getAmount()))
                .append(type)
                .append(SwiftText.customerReference(txn.getCustomerReference()));

        if (txn.getBankReference() != null && !txn.getBankReference().isBlank()) {
            sb.append("//").append(SwiftText.customerReference(txn.getBankReference()));
        }
        return sb.toString();
    }

    /** :20: - prefix + YYMMDD + last six characters of the account number (16x). */
    private String buildReference(Mt940RenderRequest request, LocalDate statementDate, SwiftProperties cfg) {
        String account = SwiftText.sanitise(request.getAccountNumber(), true);
        String suffix = account.length() <= 6 ? account : account.substring(account.length() - 6);
        String raw = cfg.getReferencePrefix() + statementDate.format(YYMMDD) + suffix;
        return raw.length() > 16 ? raw.substring(0, 16) : raw;
    }

    private String resolveIsn(Mt940RenderRequest request, SwiftProperties cfg) {
        if (request.getIsn() != null && !request.getIsn().isBlank()) {
            return SwiftText.padLeft(Long.parseLong(request.getIsn().replaceAll("\\D", "")), 6);
        }
        return switch (cfg.getIsnStrategy()) {
            case STATIC -> SwiftText.padLeft(Long.parseLong(cfg.getStaticIsn().replaceAll("\\D", "")), 6);
            case ACCOUNT_SEQUENCE, MIRROR_STATEMENT_NUMBER ->
                    SwiftText.padLeft(request.getStatementNumber(), 6);
        };
    }

    /** count + currency + total, e.g. 1ETB2340,00 */
    private String countAndSum(List<Mt940Transaction> transactions, boolean debits, String currency) {
        long count = 0;
        BigDecimal total = BigDecimal.ZERO;
        for (Mt940Transaction txn : transactions) {
            boolean isDebit = !"C".equalsIgnoreCase(txn.getDebitCredit());
            if (isDebit == debits) {
                count++;
                total = total.add(nvl(txn.getAmount()));
            }
        }
        return count + currency + SwiftText.amount(total);
    }

    private BigDecimal deriveOpening(BigDecimal closing, List<Mt940Transaction> transactions) {
        if (closing == null) {
            return BigDecimal.ZERO;
        }
        BigDecimal opening = closing;
        for (Mt940Transaction txn : transactions) {
            BigDecimal amount = nvl(txn.getAmount());
            opening = "C".equalsIgnoreCase(txn.getDebitCredit()) ? opening.subtract(amount) : opening.add(amount);
        }
        return opening;
    }

    private BigDecimal deriveClosing(BigDecimal opening, List<Mt940Transaction> transactions) {
        BigDecimal closing = nvl(opening);
        for (Mt940Transaction txn : transactions) {
            BigDecimal amount = nvl(txn.getAmount());
            closing = "C".equalsIgnoreCase(txn.getDebitCredit()) ? closing.add(amount) : closing.subtract(amount);
        }
        return closing;
    }

    private static BigDecimal nvl(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
