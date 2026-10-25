import { useState, useRef } from 'react'
import { useParams, useNavigate } from 'react-router-dom'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import {
  ArrowLeft, Send, X, CreditCard, Link as LinkIcon,
  Clock, CheckCircle2, XCircle, AlertCircle,
} from 'lucide-react'
import toast from 'react-hot-toast'
import { invoicesApi, customersApi } from '../api'
import type { ReminderType } from '../api/types'
import { Badge } from '../components/Badge'
import { Card, CardHeader, CardBody } from '../components/Card'
import { Modal } from '../components/Modal'
import { MoneyText } from '../components/MoneyText'
import { PageSpinner } from '../components/Spinner'
import { formatDate, formatDateTime } from '../lib/dates'
import { apiError } from '../lib/utils'

const REMINDER_LABELS: Record<ReminderType, string> = {
  PRE_DUE:  '3 days before due',
  ON_DUE:   'On due date',
  POST_3:   '3 days after',
  POST_7:   '7 days after',
  POST_14:  '14 days after',
}

const PAYMENT_METHODS = [
  'BANK_TRANSFER', 'CASH', 'CHEQUE', 'UPI', 'ONLINE',
]

export default function InvoiceDetail() {
  const { id } = useParams<{ id: string }>()
  const navigate = useNavigate()
  const qc = useQueryClient()
  const [payOpen, setPayOpen] = useState(false)
  const [payMethod, setPayMethod] = useState('BANK_TRANSFER')
  // Idempotency key: generated once when the dialog opens, not on every render
  const idempotencyKey = useRef<string>('')

  const { data: invoice, isLoading } = useQuery({
    queryKey: ['invoice', id],
    queryFn: () => invoicesApi.get(id!).then(r => r.data),
    enabled: !!id,
    // Poll while reminders are still PENDING
    refetchInterval: (query) => {
      const inv = query.state.data
      if (!inv) return false
      return inv.status === 'SENT' ? 5000 : false
    },
  })

  const { data: customer } = useQuery({
    queryKey: ['customer', invoice?.customerId],
    queryFn: () => customersApi.get(invoice!.customerId).then(r => r.data),
    enabled: !!invoice?.customerId,
  })

  const { data: reminders } = useQuery({
    queryKey: ['reminders', id],
    queryFn: () => invoicesApi.reminders(id!).then(r => r.data),
    enabled: !!id && invoice?.status !== 'DRAFT',
    // Poll while reminders are still PENDING or SENDING
    refetchInterval: (query) => {
      const rems = query.state.data
      if (!rems) return false
      const active = rems.some(r => r.status === 'PENDING' || r.status === 'SENDING')
      return active ? 5000 : false
    },
  })

  const invalidate = () => {
    qc.invalidateQueries({ queryKey: ['invoice', id] })
    qc.invalidateQueries({ queryKey: ['invoices'] })
    qc.invalidateQueries({ queryKey: ['dashboard'] })
    qc.invalidateQueries({ queryKey: ['reminders', id] })
  }

  const sendMut = useMutation({
    mutationFn: () => invoicesApi.send(id!),
    onSuccess: () => { toast.success('Invoice sent — email queued'); invalidate() },
    onError: (err) => toast.error(apiError(err)),
  })

  const cancelMut = useMutation({
    mutationFn: () => invoicesApi.cancel(id!),
    onSuccess: () => { toast.success('Invoice cancelled'); invalidate() },
    onError: (err) => toast.error(apiError(err)),
  })

  const payMut = useMutation({
    mutationFn: () => invoicesApi.pay(id!, invoice!.totalPaise, payMethod, idempotencyKey.current),
    onSuccess: () => {
      toast.success('Payment recorded — reminders cancelled')
      setPayOpen(false)
      invalidate()
    },
    onError: (err) => toast.error(apiError(err)),
  })

  async function copyPayLink() {
    try {
      const { data } = await invoicesApi.createPaymentLink(id!)
      const url = `${window.location.origin}/pay/${data.token}`
      await navigator.clipboard.writeText(url)
      toast.success('Payment link copied to clipboard')
    } catch (err) {
      toast.error(apiError(err))
    }
  }

  function openPayDialog() {
    // Generate a fresh idempotency key each time the dialog opens
    idempotencyKey.current = crypto.randomUUID()
    setPayOpen(true)
  }

  if (isLoading) return <PageSpinner />
  if (!invoice) return <p className="text-gray-500">Invoice not found</p>

  const canSend   = invoice.status === 'DRAFT'
  const canCancel = ['DRAFT', 'SENT', 'OVERDUE'].includes(invoice.status)
  const canPay    = ['SENT', 'OVERDUE'].includes(invoice.status)

  return (
    <div className="max-w-3xl space-y-5">
      {/* Header */}
      <div className="flex items-center gap-3 flex-wrap">
        <button onClick={() => navigate(-1)} className="p-1.5 rounded-lg border border-gray-200 hover:bg-gray-50">
          <ArrowLeft className="w-4 h-4" />
        </button>
        <div className="flex items-center gap-2.5 flex-1">
          <h1 className="text-2xl font-bold text-gray-900">{invoice.number}</h1>
          <Badge status={invoice.status} />
        </div>
        <div className="flex flex-wrap gap-2">
          {canSend && (
            <button onClick={() => sendMut.mutate()} disabled={sendMut.isPending}
              className="inline-flex items-center gap-1.5 px-3 py-1.5 bg-blue-600 text-white rounded-lg text-sm font-medium hover:bg-blue-700 disabled:opacity-60">
              <Send className="w-3.5 h-3.5" />
              {sendMut.isPending ? 'Sending…' : 'Send'}
            </button>
          )}
          {canPay && (
            <>
              <button onClick={openPayDialog}
                className="inline-flex items-center gap-1.5 px-3 py-1.5 bg-emerald-600 text-white rounded-lg text-sm font-medium hover:bg-emerald-700">
                <CreditCard className="w-3.5 h-3.5" /> Mark Paid
              </button>
              <button onClick={copyPayLink}
                className="inline-flex items-center gap-1.5 px-3 py-1.5 border border-gray-200 text-gray-700 rounded-lg text-sm font-medium hover:bg-gray-50">
                <LinkIcon className="w-3.5 h-3.5" /> Copy Pay Link
              </button>
            </>
          )}
          {canCancel && (
            <button onClick={() => confirm('Cancel this invoice?') && cancelMut.mutate()}
              disabled={cancelMut.isPending}
              className="inline-flex items-center gap-1.5 px-3 py-1.5 border border-red-200 text-red-600 rounded-lg text-sm font-medium hover:bg-red-50">
              <X className="w-3.5 h-3.5" /> Cancel
            </button>
          )}
        </div>
      </div>

      {/* Meta */}
      <Card className="grid grid-cols-2 sm:grid-cols-4 gap-4 p-5 text-sm">
        <div>
          <p className="text-gray-400 text-xs mb-0.5">Customer</p>
          <p className="font-medium text-gray-900">{customer?.name ?? '…'}</p>
          {customer?.email && <p className="text-xs text-gray-400 truncate">{customer.email}</p>}
        </div>
        <div>
          <p className="text-gray-400 text-xs mb-0.5">Issue Date</p>
          <p className="font-medium">{formatDate(invoice.issueDate)}</p>
        </div>
        <div>
          <p className="text-gray-400 text-xs mb-0.5">Due Date</p>
          <p className={`font-medium ${invoice.status === 'OVERDUE' ? 'text-red-600' : ''}`}>
            {formatDate(invoice.dueDate)}
          </p>
        </div>
        <div>
          <p className="text-gray-400 text-xs mb-0.5">Total</p>
          <MoneyText paise={invoice.totalPaise} size="lg" className="text-gray-900" />
        </div>
      </Card>

      {/* Line items */}
      <Card className="overflow-hidden">
        <table className="w-full text-sm">
          <thead className="bg-gray-50 border-b border-gray-100">
            <tr>
              {['Description', 'Qty', 'Unit Price', 'Total'].map(h => (
                <th key={h} className="text-left px-5 py-2.5 text-xs font-medium text-gray-500 uppercase">{h}</th>
              ))}
            </tr>
          </thead>
          <tbody className="divide-y divide-gray-50">
            {(invoice.items ?? []).map(item => (
              <tr key={item.id}>
                <td className="px-5 py-3 text-gray-900">{item.description}</td>
                <td className="px-5 py-3 text-gray-600">{item.qty}</td>
                <td className="px-5 py-3 text-gray-600"><MoneyText paise={item.unitPricePaise} /></td>
                <td className="px-5 py-3 font-medium"><MoneyText paise={item.lineTotalPaise} /></td>
              </tr>
            ))}
          </tbody>
          <tfoot className="border-t-2 border-gray-200 bg-gray-50">
            <tr>
              <td colSpan={3} className="px-5 py-3 text-sm font-semibold text-gray-700 text-right">Total</td>
              <td className="px-5 py-3"><MoneyText paise={invoice.totalPaise} size="lg" /></td>
            </tr>
          </tfoot>
        </table>
      </Card>

      {/* Reminder timeline (Phase 2) */}
      {reminders && reminders.length > 0 && (
        <Card>
          <CardHeader>
            <h2 className="font-semibold text-gray-900 text-sm">Reminder Timeline</h2>
          </CardHeader>
          <CardBody>
            <div className="space-y-3">
              {reminders.map(r => {
                const Icon = r.status === 'SENT'
                  ? CheckCircle2
                  : r.status === 'CANCELLED' || r.status === 'FAILED'
                    ? XCircle
                    : r.status === 'SENDING'
                      ? AlertCircle
                      : Clock

                const color = r.status === 'SENT'
                  ? 'text-emerald-500'
                  : r.status === 'CANCELLED'
                    ? 'text-gray-400'
                    : r.status === 'FAILED'
                      ? 'text-red-500'
                      : r.status === 'SENDING'
                        ? 'text-purple-500 animate-pulse'
                        : 'text-amber-500'

                return (
                  <div key={r.id} className="flex items-start gap-3">
                    <Icon className={`w-4 h-4 mt-0.5 shrink-0 ${color}`} />
                    <div className="flex-1 min-w-0">
                      <div className="flex items-center gap-2">
                        <span className="text-sm font-medium text-gray-900">
                          {REMINDER_LABELS[r.type]}
                        </span>
                        <Badge status={r.status} />
                      </div>
                      <p className="text-xs text-gray-400 mt-0.5">
                        {r.status === 'SENT'
                          ? `Sent ${formatDateTime(r.sentAt)}`
                          : `Scheduled ${formatDateTime(r.scheduledAt)}`}
                      </p>
                      {r.lastError && (
                        <p className="text-xs text-red-500 mt-0.5 truncate">{r.lastError}</p>
                      )}
                    </div>
                  </div>
                )
              })}
            </div>
          </CardBody>
        </Card>
      )}

      {/* Pay modal */}
      <Modal open={payOpen} onClose={() => setPayOpen(false)} title="Record Payment">
        <div className="space-y-4">
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1">Amount</label>
            <p className="text-2xl font-bold text-gray-900"><MoneyText paise={invoice.totalPaise} /></p>
          </div>
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1">Payment method</label>
            <select
              value={payMethod}
              onChange={e => setPayMethod(e.target.value)}
              className="w-full px-3 py-2 border border-gray-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
            >
              {PAYMENT_METHODS.map(m => <option key={m} value={m}>{m.replace('_', ' ')}</option>)}
            </select>
          </div>
          <p className="text-xs text-gray-400">
            This will mark the invoice as paid and cancel any pending reminders.
          </p>
          <div className="flex gap-2 pt-1">
            <button
              onClick={() => payMut.mutate()}
              disabled={payMut.isPending}
              className="flex-1 py-2.5 bg-emerald-600 text-white rounded-lg text-sm font-medium hover:bg-emerald-700 disabled:opacity-60"
            >
              {payMut.isPending ? 'Processing…' : 'Confirm Payment'}
            </button>
            <button onClick={() => setPayOpen(false)} className="px-4 py-2 border border-gray-200 rounded-lg text-sm text-gray-600 hover:bg-gray-50">
              Cancel
            </button>
          </div>
        </div>
      </Modal>
    </div>
  )
}
