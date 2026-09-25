package com.bank.mt940portal.domain;

import com.bank.mt940portal.domain.enums.BalanceTag;
import com.bank.mt940portal.domain.enums.ClientStatus;
import com.bank.mt940portal.domain.enums.FundsCodeStrategy;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/**
 * A corporate client that receives MT940 statements.
 * <p>
 * The {@code senderLtAddress}, {@code fundsCodeStrategy} and {@code emit*}
 * fields are optional per-client overrides of the global SWIFT configuration.
 * When null the global default is used.
 */
@Entity
@Table(name = "MT_CLIENT")
@Getter
@Setter
public class Client extends BaseEntity {

    @Column(name = "CLIENT_CODE", nullable = false, unique = true, length = 32)
    private String clientCode;

    @Column(name = "CLIENT_NAME", nullable = false, length = 200)
    private String clientName;

    @Column(name = "PRIMARY_EMAIL", length = 320)
    private String primaryEmail;

    @Column(name = "CONTACT_PERSON", length = 120)
    private String contactPerson;

    @Column(name = "PHONE", length = 40)
    private String phone;

    @Column(name = "ADDRESS", length = 500)
    private String address;

    @Column(name = "DEFAULT_CURRENCY", nullable = false, length = 3)
    private String defaultCurrency = "ETB";

    @Column(name = "TIMEZONE", nullable = false, length = 64)
    private String timezone = "Africa/Addis_Ababa";

    @Enumerated(EnumType.STRING)
    @Column(name = "STATUS", nullable = false, length = 20)
    private ClientStatus status = ClientStatus.ACTIVE;

    // --- SWIFT rendering overrides ----------------------------------------
    @Column(name = "SENDER_LT_ADDRESS", length = 12)
    private String senderLtAddress;

    @Column(name = "RECEIVER_LT_ADDRESS", length = 12)
    private String receiverLtAddress;

    @Enumerated(EnumType.STRING)
    @Column(name = "FUNDS_CODE_STRATEGY", length = 24)
    private FundsCodeStrategy fundsCodeStrategy;

    @Enumerated(EnumType.STRING)
    @Column(name = "OPENING_BALANCE_TAG", length = 1)
    private BalanceTag openingBalanceTag;

    @Enumerated(EnumType.STRING)
    @Column(name = "CLOSING_BALANCE_TAG", length = 1)
    private BalanceTag closingBalanceTag;

    @Column(name = "EMIT_13D", nullable = false)
    private boolean emit13d = false;

    @Column(name = "EMIT_64", nullable = false)
    private boolean emit64 = false;

    @Column(name = "EMIT_65", nullable = false)
    private boolean emit65 = false;

    @Column(name = "EMIT_90D", nullable = false)
    private boolean emit90d = false;

    @Column(name = "BLANK_LINES", nullable = false)
    private boolean blankLineBetweenTags = false;

    /** Starting point of the :28C: statement number for this client's accounts. */
    @Column(name = "STATEMENT_NO_SEED", nullable = false)
    private Long statementNumberSeed = 0L;

    @Column(name = "LAST_STATEMENT_NO", nullable = false)
    private Long lastStatementNumber = 0L;

    @Column(name = "REMARKS", length = 1000)
    private String remarks;

    @OneToMany(mappedBy = "client", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("accountNumber ASC")
    private List<Account> accounts = new ArrayList<>();

    public void addAccount(Account account) {
        accounts.add(account);
        account.setClient(this);
    }
}
