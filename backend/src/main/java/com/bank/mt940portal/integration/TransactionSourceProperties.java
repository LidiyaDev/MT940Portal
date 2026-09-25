package com.bank.mt940portal.integration;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** Core banking transaction endpoint configuration. */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "mt940.transaction-source")
public class TransactionSourceProperties {

    private String baseUrl = "http://192.168.12.47:5468";
    private String path = "/phiBela/getAmboTransaction";
    private int connectTimeoutMs = 5000;
    private int readTimeoutMs = 30000;

    /** Date pattern sent to the core banking endpoint, e.g. 01-Sep-26. */
    private String datePattern = "dd-MMM-yy";

    @Getter
    @Setter
    public static class Auth {
        private boolean enabled = false;
        private String headerName = "Authorization";
        private String token = "";
    }

    private Auth auth = new Auth();
}
