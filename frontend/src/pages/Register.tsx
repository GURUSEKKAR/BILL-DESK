import { useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { z } from 'zod'
import { Link, useNavigate } from 'react-router-dom'
import { Receipt } from 'lucide-react'
import toast from 'react-hot-toast'
import { authApi } from '../api'
import { auth } from '../lib/auth'
import { apiError } from '../lib/utils'

const schema = z.object({
  businessName: z.string().min(1, 'Business name is required'),
  businessCode: z.string()
    .regex(/^[A-Za-z0-9-]{3,20}$/, '3–20 letters, digits or hyphens')
    .or(z.literal(''))
    .optional(),
  email:        z.string().email('Enter a valid email'),
  password:     z.string().min(8, 'Password must be at least 8 characters'),
})
type FormData = z.infer<typeof schema>

export default function Register() {
  const navigate = useNavigate()
  const { register, handleSubmit, formState: { errors, isSubmitting } } = useForm<FormData>({
    resolver: zodResolver(schema),
  })

  async function onSubmit({ email, password, businessName, businessCode }: FormData) {
    try {
      const { data } = await authApi.register(
        email, password, businessName,
        businessCode ? businessCode.trim().toUpperCase() : undefined,
      )
      auth.save(data)
      toast.success(`Account created! Your business code is ${data.businessLoginCode}`)
      navigate('/')
    } catch (err) {
      toast.error(apiError(err))
    }
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
    <div className="min-h-screen flex items-center justify-center bg-gray-50 px-4">
      <div className="w-full max-w-sm">
        <div className="flex justify-center mb-8">
          <div className="w-12 h-12 bg-blue-600 rounded-2xl flex items-center justify-center shadow-lg">
            <Receipt className="w-6 h-6 text-white" />
          </div>
        </div>

        <div className="bg-white rounded-2xl border border-gray-200 shadow-sm p-8">
          <h1 className="text-xl font-bold text-gray-900 mb-1">Create account</h1>
          <p className="text-sm text-gray-500 mb-6">Start managing your invoices</p>

          <form onSubmit={handleSubmit(onSubmit)} className="space-y-4" noValidate>
            {field('Business name', 'businessName', { placeholder: 'Acme Ltd' })}
            <div>
              <label className="block text-sm font-medium text-gray-700 mb-1">
                Business code <span className="text-gray-400 font-normal">(optional)</span>
              </label>
              <input
                {...register('businessCode')}
                placeholder="e.g. ACME01 — leave blank to auto-generate"
                autoCapitalize="characters"
                className="w-full px-3 py-2 border border-gray-300 rounded-lg text-sm uppercase focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-transparent"
              />
              <p className="text-xs text-gray-400 mt-1">
                Your counters use this to sign in. Choose something memorable, or we&apos;ll make one for you.
              </p>
              {errors.businessCode && <p className="text-xs text-red-600 mt-1">{errors.businessCode.message}</p>}
            </div>
            {field('Email', 'email', { type: 'email', placeholder: 'you@company.com' })}
            {field('Password', 'password', { type: 'password', placeholder: 'Min 8 characters' })}
            <button
              type="submit"
              disabled={isSubmitting}
              className="w-full flex justify-center py-2.5 bg-blue-600 text-white rounded-lg text-sm font-medium hover:bg-blue-700 transition-colors disabled:opacity-60"
            >
              {isSubmitting ? 'Creating…' : 'Create account'}
            </button>
          </form>
        </div>

        <p className="text-center text-sm text-gray-500 mt-4">
          Already have an account?{' '}
          <Link to="/login" className="text-blue-600 font-medium hover:underline">Sign in</Link>
        </p>
      </div>
    </div>
  )
}
