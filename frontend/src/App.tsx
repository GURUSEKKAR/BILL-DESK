import { Routes, Route, Navigate } from 'react-router-dom'
import { Layout } from './components/Layout'
import { ProtectedRoute } from './components/ProtectedRoute'
import { OwnerRoute } from './components/OwnerRoute'
import Login from './pages/Login'
import Register from './pages/Register'
import ForgotPassword from './pages/ForgotPassword'
import ResetPassword from './pages/ResetPassword'
import Dashboard from './pages/Dashboard'
import Customers from './pages/Customers'
import Invoices from './pages/Invoices'
import InvoiceDetail from './pages/InvoiceDetail'
import NewInvoice from './pages/NewInvoice'
import Jobs from './pages/Jobs'
import PublicPay from './pages/PublicPay'
import Counters from './pages/Counters'
import Users from './pages/Users'

function AppShell({ children }: { children: React.ReactNode }) {
  return (
    <ProtectedRoute>
      <Layout>{children}</Layout>
    </ProtectedRoute>
  )
}

export default function App() {
  return (
    <Routes>
      {/* Public */}
      <Route path="/login"            element={<Login />} />
      <Route path="/register"         element={<Register />} />
      <Route path="/forgot-password"  element={<ForgotPassword />} />
      <Route path="/reset-password"   element={<ResetPassword />} />
      <Route path="/pay/:token"       element={<PublicPay />} />

      {/* Protected */}
      <Route path="/"              element={<AppShell><Dashboard /></AppShell>} />
      <Route path="/customers"     element={<AppShell><Customers /></AppShell>} />
      <Route path="/invoices"      element={<AppShell><Invoices /></AppShell>} />
      <Route path="/invoices/new"  element={<AppShell><NewInvoice /></AppShell>} />
      <Route path="/invoices/:id"  element={<AppShell><InvoiceDetail /></AppShell>} />
      <Route path="/jobs"          element={<AppShell><Jobs /></AppShell>} />

      {/* Owner-only */}
      <Route path="/counters" element={<AppShell><OwnerRoute><Counters /></OwnerRoute></AppShell>} />
      <Route path="/cashiers" element={<AppShell><OwnerRoute><Users /></OwnerRoute></AppShell>} />

      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  )
}
