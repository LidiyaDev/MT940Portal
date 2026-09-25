package com.bank.mt940portal.service;

import com.bank.mt940portal.domain.DeliverySchedule;
import com.bank.mt940portal.domain.enums.AccountStatus;
import com.bank.mt940portal.domain.enums.ClientStatus;
import com.bank.mt940portal.domain.enums.DeliveryStatus;
import com.bank.mt940portal.dto.DashboardDto;
import com.bank.mt940portal.dto.UpcomingDeliveryDto;
import com.bank.mt940portal.repository.AccountRepository;
import com.bank.mt940portal.repository.ClientRepository;
import com.bank.mt940portal.repository.DeliveryLogRepository;
import com.bank.mt940portal.repository.DeliveryScheduleRepository;
import com.bank.mt940portal.repository.StatementRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** Numbers shown on the landing page. */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DashboardService {

    private final ClientRepository clientRepository;
    private final AccountRepository accountRepository;
    private final DeliveryScheduleRepository scheduleRepository;
    private final StatementRepository statementRepository;
    private final DeliveryLogRepository deliveryLogRepository;
    private final ApprovalService approvalService;
    private final AuditQueryService auditQueryService;

    public DashboardDto build() {
        LocalDateTime startOfToday = LocalDate.now().atStartOfDay();

        List<UpcomingDeliveryDto> upcoming = scheduleRepository
                .findAllByEnabledTrueOrderByNextRunAtAsc()
                .stream()
                .filter(schedule -> schedule.getNextRunAt() != null)
                .limit(8)
                .map(this::upcoming)
                .toList();

        return DashboardDto.builder()
                .activeClients(clientRepository.countByStatus(ClientStatus.ACTIVE))
                .activeAccounts(accountRepository.countByStatus(AccountStatus.ACTIVE))
                .pendingApprovals(approvalService.countPending())
                .statementsToday(statementRepository.countGeneratedSince(startOfToday))
                .deliveredToday(deliveryLogRepository.countSentSince(startOfToday))
                .failedToday(deliveryLogRepository.countFailedSince(startOfToday))
                .pendingDeliveries(statementRepository
                        .findAllByDeliveryStatusOrderByGeneratedAtAsc(DeliveryStatus.PENDING).size())
                .upcoming(upcoming)
                .recentActivity(auditQueryService.recent(10))
                .build();
    }

    private UpcomingDeliveryDto upcoming(DeliverySchedule schedule) {
        return UpcomingDeliveryDto.builder()
                .scheduleId(schedule.getId())
                .clientCode(schedule.getClient().getClientCode())
                .clientName(schedule.getClient().getClientName())
                .accountNumber(schedule.getAccount() == null
                        ? "All accounts" : schedule.getAccount().getAccountNumber())
                .frequency(schedule.describe())
                .nextRunAt(schedule.getNextRunAt())
                .build();
    }
}
