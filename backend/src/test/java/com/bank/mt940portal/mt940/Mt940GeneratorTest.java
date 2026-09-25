package com.bank.mt940portal.mt940;

import com.bank.mt940portal.domain.enums.BalanceTag;
import com.bank.mt940portal.domain.enums.FundsCodeStrategy;
import com.bank.mt940portal.domain.enums.IsnStrategy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins the generator to the sample statement supplied by the bank. If this test
 * fails, the output no longer matches what the bank expects.
 */
class Mt940GeneratorTest {

    private static final String ACCOUNT = "1144355935012";

    private SwiftProperties defaults;
    private Mt940Generator generator;

    @BeforeEach
    void setUp() {
        defaults = new SwiftProperties();
        defaults.setSenderLtAddress("DASHETAXXXXX");
        defaults.setReceiverLtAddress("RECVETAAXXXX");
        defaults.setSessionNumber("0001");
        defaults.setIsnStrategy(IsnStrategy.MIRROR_STATEMENT_NUMBER);
        defaults.setPriority("N");
        defaults.setDefaultCurrency("ETB");
        defaults.setReferencePrefix("STMT");
        defaults.setFundsCodeStrategy(FundsCodeStrategy.NONE);
        defaults.setDefaultTransactionType("NTRF");
        defaults.setOpeningBalanceTag(BalanceTag.F);
        defaults.setClosingBalanceTag(BalanceTag.F);
        defaults.setBlankLineBetweenTags(false);
        defaults.setLineSeparator(SwiftProperties.LineSeparator.LF);
        defaults.setUppercaseText(true);
        defaults.setEmit13d(false);
        defaults.setEmit90d(false);
        defaults.setEmit64(false);
        defaults.setEmit65(false);
        generator = new Mt940Generator(defaults);
    }

    @Test
    @DisplayName("Reproduces the reference statement exactly")
    void rendersReferenceStatement() {
        Mt940Transaction transaction = Mt940Transaction.builder()
                .transactionDate(LocalDate.of(2026, 9, 23))
                .valueDate(LocalDate.of(2026, 9, 23))
                .entryDate(LocalDate.of(2026, 9, 23))
                .amount(new BigDecimal("2340.00"))
                .debitCredit("D")
                .customerReference("879FXSA262660001")
                .transactionType("NTRF")
                .description("CASH FCY BOUGHT AND SOLD")
                .build();

        Mt940RenderRequest request = Mt940RenderRequest.builder()
                .accountNumber(ACCOUNT)
                .currency("ETB")
                .statementDate(LocalDate.of(2026, 9, 23))
                .periodFrom(LocalDate.of(2026, 9, 23))
                .periodTo(LocalDate.of(2026, 9, 23))
                .statementNumber(20719L)
                .pageSequence(1)
                .openingBalance(new BigDecimal("327060.15"))
                .closingBalance(new BigDecimal("324720.15"))
                .transactions(List.of(transaction))
                .settings(defaults)
                .build();

        String expected = """
                {1:F01DASHETAXXXXX0001020719}{2:I940RECVETAAXXXXN}{4:
                :20:STMT260923935012
                :25:1144355935012
                :28C:20719/1
                :60F:C260923ETB327060,15
                :61:2609230923D2340,00NTRF879FXSA262660001
                :86:CASH FCY BOUGHT AND SOLD
                :62F:C260923ETB324720,15
                -}
                """;

        Mt940RenderResult result = generator.render(request);

        assertEquals(expected, result.getContent());
        assertEquals("STMT260923935012", result.getStatementReference());
        assertEquals("020719", result.getIsn());
        assertEquals(20719L, result.getStatementNumber());
        assertEquals("C", result.getOpeningMark());
        assertEquals("C", result.getClosingMark());
        assertEquals(new BigDecimal("327060.15"), result.getOpeningBalance());
        assertEquals(new BigDecimal("324720.15"), result.getClosingBalance());
        assertEquals("1144355935012_20260923.txt", result.getSuggestedFileName());
    }

    @Test
    @DisplayName("Credit entries use the C mark and the amount stays positive")
    void rendersCreditEntries() {
        Mt940Transaction credit = Mt940Transaction.builder()
                .transactionDate(LocalDate.of(2026, 9, 23))
                .amount(new BigDecimal("150000.00"))
                .debitCredit("C")
                .customerReference("FT26266000077")
                .transactionType("NTRF")
                .description("INWARD REMITTANCE")
                .build();

        Mt940RenderResult result = generator.render(requestWith(credit));

        assertTrue(result.getContent().contains(":61:2609230923C150000,00NTRFFT26266000077"));
        assertTrue(result.getContent().contains(":86:INWARD REMITTANCE"));
    }

    @Test
    @DisplayName("Reference is truncated to the 16 character SWIFT limit")
    void truncatesReference() {
        Mt940RenderResult result = generator.render(Mt940RenderRequest.builder()
                .accountNumber("1144355935012")
                .currency("ETB")
                .statementDate(LocalDate.of(2026, 9, 23))
                .statementNumber(1L)
                .pageSequence(1)
                .openingBalance(BigDecimal.ZERO)
                .closingBalance(BigDecimal.ZERO)
                .transactions(List.of())
                .settings(defaults)
                .build());

        assertEquals(16, result.getStatementReference().length());
        assertTrue(result.getContent().contains(":20:STMT260923935012"));
    }

    @Test
    @DisplayName("Optional tags are emitted only when enabled")
    void optionalTags() {
        defaults.setEmit13d(true);
        defaults.setEmit90d(true);
        defaults.setEmit64(true);

        Mt940Transaction debit = Mt940Transaction.builder()
                .transactionDate(LocalDate.of(2026, 9, 23))
                .amount(new BigDecimal("2340.00"))
                .debitCredit("D")
                .customerReference("879FXSA262660001")
                .transactionType("NTRF")
                .description("CASH FCY BOUGHT AND SOLD")
                .build();

        String content = generator.render(
                        requestWith(debit, java.time.LocalDateTime.of(2026, 9, 24, 7, 0),
                                new BigDecimal("324720.15")))
                .getContent();

        assertTrue(content.contains(":13D:2609240700+0000"), content);
        assertTrue(content.contains(":90D:1ETB2340,00"), content);
        assertTrue(content.contains(":90C:0ETB0,00"), content);
        assertTrue(content.contains(":64:C260923ETB324720,15"), content);
    }

    @Test
    @DisplayName("The funds code can be switched on for banks that require it")
    void fundsCode() {
        defaults.setFundsCodeStrategy(FundsCodeStrategy.CURRENCY_THIRD_CHAR);

        Mt940Transaction debit = Mt940Transaction.builder()
                .transactionDate(LocalDate.of(2026, 9, 23))
                .amount(new BigDecimal("2340.00"))
                .debitCredit("D")
                .customerReference("879FXSA262660001")
                .transactionType("NTRF")
                .description("CASH FCY BOUGHT AND SOLD")
                .build();

        String content = generator.render(requestWith(debit)).getContent();
        assertTrue(content.contains(":61:2609230923DB2340,00NTRF879FXSA262660001"), content);
    }

    @Test
    @DisplayName("Blank lines can be emitted for consumers that expect them")
    void blankLines() {
        defaults.setBlankLineBetweenTags(true);

        Mt940Transaction debit = Mt940Transaction.builder()
                .transactionDate(LocalDate.of(2026, 9, 23))
                .amount(new BigDecimal("2340.00"))
                .debitCredit("D")
                .customerReference("879FXSA262660001")
                .transactionType("NTRF")
                .description("CASH FCY BOUGHT AND SOLD")
                .build();

        String content = generator.render(requestWith(debit)).getContent();
        assertTrue(content.contains(":25:1144355935012\n\n:28C:"), content);
    }

    @Test
    @DisplayName("Closing balance is derived when the feed does not supply one")
    void derivesClosingBalance() {
        Mt940Transaction debit = Mt940Transaction.builder()
                .transactionDate(LocalDate.of(2026, 9, 23))
                .amount(new BigDecimal("2340.00"))
                .debitCredit("D")
                .customerReference("REF1")
                .transactionType("NTRF")
                .description("TEST")
                .build();

        Mt940RenderResult result = generator.render(requestWith(debit, null, null));
        assertEquals(new BigDecimal("324720.15"), result.getClosingBalance());
    }

    private Mt940RenderRequest requestWith(Mt940Transaction transaction) {
        return requestWith(transaction, null, null);
    }

    private Mt940RenderRequest requestWith(Mt940Transaction transaction,
                                           java.time.LocalDateTime generationTime,
                                           BigDecimal closingBalance) {
        return Mt940RenderRequest.builder()
                .generationTime(generationTime)
                .closingBalance(closingBalance)
                .accountNumber(ACCOUNT)
                .currency("ETB")
                .statementDate(LocalDate.of(2026, 9, 23))
                .statementNumber(20719L)
                .pageSequence(1)
                .openingBalance(new BigDecimal("327060.15"))
                .transactions(List.of(transaction))
                .settings(defaults)
                .build();
    }
}
