package com.bank.mt940portal.domain;

import com.bank.mt940portal.domain.enums.Frequency;
import com.bank.mt940portal.domain.enums.PeriodStrategy;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * When and how a generated MT940 is emailed.
 * <p>
 * {@code accountId} may be null, in which case the schedule covers every active
 * account of the client.
 */
@Entity
@Table(name = "MT_DELIVERY_SCHEDULE")
@Getter
@Setter
public class DeliverySchedule extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "CLIENT_ID", nullable = false,
            foreignKey = @ForeignKey(name = "FK_MT_SCHED_CLIENT"))
    private Client client;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ACCOUNT_ID",
            foreignKey = @ForeignKey(name = "FK_MT_SCHED_ACCOUNT"))
    private Account account;

    @Column(name = "NAME", length = 120)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "FREQUENCY", nullable = false, length = 16)
    private Frequency frequency = Frequency.DAILY;

    /** 1 = Monday ... 7 = Sunday. Only used when frequency = WEEKLY. */
    @Column(name = "DAY_OF_WEEK")
    private Integer dayOfWeek;

    /** 1..31 (values above month length clamp to the last day). MONTHLY only. */
    @Column(name = "DAY_OF_MONTH")
    private Integer dayOfMonth;

    /** Local send time in HH:mm. */
    @Column(name = "SEND_TIME", nullable = false, length = 5)
    private String sendTime = "07:00";

    @Column(name = "TIMEZONE", nullable = false, length = 64)
    private String timezone = "Africa/Addis_Ababa";

    @Enumerated(EnumType.STRING)
    @Column(name = "PERIOD_STRATEGY", nullable = false, length = 24)
    private PeriodStrategy periodStrategy = PeriodStrategy.PREVIOUS_PERIOD;

    @Column(name = "ENABLED", nullable = false)
    private boolean enabled = true;

    /** Skip delivery when the period produced no transactions. */
    @Column(name = "INCLUDE_ZERO_TXN", nullable = false)
    private boolean includeZeroTransactionStatements = false;

    @Column(name = "LAST_SENT_AT")
    private LocalDateTime lastSentAt;

    @Column(name = "NEXT_RUN_AT")
    private LocalDateTime nextRunAt;

    @Column(name = "REMARKS", length = 1000)
    private String remarks;

    @Transient
    public LocalTime getSendTimeAsLocalTime() {
        return LocalTime.parse(sendTime);
    }

    @Transient
    public String describe() {
        StringBuilder sb = new StringBuilder(frequency.name().toLowerCase());
        if (frequency == Frequency.WEEKLY && dayOfWeek != null) {
            sb.append(" on ").append(dayName(dayOfWeek));
        }
        if (frequency == Frequency.MONTHLY && dayOfMonth != null) {
            sb.append(" on day ").append(dayOfMonth);
        }
        return sb.append(" at ").append(sendTime).toString();
    }

    private static String dayName(int day) {
        String[] names = {"", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday"};
        return day >= 1 && day <= 7 ? names[day] : String.valueOf(day);
    }
}
