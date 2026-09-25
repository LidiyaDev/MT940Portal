package com.bank.mt940portal.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;

@Data
public class GenerateStatementRequest {

    @NotNull
    private Long accountId;

    @NotNull
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate periodFrom;

    @NotNull
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate periodTo;

    /** Persist the statement without emailing it. */
    private boolean persist = true;

    /** Preview only - do not store and do not send. */
    private boolean previewOnly = false;
}
