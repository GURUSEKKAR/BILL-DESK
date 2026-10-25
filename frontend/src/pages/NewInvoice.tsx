import { useNavigate } from 'react-router-dom'
import { useForm, useFieldArray } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { z } from 'zod'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { Plus, Trash2, ArrowLeft } from 'lucide-react'
import toast from 'react-hot-toast'
import { customersApi, invoicesApi, countersApi } from '../api'
import { MoneyText } from '../components/MoneyText'
import { Card, CardHeader, CardBody } from '../components/Card'
import { parseToPaise } from '../lib/money'
import { daysFromNow } from '../lib/dates'
import { apiError } from '../lib/utils'
import { auth } from '../lib/auth'

const itemSchema = z.object({
  description:  z.string().min(1, 'Required'),
  qty:          z.string().refine(v => parseInt(v) > 0, 'Must be ≥ 1'),
  unitPriceRs:  z.string().refine(v => parseFloat(v) >= 0, 'Must be ≥ 0'),
})

const schema = z.object({
  customerId: z.string().min(1, 'Select a customer'),
  counterId:  z.string().optional(),
  dueDate:    z.string().min(1, 'Select a due date'),
  items:      z.array(itemSchema).min(1, 'Add at least one item'),
})

type FormData = z.infer<typeof schema>

export default function NewInvoice() {
  const navigate = useNavigate()
  const qc = useQueryClient()
  const isOwner = auth.isOwner()

  const { data: custPage } = useQuery({
    queryKey: ['customers', 0, ''],
    queryFn: () => customersApi.list(0).then(r => r.data),
  })

  // Owners must pick which counter the invoice is billed at; cashiers are
  // pinned to their own counter server-side, so no picker is shown for them.
  const { data: counters } = useQuery({
    queryKey: ['counters'],
    queryFn: () => countersApi.list().then(r => r.data),
    enabled: isOwner,
  })
  const activeCounters = counters?.filter(c => c.active) ?? []

  const {
    register, handleSubmit, control, watch,
    formState: { errors, isSubmitting },
  } = useForm<FormData>({
    resolver: zodResolver(schema),
    defaultValues: {
      customerId: '',
      counterId: '',
      dueDate: daysFromNow(30),
      items: [{ description: '', qty: '1', unitPriceRs: '' }],
    },
  })

  const { fields, append, remove } = useFieldArray({ control, name: 'items' })
  const watchedItems = watch('items')

  // Live total — server is the authority, but show a preview
  const previewTotal = watchedItems.reduce((sum, it) => {
    const qty = parseInt(it.qty) || 0
    const paise = parseToPaise(it.unitPriceRs)
    return sum + (isNaN(paise) ? 0 : qty * paise)
  }, 0)

  const createMut = useMutation({
    mutationFn: (d: FormData) =>
      invoicesApi.create({
        customerId: d.customerId,
        dueDate: d.dueDate,         // send as-is YYYY-MM-DD — no timezone conversion
        counterId: isOwner ? d.counterId || undefined : undefined,
        items: d.items.map(it => ({
          description:  it.description,
          qty:          parseInt(it.qty),
          unitPricePaise: parseToPaise(it.unitPriceRs),
        })),
      }),
    onSuccess: ({ data }) => {
      qc.invalidateQueries({ queryKey: ['invoices'] })
      qc.invalidateQueries({ queryKey: ['dashboard'] })
      toast.success(`Invoice ${data.number} created`)
      navigate(`/invoices/${data.id}`)
    },
    onError: (err) => toast.error(apiError(err)),
  })

  function onSubmit(d: FormData) {
    if (isOwner && !d.counterId) {
      toast.error('Select a counter for this invoice')
      return
    }
    createMut.mutate(d)
  }

  return (
    <div className="max-w-3xl space-y-5">
      <div className="flex items-center gap-3">
        <button onClick={() => navigate(-1)} className="p-1.5 rounded-lg border border-gray-200 hover:bg-gray-50">
          <ArrowLeft className="w-4 h-4" />
        </button>
        <h1 className="text-2xl font-bold text-gray-900">New Invoice</h1>
      </div>

      <form onSubmit={handleSubmit(onSubmit)} noValidate className="space-y-5">
        {/* Header fields */}
        <Card>
          <CardBody className="grid grid-cols-2 gap-4">
            <div>
              <label className="block text-sm font-medium text-gray-700 mb-1">Customer *</label>
              <select
                {...register('customerId')}
                className="w-full px-3 py-2 border border-gray-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
              >
                <option value="">Select customer…</option>
                {custPage?.content.map(c => (
                  <option key={c.id} value={c.id}>{c.name}</option>
                ))}
              </select>
              {errors.customerId && <p className="text-xs text-red-600 mt-1">{errors.customerId.message}</p>}
            </div>
            <div>
              <label className="block text-sm font-medium text-gray-700 mb-1">Due Date *</label>
              <input
                type="date"
                {...register('dueDate')}
                className="w-full px-3 py-2 border border-gray-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
              />
              {errors.dueDate && <p className="text-xs text-red-600 mt-1">{errors.dueDate.message}</p>}
            </div>
            {isOwner && (
              <div className="col-span-2">
                <label className="block text-sm font-medium text-gray-700 mb-1">Counter *</label>
                <select
                  {...register('counterId')}
                  className="w-full px-3 py-2 border border-gray-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
                >
                  <option value="">Select counter…</option>
                  {activeCounters.map(c => (
                    <option key={c.id} value={c.id}>{c.code} · {c.name}</option>
                  ))}
                </select>
                {!activeCounters.length && (
                  <p className="text-xs text-amber-600 mt-1">No counters yet — create one first.</p>
                )}
              </div>
            )}
          </CardBody>
        </Card>

        {/* Line items */}
        <Card>
          <CardHeader>
            <h2 className="font-semibold text-gray-900 text-sm">Line Items</h2>
          </CardHeader>
          <CardBody className="space-y-3">
            {fields.map((field, idx) => (
              <div key={field.id} className="grid grid-cols-12 gap-2 items-start">
                <div className="col-span-5">
                  {idx === 0 && <label className="block text-xs text-gray-500 mb-1">Description</label>}
                  <input
                    {...register(`items.${idx}.description`)}
                    placeholder="Service description"
                    className="w-full px-3 py-2 border border-gray-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
                  />
                  {errors.items?.[idx]?.description && (
                    <p className="text-xs text-red-500 mt-0.5">{errors.items[idx]?.description?.message}</p>
                  )}
                </div>
                <div className="col-span-2">
                  {idx === 0 && <label className="block text-xs text-gray-500 mb-1">Qty</label>}
                  <input
                    {...register(`items.${idx}.qty`)}
                    type="number" min="1"
                    className="w-full px-3 py-2 border border-gray-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
                  />
                </div>
                <div className="col-span-4">
                  {idx === 0 && <label className="block text-xs text-gray-500 mb-1">Unit Price (₹)</label>}
                  <input
                    {...register(`items.${idx}.unitPriceRs`)}
                    type="number" step="0.01" min="0"
                    placeholder="e.g. 500"
                    className="w-full px-3 py-2 border border-gray-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
                  />
                </div>
                <div className="col-span-1 flex items-end pb-0.5">
                  {idx === 0 && <div className="h-6" />}
                  {fields.length > 1 && (
                    <button
                      type="button"
                      onClick={() => remove(idx)}
                      className="p-1.5 text-gray-400 hover:text-red-500 rounded"
                    >
                      <Trash2 className="w-4 h-4" />
                    </button>
                  )}
                </div>
              </div>
            ))}

            <button
              type="button"
              onClick={() => append({ description: '', qty: '1', unitPriceRs: '' })}
              className="inline-flex items-center gap-1.5 text-xs text-blue-600 hover:text-blue-700 font-medium mt-1"
            >
              <Plus className="w-3.5 h-3.5" /> Add Item
            </button>

            {/* Preview total (not submitted — server computes the authoritative total) */}
            {previewTotal > 0 && (
              <div className="pt-3 border-t border-gray-100 flex justify-end">
                <div className="text-right">
                  <p className="text-xs text-gray-400">Preview total</p>
                  <MoneyText paise={previewTotal} size="xl" className="text-gray-900" />
                </div>
              </div>
            )}
          </CardBody>
        </Card>

        <div className="flex gap-2">
          <button
            type="submit"
            disabled={isSubmitting || createMut.isPending}
            className="inline-flex items-center gap-2 px-5 py-2.5 bg-blue-600 text-white rounded-lg text-sm font-medium hover:bg-blue-700 disabled:opacity-60"
          >
            {createMut.isPending ? 'Creating…' : 'Create Invoice'}
          </button>
          <button type="button" onClick={() => navigate(-1)} className="px-4 py-2.5 border border-gray-200 rounded-lg text-sm text-gray-600 hover:bg-gray-50">
            Cancel
          </button>
        </div>
      </form>
    </div>
  )
}
