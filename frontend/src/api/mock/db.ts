import type {
  Account,
  ApprovalRequest,
  AuditLog,
  Client,
  DeliveryLog,
  DeliverySchedule,
  EmailConfig,
  Statement,
} from '../types';

// ---------------------------------------------------------------------------
// Seed data for the demonstration mode. Mirrors the shapes returned by the
// Spring Boot API so the UI behaves identically against the real backend.
// ---------------------------------------------------------------------------

let sequence = 1000;
export const nextId = () => ++sequence;

const now = new Date();
const daysAgo = (days: number) => new Date(now.getTime() - days * 86400000).toISOString();
const daysAhead = (days: number) => new Date(now.getTime() + days * 86400000).toISOString();

export const db: {
  clients: Client[];
  accounts: Account[];
  schedules: DeliverySchedule[];
  emailConfigs: EmailConfig[];
  statements: Statement[];
  approvals: ApprovalRequest[];
  audit: AuditLog[];
  deliveries: DeliveryLog[];
} = {
  clients: [
    {
      id: 1,
      clientCode: 'AMBO001',
      clientName: 'Ambo Mineral Water PLC',
      primaryEmail: 'finance@ambomineral.example',
      contactPerson: 'Dawit Haile',
      phone: '+251 11 555 0101',
      address: 'Bole Road, Addis Ababa',
      defaultCurrency: 'ETB',
      timezone: 'Africa/Addis_Ababa',
      status: 'ACTIVE',
      emit13d: false,
      emit64: false,
      emit65: false,
      emit90d: false,
      blankLineBetweenTags: false,
      statementNumberSeed: 0,
      remarks: 'Daily statements requested by group treasury.',
      accountCount: 3,
      createdAt: daysAgo(120),
      createdBy: 'abebe.maker',
      updatedAt: daysAgo(12),
      updatedBy: 'abebe.maker',
    },
    {
      id: 2,
      clientCode: 'DASHTRADE',
      clientName: 'Dashen Trading House',
      primaryEmail: 'treasury@dashentrade.example',
      contactPerson: 'Sara Tesfaye',
      phone: '+251 11 555 0222',
      address: 'Meskel Square, Addis Ababa',
      defaultCurrency: 'ETB',
      timezone: 'Africa/Addis_Ababa',
      status: 'ACTIVE',
      emit13d: false,
      emit64: false,
      emit65: false,
      emit90d: true,
      blankLineBetweenTags: false,
      statementNumberSeed: 0,
      accountCount: 2,
      createdAt: daysAgo(90),
      createdBy: 'abebe.maker',
      updatedAt: daysAgo(40),
      updatedBy: 'almaz.checker',
    },
    {
      id: 3,
      clientCode: 'LOGEX',
      clientName: 'Logistics Express SC',
      primaryEmail: 'accounts@logex.example',
      contactPerson: 'Yonas Bekele',
      phone: '+251 11 555 0333',
      status: 'SUSPENDED',
      defaultCurrency: 'ETB',
      timezone: 'Africa/Addis_Ababa',
      emit13d: false,
      emit64: false,
      emit65: false,
      emit90d: false,
      blankLineBetweenTags: false,
      accountCount: 1,
      createdAt: daysAgo(60),
      createdBy: 'abebe.maker',
    },
  ],

  accounts: [
    {
      id: 101,
      clientId: 1,
      clientCode: 'AMBO001',
      accountNumber: '1144355935012',
      accountName: 'Ambo Mineral Water PLC - Operating',
      currency: 'ETB',
      branchCode: '0114',
      status: 'ACTIVE',
      statementNumberSeed: 0,
      lastStatementNumber: 20719,
      statementCount: 12,
      createdAt: daysAgo(120),
      createdBy: 'abebe.maker',
    },
    {
      id: 102,
      clientId: 1,
      clientCode: 'AMBO001',
      accountNumber: '1144355935013',
      accountName: 'Ambo Mineral Water PLC - Payroll',
      currency: 'ETB',
      branchCode: '0114',
      status: 'ACTIVE',
      statementNumberSeed: 0,
      lastStatementNumber: 4,
      statementCount: 4,
      createdAt: daysAgo(110),
      createdBy: 'abebe.maker',
    },
    {
      id: 103,
      clientId: 1,
      clientCode: 'AMBO001',
      accountNumber: '1144355935014',
      accountName: 'Ambo Mineral Water PLC - USD Collection',
      currency: 'USD',
      branchCode: '0114',
      status: 'DORMANT',
      statementNumberSeed: 0,
      lastStatementNumber: 0,
      statementCount: 0,
      createdAt: daysAgo(100),
      createdBy: 'abebe.maker',
    },
    {
      id: 201,
      clientId: 2,
      clientCode: 'DASHTRADE',
      accountNumber: '1000223344556',
      accountName: 'Dashen Trading - Main',
      currency: 'ETB',
      branchCode: '0021',
      status: 'ACTIVE',
      statementNumberSeed: 0,
      lastStatementNumber: 31,
      statementCount: 31,
      createdAt: daysAgo(90),
      createdBy: 'abebe.maker',
    },
    {
      id: 202,
      clientId: 2,
      clientCode: 'DASHTRADE',
      accountNumber: '1000223344557',
      accountName: 'Dashen Trading - Imports',
      currency: 'ETB',
      branchCode: '0021',
      status: 'ACTIVE',
      statementNumberSeed: 0,
      lastStatementNumber: 9,
      statementCount: 9,
      createdAt: daysAgo(88),
      createdBy: 'abebe.maker',
    },
    {
      id: 301,
      clientId: 3,
      clientCode: 'LOGEX',
      accountNumber: '1000999888777',
      accountName: 'Logistics Express - Operating',
      currency: 'ETB',
      branchCode: '0031',
      status: 'ACTIVE',
      statementNumberSeed: 0,
      lastStatementNumber: 2,
      statementCount: 2,
      createdAt: daysAgo(60),
      createdBy: 'abebe.maker',
    },
  ],

  schedules: [
    {
      id: 501,
      clientId: 1,
      clientCode: 'AMBO001',
      accountId: null,
      accountNumber: null,
      name: 'Daily treasury statement',
      frequency: 'DAILY',
      sendTime: '07:00',
      timezone: 'Africa/Addis_Ababa',
      periodStrategy: 'PREVIOUS_PERIOD',
      enabled: true,
      includeZeroTransactionStatements: false,
      lastSentAt: daysAgo(1),
      nextRunAt: daysAhead(0.2),
      description: 'daily at 07:00',
      createdAt: daysAgo(100),
      createdBy: 'abebe.maker',
    },
    {
      id: 502,
      clientId: 1,
      clientCode: 'AMBO001',
      accountId: 103,
      accountNumber: '1144355935014',
      name: 'Monthly dormant review',
      frequency: 'MONTHLY',
      dayOfMonth: 1,
      sendTime: '08:30',
      timezone: 'Africa/Addis_Ababa',
      periodStrategy: 'PREVIOUS_PERIOD',
      enabled: true,
      includeZeroTransactionStatements: true,
      nextRunAt: daysAhead(6),
      description: 'monthly on day 1 at 08:30',
      createdAt: daysAgo(80),
      createdBy: 'abebe.maker',
    },
    {
      id: 503,
      clientId: 2,
      clientCode: 'DASHTRADE',
      accountId: null,
      accountNumber: null,
      name: 'Weekly board pack',
      frequency: 'WEEKLY',
      dayOfWeek: 1,
      sendTime: '06:45',
      timezone: 'Africa/Addis_Ababa',
      periodStrategy: 'PREVIOUS_PERIOD',
      enabled: true,
      includeZeroTransactionStatements: false,
      lastSentAt: daysAgo(7),
      nextRunAt: daysAhead(3),
      description: 'weekly on Monday at 06:45',
      createdAt: daysAgo(70),
      createdBy: 'abebe.maker',
    },
  ],

  emailConfigs: [
    {
      id: 701,
      clientId: 1,
      clientCode: 'AMBO001',
      name: 'Treasury distribution',
      toAddresses: 'finance@ambomineral.example, treasury@ambomineral.example',
      ccAddresses: 'cfo@ambomineral.example',
      bccAddresses: '',
      subjectTemplate: 'MT940 Statement - Account ${accountNumber} - ${periodFrom} to ${periodTo}',
      bodyTemplate:
        'Dear ${clientName},\n\nPlease find attached the MT940 statement for account ${accountNumber}.\n\nStatement reference : ${statementReference}\nPeriod              : ${periodFrom} to ${periodTo}\nClosing balance     : ${closingBalance}\nTransactions        : ${transactionCount}\n\nRegards,\n${fromName}',
      enabled: true,
      isDefault: true,
      createdAt: daysAgo(100),
      createdBy: 'abebe.maker',
    },
    {
      id: 702,
      clientId: 2,
      clientCode: 'DASHTRADE',
      name: 'Board pack recipients',
      toAddresses: 'treasury@dashentrade.example',
      ccAddresses: '',
      bccAddresses: 'audit@dashentrade.example',
      subjectTemplate: 'Weekly MT940 - ${accountNumber} - week ending ${periodTo}',
      bodyTemplate:
        'Dear ${clientName},\n\nAttached is the weekly MT940 statement for account ${accountNumber}.\n\nRegards,\n${fromName}',
      enabled: true,
      isDefault: true,
      createdAt: daysAgo(70),
      createdBy: 'abebe.maker',
    },
  ],

  statements: [],

  approvals: [
    {
      id: 9001,
      entityType: 'ACCOUNT',
      entityId: null,
      entityLabel: '2 accounts for AMBO001',
      operation: 'CREATE',
      status: 'PENDING',
      requestedBy: 'abebe.maker',
      requestedAt: daysAgo(0.3),
      requestReason: 'New collection accounts opened by branch 0114.',
      payloadJson: JSON.stringify(
        {
          clientId: 1,
          accounts: [
            { accountNumber: '1144355935020', accountName: 'Ambo - Collection A', currency: 'ETB' },
            { accountNumber: '1144355935021', accountName: 'Ambo - Collection B', currency: 'ETB' },
          ],
        },
        null,
        2
      ),
      currentJson: null,
      diffSummary: 'New record',
      selfApprovalBlocked: false,
    },
    {
      id: 9002,
      entityType: 'DELIVERY_SCHEDULE',
      entityId: 503,
      entityLabel: 'weekly on Monday at 06:45',
      operation: 'UPDATE',
      status: 'PENDING',
      requestedBy: 'abebe.maker',
      requestedAt: daysAgo(0.5),
      requestReason: 'Board meeting moved from Monday to Tuesday.',
      payloadJson: JSON.stringify(
        { clientId: 2, frequency: 'WEEKLY', dayOfWeek: 2, sendTime: '06:45', enabled: true },
        null,
        2
      ),
      currentJson: JSON.stringify(
        { clientId: 2, frequency: 'WEEKLY', dayOfWeek: 1, sendTime: '06:45', enabled: true },
        null,
        2
      ),
      diffSummary: 'dayOfWeek: 1 -> 2',
      selfApprovalBlocked: false,
    },
    {
      id: 9003,
      entityType: 'CLIENT',
      entityId: 3,
      entityLabel: 'LOGEX',
      operation: 'UPDATE',
      status: 'PENDING',
      requestedBy: 'abebe.maker',
      requestedAt: daysAgo(1),
      requestReason: 'Suspension lifted after compliance sign-off.',
      payloadJson: JSON.stringify({ clientCode: 'LOGEX', status: 'ACTIVE' }, null, 2),
      currentJson: JSON.stringify({ clientCode: 'LOGEX', status: 'SUSPENDED' }, null, 2),
      diffSummary: 'status: SUSPENDED -> ACTIVE',
      selfApprovalBlocked: false,
    },
    {
      id: 9000,
      entityType: 'CLIENT',
      entityId: 2,
      entityLabel: 'DASHTRADE',
      operation: 'CREATE',
      status: 'APPROVED',
      requestedBy: 'abebe.maker',
      requestedAt: daysAgo(91),
      reviewedBy: 'almaz.checker',
      reviewedAt: daysAgo(90),
      diffSummary: 'New record',
      selfApprovalBlocked: false,
    },
  ],

  deliveries: [],

  audit: [],
};

// ---------------------------------------------------------------------------
// MT940 rendering used by the demonstration mode. Mirrors the backend
// Mt940Generator so the preview shows exactly what Spring Boot will produce.
// ---------------------------------------------------------------------------

export interface MockTransaction {
  transactionDate: string;
  trans_reference: string;
  debit_amt: string;
  credit_amt?: string;
  beginning_balance: string;
  closing_balance: string;
  txnDescription: string;
}

export const SAMPLE_TRANSACTIONS: MockTransaction[] = [
  {
    transactionDate: '2026-09-23',
    trans_reference: '879FXSA262660001',
    debit_amt: '2340.00',
    beginning_balance: '327060.15',
    closing_balance: '324720.15',
    txnDescription: 'CASH FCY BOUGHT AND SOLD',
  },
  {
    transactionDate: '2026-09-23',
    trans_reference: '8798799262450001 ACDB/TT/00971/26',
    debit_amt: '20.00',
    credit_amt: '0.00',
    beginning_balance: '324720.15',
    closing_balance: '324700.15',
    txnDescription: 'SERVICE CHARGE FOR CORESPONDENT BANK',
  },
  {
    transactionDate: '2026-09-23',
    trans_reference: '8798799262450003 ACDB/TT/00972/26',
    debit_amt: '20.00',
    credit_amt: '0.00',
    beginning_balance: '324700.15',
    closing_balance: '324680.15',
    txnDescription: 'SERVICE CHARGE FOR CORESPONDENT BANK',
  },
  {
    transactionDate: '2026-09-23',
    trans_reference: 'FT26266000077',
    debit_amt: '0.00',
    credit_amt: '150000.00',
    beginning_balance: '324680.15',
    closing_balance: '474680.15',
    txnDescription: 'INWARD REMITTANCE - EXPORT PROCEEDS',
  },
];

/** SWIFT decimal: comma separator, always two decimals, no grouping, no sign. */
const swiftAmount = (value: number) =>
  Math.abs(value)
    .toFixed(2)
    .replace('.', ',');

const pad = (value: number, width: number) =>
  String(value).length >= width
    ? String(value).slice(-width)
    : '0'.repeat(width - String(value).length) + String(value);

const fmt = (date: Date, pattern: 'yyMMdd' | 'MMdd') => {
  const yy = String(date.getFullYear()).slice(2);
  const mm = pad(date.getMonth() + 1, 2);
  const dd = pad(date.getDate(), 2);
  return pattern === 'yyMMdd' ? `${yy}${mm}${dd}` : `${mm}${dd}`;
};

const sanitise = (value: string) =>
  value
    .toUpperCase()
    .replace(/[^A-Z0-9/\-?:().,'+ ]/g, ' ')
    .replace(/\s+/g, ' ')
    .trim();

export function renderMt940(options: {
  accountNumber: string;
  currency: string;
  statementDate: Date;
  statementNumber: number;
  pageSequence: number;
  transactions: MockTransaction[];
  senderLtAddress: string;
  receiverLtAddress: string;
  sessionNumber: string;
  priority: string;
  transactionType: string;
  openingBalance: number;
  closingBalance: number;
}): string {
  const {
    accountNumber,
    currency,
    statementDate,
    statementNumber,
    pageSequence,
    transactions,
    senderLtAddress,
    receiverLtAddress,
    sessionNumber,
    priority,
    transactionType,
    openingBalance,
    closingBalance,
  } = options;

  const date = fmt(statementDate, 'yyMMdd');
  const suffix = accountNumber.slice(-6);
  const reference = ('STMT' + date + suffix).slice(0, 16);
  const isn = pad(statementNumber, 6);
  const mark = (value: number) => (value < 0 ? 'D' : 'C');

  const lines: string[] = [];
  lines.push(
    `{1:F01${senderLtAddress.padEnd(12, 'X').slice(0, 12)}${sessionNumber}${isn}}` +
      `{2:I940${receiverLtAddress.padEnd(12, 'X').slice(0, 12)}${priority}}{4:`
  );
  lines.push(`:20:${reference}`);
  lines.push(`:25:${accountNumber}`);
  lines.push(`:28C:${statementNumber}/${pageSequence}`);
  lines.push(`:60F:${mark(openingBalance)}${date}${currency}${swiftAmount(openingBalance)}`);

  for (const txn of transactions) {
    const txnDate = new Date(txn.transactionDate);
    const debit = Number(txn.debit_amt ?? 0);
    const credit = Number(txn.credit_amt ?? 0);
    const isDebit = debit > 0 || credit === 0;
    const amount = isDebit ? debit : credit;
    const reference16 = sanitise(txn.trans_reference).replace(/ /g, '').slice(0, 16);
    lines.push(
      `:61:${fmt(txnDate, 'yyMMdd')}${fmt(txnDate, 'MMdd')}${isDebit ? 'D' : 'C'}` +
        `${swiftAmount(amount)}${transactionType}${reference16}`
    );
    lines.push(`:86:${sanitise(txn.txnDescription)}`);
  }

  lines.push(`:62F:${mark(closingBalance)}${date}${currency}${swiftAmount(closingBalance)}`);
  lines.push('-}');

  return lines.join('\n') + '\n';
}

/** Builds a statement row from the sample feed. */
export function buildStatement(account: Account, client: Client, periodFrom: string, periodTo: string) {
  const txns = SAMPLE_TRANSACTIONS;
  const opening = Number(txns[0].beginning_balance);
  const closing = Number(txns[txns.length - 1].closing_balance);
  const statementNumber = (account.lastStatementNumber ?? 0) + 1;
  const statementDate = new Date(periodTo);

  const content = renderMt940({
    accountNumber: account.accountNumber,
    currency: account.currency,
    statementDate,
    statementNumber,
    pageSequence: 1,
    transactions: txns,
    senderLtAddress: client.senderLtAddress || 'DASHETAXXXXX',
    receiverLtAddress: client.receiverLtAddress || 'RECVETAAXXXX',
    sessionNumber: '0001',
    priority: 'N',
    transactionType: 'NTRF',
    openingBalance: opening,
    closingBalance: closing,
  });

  const statement: Statement = {
    id: nextId(),
    clientId: client.id,
    clientCode: client.clientCode,
    clientName: client.clientName,
    accountId: account.id,
    accountNumber: account.accountNumber,
    statementReference: ('STMT' + fmt(statementDate, 'yyMMdd') + account.accountNumber.slice(-6)).slice(0, 16),
    statementNumber,
    pageSequence: 1,
    isn: pad(statementNumber, 6),
    periodFrom,
    periodTo,
    currency: account.currency,
    openingBalance: opening,
    openingMark: 'C',
    closingBalance: closing,
    closingMark: 'C',
    transactionCount: txns.length,
    fileName: `${account.accountNumber}_${statementDate.toISOString().slice(0, 10).replace(/-/g, '')}.txt`,
    checksum: Math.random().toString(16).slice(2, 18),
    status: 'GENERATED',
    deliveryStatus: 'PENDING',
    generatedAt: new Date().toISOString(),
    generatedBy: 'abebe.maker',
    content,
  };
  return statement;
}
