import type {
  Account,
  ApprovalRequest,
  AuditLog,
  Client,
  Dashboard,
  DeliveryLog,
  DeliverySchedule,
  EmailConfig,
  Page,
  Statement,
} from './types';
import { buildStatement, db, nextId } from './mock/db';

/**
 * In-memory implementation of the portal API used when VITE_USE_MOCK is not
 * "false". Every write behaves like the real backend: mutations are queued as
 * maker-checker requests and only applied on approval.
 */

const delay = (ms = 180) => new Promise((resolve) => setTimeout(resolve, ms));

export const currentUser = {
  username: 'abebe.maker',
  fullName: 'Abebe Maker',
  authorities: ['ROLE_MT940_MAKER', 'ROLE_MT940_VIEWER'],
  isMaker: true,
  isChecker: false,
  isAdmin: false,
  roles: {
    maker: 'ROLE_MT940_MAKER',
    checker: 'ROLE_MT940_CHECKER',
    admin: 'ROLE_MT940_ADMIN',
    viewer: 'ROLE_MT940_VIEWER',
  },
};

/** Switches the acting operator in demonstration mode. */
export function setMockUser(identity: {
  username: string;
  fullName: string;
  authorities: string[];
}) {
  currentUser.username = identity.username;
  currentUser.fullName = identity.fullName;
  currentUser.authorities = identity.authorities;
  currentUser.isMaker = identity.authorities.includes('ROLE_MT940_MAKER');
  currentUser.isChecker = identity.authorities.includes('ROLE_MT940_CHECKER');
  currentUser.isAdmin = identity.authorities.includes('ROLE_MT940_ADMIN');
}

function audit(action: string, entityType: string, description: string, entityId?: string) {
  db.audit.unshift({
    id: nextId(),
    eventTime: new Date().toISOString(),
    actorUsername: currentUser.username,
    actorName: currentUser.fullName,
    actorRoles: currentUser.authorities.join(','),
    action,
    entityType,
    entityId,
    description,
    outcome: 'SUCCESS',
    ipAddress: '10.0.0.12',
    correlationId: crypto.randomUUID(),
  });
}

function seedAudit() {
  const seeds: Array<[string, string, string]> = [
    ['CLIENT_CREATE', 'CLIENT', 'Approved creation of client DASHTRADE'],
    ['ACCOUNT_CREATE', 'ACCOUNT', 'Approved creation of 2 account(s) for client DASHTRADE'],
    ['SCHEDULE_CREATE', 'DELIVERY_SCHEDULE', 'Approved creation of delivery schedule: weekly on Monday at 06:45'],
    ['STATEMENT_GENERATE', 'STATEMENT', 'Generated MT940 STMT260923935012 for account 1144355935012 (4 transactions)'],
    ['STATEMENT_SEND', 'STATEMENT', 'Emailed statement STMT260923935012 to finance@ambomineral.example'],
    ['APPROVAL_SUBMIT', 'APPROVAL_REQUEST', 'Submitted for approval: create account 2 accounts for AMBO001'],
    ['EMAIL_CONFIG_CREATE', 'EMAIL_CONFIG', 'Approved creation of email configuration Treasury distribution'],
    ['SCHEDULER_RUN', 'DELIVERY_SCHEDULE', 'Scheduled run delivered 3 statement(s) for daily at 07:00'],
    ['STATEMENT_PREVIEW', 'ACCOUNT', 'Previewed MT940 for account 1144355935012 covering 2026-09-01 to 2026-09-24'],
    ['CLIENT_UPDATE', 'CLIENT', 'Approved update of client AMBO001'],
  ];
  let offset = 0;
  for (const [action, entityType, description] of seeds) {
    offset += 7;
    db.audit.push({
      id: nextId(),
      eventTime: new Date(Date.now() - offset * 60000).toISOString(),
      actorUsername: offset % 2 === 0 ? 'maker.user' : 'checker.user',
      actorName: offset % 2 === 0 ? 'Abebe Maker' : 'Almaz Checker',
      actorRoles: offset % 2 === 0 ? 'ROLE_MT940_MAKER' : 'ROLE_MT940_CHECKER',
      action,
      entityType,
      description,
      outcome: offset === 63 ? 'FAILURE' : 'SUCCESS',
      ipAddress: '10.0.0.12',
      correlationId: crypto.randomUUID(),
    });
  }
}
seedAudit();

// Seed a couple of delivered statements so the history screens have content.
(() => {
  const account = db.accounts[0];
  const client = db.clients[0];
  const to = new Date(Date.now() - 86400000).toISOString().slice(0, 10);
  const from = new Date(Date.now() - 2 * 86400000).toISOString().slice(0, 10);
  const statement = buildStatement(account, client, from, to);
  statement.deliveryStatus = 'SENT';
  db.statements.push(statement);
  db.deliveries.push({
    id: nextId(),
    statementId: statement.id,
    statementReference: statement.statementReference,
    clientId: client.id,
    clientCode: client.clientCode,
    accountId: account.id,
    accountNumber: account.accountNumber,
    recipient: 'finance@ambomineral.example, treasury@ambomineral.example',
    subject: `MT940 Statement - Account ${account.accountNumber} - ${from} to ${to}`,
    status: 'SENT',
    attempt: 1,
    provider: 'MOCK',
    providerResponse: '{"delivered":true,"provider":"mock","attachmentBytes":712}',
    sentBy: 'scheduler',
    sentAt: new Date(Date.now() - 86400000).toISOString(),
    correlationId: crypto.randomUUID(),
  });
})();

const paginate = <T,>(items: T[], page = 0, size = 25): Page<T> => ({
  content: items.slice(page * size, page * size + size),
  page,
  size,
  totalElements: items.length,
  totalPages: Math.max(1, Math.ceil(items.length / size)),
  last: (page + 1) * size >= items.length,
});

const queue = (input: {
  entityType: ApprovalRequest['entityType'];
  entityId?: number | null;
  entityLabel: string;
  operation: ApprovalRequest['operation'];
  payload: unknown;
  current?: unknown;
  reason?: string;
}) => {
  const request: ApprovalRequest = {
    id: nextId(),
    entityType: input.entityType,
    entityId: input.entityId ?? null,
    entityLabel: input.entityLabel,
    operation: input.operation,
    status: 'PENDING',
    requestedBy: currentUser.username,
    requestedAt: new Date().toISOString(),
    requestReason: input.reason,
    payloadJson: JSON.stringify(input.payload, null, 2),
    currentJson: input.current ? JSON.stringify(input.current, null, 2) : null,
    diffSummary: input.current ? 'Updated record' : 'New record',
    selfApprovalBlocked: false,
  };
  db.approvals.unshift(request);
  audit('APPROVAL_SUBMIT', 'APPROVAL_REQUEST', `Submitted for approval: ${input.operation} ${input.entityLabel}`);
  return request;
};

// ---------------------------------------------------------------------------

export const mock = {
  async getMe() {
    await delay(60);
    return currentUser;
  },

  async getDashboard(): Promise<Dashboard> {
    await delay();
    return {
      activeClients: db.clients.filter((c) => c.status === 'ACTIVE').length,
      activeAccounts: db.accounts.filter((a) => a.status === 'ACTIVE').length,
      pendingApprovals: db.approvals.filter((a) => a.status === 'PENDING').length,
      statementsToday: db.statements.length,
      deliveredToday: db.deliveries.filter((d) => d.status === 'SENT').length,
      failedToday: db.deliveries.filter((d) => d.status === 'FAILED').length,
      pendingDeliveries: db.statements.filter((s) => s.deliveryStatus === 'PENDING').length,
      upcoming: db.schedules
        .filter((s) => s.enabled && s.nextRunAt)
        .sort((a, b) => (a.nextRunAt! < b.nextRunAt! ? -1 : 1))
        .slice(0, 8)
        .map((s) => ({
          scheduleId: s.id,
          clientCode: s.clientCode ?? '',
          clientName: db.clients.find((c) => c.id === s.clientId)?.clientName ?? '',
          accountNumber: s.accountNumber ?? 'All accounts',
          frequency: s.description ?? s.frequency,
          nextRunAt: s.nextRunAt!,
        })),
      recentActivity: db.audit.slice(0, 10),
    };
  },

  // --- clients ---------------------------------------------------------------
  async listClients(params: { search?: string; page?: number; size?: number }): Promise<Page<Client>> {
    await delay();
    const { search, page = 0, size = 25 } = params;
    const filtered = search
      ? db.clients.filter(
          (c) =>
            c.clientName.toLowerCase().includes(search.toLowerCase()) ||
            c.clientCode.toLowerCase().includes(search.toLowerCase())
        )
      : db.clients;
    return paginate(filtered, page, size);
  },

  async getClient(id: number): Promise<Client> {
    await delay(80);
    const client = db.clients.find((c) => c.id === id);
    if (!client) throw new Error('Client not found');
    return client;
  },

  async createClient(dto: Partial<Client>, reason?: string) {
    await delay();
    return queue({
      entityType: 'CLIENT',
      entityLabel: dto.clientCode ?? 'new client',
      operation: 'CREATE',
      payload: dto,
      reason,
    });
  },

  async updateClient(id: number, dto: Partial<Client>, reason?: string) {
    await delay();
    const existing = db.clients.find((c) => c.id === id)!;
    return queue({
      entityType: 'CLIENT',
      entityId: id,
      entityLabel: existing.clientCode,
      operation: 'UPDATE',
      payload: dto,
      current: existing,
      reason,
    });
  },

  async changeClientStatus(id: number, status: string, reason?: string) {
    await delay();
    const existing = db.clients.find((c) => c.id === id)!;
    return queue({
      entityType: 'CLIENT',
      entityId: id,
      entityLabel: existing.clientCode,
      operation: 'UPDATE',
      payload: { clientCode: existing.clientCode, status },
      current: { clientCode: existing.clientCode, status: existing.status },
      reason,
    });
  },

  // --- accounts --------------------------------------------------------------
  async listAccounts(clientId: number): Promise<Account[]> {
    await delay(90);
    return db.accounts.filter((a) => a.clientId === clientId);
  },

  async createAccounts(clientId: number, accounts: Partial<Account>[], reason?: string) {
    await delay();
    const client = db.clients.find((c) => c.id === clientId)!;
    return queue({
      entityType: 'ACCOUNT',
      entityLabel:
        accounts.length === 1
          ? String(accounts[0].accountNumber)
          : `${accounts.length} accounts for ${client.clientCode}`,
      operation: 'CREATE',
      payload: { clientId, accounts },
      reason,
    });
  },

  async updateAccount(id: number, dto: Partial<Account>, reason?: string) {
    await delay();
    const existing = db.accounts.find((a) => a.id === id)!;
    return queue({
      entityType: 'ACCOUNT',
      entityId: id,
      entityLabel: existing.accountNumber,
      operation: 'UPDATE',
      payload: dto,
      current: existing,
      reason,
    });
  },

  async changeAccountStatus(id: number, status: string, reason?: string) {
    await delay();
    const existing = db.accounts.find((a) => a.id === id)!;
    return queue({
      entityType: 'ACCOUNT',
      entityId: id,
      entityLabel: existing.accountNumber,
      operation: 'UPDATE',
      payload: { accountNumber: existing.accountNumber, status },
      current: { accountNumber: existing.accountNumber, status: existing.status },
      reason,
    });
  },

  // --- schedules -------------------------------------------------------------
  async listSchedules(clientId: number): Promise<DeliverySchedule[]> {
    await delay(90);
    return db.schedules.filter((s) => s.clientId === clientId);
  },

  async createSchedule(dto: Partial<DeliverySchedule>, reason?: string) {
    await delay();
    return queue({
      entityType: 'DELIVERY_SCHEDULE',
      entityLabel: `${dto.frequency} at ${dto.sendTime}`,
      operation: 'CREATE',
      payload: dto,
      reason,
    });
  },

  async updateSchedule(id: number, dto: Partial<DeliverySchedule>, reason?: string) {
    await delay();
    const existing = db.schedules.find((s) => s.id === id)!;
    return queue({
      entityType: 'DELIVERY_SCHEDULE',
      entityId: id,
      entityLabel: existing.description ?? `schedule ${id}`,
      operation: 'UPDATE',
      payload: dto,
      current: existing,
      reason,
    });
  },

  async toggleSchedule(id: number, enabled: boolean, reason?: string) {
    await delay();
    const existing = db.schedules.find((s) => s.id === id)!;
    return queue({
      entityType: 'DELIVERY_SCHEDULE',
      entityId: id,
      entityLabel: existing.description ?? `schedule ${id}`,
      operation: 'UPDATE',
      payload: { ...existing, enabled },
      current: existing,
      reason,
    });
  },

  async deleteSchedule(id: number, reason?: string) {
    await delay();
    const existing = db.schedules.find((s) => s.id === id)!;
    return queue({
      entityType: 'DELIVERY_SCHEDULE',
      entityId: id,
      entityLabel: existing.description ?? `schedule ${id}`,
      operation: 'DELETE',
      payload: existing,
      current: existing,
      reason,
    });
  },

  // --- email configuration ---------------------------------------------------
  async listEmailConfigs(clientId: number): Promise<EmailConfig[]> {
    await delay(90);
    return db.emailConfigs.filter((e) => e.clientId === clientId);
  },

  async createEmailConfig(dto: Partial<EmailConfig>, reason?: string) {
    await delay();
    return queue({
      entityType: 'EMAIL_CONFIG',
      entityLabel: dto.name ?? 'new configuration',
      operation: 'CREATE',
      payload: dto,
      reason,
    });
  },

  async updateEmailConfig(id: number, dto: Partial<EmailConfig>, reason?: string) {
    await delay();
    const existing = db.emailConfigs.find((e) => e.id === id)!;
    return queue({
      entityType: 'EMAIL_CONFIG',
      entityId: id,
      entityLabel: existing.name ?? `config ${id}`,
      operation: 'UPDATE',
      payload: dto,
      current: existing,
      reason,
    });
  },

  async deleteEmailConfig(id: number, reason?: string) {
    await delay();
    const existing = db.emailConfigs.find((e) => e.id === id)!;
    return queue({
      entityType: 'EMAIL_CONFIG',
      entityId: id,
      entityLabel: existing.name ?? `config ${id}`,
      operation: 'DELETE',
      payload: existing,
      current: existing,
      reason,
    });
  },

  // --- statements ------------------------------------------------------------
  async previewStatement(accountId: number, periodFrom: string, periodTo: string): Promise<Statement> {
    await delay(320);
    const account = db.accounts.find((a) => a.id === accountId)!;
    const client = db.clients.find((c) => c.id === account.clientId)!;
    audit(
      'STATEMENT_PREVIEW',
      'ACCOUNT',
      `Previewed MT940 for account ${account.accountNumber} covering ${periodFrom} to ${periodTo}`,
      String(accountId)
    );
    const statement = buildStatement(account, client, periodFrom, periodTo);
    return { ...statement, id: 0 };
  },

  async generateStatement(accountId: number, periodFrom: string, periodTo: string): Promise<Statement> {
    await delay(420);
    const account = db.accounts.find((a) => a.id === accountId)!;
    const client = db.clients.find((c) => c.id === account.clientId)!;
    const statement = buildStatement(account, client, periodFrom, periodTo);
    account.lastStatementNumber = statement.statementNumber;
    db.statements.unshift(statement);
    audit(
      'STATEMENT_GENERATE',
      'STATEMENT',
      `Generated MT940 ${statement.statementReference} for account ${account.accountNumber} (${statement.transactionCount} transactions)`,
      String(statement.id)
    );
    return statement;
  },

  async listStatements(params: { clientId?: number; accountId?: number }): Promise<Statement[]> {
    await delay();
    let items = [...db.statements];
    if (params.accountId) items = items.filter((s) => s.accountId === params.accountId);
    if (params.clientId) items = items.filter((s) => s.clientId === params.clientId);
    return items;
  },

  async getStatement(id: number): Promise<Statement> {
    await delay(80);
    return db.statements.find((s) => s.id === id)!;
  },

  async requestSend(payload: unknown, reason?: string) {
    await delay();
    return queue({
      entityType: 'STATEMENT',
      entityLabel: 'manual statement delivery',
      operation: 'SEND',
      payload,
      reason,
    });
  },

  // --- approvals -------------------------------------------------------------
  async listApprovals(status?: string): Promise<ApprovalRequest[]> {
    await delay();
    return status ? db.approvals.filter((a) => a.status === status) : db.approvals;
  },

  async getApproval(id: number): Promise<ApprovalRequest> {
    await delay(60);
    return db.approvals.find((a) => a.id === id)!;
  },

  async approve(id: number, note?: string): Promise<ApprovalRequest> {
    await delay(260);
    const request = db.approvals.find((a) => a.id === id)!;
    if (request.status !== 'PENDING') throw new Error('Request is already ' + request.status.toLowerCase());
    if (request.requestedBy === currentUser.username) {
      throw new Error('A maker cannot approve their own request. Ask another checker to review it.');
    }
    applyApproval(request);
    request.status = 'APPROVED';
    request.reviewedBy = currentUser.username;
    request.reviewedAt = new Date().toISOString();
    request.reviewNote = note;
    audit('APPROVAL_APPROVE', 'APPROVAL_REQUEST', `Approved ${request.operation} of ${request.entityLabel}`, String(id));
    return request;
  },

  async reject(id: number, note?: string): Promise<ApprovalRequest> {
    await delay(200);
    const request = db.approvals.find((a) => a.id === id)!;
    request.status = 'REJECTED';
    request.reviewedBy = currentUser.username;
    request.reviewedAt = new Date().toISOString();
    request.reviewNote = note;
    audit('APPROVAL_REJECT', 'APPROVAL_REQUEST', `Rejected ${request.operation} of ${request.entityLabel}`, String(id));
    return request;
  },

  async cancel(id: number, note?: string): Promise<ApprovalRequest> {
    await delay(200);
    const request = db.approvals.find((a) => a.id === id)!;
    request.status = 'CANCELLED';
    request.reviewedBy = currentUser.username;
    request.reviewedAt = new Date().toISOString();
    request.reviewNote = note;
    audit('APPROVAL_CANCEL', 'APPROVAL_REQUEST', `Cancelled request ${id}`, String(id));
    return request;
  },

  // --- audit & deliveries -----------------------------------------------------
  async listAudit(): Promise<AuditLog[]> {
    await delay();
    return db.audit;
  },

  async listDeliveries(): Promise<DeliveryLog[]> {
    await delay();
    return db.deliveries;
  },
};

// ---------------------------------------------------------------------------
// Applies an approved request to the in-memory tables.
// ---------------------------------------------------------------------------

function applyApproval(request: ApprovalRequest) {
  const payload = request.payloadJson ? JSON.parse(request.payloadJson) : null;
  if (!payload) return;

  switch (request.entityType) {
    case 'CLIENT': {
      if (request.operation === 'CREATE') {
        const client: Client = {
          id: nextId(),
          clientCode: (payload.clientCode ?? 'NEW').toUpperCase(),
          clientName: payload.clientName ?? 'New client',
          primaryEmail: payload.primaryEmail,
          contactPerson: payload.contactPerson,
          phone: payload.phone,
          address: payload.address,
          defaultCurrency: payload.defaultCurrency ?? 'ETB',
          timezone: payload.timezone ?? 'Africa/Addis_Ababa',
          status: payload.status ?? 'ACTIVE',
          emit13d: payload.emit13d ?? false,
          emit64: payload.emit64 ?? false,
          emit65: payload.emit65 ?? false,
          emit90d: payload.emit90d ?? false,
          blankLineBetweenTags: payload.blankLineBetweenTags ?? false,
          statementNumberSeed: payload.statementNumberSeed ?? 0,
          remarks: payload.remarks,
          accountCount: 0,
          createdAt: new Date().toISOString(),
          createdBy: currentUser.username,
        };
        db.clients.push(client);
      } else {
        const client = db.clients.find((c) => c.id === request.entityId);
        if (client) Object.assign(client, payload, { updatedAt: new Date().toISOString() });
      }
      break;
    }

    case 'ACCOUNT': {
      if (request.operation === 'CREATE') {
        const client = db.clients.find((c) => c.id === payload.clientId)!;
        for (const dto of payload.accounts ?? []) {
          db.accounts.push({
            id: nextId(),
            clientId: client.id,
            clientCode: client.clientCode,
            accountNumber: String(dto.accountNumber).toUpperCase(),
            accountName: dto.accountName ?? client.clientName,
            currency: dto.currency ?? client.defaultCurrency,
            branchCode: dto.branchCode,
            status: dto.status ?? 'ACTIVE',
            statementNumberSeed: 0,
            lastStatementNumber: 0,
            statementCount: 0,
            createdAt: new Date().toISOString(),
            createdBy: currentUser.username,
          });
        }
        client.accountCount = db.accounts.filter((a) => a.clientId === client.id).length;
      } else {
        const account = db.accounts.find((a) => a.id === request.entityId);
        if (account) Object.assign(account, payload, { updatedAt: new Date().toISOString() });
      }
      break;
    }

    case 'DELIVERY_SCHEDULE': {
      if (request.operation === 'CREATE') {
        db.schedules.push({
          id: nextId(),
          clientId: payload.clientId,
          clientCode: db.clients.find((c) => c.id === payload.clientId)?.clientCode,
          accountId: payload.accountId ?? null,
          name: payload.name,
          frequency: payload.frequency,
          dayOfWeek: payload.dayOfWeek ?? null,
          dayOfMonth: payload.dayOfMonth ?? null,
          sendTime: payload.sendTime ?? '07:00',
          timezone: payload.timezone ?? 'Africa/Addis_Ababa',
          periodStrategy: payload.periodStrategy ?? 'PREVIOUS_PERIOD',
          enabled: payload.enabled ?? true,
          includeZeroTransactionStatements: payload.includeZeroTransactionStatements ?? false,
          createdAt: new Date().toISOString(),
          createdBy: currentUser.username,
          description: describeSchedule(payload),
          nextRunAt: new Date(Date.now() + 86400000).toISOString(),
        });
      } else if (request.operation === 'DELETE') {
        db.schedules = db.schedules.filter((s) => s.id !== request.entityId);
      } else {
        const schedule = db.schedules.find((s) => s.id === request.entityId);
        if (schedule) {
          Object.assign(schedule, payload, { updatedAt: new Date().toISOString() });
          schedule.description = describeSchedule(schedule);
        }
      }
      break;
    }

    case 'EMAIL_CONFIG': {
      if (request.operation === 'CREATE') {
        db.emailConfigs.push({
          id: nextId(),
          clientId: payload.clientId ?? null,
          name: payload.name,
          toAddresses: payload.toAddresses,
          ccAddresses: payload.ccAddresses,
          bccAddresses: payload.bccAddresses,
          subjectTemplate: payload.subjectTemplate,
          bodyTemplate: payload.bodyTemplate,
          enabled: payload.enabled ?? true,
          isDefault: payload.isDefault ?? false,
          createdAt: new Date().toISOString(),
          createdBy: currentUser.username,
        });
      } else if (request.operation === 'DELETE') {
        db.emailConfigs = db.emailConfigs.filter((e) => e.id !== request.entityId);
      } else {
        const config = db.emailConfigs.find((e) => e.id === request.entityId);
        if (config) Object.assign(config, payload, { updatedAt: new Date().toISOString() });
      }
      break;
    }

    case 'STATEMENT': {
      const account = db.accounts.find((a) => a.id === payload.accountId)!;
      const client = db.clients.find((c) => c.id === account.clientId)!;
      const statement = buildStatement(account, client, payload.periodFrom, payload.periodTo);
      statement.deliveryStatus = 'SENT';
      db.statements.unshift(statement);
      db.deliveries.unshift({
        id: nextId(),
        statementId: statement.id,
        statementReference: statement.statementReference,
        clientId: client.id,
        clientCode: client.clientCode,
        accountId: account.id,
        accountNumber: account.accountNumber,
        recipient: (payload.recipients ?? []).join(', ') || client.primaryEmail || 'configured recipients',
        subject: `MT940 Statement - Account ${account.accountNumber} - ${payload.periodFrom} to ${payload.periodTo}`,
        status: 'SENT',
        attempt: 1,
        provider: 'MOCK',
        providerResponse: '{"delivered":true,"provider":"mock"}',
        sentBy: currentUser.username,
        sentAt: new Date().toISOString(),
        correlationId: crypto.randomUUID(),
      });
      break;
    }
  }
}

function describeSchedule(s: Partial<DeliverySchedule>) {
  const day = ['', 'Monday', 'Tuesday', 'Wednesday', 'Thursday', 'Friday', 'Saturday', 'Sunday'];
  let text = String(s.frequency).toLowerCase();
  if (s.frequency === 'WEEKLY' && s.dayOfWeek) text += ` on ${day[s.dayOfWeek]}`;
  if (s.frequency === 'MONTHLY' && s.dayOfMonth) text += ` on day ${s.dayOfMonth}`;
  return `${text} at ${s.sendTime}`;
}
