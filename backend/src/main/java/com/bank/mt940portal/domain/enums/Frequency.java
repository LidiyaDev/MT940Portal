package com.bank.mt940portal.domain.enums;

import java.util.Arrays;

public enum Frequency {
    DAILY, WEEKLY, MONTHLY;

    public static Frequency from(String value) {
        return Arrays.stream(values())
                .filter(f -> f.name().equalsIgnoreCase(value))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unsupported frequency: " + value));
    }
}
