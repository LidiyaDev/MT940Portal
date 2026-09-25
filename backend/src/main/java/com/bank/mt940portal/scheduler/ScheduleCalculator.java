package com.bank.mt940portal.scheduler;

import com.bank.mt940portal.domain.DeliverySchedule;
import com.bank.mt940portal.domain.enums.Frequency;
import com.bank.mt940portal.domain.enums.PeriodStrategy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;

/**
 * Works out when a schedule fires next and which statement period a run covers.
 * <p>
 * All stored instants are wall-clock values in the scheduler zone; each schedule
 * additionally carries its own timezone so that "08:00" means 08:00 for the
 * client, not for the server.
 */
@Slf4j
@Component
public class ScheduleCalculator {

    private final ZoneId schedulerZone;

    public ScheduleCalculator(@Value("${mt940.scheduler.zone:Africa/Addis_Ababa}") String zone) {
        this.schedulerZone = ZoneId.of(zone);
    }

    public ZoneId zone() {
        return schedulerZone;
    }

    public LocalDateTime now() {
        return LocalDateTime.now(schedulerZone);
    }

    /** Next firing instant, strictly after {@code after}. */
    public LocalDateTime nextRun(DeliverySchedule schedule, LocalDateTime after) {
        ZoneId zone = zoneOf(schedule);
        LocalTime time = schedule.getSendTimeAsLocalTime();
        LocalDateTime reference = after.atZone(zone).toLocalDateTime();

        return switch (schedule.getFrequency()) {
            case DAILY -> nextDaily(reference, time, zone);
            case WEEKLY -> nextWeekly(reference, time, schedule.getDayOfWeek(), zone);
            case MONTHLY -> nextMonthly(reference, time, schedule.getDayOfMonth(), zone);
        };
    }

    private LocalDateTime nextDaily(LocalDateTime reference, LocalTime time, ZoneId zone) {
        LocalDateTime candidate = reference.toLocalDate().atTime(time);
        if (!candidate.isAfter(reference)) {
            candidate = candidate.plusDays(1);
        }
        return toSchedulerZone(candidate, zone);
    }

    private LocalDateTime nextWeekly(LocalDateTime reference, LocalTime time,
                                     Integer dayOfWeek, ZoneId zone) {
        DayOfWeek target = dayOfWeek == null || dayOfWeek < 1 || dayOfWeek > 7
                ? DayOfWeek.MONDAY : DayOfWeek.of(dayOfWeek);
        LocalDate candidateDate = reference.toLocalDate().with(TemporalAdjusters.nextOrSame(target));
        LocalDateTime candidate = candidateDate.atTime(time);
        if (!candidate.isAfter(reference)) {
            candidate = candidate.plusWeeks(1);
        }
        return toSchedulerZone(candidate, zone);
    }

    private LocalDateTime nextMonthly(LocalDateTime reference, LocalTime time,
                                      Integer dayOfMonth, ZoneId zone) {
        int day = dayOfMonth == null || dayOfMonth < 1 ? 1 : Math.min(dayOfMonth, 31);
        LocalDate candidateDate = clamp(reference.toLocalDate().withDayOfMonth(1), day);
        LocalDateTime candidate = candidateDate.atTime(time);
        if (!candidate.isAfter(reference)) {
            candidateDate = clamp(reference.toLocalDate().plusMonths(1).withDayOfMonth(1), day);
            candidate = candidateDate.atTime(time);
        }
        return toSchedulerZone(candidate, zone);
    }

    /** Statement period covered by a run that fires on {@code scheduledFor}. */
    public StatementPeriod period(DeliverySchedule schedule, LocalDateTime scheduledFor) {
        ZoneId zone = zoneOf(schedule);
        LocalDate runDate = scheduledFor.atZone(zone).toLocalDate();
        LocalDate yesterday = runDate.minusDays(1);

        return switch (schedule.getPeriodStrategy()) {
            case ROLLING_7 -> new StatementPeriod(yesterday.minusDays(6), yesterday);
            case ROLLING_30 -> new StatementPeriod(yesterday.minusDays(29), yesterday);
            case PREVIOUS_PERIOD -> switch (schedule.getFrequency()) {
                case DAILY -> new StatementPeriod(yesterday, yesterday);
                case WEEKLY -> new StatementPeriod(yesterday.minusDays(6), yesterday);
                case MONTHLY -> {
                    LocalDate previousMonth = runDate.minusMonths(1);
                    yield new StatementPeriod(previousMonth.withDayOfMonth(1),
                            previousMonth.withDayOfMonth(previousMonth.lengthOfMonth()));
                }
            };
        };
    }

    private LocalDate clamp(LocalDate monthStart, int day) {
        int max = monthStart.lengthOfMonth();
        return monthStart.withDayOfMonth(Math.min(day, max));
    }

    private LocalDateTime toSchedulerZone(LocalDateTime localInScheduleZone, ZoneId zone) {
        return localInScheduleZone.atZone(zone)
                .withZoneSameInstant(schedulerZone)
                .toLocalDateTime();
    }

    private static ZoneId zoneOf(DeliverySchedule schedule) {
        String tz = schedule.getTimezone();
        if (tz == null || tz.isBlank()) {
            return ZoneId.systemDefault();
        }
        try {
            return ZoneId.of(tz);
        } catch (RuntimeException ex) {
            log.warn("Unknown timezone '{}' on schedule {}, falling back to system default",
                    tz, schedule.getId());
            return ZoneId.systemDefault();
        }
    }

    public record StatementPeriod(LocalDate from, LocalDate to) {
    }
}
