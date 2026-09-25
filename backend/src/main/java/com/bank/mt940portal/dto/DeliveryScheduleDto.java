package com.bank.mt940portal.dto;

import com.bank.mt940portal.domain.enums.Frequency;
import com.bank.mt940portal.domain.enums.PeriodStrategy;
import jakarta.validation.constraints.NotNull;
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
public class DeliveryScheduleDto {

    private Long id;

    @NotNull
    private Long clientId;
    private String clientCode;

    /** Null means "all active accounts of this client". */
    private Long accountId;
    private String accountNumber;

    @Size(max = 120)
    private String name;

    @NotNull
    @lombok.Builder.Default
    private Frequency frequency = Frequency.DAILY;

    /** 1 = Monday .. 7 = Sunday, required when frequency = WEEKLY. */
    private Integer dayOfWeek;

    /** 1..31, required when frequency = MONTHLY. */
    private Integer dayOfMonth;

    @NotNull
    @Pattern(regexp = "([01]\\d|2[0-3]):[0-5]\\d", message = "Send time must be HH:mm")
    @lombok.Builder.Default
    private String sendTime = "07:00";

    @lombok.Builder.Default
    private String timezone = "Africa/Addis_Ababa";

    @lombok.Builder.Default
    private PeriodStrategy periodStrategy = PeriodStrategy.PREVIOUS_PERIOD;

    @lombok.Builder.Default
    private Boolean enabled = true;
    @lombok.Builder.Default
    private Boolean includeZeroTransactionStatements = false;

    @Size(max = 1000)
    private String remarks;

    // --- read only ----------------------------------------------------------
    private LocalDateTime lastSentAt;
    private LocalDateTime nextRunAt;
    private String description;
    private LocalDateTime createdAt;
    private String createdBy;
    private LocalDateTime updatedAt;
    private String updatedBy;
    private PendingChangeDto pendingChange;
}
