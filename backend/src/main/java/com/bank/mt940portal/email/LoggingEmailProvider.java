package com.bank.mt940portal.email;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

/**
 * Placeholder provider: records what would have been sent without contacting an
 * external service. Used until the bank's notification endpoint is configured.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "mt940.email.provider", havingValue = "mock", matchIfMissing = true)
public class LoggingEmailProvider implements EmailProvider {

    @Override
    public String name() {
        return "MOCK";
    }

    @Override
    public EmailDeliveryResult send(EmailMessage message) {
        int bytes = message.getAttachment() == null ? 0 : message.getAttachment().length;
        String body = message.getAttachment() == null
                ? "" : new String(message.getAttachment(), StandardCharsets.UTF_8);

        log.info("[MOCK EMAIL] to={} subject={} attachment={} ({} bytes)",
                message.getTo(), message.getSubject(), message.getAttachmentFileName(), bytes);
        log.debug("[MOCK EMAIL] body={}{}", message.getBody(),
                body.isEmpty() ? "" : "\n" + body);

        return EmailDeliveryResult.ok(
                "{\"delivered\":true,\"provider\":\"mock\",\"to\":\"" + String.join(",", message.getTo())
                        + "\",\"attachmentBytes\":" + bytes + "}");
    }
}
