import { clsx, type ClassValue } from 'clsx'

export function cn(...inputs: ClassValue[]) {
  return clsx(inputs)
}

export function apiError(err: unknown): string {
  if (err && typeof err === 'object') {
    const e = err as { response?: { data?: { message?: string; error?: string } } }
    return e.response?.data?.message
      ?? e.response?.data?.error
      ?? 'Something went wrong'
  }
  return 'Something went wrong'
}

export const STATUS_COLORS: Record<string, string> = {
  DRAFT:     'bg-slate-100 text-slate-700',
  SENT:      'bg-blue-100 text-blue-700',
  PAID:      'bg-emerald-100 text-emerald-700',
  OVERDUE:   'bg-red-100 text-red-700',
  CANCELLED: 'bg-gray-100 text-gray-500',
  // Job/reminder statuses
  PENDING:   'bg-amber-100 text-amber-700',
  SENDING:   'bg-purple-100 text-purple-700',
  DONE:      'bg-emerald-100 text-emerald-700',
  FAILED:    'bg-red-100 text-red-700',
  SENT_R:    'bg-emerald-100 text-emerald-700',
}
