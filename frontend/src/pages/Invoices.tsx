import { Link, useSearchParams } from 'react-router-dom'
import { useInfiniteQuery } from '@tanstack/react-query'
import { Plus, FileText, ChevronDown } from 'lucide-react'
import { invoicesApi } from '../api'
import type { InvoiceStatus } from '../api/types'
import { Badge } from '../components/Badge'
import { Card } from '../components/Card'
import { EmptyState } from '../components/EmptyState'
import { PageSpinner, Spinner } from '../components/Spinner'
import { MoneyText } from '../components/MoneyText'
import { formatDate } from '../lib/dates'

const STATUSES: Array<{ value: InvoiceStatus | ''; label: string }> = [
  { value: '', label: 'All' },
  { value: 'DRAFT', label: 'Draft' },
  { value: 'SENT', label: 'Sent' },
  { value: 'OVERDUE', label: 'Overdue' },
  { value: 'PAID', label: 'Paid' },
  { value: 'CANCELLED', label: 'Cancelled' },
]

const PAGE_SIZE = 20

export default function Invoices() {
  const [searchParams, setSearchParams] = useSearchParams()
  const statusFilter = (searchParams.get('status') ?? '') as InvoiceStatus | ''
  const customerFilter = searchParams.get('customerId') ?? undefined

  const { data, isLoading, fetchNextPage, hasNextPage, isFetchingNextPage } =
    useInfiniteQuery({
      queryKey: ['invoices', statusFilter, customerFilter],
      queryFn: ({ pageParam }) =>
        invoicesApi.list({
          status: statusFilter || undefined,
          customerId: customerFilter,
          cursor: pageParam as string | undefined,
          limit: PAGE_SIZE,
        }).then(r => r.data),
      initialPageParam: undefined as string | undefined,
      // nextCursor is the opaque base64 string from the backend — treat it as-is
      getNextPageParam: (last) => last.nextCursor ?? undefined,
    })

  function setStatus(s: string) {
    const p = new URLSearchParams(searchParams)
    if (s) p.set('status', s); else p.delete('status')
    setSearchParams(p)
  }

  const allInvoices = data?.pages.flatMap(p => p.items) ?? []

  if (isLoading) return <PageSpinner />

  return (
    <div className="space-y-5">
      <div className="flex items-center justify-between">
        <h1 className="text-2xl font-bold text-gray-900">Invoices</h1>
        <Link
          to="/invoices/new"
          className="inline-flex items-center gap-2 px-4 py-2 bg-blue-600 text-white rounded-lg text-sm font-medium hover:bg-blue-700 transition-colors"
        >
          <Plus className="w-4 h-4" /> New Invoice
        </Link>
      </div>

      {/* Status filters */}
      <div className="flex flex-wrap gap-1.5">
        {STATUSES.map(s => (
          <button
            key={s.value || 'ALL'}
            onClick={() => setStatus(s.value)}
            className={`px-3 py-1 rounded-full text-xs font-medium border transition-colors ${
              statusFilter === s.value
                ? 'bg-blue-600 text-white border-blue-600'
                : 'bg-white text-gray-600 border-gray-200 hover:border-blue-300 hover:text-blue-600'
            }`}
          >
            {s.label}
          </button>
        ))}
        {customerFilter && (
          <button
            onClick={() => { const p = new URLSearchParams(searchParams); p.delete('customerId'); setSearchParams(p) }}
            className="px-3 py-1 rounded-full text-xs font-medium bg-amber-100 text-amber-700 border border-amber-200 hover:bg-amber-200"
          >
            Customer filter ×
          </button>
        )}
      </div>

      {/* Table */}
      <Card className="overflow-hidden">
        {allInvoices.length === 0 ? (
          <EmptyState
            icon={FileText}
            title="No invoices found"
            description={statusFilter ? `No ${statusFilter.toLowerCase()} invoices.` : 'Create your first invoice to get started.'}
            action={
              <Link to="/invoices/new" className="inline-flex items-center gap-2 px-4 py-2 bg-blue-600 text-white rounded-lg text-sm font-medium hover:bg-blue-700">
                <Plus className="w-4 h-4" /> New Invoice
              </Link>
            }
          />
        ) : (
          <>
            <table className="w-full text-sm">
              <thead className="bg-gray-50 border-b border-gray-100">
                <tr>
                  {['Number', 'Status', 'Due Date', 'Amount', ''].map(h => (
                    <th key={h} className="text-left px-5 py-2.5 text-xs font-medium text-gray-500 uppercase">{h}</th>
                  ))}
                </tr>
              </thead>
              <tbody className="divide-y divide-gray-50">
                {allInvoices.map(inv => (
                  <tr key={inv.id} className="hover:bg-gray-50">
                    <td className="px-5 py-3 font-medium text-gray-900">{inv.number}</td>
                    <td className="px-5 py-3"><Badge status={inv.status} /></td>
                    <td className={`px-5 py-3 text-sm ${inv.status === 'OVERDUE' ? 'text-red-600 font-medium' : 'text-gray-500'}`}>
                      {formatDate(inv.dueDate)}
                    </td>
                    <td className="px-5 py-3">
                      <MoneyText paise={inv.totalPaise} />
                    </td>
                    <td className="px-5 py-3 text-right">
                      <Link to={`/invoices/${inv.id}`} className="text-xs text-blue-600 hover:underline">
                        View →
                      </Link>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>

            {/* Load more */}
            {hasNextPage && (
              <div className="flex justify-center py-4 border-t border-gray-50">
                <button
                  onClick={() => fetchNextPage()}
                  disabled={isFetchingNextPage}
                  className="inline-flex items-center gap-2 px-4 py-2 text-sm text-gray-600 hover:text-gray-900 border border-gray-200 rounded-lg hover:bg-gray-50 disabled:opacity-50"
                >
                  {isFetchingNextPage ? <Spinner size="sm" /> : <ChevronDown className="w-4 h-4" />}
                  {isFetchingNextPage ? 'Loading…' : 'Load more'}
                </button>
              </div>
            )}
          </>
        )}
      </Card>
    </div>
  )
}
