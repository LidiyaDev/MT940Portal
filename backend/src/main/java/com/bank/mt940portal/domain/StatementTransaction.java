package com.bank.mt940portal.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Snapshot of one source transaction as captured on a statement. */
@Entity
@Table(name = "MT_STATEMENT_TXN")
@Getter
@Setter
public class StatementTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "mt940_seq_gen")
    @SequenceGenerator(name = "mt940_seq_gen", sequenceName = "MT940_SEQ", allocationSize = 50)
    @Column(name = "ID", nullable = false, updatable = false)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "STATEMENT_ID", nullable = false,
            foreignKey = @ForeignKey(name = "FK_MT_STMTTXN_STMT"))
    private Statement statement;

    @Column(name = "LINE_NO", nullable = false)
    private Integer lineNo;

    @Column(name = "TXN_DATE")
    private LocalDate transactionDate;

    @Column(name = "VALUE_DATE")
    private LocalDate valueDate;

    @Column(name = "ENTRY_DATE")
    private LocalDate entryDate;

    /** 'D' or 'C' as it appears in tag :61:. */
    @Column(name = "DC_MARK", nullable = false, length = 1)
    private String debitCreditMark;

    @Column(name = "AMOUNT", nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Column(name = "CURRENCY", length = 3)
    private String currency;

    /** 16x customer reference from tag :61:. */
    @Column(name = "CUST_REFERENCE", length = 64)
    private String customerReference;

    @Column(name = "BANK_REFERENCE", length = 64)
    private String bankReference;

    /** 1!a3!c transaction type identification code, e.g. NTRF. */
    @Column(name = "TRANSACTION_TYPE", length = 4)
    private String transactionType;

    @Column(name = "DESCRIPTION", length = 500)
    private String description;

    @Column(name = "BEGINNING_BALANCE", precision = 19, scale = 2)
    private BigDecimal beginningBalance;

    @Column(name = "CLOSING_BALANCE", precision = 19, scale = 2)
    private BigDecimal closingBalance;
}
