import { useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { z } from 'zod'
import { Plus, Store, Pencil, Copy, Check, KeyRound } from 'lucide-react'
import toast from 'react-hot-toast'
import { countersApi, businessApi } from '../api'
import type { Counter } from '../api/types'
import { Modal } from '../components/Modal'
import { Card } from '../components/Card'
import { EmptyState } from '../components/EmptyState'
import { PageSpinner } from '../components/Spinner'
import { formatDate } from '../lib/dates'
import { apiError } from '../lib/utils'
import { auth } from '../lib/auth'

// On create, name + code required; password optional (but recommended to enable login).
const createSchema = z.object({
  name:     z.string().min(1, 'Name is required'),
  code:     z.string().min(1, 'Code is required').max(16, 'Max 16 chars'),
  password: z.string().min(4, 'Min 4 characters').or(z.literal('')).optional(),
})
// On edit, everything optional (partial update); password rotates if provided.
const editSchema = z.object({
  name:     z.string().min(1, 'Name is required'),
  password: z.string().min(4, 'Min 4 characters').or(z.literal('')).optional(),
  active:   z.boolean(),
})
type CreateForm = z.infer<typeof createSchema>
type EditForm = z.infer<typeof editSchema>

export default function Counters() {
  const qc = useQueryClient()
  const [createOpen, setCreateOpen] = useState(false)
  const [editing, setEditing] = useState<Counter | null>(null)
  const [copied, setCopied] = useState(false)
  const [codeModalOpen, setCodeModalOpen] = useState(false)
  const [newCode, setNewCode] = useState('')

  const businessCode = auth.getBusinessCode() ?? ''

  const updateCodeMut = useMutation({
    mutationFn: (code: string) => businessApi.update({ loginCode: code }),
    onSuccess: ({ data }) => {
      // Keep the stored session in sync so the card + counter-login hint update.
      const current = {
        accessToken: auth.getToken()!, refreshToken: auth.getRefresh()!,
        businessId: auth.getBusinessId()!, businessName: data.name,
        businessLoginCode: data.loginCode, role: auth.getRole()!,
        counterId: auth.getCounterId(), counterCode: auth.getCounterCode(),
      }
      auth.save(current as any)
      toast.success('Business code updated')
      setCodeModalOpen(false)
    },
    onError: (err) => toast.error(apiError(err)),
  })

  const { data: counters, isLoading } = useQuery({
    queryKey: ['counters'],
    queryFn: () => countersApi.list().then(r => r.data),
  })

  const createForm = useForm<CreateForm>({ resolver: zodResolver(createSchema) })
  const editForm = useForm<EditForm>({ resolver: zodResolver(editSchema) })

  const createMut = useMutation({
    mutationFn: (d: CreateForm) =>
      countersApi.create({ name: d.name, code: d.code, password: d.password || undefined }),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['counters'] })
      toast.success('Counter created')
      setCreateOpen(false)
      createForm.reset({ name: '', code: '', password: '' })
    },
    onError: (err) => toast.error(apiError(err)),
  })

  const updateMut = useMutation({
    mutationFn: (d: EditForm) =>
      countersApi.update(editing!.id, {
        name: d.name,
        active: d.active,
        password: d.password || undefined,
      }),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['counters'] })
      toast.success('Counter updated')
      setEditing(null)
    },
    onError: (err) => toast.error(apiError(err)),
  })

  function openCreate() {
    createForm.reset({ name: '', code: '', password: '' })
    setCreateOpen(true)
  }

  function openEdit(c: Counter) {
    setEditing(c)
    editForm.reset({ name: c.name, password: '', active: c.active })
  }

  function copyCode() {
    navigator.clipboard.writeText(businessCode)
    setCopied(true)
    toast.success('Business code copied')
    setTimeout(() => setCopied(false), 1500)
  }

  return (
    <div className="space-y-5">
      <div className="flex items-center justify-between">
        <h1 className="text-2xl font-bold text-gray-900">Counters</h1>
        <button onClick={openCreate} className="inline-flex items-center gap-2 px-4 py-2 bg-blue-600 text-white rounded-lg text-sm font-medium hover:bg-blue-700 transition-colors">
          <Plus className="w-4 h-4" /> New Counter
        </button>
      </div>

      {/* Business code card — this is what cashiers type to log into a counter */}
      <Card className="p-4 bg-blue-50/50 border-blue-100">
        <div className="flex items-center justify-between gap-4">
          <div>
            <p className="text-xs font-medium text-blue-700 uppercase tracking-wide">Business code</p>
            <p className="text-sm text-gray-600 mt-0.5">
              Share this with your counters. A till signs in with this code, its counter number, and its counter password.
            </p>
          </div>
          <div className="flex items-center gap-2 shrink-0">
            <button
              onClick={copyCode}
              className="inline-flex items-center gap-2 px-3 py-2 bg-white border border-blue-200 rounded-lg font-mono font-bold text-gray-900 hover:bg-blue-50"
            >
              {businessCode || '—'}
              {copied ? <Check className="w-4 h-4 text-green-600" /> : <Copy className="w-4 h-4 text-gray-400" />}
            </button>
            <button
              onClick={() => { setNewCode(businessCode); setCodeModalOpen(true) }}
              className="p-2 rounded-lg hover:bg-blue-100 text-blue-600"
              title="Change business code"
            >
              <Pencil className="w-4 h-4" />
            </button>
          </div>
        </div>
      </Card>

      <Card className="overflow-hidden">
        {isLoading ? (
          <PageSpinner />
        ) : !counters?.length ? (
          <EmptyState
            icon={Store}
            title="No counters yet"
            description="Create a counter so a till can sign in and bill independently."
            action={<button onClick={openCreate} className="inline-flex items-center gap-2 px-4 py-2 bg-blue-600 text-white rounded-lg text-sm font-medium hover:bg-blue-700"><Plus className="w-4 h-4" /> New Counter</button>}
          />
        ) : (
          <table className="w-full text-sm">
            <thead className="bg-gray-50 border-b border-gray-100">
              <tr>
                {['Code', 'Name', 'Login', 'Status', 'Created', ''].map(h => (
                  <th key={h} className="text-left px-5 py-2.5 text-xs font-medium text-gray-500 uppercase">{h}</th>
                ))}
              </tr>
            </thead>
            <tbody className="divide-y divide-gray-50">
              {counters.map(c => (
                <tr key={c.id} className="hover:bg-gray-50">
                  <td className="px-5 py-3 font-mono font-semibold text-gray-900">{c.code}</td>
                  <td className="px-5 py-3 text-gray-700">{c.name}</td>
                  <td className="px-5 py-3">
                    {c.loginEnabled
                      ? <span className="inline-flex items-center gap-1 text-xs text-green-700"><KeyRound className="w-3 h-3" /> Enabled</span>
                      : <span className="text-xs text-gray-400">No password</span>}
                  </td>
                  <td className="px-5 py-3">
                    <span className={`text-xs px-2 py-0.5 rounded-full ${c.active ? 'bg-green-50 text-green-700' : 'bg-gray-100 text-gray-500'}`}>
                      {c.active ? 'Active' : 'Inactive'}
                    </span>
                  </td>
                  <td className="px-5 py-3 text-gray-400 text-xs">{formatDate(c.createdAt)}</td>
                  <td className="px-5 py-3 text-right">
                    <button onClick={() => openEdit(c)} className="p-1.5 rounded hover:bg-gray-100 text-gray-400 hover:text-blue-600">
                      <Pencil className="w-3.5 h-3.5" />
                    </button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </Card>

      {/* Create modal */}
      <Modal open={createOpen} onClose={() => setCreateOpen(false)} title="New Counter">
        <form onSubmit={createForm.handleSubmit(d => createMut.mutate(d))} className="space-y-4">
          <Field label="Code *" error={createForm.formState.errors.code?.message}>
            <input {...createForm.register('code')} placeholder="e.g. C2" className={inputCls} />
          </Field>
          <Field label="Name *" error={createForm.formState.errors.name?.message}>
            <input {...createForm.register('name')} placeholder="e.g. Front Desk" className={inputCls} />
          </Field>
          <Field label="Counter password" error={createForm.formState.errors.password?.message}
                 hint="Set this to let a till sign in at this counter. Separate from your admin password.">
            <input type="password" {...createForm.register('password')} placeholder="Min 4 characters" className={inputCls} />
          </Field>
          <div className="flex gap-2 pt-1">
            <button type="submit" disabled={createMut.isPending} className="flex-1 py-2 bg-blue-600 text-white rounded-lg text-sm font-medium hover:bg-blue-700 disabled:opacity-60">
              {createMut.isPending ? 'Creating…' : 'Create Counter'}
            </button>
            <button type="button" onClick={() => setCreateOpen(false)} className="px-4 py-2 border border-gray-200 rounded-lg text-sm text-gray-600 hover:bg-gray-50">Cancel</button>
          </div>
        </form>
      </Modal>

      {/* Edit modal */}
      <Modal open={editing !== null} onClose={() => setEditing(null)} title={`Edit ${editing?.code ?? ''}`}>
        <form onSubmit={editForm.handleSubmit(d => updateMut.mutate(d))} className="space-y-4">
          <Field label="Name *" error={editForm.formState.errors.name?.message}>
            <input {...editForm.register('name')} className={inputCls} />
          </Field>
          <Field label="Reset counter password" error={editForm.formState.errors.password?.message}
                 hint={editing?.loginEnabled ? 'Leave blank to keep the current password.' : 'Set a password to enable counter login.'}>
            <input type="password" {...editForm.register('password')} placeholder="Leave blank to keep unchanged" className={inputCls} />
          </Field>
          <label className="flex items-center gap-2 text-sm text-gray-700">
            <input type="checkbox" {...editForm.register('active')} className="rounded border-gray-300" />
            Active (uncheck to disable this counter and its logins)
          </label>
          <div className="flex gap-2 pt-1">
            <button type="submit" disabled={updateMut.isPending} className="flex-1 py-2 bg-blue-600 text-white rounded-lg text-sm font-medium hover:bg-blue-700 disabled:opacity-60">
              {updateMut.isPending ? 'Saving…' : 'Save Changes'}
            </button>
            <button type="button" onClick={() => setEditing(null)} className="px-4 py-2 border border-gray-200 rounded-lg text-sm text-gray-600 hover:bg-gray-50">Cancel</button>
          </div>
        </form>
      </Modal>

      {/* Edit business code modal */}
      <Modal open={codeModalOpen} onClose={() => setCodeModalOpen(false)} title="Change business code">
        <div className="space-y-4">
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1">Business code</label>
            <input
              value={newCode}
              onChange={e => setNewCode(e.target.value.toUpperCase())}
              placeholder="3–20 letters, digits or hyphens"
              className={`${inputCls} uppercase`}
            />
            <p className="text-xs text-gray-400 mt-1">
              Counters sign in with this code. Changing it means telling your tills the new code.
            </p>
          </div>
          <div className="flex gap-2 pt-1">
            <button
              onClick={() => updateCodeMut.mutate(newCode.trim())}
              disabled={updateCodeMut.isPending || newCode.trim().length < 3}
              className="flex-1 py-2 bg-blue-600 text-white rounded-lg text-sm font-medium hover:bg-blue-700 disabled:opacity-60"
            >
              {updateCodeMut.isPending ? 'Saving…' : 'Save code'}
            </button>
            <button type="button" onClick={() => setCodeModalOpen(false)} className="px-4 py-2 border border-gray-200 rounded-lg text-sm text-gray-600 hover:bg-gray-50">Cancel</button>
          </div>
        </div>
      </Modal>
    </div>
  )
}

const inputCls =
  'w-full px-3 py-2 border border-gray-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-transparent'

function Field({ label, error, hint, children }: {
  label: string; error?: string; hint?: string; children: React.ReactNode
}) {
  return (
    <div>
      <label className="block text-sm font-medium text-gray-700 mb-1">{label}</label>
      {children}
      {hint && !error && <p className="text-xs text-gray-400 mt-1">{hint}</p>}
      {error && <p className="text-xs text-red-600 mt-1">{error}</p>}
    </div>
  )
}
