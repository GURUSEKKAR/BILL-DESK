import { format, parseISO, isValid } from 'date-fns'

/**
 * Format ISO datetime → "24 Oct 2026"
 * Uses date-fns so we never construct `new Date(string)` inline in a component.
 */
export function formatDate(iso: string | undefined | null): string {
  if (!iso) return '—'
  const d = parseISO(iso)
  return isValid(d) ? format(d, 'd MMM yyyy') : '—'
}

/** Format ISO datetime → "24 Oct 2026, 10:30 AM" */
export function formatDateTime(iso: string | undefined | null): string {
  if (!iso) return '—'
  const d = parseISO(iso)
  return isValid(d) ? format(d, 'd MMM yyyy, h:mm a') : '—'
}

/** Today's date as YYYY-MM-DD for <input type="date"> */
export function today(): string {
  return format(new Date(), 'yyyy-MM-dd')
}

/** N days from today as YYYY-MM-DD */
export function daysFromNow(n: number): string {
  const d = new Date()
  d.setDate(d.getDate() + n)
  return format(d, 'yyyy-MM-dd')
}
