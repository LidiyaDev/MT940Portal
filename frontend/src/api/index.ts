import axios from 'axios';
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
  UserProfile,
} from './types';
import { mock } from './mockApi';

/**
 * Single API surface for the UI. When VITE_USE_MOCK is not "false" every call
 * is served from the in-memory demonstration backend; flipping the flag points
 * the exact same functions at the Spring Boot service.
 */
export const USE_MOCK = import.meta.env.VITE_USE_MOCK !== 'false';

const http = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || '',
  headers: { 'Content-Type': 'application/json' },
});

http.interceptors.request.use((config) => {
  const token = localStorage.getItem('mt940.token');
  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});

export function setAuthToken(token: string | null) {
  if (token) localStorage.setItem('mt940.token', token);
  else localStorage.removeItem('mt940.token');
}

const unwrap = <T,>(promise: Promise<{ data: T }>): Promise<T> => promise.then((r) => r.data);

export const api = {
  getMe: (): Promise<UserProfile> =>
    USE_MOCK ? mock.getMe() : unwrap(http.get('/api/me')),

  getDashboard: (): Promise<Dashboard> =>
    USE_MOCK ? mock.getDashboard() : unwrap(http.get('/api/dashboard')),

  // --- clients ---------------------------------------------------------------
  listClients: (params: { search?: string; page?: number; size?: number } = {}): Promise<Page<Client>> =>
    USE_MOCK ? mock.listClients(params) : unwrap(http.get('/api/clients', { params })),

  getClient: (id: number): Promise<Client> =>
    USE_MOCK ? mock.getClient(id) : unwrap(http.get(`/api/clients/${id}`)),

  createClient: (dto: Partial<Client>, reason?: string): Promise<ApprovalRequest> =>
    USE_MOCK
      ? mock.createClient(dto, reason)
      : unwrap(http.post('/api/clients', dto, { params: { reason } })),

  updateClient: (id: number, dto: Partial<Client>, reason?: string): Promise<ApprovalRequest> =>
    USE_MOCK
      ? mock.updateClient(id, dto, reason)
      : unwrap(http.put(`/api/clients/${id}`, dto, { params: { reason } })),

  changeClientStatus: (id: number, status: string, reason?: string): Promise<ApprovalRequest> =>
    USE_MOCK
      ? mock.changeClientStatus(id, status, reason)
      : unwrap(http.patch(`/api/clients/${id}/status`, null, { params: { status, reason } })),

  // --- accounts --------------------------------------------------------------
  listAccounts: (clientId: number): Promise<Account[]> =>
    USE_MOCK
      ? mock.listAccounts(clientId)
      : unwrap<Page<Account>>(http.get(`/api/clients/${clientId}/accounts`, { params: { size: 200 } })).then((page) => page.content),

  createAccounts: (clientId: number, accounts: Partial<Account>[], reason?: string): Promise<ApprovalRequest> =>
    USE_MOCK
      ? mock.createAccounts(clientId, accounts, reason)
      : unwrap(
          http.post(`/api/clients/${clientId}/accounts/bulk`, { clientId, accounts, reason })
        ),

  updateAccount: (id: number, dto: Partial<Account>, reason?: string): Promise<ApprovalRequest> =>
    USE_MOCK
      ? mock.updateAccount(id, dto, reason)
      : unwrap(http.put(`/api/accounts/${id}`, dto, { params: { reason } })),

  changeAccountStatus: (id: number, status: string, reason?: string): Promise<ApprovalRequest> =>
    USE_MOCK
      ? mock.changeAccountStatus(id, status, reason)
      : unwrap(http.patch(`/api/accounts/${id}/status`, null, { params: { status, reason } })),

  // --- schedules -------------------------------------------------------------
  listSchedules: (clientId: number): Promise<DeliverySchedule[]> =>
    USE_MOCK
      ? mock.listSchedules(clientId)
      : unwrap(http.get(`/api/clients/${clientId}/schedules`)),

  createSchedule: (clientId: number, dto: Partial<DeliverySchedule>, reason?: string): Promise<ApprovalRequest> =>
    USE_MOCK
      ? mock.createSchedule({ ...dto, clientId }, reason)
      : unwrap(http.post(`/api/clients/${clientId}/schedules`, dto, { params: { reason } })),

  updateSchedule: (id: number, dto: Partial<DeliverySchedule>, reason?: string): Promise<ApprovalRequest> =>
    USE_MOCK
      ? mock.updateSchedule(id, dto, reason)
      : unwrap(http.put(`/api/schedules/${id}`, dto, { params: { reason } })),

  toggleSchedule: (id: number, enabled: boolean, reason?: string): Promise<ApprovalRequest> =>
    USE_MOCK
      ? mock.toggleSchedule(id, enabled, reason)
      : unwrap(http.patch(`/api/schedules/${id}/enabled`, null, { params: { enabled, reason } })),

  deleteSchedule: (id: number, reason?: string): Promise<ApprovalRequest> =>
    USE_MOCK
      ? mock.deleteSchedule(id, reason)
      : unwrap(http.delete(`/api/schedules/${id}`, { params: { reason } })),

  // --- email configuration ---------------------------------------------------
  listEmailConfigs: (clientId: number): Promise<EmailConfig[]> =>
    USE_MOCK
      ? mock.listEmailConfigs(clientId)
      : unwrap(http.get(`/api/clients/${clientId}/email-config`)),

  createEmailConfig: (clientId: number, dto: Partial<EmailConfig>, reason?: string): Promise<ApprovalRequest> =>
    USE_MOCK
      ? mock.createEmailConfig({ ...dto, clientId }, reason)
      : unwrap(http.post(`/api/clients/${clientId}/email-config`, dto, { params: { reason } })),

  updateEmailConfig: (id: number, dto: Partial<EmailConfig>, reason?: string): Promise<ApprovalRequest> =>
    USE_MOCK
      ? mock.updateEmailConfig(id, dto, reason)
      : unwrap(http.put(`/api/email-config/${id}`, dto, { params: { reason } })),

  deleteEmailConfig: (id: number, reason?: string): Promise<ApprovalRequest> =>
    USE_MOCK
      ? mock.deleteEmailConfig(id, reason)
      : unwrap(http.delete(`/api/email-config/${id}`, { params: { reason } })),

  // --- statements ------------------------------------------------------------
  previewStatement: (accountId: number, periodFrom: string, periodTo: string): Promise<Statement> =>
    USE_MOCK
      ? mock.previewStatement(accountId, periodFrom, periodTo)
      : unwrap(
          http.post('/api/statements/preview', null, { params: { accountId, periodFrom, periodTo } })
        ),

  generateStatement: (accountId: number, periodFrom: string, periodTo: string): Promise<Statement> =>
    USE_MOCK
      ? mock.generateStatement(accountId, periodFrom, periodTo)
      : unwrap(http.post('/api/statements/generate', { accountId, periodFrom, periodTo, persist: true })),

  listStatements: (params: { clientId?: number; accountId?: number } = {}): Promise<Statement[]> =>
    USE_MOCK
      ? mock.listStatements(params)
      : unwrap<Page<Statement>>(http.get('/api/statements', { params })).then((page) => page.content),

  getStatement: (id: number): Promise<Statement> =>
    USE_MOCK ? mock.getStatement(id) : unwrap(http.get(`/api/statements/${id}`)),

  requestSend: (payload: unknown, reason?: string): Promise<ApprovalRequest> =>
    USE_MOCK
      ? mock.requestSend(payload, reason)
      : unwrap(http.post('/api/statements/send-request', payload, { params: { reason } })),

  downloadUrl: (id: number) => `/api/statements/${id}/download`,

  // --- approvals -------------------------------------------------------------
  listApprovals: (status?: string): Promise<ApprovalRequest[]> =>
    USE_MOCK
      ? mock.listApprovals(status)
      : unwrap<Page<ApprovalRequest>>(http.get('/api/approvals', { params: { status, size: 100 } })).then((page) => page.content),

  approve: (id: number, note?: string): Promise<ApprovalRequest> =>
    USE_MOCK ? mock.approve(id, note) : unwrap(http.post(`/api/approvals/${id}/approve`, { note })),

  reject: (id: number, note?: string): Promise<ApprovalRequest> =>
    USE_MOCK ? mock.reject(id, note) : unwrap(http.post(`/api/approvals/${id}/reject`, { note })),

  cancel: (id: number, note?: string): Promise<ApprovalRequest> =>
    USE_MOCK ? mock.cancel(id, note) : unwrap(http.post(`/api/approvals/${id}/cancel`, { note })),

  // --- audit & deliveries -----------------------------------------------------
  listAudit: (): Promise<AuditLog[]> =>
    USE_MOCK
      ? mock.listAudit()
      : unwrap<Page<AuditLog>>(http.get('/api/audit', { params: { size: 200 } })).then((page) => page.content),

  listDeliveries: (): Promise<DeliveryLog[]> =>
    USE_MOCK
      ? mock.listDeliveries()
      : unwrap<Page<DeliveryLog>>(http.get('/api/deliveries', { params: { size: 200 } })).then((page) => page.content),
};
