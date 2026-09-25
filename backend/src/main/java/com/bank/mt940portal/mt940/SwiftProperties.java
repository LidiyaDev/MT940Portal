package com.bank.mt940portal.mt940;

import com.bank.mt940portal.domain.enums.BalanceTag;
import com.bank.mt940portal.domain.enums.FundsCodeStrategy;
import com.bank.mt940portal.domain.enums.IsnStrategy;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Global SWIFT rendering defaults. Every value can be overridden per client
 * through the {@code MT_CLIENT} columns.
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "mt940.swift")
public class SwiftProperties {

    /** 12 character sender LT address in the basic header {1:}. */
    private String senderLtAddress = "DASHETAXXXXX";

    /** 12 character receiver LT address in the application header {2:}. */
    private String receiverLtAddress = "RECVETAAXXXX";

    /** 4 digit session number in the basic header. */
    private String sessionNumber = "0001";

    private IsnStrategy isnStrategy = IsnStrategy.MIRROR_STATEMENT_NUMBER;

    /** 6 digit ISN used when isnStrategy = STATIC. */
    private String staticIsn = "000001";

    /** Delivery monitoring field in the application header. */
    private String priority = "N";

    private String defaultCurrency = "ETB";

    /**
     * SWIFT FIN does not permit empty lines inside the text block. Set to true
     * only when a downstream consumer expects them (some core banking exports
     * contain them).
     */
    private boolean blankLineBetweenTags = false;

    private LineSeparator lineSeparator = LineSeparator.LF;

    /** Prefix of tag :20:, e.g. STMT -> STMT260923935012. */
    private String referencePrefix = "STMT";

    private FundsCodeStrategy fundsCodeStrategy = FundsCodeStrategy.NONE;

    /** 1!a3!c transaction type identification code used in :61:. */
    private String defaultTransactionType = "NTRF";

    private BalanceTag openingBalanceTag = BalanceTag.F;

    private BalanceTag closingBalanceTag = BalanceTag.F;

    /** Restrict text to the SWIFT character set and force upper case. */
    private boolean uppercaseText = true;

    /**
     * Optional keyword to transaction type mapping applied to tag :61:,
     * e.g. {CHARGE: NCHG, TRANSFER: NTRF}. The first matching key wins; when
     * nothing matches defaultTransactionType is used.
     */
    private Map<String, String> transactionTypeRules = new LinkedHashMap<>();

    private boolean emit13d = false;
    private boolean emit90d = false;
    private boolean emit64 = false;
    private boolean emit65 = false;

    private int max86Lines = 6;
    private int max86LineLength = 65;

    public enum LineSeparator {
        LF("\n"), CRLF("\r\n");

        private final String value;

        LineSeparator(String value) {
            this.value = value;
        }

        public String value() {
            return value;
        }
    }

    /** Per-client override resolution helper. */
    public SwiftProperties copyWith(ClientOverride override) {
        SwiftProperties copy = new SwiftProperties();
        copy.senderLtAddress = nvl(override.senderLtAddress, this.senderLtAddress);
        copy.receiverLtAddress = nvl(override.receiverLtAddress, this.receiverLtAddress);
        copy.sessionNumber = this.sessionNumber;
        copy.isnStrategy = this.isnStrategy;
        copy.staticIsn = this.staticIsn;
        copy.priority = this.priority;
        copy.defaultCurrency = nvl(override.currency, this.defaultCurrency);
        copy.blankLineBetweenTags = override.blankLineBetweenTags != null
                ? override.blankLineBetweenTags : this.blankLineBetweenTags;
        copy.lineSeparator = this.lineSeparator;
        copy.referencePrefix = this.referencePrefix;
        copy.fundsCodeStrategy = override.fundsCodeStrategy != null
                ? override.fundsCodeStrategy : this.fundsCodeStrategy;
        copy.defaultTransactionType = this.defaultTransactionType;
        copy.transactionTypeRules = this.transactionTypeRules;
        copy.openingBalanceTag = override.openingBalanceTag != null
                ? override.openingBalanceTag : this.openingBalanceTag;
        copy.closingBalanceTag = override.closingBalanceTag != null
                ? override.closingBalanceTag : this.closingBalanceTag;
        copy.uppercaseText = this.uppercaseText;
        copy.emit13d = override.emit13d != null ? override.emit13d : this.emit13d;
        copy.emit90d = override.emit90d != null ? override.emit90d : this.emit90d;
        copy.emit64 = override.emit64 != null ? override.emit64 : this.emit64;
        copy.emit65 = override.emit65 != null ? override.emit65 : this.emit65;
        copy.max86Lines = this.max86Lines;
        copy.max86LineLength = this.max86LineLength;
        return copy;
    }

    private static String nvl(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    /** Per-client SWIFT overrides. */
    @Getter
    @Setter
    @lombok.Builder
    public static class ClientOverride {
        private String senderLtAddress;
        private String receiverLtAddress;
        private String currency;
        private Boolean blankLineBetweenTags;
        private FundsCodeStrategy fundsCodeStrategy;
        private BalanceTag openingBalanceTag;
        private BalanceTag closingBalanceTag;
        private Boolean emit13d;
        private Boolean emit90d;
        private Boolean emit64;
        private Boolean emit65;
    }
}
