package com.bank.mt940portal.integration;

import com.bank.mt940portal.integration.dto.TransactionQuery;
import com.bank.mt940portal.integration.dto.TransactionRecord;
import com.bank.mt940portal.integration.dto.TransactionResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

/**
 * Client for the core banking transaction endpoint.
 *
 * <pre>
 * POST http://192.168.12.47:5468/phiBela/getAmboTransaction
 * { "accountNumber": "1144355935012",
 *   "startDate": "01-Sep-26",
 *   "endDate":   "24-Sep-26" }
 * </pre>
 */
@Slf4j
@Component
public class TransactionServiceClient {

    private final WebClient webClient;
    private final TransactionSourceProperties properties;

    public TransactionServiceClient(WebClient.Builder builder,
                                    TransactionSourceProperties properties) {
        this.properties = properties;
        this.webClient = builder
                .baseUrl(properties.getBaseUrl())
                .build();
    }

    /**
     * @return the transactions for the account over the inclusive date range
     */
    public List<TransactionRecord> fetchTransactions(String accountNumber,
                                                     LocalDate from,
                                                     LocalDate to) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern(
                properties.getDatePattern(), Locale.ENGLISH);

        TransactionQuery query = TransactionQuery.builder()
                .accountNumber(accountNumber)
                .startDate(from.format(formatter))
                .endDate(to.format(formatter))
                .build();

        log.debug("Fetching transactions for account {} from {} to {}", accountNumber, from, to);

        var request = webClient.post()
                .uri(properties.getPath())
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON);

        if (properties.getAuth().isEnabled()
                && !properties.getAuth().getToken().isBlank()) {
            request = request.header(properties.getAuth().getHeaderName(),
                    properties.getAuth().getToken());
        }

        TransactionResponse response;
        try {
            response = request
                    .bodyValue(query)
                    .retrieve()
                    .bodyToMono(TransactionResponse.class)
                    .block(Duration.ofMillis(properties.getReadTimeoutMs()));
        } catch (WebClientRequestException ex) {
            throw new TransactionSourceException(
                    "Unable to reach the transaction service at " + properties.getBaseUrl(), ex);
        } catch (WebClientResponseException ex) {
            throw new TransactionSourceException(
                    "Transaction service returned HTTP " + ex.getStatusCode().value()
                            + ": " + ex.getResponseBodyAsString(), ex);
        }

        if (response == null || response.getData() == null) {
            throw new TransactionSourceException(
                    "Transaction service returned an empty response for account " + accountNumber);
        }
        if (response.getStatusCode() != null && response.getStatusCode() != 200) {
            throw new TransactionSourceException(
                    "Transaction service returned status " + response.getStatusCode()
                            + " (" + response.getMessage() + ") for account " + accountNumber);
        }

        List<TransactionRecord> records = response.getData();
        log.debug("Received {} transactions for account {}", records.size(), accountNumber);
        return records;
    }

    /** Raised when the core banking endpoint cannot be reached or misbehaves. */
    public static class TransactionSourceException extends RuntimeException {
        public TransactionSourceException(String message, Throwable cause) {
            super(message, cause);
        }

        public TransactionSourceException(String message) {
            super(message);
        }
    }
}
