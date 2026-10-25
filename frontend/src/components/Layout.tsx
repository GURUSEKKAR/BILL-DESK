import { NavLink, useNavigate } from 'react-router-dom'
import {
  LayoutDashboard, Users as UsersIcon, FileText, LogOut, Receipt,
  Briefcase, AlertCircle, Store, UserCog,
} from 'lucide-react'
import { auth } from '../lib/auth'
import { queryClient } from '../lib/queryClient'

const OWNER_NAV = [
  { to: '/',          icon: LayoutDashboard, label: 'Dashboard'  },
  { to: '/customers', icon: UsersIcon,       label: 'Customers'  },
  { to: '/invoices',  icon: FileText,        label: 'Invoices'   },
  { to: '/counters',  icon: Store,           label: 'Counters'   },
  { to: '/cashiers',  icon: UserCog,         label: 'Cashiers'   },
  { to: '/jobs',      icon: AlertCircle,     label: 'Jobs'       },
]

const CASHIER_NAV = [
  { to: '/',          icon: LayoutDashboard, label: 'Dashboard'  },
  { to: '/customers', icon: UsersIcon,       label: 'Customers'  },
  { to: '/invoices',  icon: FileText,        label: 'Invoices'   },
]

export function Layout({ children }: { children: React.ReactNode }) {
  const navigate = useNavigate()
  const bizName = auth.getBusinessName() ?? 'My Business'
  const isOwner = auth.isOwner()
  const counterCode = auth.getCounterCode()
  const nav = isOwner ? OWNER_NAV : CASHIER_NAV

  function logout() {
    auth.clear()
    queryClient.clear()
    navigate('/login')
  }

  return (
    <div className="flex h-screen bg-gray-50 overflow-hidden">
      {/* Sidebar */}
      <aside className="w-56 flex flex-col bg-white border-r border-gray-200 shrink-0">
        {/* Logo */}
        <div className="h-14 flex items-center gap-2.5 px-4 border-b border-gray-100">
          <div className="w-7 h-7 bg-blue-600 rounded-lg flex items-center justify-center shrink-0">
            <Receipt className="w-3.5 h-3.5 text-white" />
          </div>
          <span className="font-bold text-gray-900 text-sm truncate">InvoiceApp</span>
        </div>

        {/* Business + role/counter */}
        <div className="px-4 py-3 border-b border-gray-100 space-y-1.5">
          <div className="flex items-center gap-2 text-xs text-gray-500">
            <Briefcase className="w-3 h-3 shrink-0" />
            <span className="truncate">{bizName}</span>
          </div>
          <span className={`inline-block text-[10px] font-semibold px-2 py-0.5 rounded-full ${
            isOwner ? 'bg-purple-50 text-purple-700' : 'bg-blue-50 text-blue-700'
          }`}>
            {isOwner ? 'OWNER' : `COUNTER ${counterCode ?? ''}`.trim()}
          </span>
        </div>

        {/* Nav */}
        <nav className="flex-1 p-2 space-y-0.5">
          {nav.map(({ to, icon: Icon, label }) => (
            <NavLink
              key={to}
              to={to}
              end={to === '/'}
              className={({ isActive }) =>
                `flex items-center gap-2.5 px-3 py-2 rounded-lg text-sm font-medium transition-colors ${
                  isActive
                    ? 'bg-blue-50 text-blue-700'
                    : 'text-gray-600 hover:bg-gray-50 hover:text-gray-900'
                }`
              }
            >
              <Icon className="w-4 h-4 shrink-0" />
              {label}
            </NavLink>
          ))}
        </nav>

        {/* Logout */}
        <div className="p-2 border-t border-gray-100">
          <button
            onClick={logout}
            className="w-full flex items-center gap-2.5 px-3 py-2 rounded-lg text-sm text-gray-500 hover:bg-gray-50 hover:text-gray-700 transition-colors"
          >
            <LogOut className="w-4 h-4 shrink-0" />
            Sign out
          </button>
        </div>
      </aside>

      {/* Page */}
      <main className="flex-1 overflow-y-auto">
        <div className="max-w-5xl mx-auto p-6">
          {children}
        </div>
      </main>
    </div>
  )
}
