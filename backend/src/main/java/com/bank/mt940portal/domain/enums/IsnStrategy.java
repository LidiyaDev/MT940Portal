package com.bank.mt940portal.domain.enums;

/**
 * How the Input Sequence Number in the basic header ({1:...}) is produced.
 */
public enum IsnStrategy {
    /** Zero-padded 6 digit copy of the :28C: statement number (020719 <-> 20719). */
    MIRROR_STATEMENT_NUMBER,
    /** Always use the configured static value. */
    STATIC,
    /** Independent per-account running sequence. */
    ACCOUNT_SEQUENCE
}
