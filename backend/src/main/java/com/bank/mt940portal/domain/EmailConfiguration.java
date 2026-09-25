package com.bank.mt940portal.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/**
 * Recipient list and message templates for a client.
 * A row with a null {@code client} is the global default used for clients
 * without their own configuration.
 */
@Entity
@Table(name = "MT_EMAIL_CONFIG")
@Getter
@Setter
public class EmailConfiguration extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "CLIENT_ID",
            foreignKey = @ForeignKey(name = "FK_MT_EMAIL_CLIENT"))
    private Client client;

    @Column(name = "NAME", length = 120)
    private String name;

    @Column(name = "TO_ADDRESSES", length = 1000)
    private String toAddresses;

    @Column(name = "CC_ADDRESSES", length = 1000)
    private String ccAddresses;

    @Column(name = "BCC_ADDRESSES", length = 1000)
    private String bccAddresses;

    @Column(name = "SUBJECT_TEMPLATE", length = 500)
    private String subjectTemplate;

    @Lob
    @Column(name = "BODY_TEMPLATE")
    private String bodyTemplate;

    @Column(name = "ENABLED", nullable = false)
    private boolean enabled = true;

    @Column(name = "IS_DEFAULT", nullable = false)
    private boolean isDefault = false;
}
