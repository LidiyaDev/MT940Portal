package com.bank.mt940portal.domain.enums;

/**
 * Controls the optional funds code that follows the D/C mark in tag :61:.
 * The reference statement supplied by the bank omits it entirely.
 */
public enum FundsCodeStrategy {
    /** No funds code emitted: ...0923D2340,00NTRF... */
    NONE,
    /** Emit the third character of the currency code, e.g. ETB -> B. */
    CURRENCY_THIRD_CHAR
}
