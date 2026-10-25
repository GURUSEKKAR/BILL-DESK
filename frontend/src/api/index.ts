import client from './client'
import type {
  AuthResponse, Customer, CustomerPage, CustomerBalance, Invoice, InvoiceStatus,
  PageResult, DashboardSummary, ByCounterResponse, Payment, PaymentLinkResponse,
  Reminder, Job, JobStats, Counter, AppUser,
} from './types'

// ── Auth ─────────────────────────────────────────────────────────────────────
export const authApi = {
  register: (email: string, password: string, businessName: string, businessCode?: string) =>
    client.post<AuthResponse>('/auth/register', { email, password, businessName, businessCode }),
  login: (email: string, password: string) =>
    client.post<AuthResponse>('/auth/login', { email, password }),
  counterLogin: (businessLoginCode: string, counterCode: string, counterPassword: string) =>
    client.post<AuthResponse>('/auth/counter-login', { businessLoginCode, counterCode, counterPassword }),
  forgotPassword: (email: string) =>
    client.post<{ message: string }>('/auth/forgot-password', { email }),
  resetPassword: (token: string, newPassword: string) =>
    client.post<{ message: string }>('/auth/reset-password', { token, newPassword }),
}

// ── Business settings (OWNER only) ───────────────────────────────────────────
export const businessApi = {
  get: () => client.get<{ id: string; name: string; loginCode: string }>('/business'),
  update: (data: { name?: string; loginCode?: string }) =>
    client.patch<{ id: string; name: string; loginCode: string }>('/business', data),
}

// ── Customers ────────────────────────────────────────────────────────────────
export const customersApi = {
  list: (page = 0, search?: string) =>
    client.get<CustomerPage>('/customers', { params: { page, size: 20, search } }),
  get: (id: string) => client.get<Customer>(`/customers/${id}`),
  balance: (id: string) => client.get<CustomerBalance>(`/customers/${id}/balance`),
  create: (data: { name: string; email?: string; phone?: string; creditLimitPaise?: number | null }) =>
    client.post<Customer>('/customers', data),
  update: (id: string, data: { name?: string; email?: string; phone?: string; creditLimitPaise?: number | null }) =>
    client.put<Customer>(`/customers/${id}`, data),
  delete: (id: string) => client.delete(`/customers/${id}`),
}

// ── Invoices ─────────────────────────────────────────────────────────────────
export const invoicesApi = {
  list: (params?: {
    status?: InvoiceStatus
    customerId?: string
    cursor?: string
    limit?: number
  }) => client.get<PageResult<Invoice>>('/invoices', { params }),

  get: (id: string) => client.get<Invoice>(`/invoices/${id}`),

  create: (data: {
    customerId: string
    dueDate: string
    counterId?: string
    items: { description: string; qty: number; unitPricePaise: number }[]
  }) => client.post<Invoice>('/invoices', data),

  send:   (id: string) => client.post<Invoice>(`/invoices/${id}/send`),
  cancel: (id: string) => client.post<Invoice>(`/invoices/${id}/cancel`),

  pay: (id: string, amountPaise: number, method: string, idempotencyKey: string) =>
    client.post<Payment>(
      `/invoices/${id}/payments`,
      { amountPaise, method },
      { headers: { 'Idempotency-Key': idempotencyKey } },
    ),

  createPaymentLink: (id: string) =>
    client.post<PaymentLinkResponse>(`/invoices/${id}/payment-link`),

  reminders: (id: string) =>
    client.get<Reminder[]>(`/invoices/${id}/reminders`),
}

// ── Counters (OWNER only) ─────────────────────────────────────────────────────
export const countersApi = {
  list: () => client.get<Counter[]>('/counters'),
  get: (id: string) => client.get<Counter>(`/counters/${id}`),
  create: (data: { name: string; code: string; password?: string }) =>
    client.post<Counter>('/counters', data),
  update: (id: string, data: { name?: string; active?: boolean; password?: string }) =>
    client.patch<Counter>(`/counters/${id}`, data),
}

// ── Users / cashiers (OWNER only) ───────────────────────────────────────────
export const usersApi = {
  list: () => client.get<AppUser[]>('/users'),
  create: (data: { email: string; password: string; counterId: string }) =>
    client.post<AppUser>('/users', data),
  update: (id: string, data: { counterId?: string; active?: boolean }) =>
    client.patch<AppUser>(`/users/${id}`, data),
}

// ── Dashboard ────────────────────────────────────────────────────────────────
export const dashboardApi = {
  summary: (counterId?: string) =>
    client.get<DashboardSummary>('/dashboard/summary', { params: counterId ? { counterId } : {} }),
  byCounter: () => client.get<ByCounterResponse>('/dashboard/by-counter'),
}

// ── Admin ────────────────────────────────────────────────────────────────────
export const adminApi = {
  jobStats:   () => client.get<JobStats>('/admin/jobs/stats'),
  failedJobs: () => client.get<Job[]>('/admin/jobs/failed'),
  replayJob:  (id: string) => client.post(`/admin/jobs/${id}/replay`),
  runOverdue: () => client.post<{ markedOverdue: number }>('/admin/run-overdue'),
}

// ── Public (no auth) ─────────────────────────────────────────────────────────
export const publicApi = {
  payViaLink: (token: string, method = 'ONLINE') =>
    client.post<Payment>(`/pay/${token}`, {}, { params: { method } }),
  getInvoiceByToken: (token: string) =>
    client.get<Invoice>(`/pay/${token}/preview`),
}
