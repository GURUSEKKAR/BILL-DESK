import { useState } from 'react'
import { useParams, Link } from 'react-router-dom'
import { Receipt, CheckCircle } from 'lucide-react'
import toast from 'react-hot-toast'
import { publicApi } from '../api'
import { MoneyText } from '../components/MoneyText'
import { PageSpinner } from '../components/Spinner'
import { formatDate } from '../lib/dates'
import { apiError } from '../lib/utils'
import { useQuery, useMutation } from '@tanstack/react-query'

const PAYMENT_METHODS = ['BANK_TRANSFER', 'CASH', 'UPI', 'ONLINE']

export default function PublicPay() {
  const { token } = useParams<{ token: string }>()
  const [method, setMethod] = useState('UPI')
  const [done, setDone] = useState(false)

  // Try to fetch invoice preview (this endpoint might not exist — graceful fallback)
  const { data: invoice, isLoading } = useQuery({
    queryKey: ['payPreview', token],
    queryFn: () => publicApi.getInvoiceByToken(token!).then(r => r.data),
    enabled: !!token,
    retry: false,
  })

  const payMut = useMutation({
    mutationFn: () => publicApi.payViaLink(token!, method),
    onSuccess: () => setDone(true),
    onError: (err) => {
      const msg = apiError(err)
      if (msg.includes('already') || msg.includes('PAID')) {
        setDone(true)
        toast.success('This invoice is already paid')
      } else {
        toast.error(msg)
      }
    },
  })

  return (
    <div className="min-h-screen bg-gray-50 flex items-center justify-center p-4">
      <div className="w-full max-w-sm">
        {/* Logo */}
        <div className="flex justify-center mb-6">
          <div className="w-11 h-11 bg-blue-600 rounded-2xl flex items-center justify-center shadow">
            <Receipt className="w-5 h-5 text-white" />
          </div>
        </div>

        <div className="bg-white rounded-2xl border border-gray-200 shadow-sm overflow-hidden">
          {done ? (
            /* Success state */
            <div className="p-8 text-center">
              <CheckCircle className="w-14 h-14 text-emerald-500 mx-auto mb-4" />
              <h2 className="text-lg font-bold text-gray-900 mb-1">Payment recorded</h2>
              <p className="text-sm text-gray-500">Thank you! The invoice has been marked as paid.</p>
            </div>
          ) : isLoading ? (
            <div className="p-8"><PageSpinner /></div>
          ) : (
            <div className="p-6 space-y-5">
              <div className="text-center">
                <h1 className="text-lg font-bold text-gray-900">Pay Invoice</h1>
                {invoice && (
                  <p className="text-sm text-gray-500 mt-1">{invoice.number}</p>
                )}
              </div>

              {invoice && (
                <div className="bg-gray-50 rounded-xl p-4 text-sm space-y-2">
                  <div className="flex justify-between">
                    <span className="text-gray-500">Amount</span>
                    <MoneyText paise={invoice.totalPaise} size="lg" className="text-gray-900" />
                  </div>
                  <div className="flex justify-between">
                    <span className="text-gray-500">Due date</span>
                    <span className="text-gray-700">{formatDate(invoice.dueDate)}</span>
                  </div>
                </div>
              )}

              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">Payment method</label>
                <select
                  value={method}
                  onChange={e => setMethod(e.target.value)}
                  className="w-full px-3 py-2 border border-gray-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
                >
                  {PAYMENT_METHODS.map(m => (
                    <option key={m} value={m}>{m.replace('_', ' ')}</option>
                  ))}
                </select>
              </div>

              <button
                onClick={() => payMut.mutate()}
                disabled={payMut.isPending}
                className="w-full py-2.5 bg-emerald-600 text-white rounded-lg text-sm font-medium hover:bg-emerald-700 disabled:opacity-60"
              >
                {payMut.isPending ? 'Processing…' : 'Pay Now'}
              </button>

              <p className="text-xs text-center text-gray-400">
                Secured by InvoiceApp
              </p>
            </div>
          )}
        </div>

        <div className="text-center mt-4">
          <Link to="/" className="text-xs text-gray-400 hover:text-gray-600">
            Back to InvoiceApp
          </Link>
        </div>
      </div>
    </div>
  )
}
