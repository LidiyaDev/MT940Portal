package com.bank.mt940portal.domain.enums;

/**
 * Determines which date range a scheduled run covers.
 */
public enum PeriodStrategy {
    /** The immediately preceding full period: yesterday / last week / last month. */
    PREVIOUS_PERIOD,
    /** Rolling window ending today, e.g. last 7 or last 30 days. */
    ROLLING_7,
    ROLLING_30
}
