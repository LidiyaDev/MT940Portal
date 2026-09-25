package com.bank.mt940portal.dto;

import com.bank.mt940portal.domain.enums.BalanceTag;
import com.bank.mt940portal.domain.enums.ClientStatus;
import com.bank.mt940portal.domain.enums.FundsCodeStrategy;
import jakarta.validation.constraints.Email;
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
public class ClientDto {

    private Long id;

    @NotBlank
    @Size(max = 32)
    private String clientCode;

    @NotBlank
    @Size(max = 200)
    private String clientName;

    @Email
    @Size(max = 320)
    private String primaryEmail;

    @Size(max = 120)
    private String contactPerson;

    @Size(max = 40)
    private String phone;

    @Size(max = 500)
    private String address;

    @Pattern(regexp = "[A-Z]{3}", message = "Currency must be a 3 letter ISO code")
    @lombok.Builder.Default
    private String defaultCurrency = "ETB";

    @lombok.Builder.Default
    private String timezone = "Africa/Addis_Ababa";

    @lombok.Builder.Default
    private ClientStatus status = ClientStatus.ACTIVE;

    // --- per client SWIFT overrides (null = use the global default) ---------
    @Size(max = 12)
    private String senderLtAddress;

    @Size(max = 12)
    private String receiverLtAddress;

    private FundsCodeStrategy fundsCodeStrategy;
    private BalanceTag openingBalanceTag;
    private BalanceTag closingBalanceTag;
    private Boolean emit13d;
    private Boolean emit64;
    private Boolean emit65;
    private Boolean emit90d;
    private Boolean blankLineBetweenTags;
    private Long statementNumberSeed;

    @Size(max = 1000)
    private String remarks;

    // --- read only ----------------------------------------------------------
    private Integer accountCount;
    private LocalDateTime createdAt;
    private String createdBy;
    private LocalDateTime updatedAt;
    private String updatedBy;
    private PendingChangeDto pendingChange;
}
