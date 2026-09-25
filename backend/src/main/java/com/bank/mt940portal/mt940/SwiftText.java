package com.bank.mt940portal.mt940;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;

/**
 * SWIFT character set handling and field formatting.
 * <p>
 * FIN only accepts: a-z A-Z 0-9 / - ? : ( ) . , ' + and space. Anything else is
 * folded to a space; accented characters are decomposed first so that e.g. "é"
 * becomes "e" rather than being dropped.
 */
public final class SwiftText {

    private static final String ALLOWED = "ABCDEFGHIJKLMNOPQRSTUVWXYZ"
            + "abcdefghijklmnopqrstuvwxyz"
            + "0123456789/-?:().,'+ ";

    private SwiftText() {
    }

    /** Folds text into the SWIFT character set, collapsing whitespace runs. */
    public static String sanitise(String value) {
        if (value == null) {
            return "";
        }
        String decomposed = Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{InCombiningDiacriticalMarks}+", "");
        StringBuilder sb = new StringBuilder(decomposed.length());
        boolean previousSpace = true;
        for (char c : decomposed.toCharArray()) {
            if (ALLOWED.indexOf(c) >= 0) {
                if (c == ' ') {
                    if (!previousSpace) {
                        sb.append(c);
                    }
                    previousSpace = true;
                } else {
                    sb.append(c);
                    previousSpace = false;
                }
            } else if (c == '\n' || c == '\r' || c == '\t') {
                if (!previousSpace) {
                    sb.append(' ');
                }
                previousSpace = true;
            } else {
                if (!previousSpace) {
                    sb.append(' ');
                }
                previousSpace = true;
            }
        }
        return sb.toString().trim();
    }

    /** Sanitises and, when configured, upper cases. */
    public static String sanitise(String value, boolean uppercase) {
        String cleaned = sanitise(value);
        return uppercase ? cleaned.toUpperCase() : cleaned;
    }

    /**
     * SWIFT decimal: comma separator, exactly two decimals, no grouping, no sign.
     * 327060.15 -> 327060,15
     */
    public static String amount(BigDecimal value) {
        if (value == null) {
            value = BigDecimal.ZERO;
        }
        return value.abs()
                .setScale(2, RoundingMode.HALF_UP)
                .toPlainString()
                .replace('.', ',');
    }

    /** 'C' for a credit (positive or zero) balance, 'D' for an overdrawn one. */
    public static String balanceMark(BigDecimal value) {
        return value != null && value.signum() < 0 ? "D" : "C";
    }

    /** Contracts a reference to the 16x customer reference of tag :61:. */
    public static String customerReference(String value) {
        String cleaned = sanitise(value, true).replace(" ", "");
        return cleaned.length() <= 16 ? cleaned : cleaned.substring(0, 16);
    }

    /** Wraps tag :86: narrative onto at most maxLines lines of maxLength. */
    public static List<String> narrativeLines(String value, boolean uppercase, int maxLines, int maxLength) {
        String cleaned = sanitise(value, uppercase);
        List<String> lines = new ArrayList<>();
        if (cleaned.isEmpty()) {
            return lines;
        }
        int index = 0;
        while (index < cleaned.length() && lines.size() < maxLines) {
            int end = Math.min(index + maxLength, cleaned.length());
            if (end < cleaned.length()) {
                int lastSpace = cleaned.lastIndexOf(' ', end);
                if (lastSpace > index) {
                    end = lastSpace;
                }
            }
            String chunk = cleaned.substring(index, end).trim();
            if (!chunk.isEmpty()) {
                lines.add(chunk);
            }
            index = end + 1;
        }
        return lines;
    }

    /** Zero pads a number to the requested width, truncating from the left if longer. */
    public static String padLeft(long value, int width) {
        String raw = Long.toString(value);
        if (raw.length() >= width) {
            return raw.substring(raw.length() - width);
        }
        return "0".repeat(width - raw.length()) + raw;
    }

    /** Truncates or right pads a fixed width alphanumeric field. */
    public static String fixed(String value, int width) {
        String cleaned = sanitise(value, true);
        if (cleaned.length() == width) {
            return cleaned;
        }
        if (cleaned.length() > width) {
            return cleaned.substring(0, width);
        }
        return cleaned + "X".repeat(width - cleaned.length());
    }
}
