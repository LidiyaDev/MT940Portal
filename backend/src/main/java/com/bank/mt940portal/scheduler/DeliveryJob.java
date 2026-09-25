package com.bank.mt940portal.scheduler;

import com.bank.mt940portal.audit.AuditAction;
import com.bank.mt940portal.audit.AuditEntry;
import com.bank.mt940portal.audit.AuditService;
import com.bank.mt940portal.domain.DeliverySchedule;
import com.bank.mt940portal.repository.DeliveryScheduleRepository;
import com.bank.mt940portal.service.StatementDeliveryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Dispatches statements whose delivery time has come.
 * <p>
 * Polls MT_DELIVERY_SCHEDULE for enabled rows whose NEXT_RUN_AT has passed,
 * generates a statement per covered account for the period implied by the
 * frequency, emails it and moves NEXT_RUN_AT to the next occurrence.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "mt940.scheduler.enabled", havingValue = "true", matchIfMissing = true)
public class DeliveryJob {

    private final DeliveryScheduleRepository scheduleRepository;
    private final StatementDeliveryService deliveryService;
    private final ScheduleCalculator calculator;
    private final AuditService auditService;

    /** Stops a slow run from overlapping with the next tick. */
    private final AtomicBoolean running = new AtomicBoolean(false);

    private final long misfireThresholdMinutes;

    public DeliveryJob(@Value("${mt940.scheduler.misfire-threshold-minutes:1440}") long misfireThresholdMinutes) {
        this.misfireThresholdMinutes = misfireThresholdMinutes;
    }

    @Scheduled(initialDelayString = "${mt940.scheduler.initial-delay-ms:30000}",
            fixedDelayString = "${mt940.scheduler.poll-interval-ms:60000}")
    public void dispatch() {
        if (!running.compareAndSet(false, true)) {
            log.debug("Previous dispatch run is still in progress, skipping this tick");
            return;
        }
        try {
            LocalDateTime now = calculator.now();
            List<DeliverySchedule> due = scheduleRepository.findDueSchedules(now);
            if (due.isEmpty()) {
                return;
            }
            log.info("Dispatching {} due delivery schedule(s)", due.size());

            for (DeliverySchedule schedule : due) {
                dispatchOne(schedule, now);
            }
        } catch (RuntimeException ex) {
            log.error("Scheduled delivery run failed", ex);
            auditService.record(AuditEntry.builder()
                    .action(AuditAction.SCHEDULER_RUN)
                    .entityType("SCHEDULER")
                    .description("Scheduled delivery run failed: " + ex.getMessage())
                    .outcome(com.bank.mt940portal.domain.enums.AuditOutcome.FAILURE)
                    .actorOverride("scheduler")
                    .build());
        } finally {
            running.set(false);
        }
    }

    private void dispatchOne(DeliverySchedule schedule, LocalDateTime now) {
        LocalDateTime scheduledFor = schedule.getNextRunAt() == null ? now : schedule.getNextRunAt();

        // A schedule that has not run for a very long time (server down, clock
        // change) is rescheduled rather than replayed.
        if (scheduledFor.isBefore(now.minusMinutes(misfireThresholdMinutes))) {
            log.warn("Schedule {} missed its window (was due {}), rescheduling",
                    schedule.getId(), scheduledFor);
            deliveryService.reschedule(schedule, now);
            return;
        }

        try {
            int delivered = deliveryService.runSchedule(schedule, scheduledFor);
            auditService.record(AuditEntry.builder()
                    .action(AuditAction.SCHEDULER_RUN)
                    .entityType("DELIVERY_SCHEDULE")
                    .entityId(String.valueOf(schedule.getId()))
                    .entityLabel(schedule.getClient().getClientCode())
                    .description("Scheduled run delivered " + delivered
                            + " statement(s) for " + schedule.describe())
                    .actorOverride("scheduler")
                    .build());
        } catch (RuntimeException ex) {
            log.error("Schedule {} failed", schedule.getId(), ex);
            auditService.record(AuditEntry.builder()
                    .action(AuditAction.SCHEDULER_RUN)
                    .entityType("DELIVERY_SCHEDULE")
                    .entityId(String.valueOf(schedule.getId()))
                    .description("Scheduled run failed: " + ex.getMessage())
                    .outcome(com.bank.mt940portal.domain.enums.AuditOutcome.FAILURE)
                    .actorOverride("scheduler")
                    .build());
        }
    }
}
