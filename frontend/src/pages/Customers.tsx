import { useState } from 'react'
import { Link } from 'react-router-dom'
import {
  useQuery, useMutation, useQueryClient,
} from '@tanstack/react-query'
import { useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { z } from 'zod'
import { Plus, Search, Users, Pencil, Trash2, ChevronLeft, ChevronRight, Wallet } from 'lucide-react'
import toast from 'react-hot-toast'
import { customersApi } from '../api'
import type { Customer, CustomerBalance } from '../api/types'
import { Modal } from '../components/Modal'
import { Card } from '../components/Card'
import { EmptyState } from '../components/EmptyState'
import { PageSpinner } from '../components/Spinner'
import { MoneyText } from '../components/MoneyText'
import { formatDate } from '../lib/dates'
import { apiError } from '../lib/utils'
import { parseToPaise } from '../lib/money'

const schema = z.object({
  name:  z.string().min(1, 'Name is required'),
  email: z.string().email('Invalid email').or(z.literal('')).optional(),
  phone: z.string().optional(),
  creditLimitRs: z.string()
    .refine(v => v === '' || parseFloat(v) >= 0, 'Must be ≥ 0')
    .optional(),
})
type FormData = z.infer<typeof schema>

export default function Customers() {
  const qc = useQueryClient()
  const [page, setPage] = useState(0)
  const [search, setSearch] = useState('')
  const [editingCustomer, setEditingCustomer] = useState<Customer | null>(null)
  const [modalOpen, setModalOpen] = useState(false)
  const [balanceFor, setBalanceFor] = useState<Customer | null>(null)

  const { data, isLoading } = useQuery({
    queryKey: ['customers', page, search],
    queryFn: () => customersApi.list(page, search || undefined).then(r => r.data),
    placeholderData: (prev) => prev,
  })

  const { data: balance, isLoading: loadingBalance } = useQuery<CustomerBalance>({
    queryKey: ['customer-balance', balanceFor?.id],
    queryFn: () => customersApi.balance(balanceFor!.id).then(r => r.data),
    enabled: !!balanceFor,
  })

  const { register, handleSubmit, reset, formState: { errors, isSubmitting } } = useForm<FormData>({
    resolver: zodResolver(schema),
  })

  const createMut = useMutation({
    mutationFn: (d: FormData) => customersApi.create(toPayload(d)),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['customers'] })
      toast.success('Customer created')
      closeModal()
    },
    onError: (err) => toast.error(apiError(err)),
  })

  const updateMut = useMutation({
    mutationFn: (d: FormData) => customersApi.update(editingCustomer!.id, toPayload(d)),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['customers'] })
      toast.success('Customer updated')
      closeModal()
    },
    onError: (err) => toast.error(apiError(err)),
  })

  const deleteMut = useMutation({
    mutationFn: (id: string) => customersApi.delete(id),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['customers'] })
      toast.success('Customer deleted')
    },
    onError: (err) => toast.error(apiError(err)),
  })

  function openCreate() {
    setEditingCustomer(null)
    reset({ name: '', email: '', phone: '', creditLimitRs: '' })
    setModalOpen(true)
  }

  function openEdit(c: Customer) {
    setEditingCustomer(c)
    reset({
      name: c.name,
      email: c.email ?? '',
      phone: c.phone ?? '',
      creditLimitRs: c.creditLimitPaise != null ? String(c.creditLimitPaise / 100) : '',
    })
    setModalOpen(true)
  }

  function closeModal() {
    setModalOpen(false)
    setEditingCustomer(null)
  }

  /** Map the rupee credit-limit string to the API payload (paise, or null). */
  function toPayload(d: FormData) {
    const trimmed = (d.creditLimitRs ?? '').trim()
    return {
      name: d.name,
      email: d.email || undefined,
      phone: d.phone || undefined,
      creditLimitPaise: trimmed === '' ? null : parseToPaise(trimmed),
    }
  }

  function onSubmit(d: FormData) {
    if (editingCustomer) updateMut.mutate(d)
    else createMut.mutate(d)
  }

  const field = (label: string, name: keyof FormData, props?: React.InputHTMLAttributes<HTMLInputElement>) => (
    <div>
      <label className="block text-sm font-medium text-gray-700 mb-1">{label}</label>
      <input
        {...register(name)}
        {...props}
        className="w-full px-3 py-2 border border-gray-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-transparent"
      />
      {errors[name] && <p className="text-xs text-red-600 mt-1">{errors[name]?.message}</p>}
    </div>
  )

  return (
    <div className="space-y-5">
      <div className="flex items-center justify-between">
        <h1 className="text-2xl font-bold text-gray-900">Customers</h1>
        <button onClick={openCreate} className="inline-flex items-center gap-2 px-4 py-2 bg-blue-600 text-white rounded-lg text-sm font-medium hover:bg-blue-700 transition-colors">
          <Plus className="w-4 h-4" /> New Customer
        </button>
      </div>

      {/* Search */}
      <div className="relative max-w-xs">
        <Search className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-gray-400" />
        <input
          className="w-full pl-9 pr-3 py-2 border border-gray-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
          placeholder="Search customers…"
          value={search}
          onChange={e => { setSearch(e.target.value); setPage(0) }}
        />
      </div>

      {/* Table */}
      <Card className="overflow-hidden">
        {isLoading ? (
          <PageSpinner />
        ) : !data?.content.length ? (
          <EmptyState
            icon={Users}
            title="No customers yet"
            description="Add your first customer to get started."
            action={<button onClick={openCreate} className="inline-flex items-center gap-2 px-4 py-2 bg-blue-600 text-white rounded-lg text-sm font-medium hover:bg-blue-700"><Plus className="w-4 h-4" /> New Customer</button>}
          />
        ) : (
          <table className="w-full text-sm">
            <thead className="bg-gray-50 border-b border-gray-100">
              <tr>
                {['Name', 'Email', 'Phone', 'Created', ''].map(h => (
                  <th key={h} className="text-left px-5 py-2.5 text-xs font-medium text-gray-500 uppercase">{h}</th>
                ))}
              </tr>
            </thead>
            <tbody className="divide-y divide-gray-50">
              {data.content.map(c => (
                <tr key={c.id} className="hover:bg-gray-50">
                  <td className="px-5 py-3">
                    <Link to={`/invoices?customerId=${c.id}`} className="font-medium text-gray-900 hover:text-blue-600">
                      {c.name}
                    </Link>
                  </td>
                  <td className="px-5 py-3 text-gray-500">{c.email ?? '—'}</td>
                  <td className="px-5 py-3 text-gray-500">{c.phone ?? '—'}</td>
                  <td className="px-5 py-3 text-gray-400 text-xs">{formatDate(c.createdAt)}</td>
                  <td className="px-5 py-3 text-right">
                    <div className="flex justify-end gap-1">
                      <button onClick={() => setBalanceFor(c)} className="p-1.5 rounded hover:bg-gray-100 text-gray-400 hover:text-emerald-600" title="View balance">
                        <Wallet className="w-3.5 h-3.5" />
                      </button>
                      <button onClick={() => openEdit(c)} className="p-1.5 rounded hover:bg-gray-100 text-gray-400 hover:text-blue-600">
                        <Pencil className="w-3.5 h-3.5" />
                      </button>
                      <button
                        onClick={() => confirm(`Delete ${c.name}?`) && deleteMut.mutate(c.id)}
                        className="p-1.5 rounded hover:bg-gray-100 text-gray-400 hover:text-red-600"
                      >
                        <Trash2 className="w-3.5 h-3.5" />
                      </button>
                    </div>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </Card>

      {/* Pagination */}
      {data && data.totalPages > 1 && (
        <div className="flex items-center gap-2 justify-end">
          <button disabled={page === 0} onClick={() => setPage(p => p - 1)} className="p-1.5 rounded border border-gray-200 disabled:opacity-40 hover:bg-gray-50">
            <ChevronLeft className="w-4 h-4" />
          </button>
          <span className="text-sm text-gray-500">Page {page + 1} of {data.totalPages}</span>
          <button disabled={page >= data.totalPages - 1} onClick={() => setPage(p => p + 1)} className="p-1.5 rounded border border-gray-200 disabled:opacity-40 hover:bg-gray-50">
            <ChevronRight className="w-4 h-4" />
          </button>
        </div>
      )}

      {/* Create / Edit Modal */}
      <Modal
        open={modalOpen}
        onClose={closeModal}
        title={editingCustomer ? 'Edit Customer' : 'New Customer'}
      >
        <form onSubmit={handleSubmit(onSubmit)} className="space-y-4">
          {field('Name *', 'name', { placeholder: 'Full name' })}
          {field('Email', 'email', { type: 'email', placeholder: 'billing@company.com' })}
          {field('Phone', 'phone', { placeholder: '+91 98765 43210' })}
          {field('Credit limit (₹)', 'creditLimitRs', { type: 'number', step: '0.01', min: '0', placeholder: 'Leave blank for no limit' })}
          <div className="flex gap-2 pt-1">
            <button
              type="submit"
              disabled={isSubmitting}
              className="flex-1 py-2 bg-blue-600 text-white rounded-lg text-sm font-medium hover:bg-blue-700 disabled:opacity-60"
            >
              {isSubmitting ? 'Saving…' : editingCustomer ? 'Save Changes' : 'Create Customer'}
            </button>
            <button type="button" onClick={closeModal} className="px-4 py-2 border border-gray-200 rounded-lg text-sm text-gray-600 hover:bg-gray-50">
              Cancel
            </button>
          </div>
        </form>
      </Modal>

      {/* Balance modal — business-wide outstanding + credit status */}
      <Modal open={balanceFor !== null} onClose={() => setBalanceFor(null)} title={`Balance — ${balanceFor?.name ?? ''}`}>
        {loadingBalance || !balance ? (
          <PageSpinner />
        ) : (
          <div className="space-y-4">
            <div className="grid grid-cols-2 gap-4">
              <div>
                <p className="text-xs text-gray-400">Outstanding (all counters)</p>
                <MoneyText paise={balance.outstandingPaise} size="xl" className="text-blue-600" />
              </div>
              <div>
                <p className="text-xs text-gray-400">Credit limit</p>
                {balance.creditLimitPaise != null
                  ? <MoneyText paise={balance.creditLimitPaise} size="xl" className="text-gray-900" />
                  : <p className="text-lg font-semibold text-gray-400">No limit</p>}
              </div>
            </div>
            {balance.availableCreditPaise != null && (
              <div className="pt-3 border-t border-gray-100">
                <p className="text-xs text-gray-400">Available credit</p>
                <MoneyText
                  paise={balance.availableCreditPaise}
                  size="lg"
                  className={balance.availableCreditPaise > 0 ? 'text-emerald-600' : 'text-red-600'}
                />
              </div>
            )}
            <p className="text-xs text-gray-400">
              Shows the business-wide amount this customer owes. Individual invoices aren&apos;t listed here.
            </p>
          </div>
        )}
      </Modal>
    </div>
  )
}
