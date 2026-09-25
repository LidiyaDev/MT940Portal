package com.bank.mt940portal.domain;

import com.bank.mt940portal.domain.enums.DeliveryStatus;
import com.bank.mt940portal.domain.enums.StatementStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/** A generated MT940 statement and the line items it was built from. */
@Entity
@Table(name = "MT_STATEMENT")
@Getter
@Setter
public class Statement {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "mt940_seq_gen")
    @SequenceGenerator(name = "mt940_seq_gen", sequenceName = "MT940_SEQ", allocationSize = 50)
    @Column(name = "ID", nullable = false, updatable = false)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "CLIENT_ID", nullable = false,
            foreignKey = @ForeignKey(name = "FK_MT_STMT_CLIENT"))
    private Client client;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ACCOUNT_ID", nullable = false,
            foreignKey = @ForeignKey(name = "FK_MT_STMT_ACCOUNT"))
    private Account account;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "SCHEDULE_ID",
            foreignKey = @ForeignKey(name = "FK_MT_STMT_SCHEDULE"))
    private DeliverySchedule schedule;

    /** Tag :20: - max 16 characters. */
    @Column(name = "STATEMENT_REFERENCE", nullable = false, length = 16)
    private String statementReference;

    /** Tag :28C: - statement number. */
    @Column(name = "STATEMENT_NUMBER", nullable = false)
    private Long statementNumber;

    /** Tag :28C: - page/sequence number. */
    @Column(name = "PAGE_SEQUENCE", nullable = false)
    private Integer pageSequence = 1;

    /** Input Sequence Number from the basic header {1:}. */
    @Column(name = "ISN", length = 6)
    private String isn;

    @Column(name = "PERIOD_FROM", nullable = false)
    private LocalDate periodFrom;

    @Column(name = "PERIOD_TO", nullable = false)
    private LocalDate periodTo;

    @Column(name = "CURRENCY", nullable = false, length = 3)
    private String currency;

    @Column(name = "OPENING_BALANCE", precision = 19, scale = 2)
    private BigDecimal openingBalance;

    @Column(name = "OPENING_MARK", length = 1)
    private String openingMark;

    @Column(name = "CLOSING_BALANCE", precision = 19, scale = 2)
    private BigDecimal closingBalance;

    @Column(name = "CLOSING_MARK", length = 1)
    private String closingMark;

    @Column(name = "TRANSACTION_COUNT", nullable = false)
    private Integer transactionCount = 0;

    @Column(name = "FILE_NAME", length = 255)
    private String fileName;

    @Column(name = "FILE_PATH", length = 500)
    private String filePath;

    @Column(name = "CHECKSUM", length = 64)
    private String checksum;

    /** The rendered SWIFT text, kept for re-download and re-send. */
    @Lob
    @Column(name = "CONTENT")
    private String content;

    @Enumerated(EnumType.STRING)
    @Column(name = "STATUS", nullable = false, length = 20)
    private StatementStatus status = StatementStatus.GENERATED;

    @Enumerated(EnumType.STRING)
    @Column(name = "DELIVERY_STATUS", nullable = false, length = 20)
    private DeliveryStatus deliveryStatus = DeliveryStatus.PENDING;

    @Column(name = "ERROR_MESSAGE", length = 2000)
    private String errorMessage;

    @Column(name = "GENERATED_BY", length = 120)
    private String generatedBy;

    @Column(name = "GENERATED_AT")
    private LocalDateTime generatedAt;

    @OneToMany(mappedBy = "statement", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("lineNo ASC")
    private List<StatementTransaction> transactions = new ArrayList<>();

    public void addTransaction(StatementTransaction txn) {
        transactions.add(txn);
        txn.setStatement(this);
    }
}
