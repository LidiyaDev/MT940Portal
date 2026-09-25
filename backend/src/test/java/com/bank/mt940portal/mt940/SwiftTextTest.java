package com.bank.mt940portal.mt940;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SwiftTextTest {

    @Test
    @DisplayName("Amounts use a comma decimal separator and always two decimals")
    void amounts() {
        assertEquals("327060,15", SwiftText.amount(new BigDecimal("327060.15")));
        assertEquals("2340,00", SwiftText.amount(new BigDecimal("2340")));
        assertEquals("2340,00", SwiftText.amount(new BigDecimal("2340.00")));
        assertEquals("0,00", SwiftText.amount(null));
        assertEquals("2340,00", SwiftText.amount(new BigDecimal("-2340")));
    }

    @Test
    @DisplayName("Balance marks follow the sign of the balance")
    void balanceMarks() {
        assertEquals("C", SwiftText.balanceMark(new BigDecimal("1.00")));
        assertEquals("C", SwiftText.balanceMark(BigDecimal.ZERO));
        assertEquals("D", SwiftText.balanceMark(new BigDecimal("-1.00")));
    }

    @Test
    @DisplayName("Customer references are folded to the SWIFT set and capped at 16")
    void customerReference() {
        assertEquals("", SwiftText.customerReference(null));
        assertEquals("879FXSA262660001", SwiftText.customerReference("879FXSA262660001"));
        assertEquals("8798799262450001",
                SwiftText.customerReference("8798799262450001 ACDB/TT/00971/26"));
        assertEquals("ABC123", SwiftText.customerReference("abc 123"));
    }

    @Test
    @DisplayName("Text outside the SWIFT character set is folded, not lost")
    void sanitise() {
        assertEquals("CAFE LTD", SwiftText.sanitise("Café Ltd", true));
        assertEquals("A B", SwiftText.sanitise("A\t\tB", true));
        assertEquals("PAYMENT 100", SwiftText.sanitise("Payment — 100", true));
        assertEquals("", SwiftText.sanitise(null, true));
    }

    @Test
    @DisplayName("Tag :86: wraps at word boundaries and honours the line cap")
    void narrative() {
        String longText = "VERY LONG NARRATIVE TEXT THAT MUST BE WRAPPED ACROSS SEVERAL LINES "
                + "BECAUSE SWIFT ONLY ALLOWS SIXTY FIVE CHARACTERS PER LINE";

        List<String> lines = SwiftText.narrativeLines(longText, true, 6, 65);

        assertTrue(lines.size() > 1);
        assertTrue(lines.size() <= 6);
        for (String line : lines) {
            assertTrue(line.length() <= 65, "line too long: " + line);
        }
        assertEquals(List.of("CASH FCY BOUGHT AND SOLD"),
                SwiftText.narrativeLines("CASH FCY BOUGHT AND SOLD", true, 6, 65));
    }

    @Test
    @DisplayName("Fixed width fields are padded or truncated")
    void fixedWidth() {
        assertEquals("DASHETAXXXXX", SwiftText.fixed("DASHETAXXXXX", 12));
        assertEquals("DASHETAXXXXX", SwiftText.fixed("DASHETAA", 12));
        assertEquals("ABCD", SwiftText.fixed("ABCDEFGH", 4));
        assertEquals("020719", SwiftText.padLeft(20719, 6));
        assertEquals("20719", SwiftText.padLeft(20719, 5));
    }
}
