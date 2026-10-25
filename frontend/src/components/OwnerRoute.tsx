import { Navigate } from 'react-router-dom'
import { auth } from '../lib/auth'

/**
 * Guards OWNER-only pages (Counters, Cashiers). A CASHIER session is bounced
 * back to the dashboard. The backend enforces this too (@PreAuthorize); this is
 * just so cashiers never see the screens.
 */
export function OwnerRoute({ children }: { children: React.ReactNode }) {
  if (!auth.isOwner()) return <Navigate to="/" replace />
  return <>{children}</>
}
