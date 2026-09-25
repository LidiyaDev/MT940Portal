package com.bank.mt940portal.domain;

import com.bank.mt940portal.domain.enums.AccountStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "MT_ACCOUNT",
        uniqueConstraints = @UniqueConstraint(name = "UQ_MT_ACCOUNT_NO", columnNames = "ACCOUNT_NUMBER"))
@Getter
@Setter
public class Account extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "CLIENT_ID", nullable = false,
            foreignKey = @ForeignKey(name = "FK_MT_ACCOUNT_CLIENT"))
    private Client client;

    @Column(name = "ACCOUNT_NUMBER", nullable = false, unique = true, length = 40)
    private String accountNumber;

    @Column(name = "ACCOUNT_NAME", length = 200)
    private String accountName;

    @Column(name = "CURRENCY", nullable = false, length = 3)
    private String currency = "ETB";

    @Column(name = "BRANCH_CODE", length = 20)
    private String branchCode;

    @Column(name = "IBAN", length = 40)
    private String iban;

    @Column(name = "BIC", length = 12)
    private String bic;

    @Enumerated(EnumType.STRING)
    @Column(name = "STATUS", nullable = false, length = 20)
    private AccountStatus status = AccountStatus.ACTIVE;

    @Column(name = "STATEMENT_NO_SEED", nullable = false)
    private Long statementNumberSeed = 0L;

    /** Last value used in tag :28C:. Incremented on every generated statement. */
    @Column(name = "LAST_STATEMENT_NO", nullable = false)
    private Long lastStatementNumber = 0L;

    @Column(name = "REMARKS", length = 1000)
    private String remarks;
}
