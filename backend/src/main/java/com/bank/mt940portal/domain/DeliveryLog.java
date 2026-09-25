package com.bank.mt940portal.domain;

import com.bank.mt940portal.domain.enums.DeliveryStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/** One attempt to email a statement. */
@Entity
@Table(name = "MT_DELIVERY_LOG")
@Getter
@Setter
public class DeliveryLog {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "mt940_seq_gen")
    @SequenceGenerator(name = "mt940_seq_gen", sequenceName = "MT940_SEQ", allocationSize = 50)
    @Column(name = "ID", nullable = false, updatable = false)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "STATEMENT_ID",
            foreignKey = @ForeignKey(name = "FK_MT_DLV_STMT"))
    private Statement statement;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "SCHEDULE_ID",
            foreignKey = @ForeignKey(name = "FK_MT_DLV_SCHED"))
    private DeliverySchedule schedule;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "CLIENT_ID",
            foreignKey = @ForeignKey(name = "FK_MT_DLV_CLIENT"))
    private Client client;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ACCOUNT_ID",
            foreignKey = @ForeignKey(name = "FK_MT_DLV_ACCOUNT"))
    private Account account;

    @Column(name = "RECIPIENT", length = 1000)
    private String recipient;

    @Column(name = "SUBJECT", length = 500)
    private String subject;

    @Enumerated(EnumType.STRING)
    @Column(name = "STATUS", nullable = false, length = 20)
    private DeliveryStatus status;

    @Column(name = "ATTEMPT", nullable = false)
    private Integer attempt = 1;

    @Column(name = "PROVIDER", length = 32)
    private String provider;

    @Lob
    @Column(name = "PROVIDER_RESPONSE")
    private String providerResponse;

    @Column(name = "ERROR_MESSAGE", length = 2000)
    private String errorMessage;

    @Column(name = "CORRELATION_ID", length = 64)
    private String correlationId;

    @Column(name = "SENT_BY", length = 120)
    private String sentBy;

    @Column(name = "SENT_AT")
    private LocalDateTime sentAt;
}
