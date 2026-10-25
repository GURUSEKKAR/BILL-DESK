import { Navigate, useLocation } from 'react-router-dom'
import { auth } from '../lib/auth'

export function ProtectedRoute({ children }: { children: React.ReactNode }) {
  const location = useLocation()
  if (!auth.isLoggedIn()) {
    return <Navigate to="/login" state={{ from: location }} replace />
  }
  return <>{children}</>
}
