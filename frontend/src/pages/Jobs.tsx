import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { RefreshCw, RotateCcw, AlertCircle, CheckCircle, Clock, Loader2 } from 'lucide-react'
import toast from 'react-hot-toast'
import { adminApi } from '../api'
import { Card, CardHeader } from '../components/Card'
import { PageSpinner } from '../components/Spinner'
import { EmptyState } from '../components/EmptyState'
import { formatDateTime } from '../lib/dates'
import { apiError } from '../lib/utils'

export default function Jobs() {
  const qc = useQueryClient()

  const { data: stats, isLoading: statsLoading } = useQuery({
    queryKey: ['jobStats'],
    queryFn: () => adminApi.jobStats().then(r => r.data),
    refetchInterval: 5000,
  })

  const { data: failedJobs, isLoading: jobsLoading } = useQuery({
    queryKey: ['failedJobs'],
    queryFn: () => adminApi.failedJobs().then(r => r.data),
    refetchInterval: 10000,
  })

  const replayMut = useMutation({
    mutationFn: (id: string) => adminApi.replayJob(id),
    onSuccess: () => {
      toast.success('Job queued for retry')
      qc.invalidateQueries({ queryKey: ['failedJobs'] })
      qc.invalidateQueries({ queryKey: ['jobStats'] })
    },
    onError: (err) => toast.error(apiError(err)),
  })

  const overdueMut = useMutation({
    mutationFn: () => adminApi.runOverdue(),
    onSuccess: ({ data }) => {
      toast.success(`Marked ${data.markedOverdue} invoice(s) as overdue`)
      qc.invalidateQueries({ queryKey: ['invoices'] })
      qc.invalidateQueries({ queryKey: ['dashboard'] })
    },
    onError: (err) => toast.error(apiError(err)),
  })

  if (statsLoading) return <PageSpinner />

  const statCards = [
    { label: 'Pending', value: stats?.pending ?? 0,  icon: Clock,        color: 'text-amber-600',   bg: 'bg-amber-50'   },
    { label: 'Sending', value: stats?.sending ?? 0,  icon: Loader2,      color: 'text-purple-600',  bg: 'bg-purple-50'  },
    { label: 'Done',    value: stats?.done ?? 0,     icon: CheckCircle,  color: 'text-emerald-600', bg: 'bg-emerald-50' },
    { label: 'Failed',  value: stats?.failed ?? 0,   icon: AlertCircle,  color: 'text-red-600',     bg: 'bg-red-50'     },
  ]

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <h1 className="text-2xl font-bold text-gray-900">Background Jobs</h1>
        <button
          onClick={() => overdueMut.mutate()}
          disabled={overdueMut.isPending}
          className="inline-flex items-center gap-2 px-3 py-1.5 border border-gray-200 rounded-lg text-sm text-gray-600 hover:bg-gray-50 disabled:opacity-60"
        >
          <RefreshCw className={`w-4 h-4 ${overdueMut.isPending ? 'animate-spin' : ''}`} />
          Run Overdue Check
        </button>
      </div>

      {/* Stats */}
      <div className="grid grid-cols-2 sm:grid-cols-4 gap-4">
        {statCards.map(s => (
          <Card key={s.label} className="flex items-center gap-3 p-4">
            <div className={`w-9 h-9 ${s.bg} rounded-lg flex items-center justify-center shrink-0`}>
              <s.icon className={`w-4 h-4 ${s.color}`} />
            </div>
            <div>
              <p className="text-2xl font-bold text-gray-900">{s.value}</p>
              <p className="text-xs text-gray-500">{s.label}</p>
            </div>
          </Card>
        ))}
      </div>

      {/* Failed jobs table */}
      <Card>
        <CardHeader className="flex items-center justify-between">
          <h2 className="font-semibold text-gray-900 text-sm">Failed Jobs</h2>
          <button
            onClick={() => qc.invalidateQueries({ queryKey: ['failedJobs'] })}
            className="p-1 rounded text-gray-400 hover:text-gray-600 hover:bg-gray-100"
          >
            <RefreshCw className="w-3.5 h-3.5" />
          </button>
        </CardHeader>

        {jobsLoading ? (
          <PageSpinner />
        ) : !failedJobs?.length ? (
          <EmptyState icon={CheckCircle} title="No failed jobs" description="All jobs have completed successfully." />
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full text-sm">
              <thead className="bg-gray-50 border-b border-gray-100">
                <tr>
                  {['Type', 'Attempts', 'Last Error', 'Updated', ''].map(h => (
                    <th key={h} className="text-left px-5 py-2.5 text-xs font-medium text-gray-500 uppercase">{h}</th>
                  ))}
                </tr>
              </thead>
              <tbody className="divide-y divide-gray-50">
                {failedJobs.map(job => (
                  <tr key={job.id} className="hover:bg-gray-50">
                    <td className="px-5 py-3">
                      <code className="text-xs bg-gray-100 px-1.5 py-0.5 rounded">{job.type}</code>
                    </td>
                    <td className="px-5 py-3 text-gray-500">{job.attempts}/{job.maxAttempts}</td>
                    <td className="px-5 py-3 max-w-xs">
                      <p className="truncate text-xs text-red-600">{job.lastError ?? '—'}</p>
                    </td>
                    <td className="px-5 py-3 text-gray-400 text-xs">{formatDateTime(job.updatedAt)}</td>
                    <td className="px-5 py-3 text-right">
                      <button
                        onClick={() => replayMut.mutate(job.id)}
                        disabled={replayMut.isPending}
                        className="inline-flex items-center gap-1 text-xs text-blue-600 hover:underline disabled:opacity-50"
                      >
                        <RotateCcw className="w-3 h-3" /> Retry
                      </button>
                    </td>
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
