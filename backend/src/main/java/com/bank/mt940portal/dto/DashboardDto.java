package com.bank.mt940portal.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class DashboardDto {

    private long activeClients;
    private long activeAccounts;
    private long pendingApprovals;
    private long statementsToday;
    private long deliveredToday;
    private long failedToday;
    private long pendingDeliveries;
    private List<UpcomingDeliveryDto> upcoming;
    private List<AuditLogDto> recentActivity;
}
