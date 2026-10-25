import { useQuery } from '@tanstack/react-query'
import { Link } from 'react-router-dom'
import {
  TrendingUp, AlertCircle, CheckCircle, Clock,
  ChevronRight, FileText, Plus, Store, Users2,
} from 'lucide-react'
import {
  BarChart, Bar, XAxis, YAxis, Tooltip, ResponsiveContainer, CartesianGrid,
} from 'recharts'
import { dashboardApi, invoicesApi } from '../api'
import { MoneyText } from '../components/MoneyText'
import { Card, CardHeader, CardBody } from '../components/Card'
import { Badge } from '../components/Badge'
import { PageSpinner } from '../components/Spinner'
import { EmptyState } from '../components/EmptyState'
import { formatDate } from '../lib/dates'
import { formatINR } from '../lib/money'
import { auth } from '../lib/auth'

export default function Dashboard() {
  const isOwner = auth.isOwner()

  const { data: summary, isLoading: loadingSum } = useQuery({
    queryKey: ['dashboard'],
    queryFn: () => dashboardApi.summary().then(r => r.data),
  })

  // Owner-only per-counter breakdown (boxes).
  const { data: byCounter } = useQuery({
    queryKey: ['dashboard', 'by-counter'],
    queryFn: () => dashboardApi.byCounter().then(r => r.data),
    enabled: isOwner,
  })

  const { data: recentPage } = useQuery({
    queryKey: ['invoices', 'recent'],
    queryFn: () => invoicesApi.list({ limit: 5 }).then(r => r.data),
  })

  const { data: overduePage } = useQuery({
    queryKey: ['invoices', 'overdue'],
    queryFn: () => invoicesApi.list({ status: 'OVERDUE', limit: 5 }).then(r => r.data),
  })

  if (loadingSum) return <PageSpinner />
  if (!summary) return null

  const stats = [
    {
      label: 'Outstanding',
      value: summary.totalOutstandingPaise,
      sub: `${summary.invoiceCount} unpaid`,
      icon: TrendingUp,
      color: 'text-blue-600', bg: 'bg-blue-50',
    },
    {
      label: 'Overdue',
      value: summary.totalOverduePaise,
      sub: `${summary.overdueCount} invoice${summary.overdueCount !== 1 ? 's' : ''}`,
      icon: AlertCircle,
      color: 'text-red-600', bg: 'bg-red-50',
    },
    {
      label: 'Collected',
      value: summary.totalPaidPaise,
      sub: 'total paid',
      icon: CheckCircle,
      color: 'text-emerald-600', bg: 'bg-emerald-50',
    },
  ]

  // Chart data: top 8 customers by owed amount
  const chartData = summary.outstandingPerCustomer
    .slice(0, 8)
    .map(c => ({ name: c.customerName.split(' ')[0], paise: c.owedPaise }))

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <h1 className="text-2xl font-bold text-gray-900">Dashboard</h1>
        <Link to="/invoices/new" className="inline-flex items-center gap-2 px-4 py-2 bg-blue-600 text-white rounded-lg text-sm font-medium hover:bg-blue-700 transition-colors">
          <Plus className="w-4 h-4" /> New Invoice
        </Link>
      </div>

      {/* Stat cards */}
      <div className="grid grid-cols-1 sm:grid-cols-3 gap-4">
        {stats.map(s => (
          <Card key={s.label} className="flex items-start gap-4 p-5">
            <div className={`w-10 h-10 ${s.bg} rounded-xl flex items-center justify-center shrink-0`}>
              <s.icon className={`w-5 h-5 ${s.color}`} />
            </div>
            <div>
              <p className="text-xs text-gray-500">{s.label}</p>
              <MoneyText paise={s.value} size="xl" className="text-gray-900" />
              <p className="text-xs text-gray-400 mt-0.5">{s.sub}</p>
            </div>
          </Card>
        ))}
      </div>

      {/* Per-counter boxes (owner only) */}
      {isOwner && byCounter && byCounter.counters.length > 0 && (
        <div>
          <div className="flex items-center gap-2 mb-3">
            <Store className="w-4 h-4 text-gray-400" />
            <h2 className="font-semibold text-gray-900 text-sm">By Counter (today)</h2>
          </div>
          <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-4">
            {byCounter.counters.map(c => (
              <Card key={c.counterId} className="p-5 space-y-3">
                <div className="flex items-center justify-between">
                  <div>
                    <p className="font-mono font-bold text-gray-900">{c.counterCode}</p>
                    <p className="text-xs text-gray-400">{c.counterName}</p>
                  </div>
                  <span className="inline-flex items-center gap-1 text-xs text-gray-500">
                    <Users2 className="w-3.5 h-3.5" /> {c.customerCount} customer{c.customerCount !== 1 ? 's' : ''}
                  </span>
                </div>
                <div className="grid grid-cols-2 gap-3 pt-1">
                  <div>
                    <p className="text-[11px] text-gray-400">Sales today</p>
                    <MoneyText paise={c.salesTodayPaise} className="text-gray-900 font-semibold" />
                  </div>
                  <div>
                    <p className="text-[11px] text-gray-400">Collected today</p>
                    <MoneyText paise={c.collectedTodayPaise} className="text-emerald-600 font-semibold" />
                  </div>
                  <div>
                    <p className="text-[11px] text-gray-400">Outstanding</p>
                    <MoneyText paise={c.outstandingPaise} className="text-blue-600 font-semibold" />
                  </div>
                  <div>
                    <p className="text-[11px] text-gray-400">Overdue</p>
                    <MoneyText paise={c.overduePaise} className="text-red-600 font-semibold" />
                  </div>
                </div>
              </Card>
            ))}
          </div>
        </div>
      )}

      {/* Chart + Top customers */}
      <div className="grid grid-cols-1 lg:grid-cols-2 gap-5">
        {/* Bar chart */}
        <Card>
          <CardHeader>
            <h2 className="font-semibold text-gray-900 text-sm">Outstanding by Customer</h2>
          </CardHeader>
          <CardBody>
            {chartData.length === 0 ? (
              <EmptyState icon={Clock} title="No outstanding invoices" />
            ) : (
              <ResponsiveContainer width="100%" height={200}>
                <BarChart data={chartData} barSize={24}>
                  <CartesianGrid strokeDasharray="3 3" stroke="#f1f5f9" />
                  <XAxis dataKey="name" tick={{ fontSize: 11 }} />
                  <YAxis tickFormatter={v => `₹${(v / 100).toLocaleString('en-IN', { notation: 'compact' })}`} tick={{ fontSize: 11 }} />
                  <Tooltip formatter={(v: number) => formatINR(v)} labelStyle={{ fontWeight: 600 }} />
                  <Bar dataKey="paise" fill="#3b82f6" radius={[4, 4, 0, 0]} name="Owed" />
                </BarChart>
              </ResponsiveContainer>
            )}
          </CardBody>
        </Card>

        {/* Overdue list */}
        <Card>
          <CardHeader className="flex items-center justify-between">
            <h2 className="font-semibold text-gray-900 text-sm">Overdue</h2>
            <Link to="/invoices?status=OVERDUE" className="text-xs text-blue-600 hover:underline flex items-center gap-0.5">
              View all <ChevronRight className="w-3 h-3" />
            </Link>
          </CardHeader>
          {overduePage?.items.length === 0 ? (
            <EmptyState icon={CheckCircle} title="No overdue invoices" />
          ) : (
            <div className="divide-y divide-gray-50">
              {overduePage?.items.map(inv => (
                <Link
                  key={inv.id}
                  to={`/invoices/${inv.id}`}
                  className="flex items-center justify-between px-5 py-3 hover:bg-gray-50 transition-colors"
                >
                  <div>
                    <p className="text-sm font-medium text-gray-900">{inv.number}</p>
                    <p className="text-xs text-red-500">Due {formatDate(inv.dueDate)}</p>
                  </div>
                  <MoneyText paise={inv.totalPaise} size="sm" className="text-red-600" />
                </Link>
              ))}
            </div>
          )}
        </Card>
      </div>

      {/* Recent invoices */}
      <Card>
        <CardHeader className="flex items-center justify-between">
          <h2 className="font-semibold text-gray-900 text-sm">Recent Invoices</h2>
          <Link to="/invoices" className="text-xs text-blue-600 hover:underline flex items-center gap-0.5">
            View all <ChevronRight className="w-3 h-3" />
          </Link>
        </CardHeader>
        {recentPage?.items.length === 0 ? (
          <EmptyState
            icon={FileText}
            title="No invoices yet"
            action={<Link to="/invoices/new" className="inline-flex items-center gap-2 px-4 py-2 bg-blue-600 text-white rounded-lg text-sm font-medium hover:bg-blue-700"><Plus className="w-4 h-4" /> Create Invoice</Link>}
          />
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full text-sm">
              <thead className="bg-gray-50 border-b border-gray-100">
                <tr>
                  {['Number', 'Status', 'Due', 'Amount'].map(h => (
                    <th key={h} className="text-left px-5 py-2.5 text-xs font-medium text-gray-500 uppercase">{h}</th>
                  ))}
                </tr>
              </thead>
              <tbody className="divide-y divide-gray-50">
                {recentPage?.items.map(inv => (
                  <tr key={inv.id} className="hover:bg-gray-50">
                    <td className="px-5 py-3">
                      <Link to={`/invoices/${inv.id}`} className="font-medium text-blue-600 hover:underline">{inv.number}</Link>
                    </td>
                    <td className="px-5 py-3"><Badge status={inv.status} /></td>
                    <td className="px-5 py-3 text-gray-500">{formatDate(inv.dueDate)}</td>
                    <td className="px-5 py-3"><MoneyText paise={inv.totalPaise} /></td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </Card>
    </div>
  )
}
