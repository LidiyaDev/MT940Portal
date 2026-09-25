-- =============================================================================
-- MT940 Portal - initial Oracle schema
-- Applied by Flyway in the `prod` (Oracle) profile only.
-- The `dev` profile runs on H2 and derives its schema from the JPA mappings.
-- =============================================================================

CREATE SEQUENCE MT940_SEQ START WITH 1 INCREMENT BY 50 CACHE 20 NOCYCLE;

-- -----------------------------------------------------------------------------
-- Clients
-- -----------------------------------------------------------------------------
CREATE TABLE MT_CLIENT (
    ID                    NUMBER(19)     NOT NULL,
    CLIENT_CODE           VARCHAR2(32)   NOT NULL,
    CLIENT_NAME           VARCHAR2(200)  NOT NULL,
    PRIMARY_EMAIL         VARCHAR2(320),
    CONTACT_PERSON        VARCHAR2(120),
    PHONE                 VARCHAR2(40),
    ADDRESS               VARCHAR2(500),
    DEFAULT_CURRENCY      VARCHAR2(3)    DEFAULT 'ETB' NOT NULL,
    TIMEZONE              VARCHAR2(64)   DEFAULT 'Africa/Addis_Ababa' NOT NULL,
    STATUS                VARCHAR2(20)   DEFAULT 'ACTIVE' NOT NULL,
    SENDER_LT_ADDRESS     VARCHAR2(12),
    RECEIVER_LT_ADDRESS   VARCHAR2(12),
    FUNDS_CODE_STRATEGY   VARCHAR2(24),
    OPENING_BALANCE_TAG   VARCHAR2(1),
    CLOSING_BALANCE_TAG   VARCHAR2(1),
    EMIT_13D              NUMBER(1)      DEFAULT 0 NOT NULL,
    EMIT_64               NUMBER(1)      DEFAULT 0 NOT NULL,
    EMIT_65               NUMBER(1)      DEFAULT 0 NOT NULL,
    EMIT_90D              NUMBER(1)      DEFAULT 0 NOT NULL,
    BLANK_LINES           NUMBER(1)      DEFAULT 0 NOT NULL,
    STATEMENT_NO_SEED     NUMBER(19)     DEFAULT 0 NOT NULL,
    LAST_STATEMENT_NO     NUMBER(19)     DEFAULT 0 NOT NULL,
    REMARKS               VARCHAR2(1000),
    CREATED_BY            VARCHAR2(120),
    CREATED_AT            TIMESTAMP,
    UPDATED_BY            VARCHAR2(120),
    UPDATED_AT            TIMESTAMP,
    VERSION               NUMBER(10)     DEFAULT 0 NOT NULL,
    CONSTRAINT PK_MT_CLIENT PRIMARY KEY (ID),
    CONSTRAINT UQ_MT_CLIENT_CODE UNIQUE (CLIENT_CODE)
);

-- -----------------------------------------------------------------------------
-- Accounts belonging to a client
-- -----------------------------------------------------------------------------
CREATE TABLE MT_ACCOUNT (
    ID                    NUMBER(19)     NOT NULL,
    CLIENT_ID             NUMBER(19)     NOT NULL,
    ACCOUNT_NUMBER        VARCHAR2(40)   NOT NULL,
    ACCOUNT_NAME          VARCHAR2(200),
    CURRENCY              VARCHAR2(3)    DEFAULT 'ETB' NOT NULL,
    BRANCH_CODE           VARCHAR2(20),
    IBAN                  VARCHAR2(40),
    BIC                   VARCHAR2(12),
    STATUS                VARCHAR2(20)   DEFAULT 'ACTIVE' NOT NULL,
    STATEMENT_NO_SEED     NUMBER(19)     DEFAULT 0 NOT NULL,
    LAST_STATEMENT_NO     NUMBER(19)     DEFAULT 0 NOT NULL,
    REMARKS               VARCHAR2(1000),
    CREATED_BY            VARCHAR2(120),
    CREATED_AT            TIMESTAMP,
    UPDATED_BY            VARCHAR2(120),
    UPDATED_AT            TIMESTAMP,
    VERSION               NUMBER(10)     DEFAULT 0 NOT NULL,
    CONSTRAINT PK_MT_ACCOUNT PRIMARY KEY (ID),
    CONSTRAINT UQ_MT_ACCOUNT_NO UNIQUE (ACCOUNT_NUMBER),
    CONSTRAINT FK_MT_ACCOUNT_CLIENT FOREIGN KEY (CLIENT_ID) REFERENCES MT_CLIENT (ID)
);

-- -----------------------------------------------------------------------------
-- Delivery schedule (daily / weekly / monthly + time of day)
-- -----------------------------------------------------------------------------
CREATE TABLE MT_DELIVERY_SCHEDULE (
    ID                    NUMBER(19)     NOT NULL,
    CLIENT_ID             NUMBER(19)     NOT NULL,
    ACCOUNT_ID            NUMBER(19),
    NAME                  VARCHAR2(120),
    FREQUENCY             VARCHAR2(16)   NOT NULL,
    DAY_OF_WEEK           NUMBER(1),
    DAY_OF_MONTH          NUMBER(2),
    SEND_TIME             VARCHAR2(5)    NOT NULL,
    TIMEZONE              VARCHAR2(64)   DEFAULT 'Africa/Addis_Ababa' NOT NULL,
    PERIOD_STRATEGY       VARCHAR2(24)   DEFAULT 'PREVIOUS_PERIOD' NOT NULL,
    ENABLED               NUMBER(1)      DEFAULT 1 NOT NULL,
    INCLUDE_ZERO_TXN      NUMBER(1)      DEFAULT 0 NOT NULL,
    LAST_SENT_AT          TIMESTAMP,
    NEXT_RUN_AT           TIMESTAMP,
    REMARKS               VARCHAR2(1000),
    CREATED_BY            VARCHAR2(120),
    CREATED_AT            TIMESTAMP,
    UPDATED_BY            VARCHAR2(120),
    UPDATED_AT            TIMESTAMP,
    VERSION               NUMBER(10)     DEFAULT 0 NOT NULL,
    CONSTRAINT PK_MT_DELIVERY_SCHEDULE PRIMARY KEY (ID),
    CONSTRAINT FK_MT_SCHED_CLIENT FOREIGN KEY (CLIENT_ID) REFERENCES MT_CLIENT (ID),
    CONSTRAINT FK_MT_SCHED_ACCOUNT FOREIGN KEY (ACCOUNT_ID) REFERENCES MT_ACCOUNT (ID)
);

-- -----------------------------------------------------------------------------
-- Recipient / template configuration per client (null client = global default)
-- -----------------------------------------------------------------------------
CREATE TABLE MT_EMAIL_CONFIG (
    ID                    NUMBER(19)     NOT NULL,
    CLIENT_ID             NUMBER(19),
    NAME                  VARCHAR2(120),
    TO_ADDRESSES          VARCHAR2(1000),
    CC_ADDRESSES          VARCHAR2(1000),
    BCC_ADDRESSES         VARCHAR2(1000),
    SUBJECT_TEMPLATE      VARCHAR2(500),
    BODY_TEMPLATE         CLOB,
    ENABLED               NUMBER(1)      DEFAULT 1 NOT NULL,
    IS_DEFAULT            NUMBER(1)      DEFAULT 0 NOT NULL,
    CREATED_BY            VARCHAR2(120),
    CREATED_AT            TIMESTAMP,
    UPDATED_BY            VARCHAR2(120),
    UPDATED_AT            TIMESTAMP,
    VERSION               NUMBER(10)     DEFAULT 0 NOT NULL,
    CONSTRAINT PK_MT_EMAIL_CONFIG PRIMARY KEY (ID),
    CONSTRAINT FK_MT_EMAIL_CLIENT FOREIGN KEY (CLIENT_ID) REFERENCES MT_CLIENT (ID)
);

-- -----------------------------------------------------------------------------
-- Generated MT940 statements
-- -----------------------------------------------------------------------------
CREATE TABLE MT_STATEMENT (
    ID                    NUMBER(19)     NOT NULL,
    CLIENT_ID             NUMBER(19)     NOT NULL,
    ACCOUNT_ID            NUMBER(19)     NOT NULL,
    SCHEDULE_ID           NUMBER(19),
    STATEMENT_REFERENCE   VARCHAR2(16)   NOT NULL,
    STATEMENT_NUMBER      NUMBER(19)     NOT NULL,
    PAGE_SEQUENCE         NUMBER(5)      DEFAULT 1 NOT NULL,
    ISN                   VARCHAR2(6),
    PERIOD_FROM           DATE           NOT NULL,
    PERIOD_TO             DATE           NOT NULL,
    CURRENCY              VARCHAR2(3)    NOT NULL,
    OPENING_BALANCE       NUMBER(19,2),
    OPENING_MARK          VARCHAR2(1),
    CLOSING_BALANCE       NUMBER(19,2),
    CLOSING_MARK          VARCHAR2(1),
    TRANSACTION_COUNT     NUMBER(10)     DEFAULT 0 NOT NULL,
    FILE_NAME             VARCHAR2(255),
    FILE_PATH             VARCHAR2(500),
    CHECKSUM              VARCHAR2(64),
    CONTENT               CLOB,
    STATUS                VARCHAR2(20)   DEFAULT 'GENERATED' NOT NULL,
    DELIVERY_STATUS       VARCHAR2(20)   DEFAULT 'PENDING' NOT NULL,
    ERROR_MESSAGE         VARCHAR2(2000),
    GENERATED_BY          VARCHAR2(120),
    GENERATED_AT          TIMESTAMP,
    CONSTRAINT PK_MT_STATEMENT PRIMARY KEY (ID),
    CONSTRAINT FK_MT_STMT_CLIENT FOREIGN KEY (CLIENT_ID) REFERENCES MT_CLIENT (ID),
    CONSTRAINT FK_MT_STMT_ACCOUNT FOREIGN KEY (ACCOUNT_ID) REFERENCES MT_ACCOUNT (ID),
    CONSTRAINT FK_MT_STMT_SCHEDULE FOREIGN KEY (SCHEDULE_ID) REFERENCES MT_DELIVERY_SCHEDULE (ID)
);

-- -----------------------------------------------------------------------------
-- Line items captured with a statement (snapshot of the source transactions)
-- -----------------------------------------------------------------------------
CREATE TABLE MT_STATEMENT_TXN (
    ID                    NUMBER(19)     NOT NULL,
    STATEMENT_ID          NUMBER(19)     NOT NULL,
    LINE_NO               NUMBER(5)      NOT NULL,
    TXN_DATE              DATE,
    VALUE_DATE            DATE,
    ENTRY_DATE            DATE,
    DC_MARK               VARCHAR2(1)    NOT NULL,
    AMOUNT                NUMBER(19,2)   NOT NULL,
    CURRENCY              VARCHAR2(3),
    CUST_REFERENCE        VARCHAR2(64),
    BANK_REFERENCE        VARCHAR2(64),
    TRANSACTION_TYPE      VARCHAR2(4),
    DESCRIPTION           VARCHAR2(500),
    BEGINNING_BALANCE     NUMBER(19,2),
    CLOSING_BALANCE       NUMBER(19,2),
    CONSTRAINT PK_MT_STATEMENT_TXN PRIMARY KEY (ID),
    CONSTRAINT FK_MT_STMTTXN_STMT FOREIGN KEY (STATEMENT_ID) REFERENCES MT_STATEMENT (ID) ON DELETE CASCADE
);

-- -----------------------------------------------------------------------------
-- Delivery attempts
-- -----------------------------------------------------------------------------
CREATE TABLE MT_DELIVERY_LOG (
    ID                    NUMBER(19)     NOT NULL,
    STATEMENT_ID          NUMBER(19),
    SCHEDULE_ID           NUMBER(19),
    CLIENT_ID             NUMBER(19),
    ACCOUNT_ID            NUMBER(19),
    RECIPIENT             VARCHAR2(1000),
    SUBJECT               VARCHAR2(500),
    STATUS                VARCHAR2(20)   NOT NULL,
    ATTEMPT               NUMBER(5)      DEFAULT 1 NOT NULL,
    PROVIDER              VARCHAR2(32),
    PROVIDER_RESPONSE     CLOB,
    ERROR_MESSAGE         VARCHAR2(2000),
    CORRELATION_ID        VARCHAR2(64),
    SENT_BY               VARCHAR2(120),
    SENT_AT               TIMESTAMP,
    CONSTRAINT PK_MT_DELIVERY_LOG PRIMARY KEY (ID),
    CONSTRAINT FK_MT_DLV_STMT FOREIGN KEY (STATEMENT_ID) REFERENCES MT_STATEMENT (ID),
    CONSTRAINT FK_MT_DLV_SCHED FOREIGN KEY (SCHEDULE_ID) REFERENCES MT_DELIVERY_SCHEDULE (ID),
    CONSTRAINT FK_MT_DLV_CLIENT FOREIGN KEY (CLIENT_ID) REFERENCES MT_CLIENT (ID),
    CONSTRAINT FK_MT_DLV_ACCOUNT FOREIGN KEY (ACCOUNT_ID) REFERENCES MT_ACCOUNT (ID)
);

-- -----------------------------------------------------------------------------
-- Maker / checker queue
-- -----------------------------------------------------------------------------
CREATE TABLE MT_APPROVAL_REQUEST (
    ID                    NUMBER(19)     NOT NULL,
    ENTITY_TYPE           VARCHAR2(40)   NOT NULL,
    ENTITY_ID             NUMBER(19),
    ENTITY_LABEL          VARCHAR2(200),
    OPERATION             VARCHAR2(20)   NOT NULL,
    STATUS                VARCHAR2(20)   DEFAULT 'PENDING' NOT NULL,
    REQUESTED_BY          VARCHAR2(120)  NOT NULL,
    REQUESTED_AT          TIMESTAMP,
    REQUEST_REASON        VARCHAR2(1000),
    PAYLOAD_JSON          CLOB,
    CURRENT_JSON          CLOB,
    DIFF_SUMMARY          VARCHAR2(2000),
    REVIEWED_BY           VARCHAR2(120),
    REVIEWED_AT           TIMESTAMP,
    REVIEW_NOTE           VARCHAR2(1000),
    VERSION               NUMBER(10)     DEFAULT 0 NOT NULL,
    CONSTRAINT PK_MT_APPROVAL_REQUEST PRIMARY KEY (ID),
    CONSTRAINT CK_MT_APPR_SELF CHECK (REVIEWED_BY IS NULL OR REVIEWED_BY <> REQUESTED_BY)
);

-- -----------------------------------------------------------------------------
-- Append-only audit trail
-- -----------------------------------------------------------------------------
CREATE TABLE MT_AUDIT_LOG (
    ID                    NUMBER(19)     NOT NULL,
    EVENT_TIME            TIMESTAMP      NOT NULL,
    ACTOR_USERNAME        VARCHAR2(120),
    ACTOR_NAME            VARCHAR2(200),
    ACTOR_ROLES           VARCHAR2(500),
    ACTION                VARCHAR2(60)   NOT NULL,
    ENTITY_TYPE           VARCHAR2(60),
    ENTITY_ID             VARCHAR2(64),
    ENTITY_LABEL          VARCHAR2(200),
    DESCRIPTION           VARCHAR2(1000),
    OUTCOME               VARCHAR2(20)   NOT NULL,
    OLD_VALUE             CLOB,
    NEW_VALUE             CLOB,
    DETAIL                CLOB,
    IP_ADDRESS            VARCHAR2(64),
    USER_AGENT            VARCHAR2(500),
    SESSION_ID            VARCHAR2(64),
    CORRELATION_ID        VARCHAR2(64),
    CONSTRAINT PK_MT_AUDIT_LOG PRIMARY KEY (ID)
);

-- -----------------------------------------------------------------------------
-- Indexes
-- -----------------------------------------------------------------------------
CREATE INDEX IX_MT_ACCOUNT_CLIENT       ON MT_ACCOUNT (CLIENT_ID);
CREATE INDEX IX_MT_ACCOUNT_NO           ON MT_ACCOUNT (ACCOUNT_NUMBER);
CREATE INDEX IX_MT_SCHED_CLIENT         ON MT_DELIVERY_SCHEDULE (CLIENT_ID);
CREATE INDEX IX_MT_SCHED_ACCOUNT        ON MT_DELIVERY_SCHEDULE (ACCOUNT_ID);
CREATE INDEX IX_MT_SCHED_NEXTRUN        ON MT_DELIVERY_SCHEDULE (ENABLED, NEXT_RUN_AT);
CREATE INDEX IX_MT_EMAIL_CLIENT         ON MT_EMAIL_CONFIG (CLIENT_ID);
CREATE INDEX IX_MT_STMT_CLIENT          ON MT_STATEMENT (CLIENT_ID);
CREATE INDEX IX_MT_STMT_ACCOUNT         ON MT_STATEMENT (ACCOUNT_ID);
CREATE INDEX IX_MT_STMT_REFERENCE       ON MT_STATEMENT (STATEMENT_REFERENCE);
CREATE INDEX IX_MT_STMT_PERIOD          ON MT_STATEMENT (PERIOD_FROM, PERIOD_TO);
CREATE INDEX IX_MT_STMTTXN_STMT         ON MT_STATEMENT_TXN (STATEMENT_ID);
CREATE INDEX IX_MT_DLV_STMT             ON MT_DELIVERY_LOG (STATEMENT_ID);
CREATE INDEX IX_MT_DLV_SENT_AT          ON MT_DELIVERY_LOG (SENT_AT);
CREATE INDEX IX_MT_APPR_STATUS          ON MT_APPROVAL_REQUEST (STATUS, REQUESTED_AT);
CREATE INDEX IX_MT_APPR_ENTITY          ON MT_APPROVAL_REQUEST (ENTITY_TYPE, ENTITY_ID);
CREATE INDEX IX_MT_AUDIT_TIME           ON MT_AUDIT_LOG (EVENT_TIME);
CREATE INDEX IX_MT_AUDIT_ACTOR          ON MT_AUDIT_LOG (ACTOR_USERNAME);
CREATE INDEX IX_MT_AUDIT_ENTITY         ON MT_AUDIT_LOG (ENTITY_TYPE, ENTITY_ID);
CREATE INDEX IX_MT_AUDIT_CORRELATION    ON MT_AUDIT_LOG (CORRELATION_ID);
