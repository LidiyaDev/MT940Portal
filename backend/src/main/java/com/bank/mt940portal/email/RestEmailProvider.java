package com.bank.mt940portal.email;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientException;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Posts the statement to the bank's notification endpoint.
 * <p>
 * The request body is produced from {@code mt940.email.rest.payload-template} so
 * that a change of endpoint contract is handled in configuration.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "mt940.email.provider", havingValue = "rest")
public class RestEmailProvider implements EmailProvider {

    private final WebClient webClient;
    private final EmailProperties properties;

    public RestEmailProvider(WebClient.Builder builder, EmailProperties properties) {
        this.properties = properties;
        this.webClient = builder.build();
    }

    @Override
    public String name() {
        return "REST";
    }

    @Override
    public EmailDeliveryResult send(EmailMessage message) {
        if (!StringUtils.hasText(properties.getRest().getUrl())) {
            return EmailDeliveryResult.failed(
                    "Email provider is set to 'rest' but mt940.email.rest.url is not configured");
        }

        String payload = render(message);
        try {
            var request = webClient.post()
                    .uri(properties.getRest().getUrl())
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON);

            if (properties.getRest().getAuth().isEnabled()
                    && StringUtils.hasText(properties.getRest().getAuth().getToken())) {
                request = request.header(properties.getRest().getAuth().getHeaderName(),
                        properties.getRest().getAuth().getToken());
            }

            String response = request
                    .bodyValue(payload)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block(Duration.ofMillis(properties.getRest().getTimeoutMs()));

            log.info("Delivered statement to {} via REST endpoint", message.getTo());
            return EmailDeliveryResult.ok(response);
        } catch (WebClientException ex) {
            String detail = ex.getMessage();
            log.error("Failed to deliver statement to {}: {}", message.getTo(), detail);
            return EmailDeliveryResult.failed(detail);
        }
    }

    /** Replaces the {{placeholder}} tokens of the configured template. */
    String render(EmailMessage message) {
        Map<String, String> tokens = new HashMap<>();

        tokens.put("from", nvl(message.getFromAddress()));
        tokens.put("fromName", nvl(message.getFromName()));
        tokens.put("to", join(message.getTo()));
        tokens.put("cc", join(message.getCc()));
        tokens.put("bcc", join(message.getBcc()));
        tokens.put("subject", nvl(message.getSubject()));
        tokens.put("body", message.getBody() == null ? "" : message.getBody());
        tokens.put("fileName", nvl(message.getAttachmentFileName()));
        tokens.put("contentType", nvl(message.getAttachmentContentType()));
        tokens.put("fileBase64", message.getAttachment() == null
                ? "" : Base64.getEncoder().encodeToString(message.getAttachment()));
        tokens.put("fileContent", message.getAttachment() == null
                ? "" : jsonEscape(new String(message.getAttachment(), StandardCharsets.UTF_8)));
        tokens.put("correlationId", nvl(message.getCorrelationId()));

        if (message.getVariables() != null) {
            message.getVariables().forEach((key, value) -> tokens.put(key, nvl(value)));
        }

        String template = properties.getRest().getPayloadTemplate();
        String rendered = template;
        for (Map.Entry<String, String> entry : tokens.entrySet()) {
            rendered = rendered.replace("{{" + entry.getKey() + "}}", jsonEscape(entry.getValue()));
        }
        return rendered;
    }

    private static String join(List<String> values) {
        return values == null ? "" : String.join(",", values);
    }

    private static String nvl(String value) {
        return value == null ? "" : value;
    }

    /** Escapes a value so that it stays valid inside a JSON string literal. */
    static String jsonEscape(String value) {
        if (value == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder(value.length() + 16);
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '\\' -> sb.append("\\\\");
                case '"' -> sb.append("\\\"");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                case '\b' -> sb.append("\\b");
                case '\f' -> sb.append("\\f");
                default -> {
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        return sb.toString();
    }
}
