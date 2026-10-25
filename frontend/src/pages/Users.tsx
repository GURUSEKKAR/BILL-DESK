import { useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { z } from 'zod'
import { Plus, UserCog, ShieldCheck } from 'lucide-react'
import toast from 'react-hot-toast'
import { usersApi, countersApi } from '../api'
import { Modal } from '../components/Modal'
import { Card } from '../components/Card'
import { EmptyState } from '../components/EmptyState'
import { PageSpinner } from '../components/Spinner'
import { formatDate } from '../lib/dates'
import { apiError } from '../lib/utils'

const schema = z.object({
  email:     z.string().email('Enter a valid email'),
  password:  z.string().min(8, 'Min 8 characters'),
  counterId: z.string().min(1, 'Choose a counter'),
})
type FormData = z.infer<typeof schema>

export default function Users() {
  const qc = useQueryClient()
  const [createOpen, setCreateOpen] = useState(false)

  const { data: users, isLoading } = useQuery({
    queryKey: ['users'],
    queryFn: () => usersApi.list().then(r => r.data),
  })
  const { data: counters } = useQuery({
    queryKey: ['counters'],
    queryFn: () => countersApi.list().then(r => r.data),
  })

  const form = useForm<FormData>({ resolver: zodResolver(schema) })

  const createMut = useMutation({
    mutationFn: (d: FormData) => usersApi.create(d),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['users'] })
      toast.success('Cashier created')
      setCreateOpen(false)
      form.reset({ email: '', password: '', counterId: '' })
    },
    onError: (err) => toast.error(apiError(err)),
  })

  const reassignMut = useMutation({
    mutationFn: ({ id, counterId }: { id: string; counterId: string }) =>
      usersApi.update(id, { counterId }),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['users'] })
      toast.success('Cashier reassigned')
    },
    onError: (err) => toast.error(apiError(err)),
  })

  const toggleActiveMut = useMutation({
    mutationFn: ({ id, active }: { id: string; active: boolean }) =>
      usersApi.update(id, { active }),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['users'] })
      toast.success('Cashier updated')
    },
    onError: (err) => toast.error(apiError(err)),
  })

  const activeCounters = counters?.filter(c => c.active) ?? []

  return (
    <div className="space-y-5">
      <div className="flex items-center justify-between">
        <h1 className="text-2xl font-bold text-gray-900">Cashiers</h1>
        <button
          onClick={() => { form.reset({ email: '', password: '', counterId: '' }); setCreateOpen(true) }}
          className="inline-flex items-center gap-2 px-4 py-2 bg-blue-600 text-white rounded-lg text-sm font-medium hover:bg-blue-700 transition-colors"
        >
          <Plus className="w-4 h-4" /> New Cashier
        </button>
      </div>

      <Card className="overflow-hidden">
        {isLoading ? (
          <PageSpinner />
        ) : !users?.length ? (
          <EmptyState
            icon={UserCog}
            title="No team members yet"
            description="Create a cashier account pinned to a counter, or share the counter password for shared-till login."
          />
        ) : (
          <table className="w-full text-sm">
            <thead className="bg-gray-50 border-b border-gray-100">
              <tr>
                {['Email', 'Role', 'Counter', 'Status', 'Created', ''].map(h => (
                  <th key={h} className="text-left px-5 py-2.5 text-xs font-medium text-gray-500 uppercase">{h}</th>
                ))}
              </tr>
            </thead>
            <tbody className="divide-y divide-gray-50">
              {users.map(u => (
                <tr key={u.id} className="hover:bg-gray-50">
                  <td className="px-5 py-3 font-medium text-gray-900">{u.email}</td>
                  <td className="px-5 py-3">
                    {u.role === 'OWNER'
                      ? <span className="inline-flex items-center gap-1 text-xs text-purple-700"><ShieldCheck className="w-3 h-3" /> Owner</span>
                      : <span className="text-xs text-gray-600">Cashier</span>}
                  </td>
                  <td className="px-5 py-3">
                    {u.role === 'CASHIER' ? (
                      <select
                        value={u.counterId ?? ''}
                        onChange={e => reassignMut.mutate({ id: u.id, counterId: e.target.value })}
                        className="text-sm border border-gray-200 rounded-md px-2 py-1 bg-white"
                      >
                        {activeCounters.map(c => (
                          <option key={c.id} value={c.id}>{c.code} · {c.name}</option>
                        ))}
                      </select>
                    ) : (
                      <span className="text-gray-400 text-xs">All counters</span>
                    )}
                  </td>
                  <td className="px-5 py-3">
                    <span className={`text-xs px-2 py-0.5 rounded-full ${u.active ? 'bg-green-50 text-green-700' : 'bg-gray-100 text-gray-500'}`}>
                      {u.active ? 'Active' : 'Disabled'}
                    </span>
                  </td>
                  <td className="px-5 py-3 text-gray-400 text-xs">{formatDate(u.createdAt)}</td>
                  <td className="px-5 py-3 text-right">
                    {u.role === 'CASHIER' && (
                      <button
                        onClick={() => toggleActiveMut.mutate({ id: u.id, active: !u.active })}
                        className="text-xs font-medium text-gray-500 hover:text-gray-900"
                      >
                        {u.active ? 'Disable' : 'Enable'}
                      </button>
                    )}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </Card>

      <Modal open={createOpen} onClose={() => setCreateOpen(false)} title="New Cashier">
        <form onSubmit={form.handleSubmit(d => createMut.mutate(d))} className="space-y-4">
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1">Email *</label>
            <input type="email" {...form.register('email')} placeholder="cashier@company.com" className={inputCls} />
            {form.formState.errors.email && <p className="text-xs text-red-600 mt-1">{form.formState.errors.email.message}</p>}
          </div>
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1">Password *</label>
            <input type="password" {...form.register('password')} placeholder="Min 8 characters" className={inputCls} />
            {form.formState.errors.password && <p className="text-xs text-red-600 mt-1">{form.formState.errors.password.message}</p>}
          </div>
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1">Counter *</label>
            <select {...form.register('counterId')} className={inputCls} defaultValue="">
              <option value="" disabled>Choose a counter…</option>
              {activeCounters.map(c => <option key={c.id} value={c.id}>{c.code} · {c.name}</option>)}
            </select>
            {form.formState.errors.counterId && <p className="text-xs text-red-600 mt-1">{form.formState.errors.counterId.message}</p>}
            {!activeCounters.length && <p className="text-xs text-amber-600 mt-1">Create a counter first.</p>}
          </div>
          <div className="flex gap-2 pt-1">
            <button type="submit" disabled={createMut.isPending || !activeCounters.length} className="flex-1 py-2 bg-blue-600 text-white rounded-lg text-sm font-medium hover:bg-blue-700 disabled:opacity-60">
              {createMut.isPending ? 'Creating…' : 'Create Cashier'}
            </button>
            <button type="button" onClick={() => setCreateOpen(false)} className="px-4 py-2 border border-gray-200 rounded-lg text-sm text-gray-600 hover:bg-gray-50">Cancel</button>
          </div>
        </form>
      </Modal>
    </div>
  )
}

const inputCls =
  'w-full px-3 py-2 border border-gray-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-transparent'
