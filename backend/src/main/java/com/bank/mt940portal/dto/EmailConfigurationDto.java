package com.bank.mt940portal.dto;

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
public class EmailConfigurationDto {

    private Long id;
    private Long clientId;
    private String clientCode;

    @Size(max = 120)
    private String name;

    /** Comma separated recipient lists. */
    @Size(max = 1000)
    private String toAddresses;
    @Size(max = 1000)
    private String ccAddresses;
    @Size(max = 1000)
    private String bccAddresses;

    @Size(max = 500)
    private String subjectTemplate;

    private String bodyTemplate;

    @lombok.Builder.Default
    private Boolean enabled = true;
    @lombok.Builder.Default
    private Boolean isDefault = false;

    private LocalDateTime createdAt;
    private String createdBy;
    private LocalDateTime updatedAt;
    private String updatedBy;
    private PendingChangeDto pendingChange;
}
