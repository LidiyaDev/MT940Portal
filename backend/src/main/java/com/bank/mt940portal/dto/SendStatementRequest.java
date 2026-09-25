package com.bank.mt940portal.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/** Manual "generate and email now" request - routed through maker/checker. */
@Data
public class SendStatementRequest {

    /** Existing statement to re-send. Mutually exclusive with accountId + range. */
    private Long statementId;

    @NotNull
    private Long accountId;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate periodFrom;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate periodTo;

    /** Overrides the recipients configured for the client. */
    private List<String> recipients = new ArrayList<>();

    private String reason;
}
