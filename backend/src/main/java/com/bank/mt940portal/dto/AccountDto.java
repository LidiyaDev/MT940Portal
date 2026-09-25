package com.bank.mt940portal.dto;

import com.bank.mt940portal.domain.enums.AccountStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AccountDto {

    private Long id;
    private Long clientId;
    private String clientCode;

    @NotBlank
    @Pattern(regexp = "[0-9A-Za-z]{5,40}", message = "Account number must be 5-40 alphanumeric characters")
    private String accountNumber;

    @Size(max = 200)
    private String accountName;

    @Pattern(regexp = "[A-Z]{3}", message = "Currency must be a 3 letter ISO code")
    @lombok.Builder.Default
    private String currency = "ETB";

    @Size(max = 20)
    private String branchCode;

    @Size(max = 40)
    private String iban;

    @Size(max = 12)
    private String bic;

    @lombok.Builder.Default
    private AccountStatus status = AccountStatus.ACTIVE;

    private Long statementNumberSeed;
    private Long lastStatementNumber;

    @Size(max = 1000)
    private String remarks;

    // --- read only ----------------------------------------------------------
    private Integer statementCount;
    private LocalDateTime createdAt;
    private String createdBy;
    private LocalDateTime updatedAt;
    private String updatedBy;
    private PendingChangeDto pendingChange;
}
