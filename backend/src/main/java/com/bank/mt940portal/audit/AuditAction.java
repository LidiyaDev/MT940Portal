package com.bank.mt940portal.audit;

/** Canonical action names written to MT_AUDIT_LOG. */
public final class AuditAction {

    private AuditAction() {
    }

    // Authentication
    public static final String LOGIN = "LOGIN";
    public static final String LOGOUT = "LOGOUT";

    // Client
    public static final String CLIENT_CREATE = "CLIENT_CREATE";
    public static final String CLIENT_UPDATE = "CLIENT_UPDATE";
    public static final String CLIENT_DELETE = "CLIENT_DELETE";
    public static final String CLIENT_STATUS_CHANGE = "CLIENT_STATUS_CHANGE";

    // Account
    public static final String ACCOUNT_CREATE = "ACCOUNT_CREATE";
    public static final String ACCOUNT_UPDATE = "ACCOUNT_UPDATE";
    public static final String ACCOUNT_DELETE = "ACCOUNT_DELETE";
    public static final String ACCOUNT_STATUS_CHANGE = "ACCOUNT_STATUS_CHANGE";

    // Schedule
    public static final String SCHEDULE_CREATE = "SCHEDULE_CREATE";
    public static final String SCHEDULE_UPDATE = "SCHEDULE_UPDATE";
    public static final String SCHEDULE_DELETE = "SCHEDULE_DELETE";
    public static final String SCHEDULE_TOGGLE = "SCHEDULE_TOGGLE";

    // Email configuration
    public static final String EMAIL_CONFIG_CREATE = "EMAIL_CONFIG_CREATE";
    public static final String EMAIL_CONFIG_UPDATE = "EMAIL_CONFIG_UPDATE";
    public static final String EMAIL_CONFIG_DELETE = "EMAIL_CONFIG_DELETE";

    // Statements
    public static final String STATEMENT_GENERATE = "STATEMENT_GENERATE";
    public static final String STATEMENT_PREVIEW = "STATEMENT_PREVIEW";
    public static final String STATEMENT_DOWNLOAD = "STATEMENT_DOWNLOAD";
    public static final String STATEMENT_SEND = "STATEMENT_SEND";

    // Maker / checker
    public static final String APPROVAL_SUBMIT = "APPROVAL_SUBMIT";
    public static final String APPROVAL_APPROVE = "APPROVAL_APPROVE";
    public static final String APPROVAL_REJECT = "APPROVAL_REJECT";
    public static final String APPROVAL_CANCEL = "APPROVAL_CANCEL";

    // Scheduler
    public static final String SCHEDULER_RUN = "SCHEDULER_RUN";
}
