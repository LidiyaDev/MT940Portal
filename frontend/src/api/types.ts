// Shared shapes mirroring the Spring Boot DTOs.

export type ClientStatus = 'ACTIVE' | 'INACTIVE' | 'SUSPENDED';
export type AccountStatus = 'ACTIVE' | 'DORMANT' | 'INACTIVE' | 'CLOSED';
export type Frequency = 'DAILY' | 'WEEKLY' | 'MONTHLY';
export type PeriodStrategy = 'PREVIOUS_PERIOD' | 'ROLLING_7' | 'ROLLING_30';
export type ApprovalStatus = 'PENDING' | 'APPROVED' | 'REJECTED' | 'CANCELLED';
export type ApprovalOperation = 'CREATE' | 'UPDATE' | 'DELETE' | 'ACTIVATE' | 'DEACTIVATE' | 'SEND';
export type EntityType =
  | 'CLIENT'
  | 'ACCOUNT'
  | 'DELIVERY_SCHEDULE'
  | 'EMAIL_CONFIG'
  | 'STATEMENT';
export type DeliveryStatus = 'PENDING' | 'SENT' | 'FAILED' | 'SKIPPED';
export type StatementStatus = 'GENERATED' | 'FAILED';
export type AuditOutcome = 'SUCCESS' | 'FAILURE';
export type BalanceTag = 'F' | 'M';
export type FundsCodeStrategy = 'NONE' | 'CURRENCY_THIRD_CHAR';

export interface PendingChange {
  approvalId: number;
  operation: ApprovalOperation;
  status: ApprovalStatus;
  requestedBy: string;
  requestedAt: string;
  reviewNote?: string;
}

export interface Client {
  id: number;
  clientCode: string;
  clientName: string;
  primaryEmail?: string;
  contactPerson?: string;
  phone?: string;
  address?: string;
  defaultCurrency: string;
  timezone: string;
  status: ClientStatus;
  senderLtAddress?: string;
  receiverLtAddress?: string;
  fundsCodeStrategy?: FundsCodeStrategy;
  openingBalanceTag?: BalanceTag;
  closingBalanceTag?: BalanceTag;
  emit13d: boolean;
  emit64: boolean;
  emit65: boolean;
  emit90d: boolean;
  blankLineBetweenTags: boolean;
  statementNumberSeed?: number;
  remarks?: string;
  accountCount?: number;
  createdAt?: string;
  createdBy?: string;
  updatedAt?: string;
  updatedBy?: string;
  pendingChange?: PendingChange;
}

export interface Account {
  id: number;
  clientId: number;
  clientCode?: string;
  accountNumber: string;
  accountName?: string;
  currency: string;
  branchCode?: string;
  iban?: string;
  bic?: string;
  status: AccountStatus;
  statementNumberSeed?: number;
  lastStatementNumber?: number;
  remarks?: string;
  statementCount?: number;
  createdAt?: string;
  createdBy?: string;
  updatedAt?: string;
  updatedBy?: string;
  pendingChange?: PendingChange;
}

export interface DeliverySchedule {
  id: number;
  clientId: number;
  clientCode?: string;
  accountId?: number | null;
  accountNumber?: string | null;
  name?: string;
  frequency: Frequency;
  dayOfWeek?: number | null;
  dayOfMonth?: number | null;
  sendTime: string;
  timezone: string;
  periodStrategy: PeriodStrategy;
  enabled: boolean;
  includeZeroTransactionStatements: boolean;
  lastSentAt?: string | null;
  nextRunAt?: string | null;
  description?: string;
  remarks?: string;
  createdAt?: string;
  createdBy?: string;
  updatedAt?: string;
  updatedBy?: string;
  pendingChange?: PendingChange;
}

export interface EmailConfig {
  id: number;
  clientId?: number | null;
  clientCode?: string;
  name?: string;
  toAddresses?: string;
  ccAddresses?: string;
  bccAddresses?: string;
  subjectTemplate?: string;
  bodyTemplate?: string;
  enabled: boolean;
  isDefault: boolean;
  createdAt?: string;
  createdBy?: string;
  updatedAt?: string;
  updatedBy?: string;
  pendingChange?: PendingChange;
}

export interface Statement {
  id: number;
  clientId: number;
  clientCode?: string;
  clientName?: string;
  accountId: number;
  accountNumber: string;
  statementReference: string;
  statementNumber: number;
  pageSequence: number;
  isn?: string;
  periodFrom: string;
  periodTo: string;
  currency: string;
  openingBalance: number;
  openingMark: string;
  closingBalance: number;
  closingMark: string;
  transactionCount: number;
  fileName: string;
  checksum?: string;
  status: StatementStatus;
  deliveryStatus: DeliveryStatus;
  errorMessage?: string;
  generatedAt?: string;
  generatedBy?: string;
  content?: string;
}

export interface ApprovalRequest {
  id: number;
  entityType: EntityType;
  entityId?: number | null;
  entityLabel?: string;
  operation: ApprovalOperation;
  status: ApprovalStatus;
  requestedBy: string;
  requestedAt: string;
  requestReason?: string;
  payloadJson?: string | null;
  currentJson?: string | null;
  diffSummary?: string;
  reviewedBy?: string;
  reviewedAt?: string;
  reviewNote?: string;
  selfApprovalBlocked: boolean;
}

export interface AuditLog {
  id: number;
  eventTime: string;
  actorUsername?: string;
  actorName?: string;
  actorRoles?: string;
  action: string;
  entityType?: string;
  entityId?: string;
  entityLabel?: string;
  description?: string;
  outcome: AuditOutcome;
  oldValue?: string;
  newValue?: string;
  detail?: string;
  ipAddress?: string;
  sessionId?: string;
  correlationId?: string;
}

export interface DeliveryLog {
  id: number;
  statementId?: number | null;
  statementReference?: string;
  clientId?: number | null;
  clientCode?: string;
  accountId?: number | null;
  accountNumber?: string;
  recipient?: string;
  subject?: string;
  status: DeliveryStatus;
  attempt: number;
  provider?: string;
  providerResponse?: string;
  errorMessage?: string;
  correlationId?: string;
  sentBy?: string;
  sentAt?: string;
}

export interface UpcomingDelivery {
  scheduleId: number;
  clientCode: string;
  clientName: string;
  accountNumber: string;
  frequency: string;
  nextRunAt: string;
}

export interface Dashboard {
  activeClients: number;
  activeAccounts: number;
  pendingApprovals: number;
  statementsToday: number;
  deliveredToday: number;
  failedToday: number;
  pendingDeliveries: number;
  upcoming: UpcomingDelivery[];
  recentActivity: AuditLog[];
}

export interface Page<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  last: boolean;
}

export interface UserProfile {
  username: string;
  fullName: string;
  authorities: string[];
  isMaker: boolean;
  isChecker: boolean;
  isAdmin: boolean;
  roles: { maker: string; checker: string; admin: string; viewer: string };
}
