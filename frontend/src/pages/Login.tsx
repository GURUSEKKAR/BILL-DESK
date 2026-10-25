import { useState } from 'react'
import { useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { z } from 'zod'
import { Link, useNavigate, useLocation, Navigate } from 'react-router-dom'
import { Receipt } from 'lucide-react'
import toast from 'react-hot-toast'
import { authApi } from '../api'
import { auth } from '../lib/auth'
import { apiError } from '../lib/utils'

type Mode = 'admin' | 'counter'

// ── Admin (owner) form ──────────────────────────────────────────────────────
const adminSchema = z.object({
  email:    z.string().email('Enter a valid email'),
  password: z.string().min(1, 'Password is required'),
})
type AdminForm = z.infer<typeof adminSchema>

// ── Counter (cashier) form ──────────────────────────────────────────────────
const counterSchema = z.object({
  businessLoginCode: z.string().min(1, 'Business code is required'),
  counterCode:       z.string().min(1, 'Counter number is required'),
  counterPassword:   z.string().min(1, 'Counter password is required'),
})
type CounterForm = z.infer<typeof counterSchema>

const inputCls =
  'w-full px-3 py-2 border border-gray-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-transparent'

export default function Login() {
  const navigate = useNavigate()
  const location = useLocation()
  const from = (location.state as { from?: Location })?.from?.pathname ?? '/'
  const [mode, setMode] = useState<Mode>('admin')

  const admin = useForm<AdminForm>({ resolver: zodResolver(adminSchema) })
  const counter = useForm<CounterForm>({ resolver: zodResolver(counterSchema) })

  // Already logged in — go to dashboard (after hooks)
  if (auth.isLoggedIn()) return <Navigate to="/" replace />

  async function onAdmin({ email, password }: AdminForm) {
    try {
      const { data } = await authApi.login(email, password)
      auth.save(data)
      navigate(from, { replace: true })
    } catch (err) {
      toast.error(apiError(err))
    }
  }

  async function onCounter({ businessLoginCode, counterCode, counterPassword }: CounterForm) {
    try {
      const { data } = await authApi.counterLogin(
        businessLoginCode.trim().toUpperCase(),
        counterCode.trim(),
        counterPassword,
      )
      auth.save(data)
      navigate(from, { replace: true })
    } catch (err) {
      toast.error(apiError(err))
    }
  }

  return (
    <div className="min-h-screen flex items-center justify-center bg-gray-50 px-4">
      <div className="w-full max-w-sm">
        {/* Logo */}
        <div className="flex justify-center mb-8">
          <div className="w-12 h-12 bg-blue-600 rounded-2xl flex items-center justify-center shadow-lg">
            <Receipt className="w-6 h-6 text-white" />
          </div>
        </div>

        <div className="bg-white rounded-2xl border border-gray-200 shadow-sm p-8">
          <h1 className="text-xl font-bold text-gray-900 mb-1">Welcome back</h1>
          <p className="text-sm text-gray-500 mb-5">Sign in to your account</p>

          {/* Mode toggle */}
          <div className="grid grid-cols-2 gap-1 p-1 bg-gray-100 rounded-lg mb-6" role="tablist">
            <button
              type="button"
              role="tab"
              aria-selected={mode === 'admin'}
              onClick={() => setMode('admin')}
              className={`py-2 text-sm font-medium rounded-md transition-colors ${
                mode === 'admin' ? 'bg-white shadow text-gray-900' : 'text-gray-500 hover:text-gray-700'
              }`}
            >
              Admin
            </button>
            <button
              type="button"
              role="tab"
              aria-selected={mode === 'counter'}
              onClick={() => setMode('counter')}
              className={`py-2 text-sm font-medium rounded-md transition-colors ${
                mode === 'counter' ? 'bg-white shadow text-gray-900' : 'text-gray-500 hover:text-gray-700'
              }`}
            >
              Counter
            </button>
          </div>

          {mode === 'admin' ? (
            <form onSubmit={admin.handleSubmit(onAdmin)} className="space-y-4" noValidate>
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">Email</label>
                <input type="email" {...admin.register('email')} placeholder="you@company.com" className={inputCls} />
                {admin.formState.errors.email && (
                  <p className="text-xs text-red-600 mt-1">{admin.formState.errors.email.message}</p>
                )}
              </div>
              <div>
                <div className="flex items-center justify-between mb-1">
                  <label className="block text-sm font-medium text-gray-700">Password</label>
                  <Link to="/forgot-password" className="text-xs text-blue-600 hover:underline font-medium">
                    Forgot password?
                  </Link>
                </div>
                <input type="password" {...admin.register('password')} placeholder="••••••••" className={inputCls} />
                {admin.formState.errors.password && (
                  <p className="text-xs text-red-600 mt-1">{admin.formState.errors.password.message}</p>
                )}
              </div>
              <button
                type="submit"
                disabled={admin.formState.isSubmitting}
                className="w-full flex justify-center items-center py-2.5 bg-blue-600 text-white rounded-lg text-sm font-medium hover:bg-blue-700 transition-colors disabled:opacity-60"
              >
                {admin.formState.isSubmitting ? 'Signing in…' : 'Sign in as admin'}
              </button>
            </form>
          ) : (
            <form onSubmit={counter.handleSubmit(onCounter)} className="space-y-4" noValidate>
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">Business code</label>
                <input
                  {...counter.register('businessLoginCode')}
                  placeholder="e.g. 7KP2QX"
                  autoCapitalize="characters"
                  className={`${inputCls} uppercase`}
                />
                <p className="text-xs text-gray-400 mt-1">
                  Ask your admin — it&apos;s shown on their Counters page.
                </p>
                {counter.formState.errors.businessLoginCode && (
                  <p className="text-xs text-red-600 mt-1">{counter.formState.errors.businessLoginCode.message}</p>
                )}
              </div>
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">Counter number</label>
                <input
                  {...counter.register('counterCode')}
                  placeholder="e.g. C2"
                  className={inputCls}
                />
                {counter.formState.errors.counterCode && (
                  <p className="text-xs text-red-600 mt-1">{counter.formState.errors.counterCode.message}</p>
                )}
              </div>
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">Counter password</label>
                <input
                  type="password"
                  {...counter.register('counterPassword')}
                  placeholder="••••••••"
                  className={inputCls}
                />
                {counter.formState.errors.counterPassword && (
                  <p className="text-xs text-red-600 mt-1">{counter.formState.errors.counterPassword.message}</p>
                )}
              </div>
              <button
                type="submit"
                disabled={counter.formState.isSubmitting}
                className="w-full flex justify-center items-center py-2.5 bg-blue-600 text-white rounded-lg text-sm font-medium hover:bg-blue-700 transition-colors disabled:opacity-60"
              >
                {counter.formState.isSubmitting ? 'Signing in…' : 'Sign in to counter'}
              </button>
            </form>
          )}
        </div>

        <p className="text-center text-sm text-gray-500 mt-4">
          No account?{' '}
          <Link to="/register" className="text-blue-600 font-medium hover:underline">
            Create one
          </Link>
        </p>
      </div>
    </div>
  )
}
