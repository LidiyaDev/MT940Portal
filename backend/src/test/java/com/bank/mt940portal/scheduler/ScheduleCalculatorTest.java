package com.bank.mt940portal.scheduler;

import com.bank.mt940portal.domain.DeliverySchedule;
import com.bank.mt940portal.domain.enums.Frequency;
import com.bank.mt940portal.domain.enums.PeriodStrategy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ScheduleCalculatorTest {

    private ScheduleCalculator calculator;

    @BeforeEach
    void setUp() {
        calculator = new ScheduleCalculator("Africa/Addis_Ababa");
    }

    @Test
    @DisplayName("A daily schedule fires tomorrow once today's time has passed")
    void daily() {
        DeliverySchedule schedule = schedule(Frequency.DAILY);
        schedule.setSendTime("07:00");

        LocalDateTime next = calculator.nextRun(schedule, LocalDateTime.of(2026, 9, 25, 9, 0));

        assertEquals(LocalDateTime.of(2026, 9, 26, 7, 0), next);
    }

    @Test
    @DisplayName("A daily schedule fires later today when the time has not yet passed")
    void dailySameDay() {
        DeliverySchedule schedule = schedule(Frequency.DAILY);
        schedule.setSendTime("18:00");

        LocalDateTime next = calculator.nextRun(schedule, LocalDateTime.of(2026, 9, 25, 9, 0));

        assertEquals(LocalDateTime.of(2026, 9, 25, 18, 0), next);
    }

    @Test
    @DisplayName("A weekly schedule lands on the requested weekday")
    void weekly() {
        DeliverySchedule schedule = schedule(Frequency.WEEKLY);
        schedule.setDayOfWeek(2); // Tuesday
        schedule.setSendTime("06:45");

        // Friday 25 Sep 2026
        LocalDateTime next = calculator.nextRun(schedule, LocalDateTime.of(2026, 9, 25, 9, 0));

        assertEquals(LocalDateTime.of(2026, 9, 29, 6, 45), next);
        assertEquals(java.time.DayOfWeek.TUESDAY, next.getDayOfWeek());
    }

    @Test
    @DisplayName("A monthly schedule clamps to the last day of short months")
    void monthly() {
        DeliverySchedule schedule = schedule(Frequency.MONTHLY);
        schedule.setDayOfMonth(31);
        schedule.setSendTime("08:30");

        LocalDateTime next = calculator.nextRun(schedule, LocalDateTime.of(2026, 9, 25, 9, 0));

        // 31 October exists, so it should be 31 Oct 2026
        assertEquals(LocalDateTime.of(2026, 10, 31, 8, 30), next);
    }

    @Test
    @DisplayName("A daily run covers yesterday")
    void dailyPeriod() {
        DeliverySchedule schedule = schedule(Frequency.DAILY);
        schedule.setPeriodStrategy(PeriodStrategy.PREVIOUS_PERIOD);

        ScheduleCalculator.StatementPeriod period =
                calculator.period(schedule, LocalDateTime.of(2026, 9, 25, 7, 0));

        assertEquals(LocalDate.of(2026, 9, 24), period.from());
        assertEquals(LocalDate.of(2026, 9, 24), period.to());
    }

    @Test
    @DisplayName("A weekly run covers the last seven days")
    void weeklyPeriod() {
        DeliverySchedule schedule = schedule(Frequency.WEEKLY);
        schedule.setDayOfWeek(1);
        schedule.setPeriodStrategy(PeriodStrategy.PREVIOUS_PERIOD);

        ScheduleCalculator.StatementPeriod period =
                calculator.period(schedule, LocalDateTime.of(2026, 9, 28, 6, 45));

        assertEquals(LocalDate.of(2026, 9, 20), period.from());
        assertEquals(LocalDate.of(2026, 9, 27), period.to());
    }

    @Test
    @DisplayName("A monthly run covers the previous calendar month")
    void monthlyPeriod() {
        DeliverySchedule schedule = schedule(Frequency.MONTHLY);
        schedule.setDayOfMonth(1);
        schedule.setPeriodStrategy(PeriodStrategy.PREVIOUS_PERIOD);

        ScheduleCalculator.StatementPeriod period =
                calculator.period(schedule, LocalDateTime.of(2026, 10, 1, 8, 30));

        assertEquals(LocalDate.of(2026, 9, 1), period.from());
        assertEquals(LocalDate.of(2026, 9, 30), period.to());
    }

    @Test
    @DisplayName("Rolling windows end yesterday")
    void rollingPeriod() {
        DeliverySchedule schedule = schedule(Frequency.DAILY);
        schedule.setPeriodStrategy(PeriodStrategy.ROLLING_7);

        ScheduleCalculator.StatementPeriod period =
                calculator.period(schedule, LocalDateTime.of(2026, 9, 25, 7, 0));

        assertEquals(LocalDate.of(2026, 9, 18), period.from());
        assertEquals(LocalDate.of(2026, 9, 24), period.to());
    }

    private DeliverySchedule schedule(Frequency frequency) {
        DeliverySchedule schedule = new DeliverySchedule();
        schedule.setId(1L);
        schedule.setFrequency(frequency);
        schedule.setTimezone("Africa/Addis_Ababa");
        schedule.setPeriodStrategy(PeriodStrategy.PREVIOUS_PERIOD);
        schedule.setSendTime("07:00");
        return schedule;
    }
}
