package com.bank.mt940portal.email;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Notification delivery configuration.
 * <p>
 * The real endpoint is supplied entirely through configuration - switching from
 * the bundled mock to the bank's notification service is a property change, not
 * a code change.
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "mt940.email")
public class EmailProperties {

    /** "mock" logs the message, "rest" posts it to the configured endpoint. */
    private String provider = "mock";

    private String fromAddress = "mt940-noreply@bank.local";
    private String fromName = "MT940 Statement Portal";

    private Rest rest = new Rest();
    private Retry retry = new Retry();

    @Getter
    @Setter
    public static class Rest {
        /** Full URL of the notification endpoint. */
        private String url = "";
        private int timeoutMs = 20000;
        private Auth auth = new Auth();

        /**
         * Request body template. Placeholders resolved per message:
         * {{from}} {{fromName}} {{to}} {{cc}} {{bcc}} {{subject}} {{body}}
         * {{fileName}} {{fileBase64}} {{contentType}}
         * {{clientCode}} {{clientName}} {{accountNumber}} {{periodFrom}} {{periodTo}}
         * {{statementReference}} {{correlationId}}
         */
        private String payloadTemplate = "{\"to\":\"{{to}}\",\"cc\":\"{{cc}}\",\"bcc\":\"{{bcc}}\","
                + "\"subject\":\"{{subject}}\",\"body\":\"{{body}}\",\"attachments\":"
                + "[{\"fileName\":\"{{fileName}}\",\"contentType\":\"{{contentType}}\","
                + "\"contentBase64\":\"{{fileBase64}}\"}]}";

        @Getter
        @Setter
        public static class Auth {
            private boolean enabled = false;
            private String headerName = "Authorization";
            private String token = "";
        }
    }

    @Getter
    @Setter
    public static class Retry {
        private int maxAttempts = 3;
        private long backoffMs = 5000L;
    }
}
