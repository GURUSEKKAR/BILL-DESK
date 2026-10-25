// ─── Auth ─────────────────────────────────────────────────────────────────────
export type Role = 'OWNER' | 'CASHIER'

export interface AuthResponse {
  accessToken: string
  refreshToken: string
  businessId: string
  businessName: string
  businessLoginCode: string
  role: Role
  counterId: string | null   // null for OWNER (all counters); set for CASHIER
  counterCode: string | null  // the counter's code for a CASHIER session
}

// ─── Counters ─────────────────────────────────────────────────────────────────
export interface Counter {
  id: string
  name: string
  code: string
  active: boolean
  loginEnabled: boolean
  createdAt: string
}

// ─── Users (cashier accounts) ───────────────────────────────────────────────
export interface AppUser {
  id: string
  email: string
  role: Role
  counterId: string | null
  active: boolean
  createdAt: string
}

// ─── Customers ────────────────────────────────────────────────────────────────
export interface Customer {
  id: string
  name: string
  email?: string
  phone?: string
  creditLimitPaise?: number | null
  createdAt: string
  updatedAt?: string
}

export interface CustomerBalance {
  customerId: string
  name: string
  outstandingPaise: number
  creditLimitPaise: number | null
  availableCreditPaise: number | null
}

export interface CustomerPage {
  content: Customer[]
  totalPages: number
  totalElements: number
  number: number
  size: number
}

// ─── Invoices ─────────────────────────────────────────────────────────────────
export type InvoiceStatus = 'DRAFT' | 'SENT' | 'PAID' | 'OVERDUE' | 'CANCELLED'

export interface InvoiceItem {
  id: string
  description: string
  qty: number
  unitPricePaise: number
  lineTotalPaise: number
}

export interface Invoice {
  id: string
  number: string
  status: InvoiceStatus
  totalPaise: number
  dueDate: string          // DATE → YYYY-MM-DD string (never run through new Date)
  issueDate: string
  customerId: string
  createdAt: string
  updatedAt?: string
  pdfPath?: string
  items?: InvoiceItem[]
}

export interface PageResult<T> {
  items: T[]
  nextCursor: string | null
  hasMore: boolean
}

// ─── Payments ─────────────────────────────────────────────────────────────────
export interface Payment {
  id: string
  invoiceId: string
  amountPaise: number
  method: string
  paidAt: string
}

// ─── Dashboard ────────────────────────────────────────────────────────────────
export interface CustomerOutstanding {
  customerId: string
  customerName: string
  owedPaise: number
}

export interface DashboardSummary {
  totalOutstandingPaise: number
  totalOverduePaise: number
  totalPaidPaise: number
  invoiceCount: number
  overdueCount: number
  salesTodayPaise: number
  collectedTodayPaise: number
  creditGivenTodayPaise: number
  collectedTodayByMethod: Record<string, number>
  outstandingPerCustomer: CustomerOutstanding[]
}

export interface CounterSummary {
  counterId: string
  counterCode: string
  counterName: string
  salesTodayPaise: number
  collectedTodayPaise: number
  creditGivenTodayPaise: number
  collectedTodayByMethod: Record<string, number>
  outstandingPaise: number
  overduePaise: number
  customerCount: number
}

export interface ByCounterResponse {
  counters: CounterSummary[]
  topDebtors: CustomerOutstanding[]
}

// ─── Reminders ────────────────────────────────────────────────────────────────
export type ReminderType = 'PRE_DUE' | 'ON_DUE' | 'POST_3' | 'POST_7' | 'POST_14'
export type ReminderStatus = 'PENDING' | 'SENDING' | 'SENT' | 'CANCELLED' | 'FAILED'

export interface Reminder {
  id: string
  invoiceId: string
  type: ReminderType
  status: ReminderStatus
  scheduledAt: string
  sentAt?: string
  lastError?: string
}

// ─── Jobs ─────────────────────────────────────────────────────────────────────
export type JobStatus = 'PENDING' | 'SENDING' | 'DONE' | 'FAILED'

export interface Job {
  id: string
  type: string
  status: JobStatus
  attempts: number
  maxAttempts: number
  nextRunAt: string
  lastError?: string
  createdAt: string
  updatedAt: string
}

export interface JobStats {
  pending: number
  sending: number
  done: number
  failed: number
}

// ─── Payment Links ────────────────────────────────────────────────────────────
export interface PaymentLinkResponse {
  token: string
  url: string
}
