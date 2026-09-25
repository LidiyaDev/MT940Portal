package com.bank.mt940portal.email;

public interface EmailProvider {

    /** Identifier stored on MT_DELIVERY_LOG.PROVIDER. */
    String name();

    EmailDeliveryResult send(EmailMessage message);
}
